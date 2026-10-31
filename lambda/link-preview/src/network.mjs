import dns from 'node:dns/promises';
import http from 'node:http';
import https from 'node:https';
import net from 'node:net';
import ipaddr from 'ipaddr.js';

const MAX_REDIRECTS = 3;
const REQUEST_TIMEOUT_MS = 5_000;

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
    throw new Error('target protocol or port denied');
  }
  if (url.username || url.password) throw new Error('target credentials denied');

  const host = url.hostname.replace(/^\[|\]$/g, '');
  const addresses = net.isIP(host)
    ? [{ address: host, family: net.isIP(host) }]
    : await resolveHost(host);
  if (!Array.isArray(addresses) || addresses.length === 0 ||
      addresses.some(({ address }) => !isPublicIp(address))) {
    throw new Error('target address denied');
  }
  return { url, address: addresses[0].address, family: addresses[0].family };
}

export async function fetchSafe(input, {
  maxBytes,
  resolveHost,
  requestOnce = readOnce,
  onRequest = () => {},
  timeoutMs = REQUEST_TIMEOUT_MS,
} = {}) {
  let current = new URL(input);
  for (let redirects = 0; redirects <= MAX_REDIRECTS; redirects += 1) {
    let target;
    try {
      target = await validateTarget(current, resolveHost);
    } catch (error) {
      onRequest({ host: current.hostname, status: 'BLOCKED', durationMs: 0 });
      throw error;
    }
    const started = Date.now();
    try {
      const response = await requestOnce(target, { maxBytes, timeoutMs });
      onRequest({ host: current.hostname, status: response.status, durationMs: Date.now() - started });
      if (response.status >= 300 && response.status < 400 && response.location) {
        if (redirects === MAX_REDIRECTS) throw new Error('too many redirects');
        current = new URL(response.location, current);
        continue;
      }
      if (response.status < 200 || response.status >= 300) throw new Error('upstream status');
      if (response.contentEncoding && response.contentEncoding !== 'identity') {
        throw new Error('compressed response denied');
      }
      return { ...response, finalUrl: current.href };
    } catch (error) {
      onRequest({ host: current.hostname, status: 'FAILED', durationMs: Date.now() - started });
      throw error;
    }
  }
  throw new Error('too many redirects');
}

export function readOnce({ url, address, family }, { maxBytes, timeoutMs }) {
  return new Promise((resolve, reject) => {
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
      const contentLength = Number(response.headers['content-length']);
      if (Number.isFinite(contentLength) && contentLength > maxBytes) {
        response.destroy();
        reject(new Error('response too large'));
        return;
      }
      const chunks = [];
      let length = 0;
      response.on('data', (chunk) => {
        length += chunk.length;
        if (length > maxBytes) {
          response.destroy(new Error('response too large'));
          return;
        }
        chunks.push(chunk);
      });
      response.on('end', () => resolve({
        status,
        body: Buffer.concat(chunks),
        contentType: String(response.headers['content-type'] || ''),
        contentEncoding: String(response.headers['content-encoding'] || ''),
      }));
      response.on('error', reject);
    });
    request.setTimeout(timeoutMs, () => request.destroy(new Error('request timed out')));
    request.on('error', reject);
    request.end();
  });
}
