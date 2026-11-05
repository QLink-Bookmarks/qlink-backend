import dns from 'node:dns/promises';
import http from 'node:http';
import https from 'node:https';
import net from 'node:net';
import ipaddr from 'ipaddr.js';

const MAX_REDIRECTS = 3;
const REQUEST_TIMEOUT_MS = 5_000;
const ALLOWED_REQUEST_HEADERS = ['accept', 'accept-language'];
const ALLOWED_RESPONSE_HEADERS = ['content-type', 'content-encoding', 'x-robots-tag', 'retry-after'];

// `reason` lets callers classify a failure without parsing messages: TIMEOUT, REDIRECTS or DENIED.
export class FetchError extends Error {
  constructor(message, reason) {
    super(message);
    this.reason = reason;
  }
}

// A final non-2xx response. The body of an error response is never read.
export class UpstreamStatusError extends Error {
  constructor(status, retryAfterSeconds = null) {
    super('upstream status');
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export function isPublicIp(address) {
  try {
    return ipaddr.process(address).range() === 'unicast';
  } catch {
    return false;
  }
}

export async function validateTarget(input, resolveHost = (host) => dns.lookup(host, { all: true })) {
  const url = input instanceof URL ? input : new URL(input);
  const port = url.port || (url.protocol === 'https:' ? '443' : '80');
  if (!['http:', 'https:'].includes(url.protocol) || !['80', '443'].includes(port)) {
    throw new FetchError('target protocol or port denied', 'DENIED');
  }
  if (url.username || url.password) throw new FetchError('target credentials denied', 'DENIED');

  const host = url.hostname.replace(/^\[|\]$/g, '');
  const addresses = net.isIP(host)
    ? [{ address: host, family: net.isIP(host) }]
    : await resolveHost(host);
  if (!Array.isArray(addresses) || addresses.length === 0 ||
      addresses.some(({ address }) => !isPublicIp(address))) {
    throw new FetchError('target address denied', 'DENIED');
  }
  return { url, address: addresses[0].address, family: addresses[0].family };
}

// Seconds from a Retry-After header (delta-seconds or HTTP-date), or null when absent or unreadable.
export function parseRetryAfter(value, now = Date.now()) {
  if (typeof value !== 'string' || value.trim() === '') return null;
  const text = value.trim();
  if (/^\d+$/.test(text)) return Number(text);
  const date = Date.parse(text);
  return Number.isNaN(date) ? null : Math.max(0, Math.ceil((date - now) / 1000));
}

const timeoutError = () => new FetchError('request timed out', 'TIMEOUT');

function abortable(promise, signal) {
  if (!signal) return promise;
  if (signal.aborted) return Promise.reject(timeoutError());
  let onAbort;
  const aborted = new Promise((_resolve, reject) => {
    onAbort = () => reject(timeoutError());
    signal.addEventListener('abort', onAbort, { once: true });
  });
  return Promise.race([promise, aborted]).finally(() => signal.removeEventListener('abort', onAbort));
}

const pick = (source, names) => Object.fromEntries(
  names.filter((name) => source[name] != null).map((name) => [name, String(source[name])]),
);

// Repeated response headers are joined one per line.
const pickLines = (source, names) => Object.fromEntries(
  names.filter((name) => source[name] != null).map((name) => [name, source[name].join('\n')]),
);

// Options added for the content function (`deadlineSignal`, `headers`, `wantHeaders`) are opt-in; without them a call
// behaves as before.
export async function fetchSafe(input, {
  maxBytes,
  truncate = false,
  resolveHost,
  requestOnce = readOnce,
  onRequest = () => {},
  timeoutMs = REQUEST_TIMEOUT_MS,
  deadlineSignal,
  headers,
  wantHeaders,
} = {}) {
  const requestHeaders = headers ? pick(headers, ALLOWED_REQUEST_HEADERS) : undefined;
  const responseHeaders = wantHeaders?.filter((name) => ALLOWED_RESPONSE_HEADERS.includes(name));
  let current = new URL(input);
  for (let redirects = 0; redirects <= MAX_REDIRECTS; redirects += 1) {
    let target;
    try {
      // DNS cannot be cancelled, so the race only stops the wait; the signal is checked again after it.
      target = await abortable(validateTarget(current, resolveHost), deadlineSignal);
      if (deadlineSignal?.aborted) throw timeoutError();
    } catch (error) {
      onRequest({ host: current.hostname, status: error?.reason === 'DENIED' ? 'BLOCKED' : 'FAILED', durationMs: 0 });
      throw error;
    }
    const started = Date.now();
    try {
      const response = await requestOnce(target, {
        maxBytes, timeoutMs, truncate, signal: deadlineSignal, headers: requestHeaders, wantHeaders: responseHeaders,
      });
      onRequest({ host: current.hostname, status: response.status, durationMs: Date.now() - started });
      if (response.status >= 300 && response.status < 400 && response.location) {
        if (redirects === MAX_REDIRECTS) throw new FetchError('too many redirects', 'REDIRECTS');
        current = new URL(response.location, current);
        continue;
      }
      if (response.status < 200 || response.status >= 300) {
        throw new UpstreamStatusError(response.status, parseRetryAfter(response.retryAfter));
      }
      if (response.contentEncoding && response.contentEncoding !== 'identity') {
        throw new Error('compressed response denied');
      }
      return { ...response, finalUrl: current.href };
    } catch (error) {
      onRequest({ host: current.hostname, status: 'FAILED', durationMs: Date.now() - started });
      throw error;
    }
  }
  throw new FetchError('too many redirects', 'REDIRECTS');
}

// With `truncate`, a body over `maxBytes` is cut at the limit instead of failing the request.
// `signal` aborts the connection, the wait and the body transfer; `timeoutMs` is the socket idle limit of one request.
export function readOnce({ url, address, family }, {
  maxBytes, timeoutMs, truncate = false, signal, headers, wantHeaders,
}) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(timeoutError());
      return;
    }
    const transport = url.protocol === 'https:' ? https : http;
    const request = transport.request(url, {
      method: 'GET',
      agent: false,
      lookup: (_hostname, options, callback) => {
        if (options.all) callback(null, [{ address, family }]);
        else callback(null, address, family);
      },
      headers: {
        accept: 'text/html,image/*;q=0.8,*/*;q=0.1',
        ...headers,
        'accept-encoding': 'identity',
        'user-agent': 'ALinkPreviewBot/1.0',
      },
    }, (response) => {
      const status = response.statusCode || 0;
      const location = response.headers.location;
      if (status >= 300 && status < 400) {
        response.resume();
        resolve({ status, location });
        return;
      }
      if (status < 200 || status >= 300) {
        response.destroy();
        resolve({ status, retryAfter: response.headers['retry-after'] });
        return;
      }
      const contentLength = Number(response.headers['content-length']);
      if (!truncate && Number.isFinite(contentLength) && contentLength > maxBytes) {
        response.destroy();
        reject(new Error('response too large'));
        return;
      }
      const chunks = [];
      let length = 0;
      let isBodyTruncated = false;
      const complete = () => resolve({
        status,
        body: Buffer.concat(chunks),
        contentType: String(response.headers['content-type'] || ''),
        contentEncoding: String(response.headers['content-encoding'] || ''),
        isBodyTruncated,
        ...(wantHeaders ? { headers: pickLines(response.headersDistinct, wantHeaders) } : {}),
      });
      response.on('data', (chunk) => {
        if (length + chunk.length > maxBytes) {
          if (!truncate) {
            response.destroy(new Error('response too large'));
            return;
          }
          chunks.push(chunk.subarray(0, maxBytes - length));
          length = maxBytes;
          isBodyTruncated = true;
          response.removeAllListeners('data');
          response.destroy();
          complete();
          return;
        }
        length += chunk.length;
        chunks.push(chunk);
      });
      response.on('end', complete);
      response.on('error', reject);
    });
    const onAbort = () => {
      reject(timeoutError());
      request.destroy(timeoutError());
    };
    signal?.addEventListener('abort', onAbort, { once: true });
    request.on('close', () => signal?.removeEventListener('abort', onAbort));
    request.setTimeout(timeoutMs, () => request.destroy(timeoutError()));
    request.on('error', reject);
    request.end();
  });
}
