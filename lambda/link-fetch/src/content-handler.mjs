import { fetchSafe, UpstreamStatusError } from './network.mjs';
import { decodeHtml, extractContent, isRobotsHeaderDenied } from './content-extract.mjs';

const MAX_HTML_BYTES = 2_000_000;
const MAX_DEADLINE_MS = 20_000;
// Time kept back from the Lambda timeout to build and return the response.
const RESPONSE_RESERVE_MS = 5_000;
const BLOCKING_CODES = new Set(['LOGIN_REQUIRED', 'PAYWALL', 'BOT_WALL', 'ACCESS_DENIED', 'ROBOTS_NOINDEX']);
const TRANSIENT_NETWORK_CODES = new Set([
  'ECONNRESET', 'ECONNREFUSED', 'ECONNABORTED', 'EPIPE', 'ETIMEDOUT', 'EAI_AGAIN', 'EHOSTUNREACH', 'ENETUNREACH',
  'ETIMEOUT', 'ESERVFAIL', 'EREFUSED',
]);
const NOT_FOUND_HOST_CODES = new Set(['ENOTFOUND', 'ENODATA', 'EBADNAME']);
const REQUEST_HEADERS = {
  accept: 'text/html,application/xhtml+xml;q=0.9,*/*;q=0.1',
  'accept-language': 'ko-KR,ko;q=0.9,en;q=0.8',
};
const EMPTY_PAGE = Object.freeze({
  finalUrl: null, canonicalUrl: null, title: null, siteName: null, author: null, publishedAt: null, language: null,
  text: null, truncation: 'NONE',
});

function statusCode(status) {
  if (status === 401) return 'LOGIN_REQUIRED';
  if (status === 403) return 'ACCESS_DENIED';
  if (status === 404 || status === 410) return 'UPSTREAM_NOT_FOUND';
  if (status === 429) return 'UPSTREAM_RATE_LIMITED';
  if (status >= 500 && status <= 599) return 'UPSTREAM_SERVER_ERROR';
  return 'UPSTREAM_OTHER_STATUS';
}

function fetchFailureCode(error, deadlineSignal) {
  if (error instanceof UpstreamStatusError) return statusCode(error.status);
  if (error?.reason === 'TIMEOUT' || deadlineSignal.aborted) return 'FETCH_TIMEOUT';
  if (error?.reason === 'REDIRECTS') return 'TOO_MANY_REDIRECTS';
  if (error?.reason === 'DENIED') return 'TARGET_DENIED';
  if (NOT_FOUND_HOST_CODES.has(error?.code)) return 'UPSTREAM_NOT_FOUND';
  if (TRANSIENT_NETWORK_CODES.has(error?.code)) return 'FETCH_TIMEOUT';
  return 'EXTRACTION_FAILED';
}

function validUrl(value) {
  if (typeof value !== 'string') return null;
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) && url.hostname ? url : null;
  } catch {
    return null;
  }
}

export function deadlineFor(event, context) {
  const requested = Number.isFinite(event?.deadlineMs) && event.deadlineMs > 0 ? event.deadlineMs : MAX_DEADLINE_MS;
  const remaining = typeof context?.getRemainingTimeInMillis === 'function'
    ? context.getRemainingTimeInMillis() - RESPONSE_RESERVE_MS
    : MAX_DEADLINE_MS;
  return Math.max(0, Math.min(requested, remaining, MAX_DEADLINE_MS));
}

const isHtml = (contentType) => /^\s*(text\/html|application\/xhtml\+xml)\b/i.test(contentType || '');

export function createContentHandler({
  fetcher = fetchSafe,
  now = Date.now,
  logger = console,
  environment = process.env.APP_ENV,
} = {}) {
  if (!['dev', 'prod'].includes(environment)) throw new Error('invalid content deployment configuration');
  const log = (event, detail) => logger.log(JSON.stringify({ event, ...detail }));

  async function collect(url, traceId, deadlineMs) {
    const deadlineSignal = AbortSignal.timeout(deadlineMs);
    let page;
    try {
      page = await fetcher(url.href, {
        maxBytes: MAX_HTML_BYTES,
        truncate: true,
        timeoutMs: deadlineMs,
        deadlineSignal,
        headers: REQUEST_HEADERS,
        wantHeaders: ['x-robots-tag'],
        onRequest: (entry) => log('LINK_CONTENT_EXTERNAL', { traceId, ...entry }),
      });
    } catch (error) {
      return {
        code: fetchFailureCode(error, deadlineSignal),
        upstreamStatus: error instanceof UpstreamStatusError ? error.status : null,
        retryAfterSeconds: error instanceof UpstreamStatusError ? error.retryAfterSeconds : null,
      };
    }
    const found = { upstreamStatus: page.status ?? null, finalUrl: page.finalUrl };
    if (!isHtml(page.contentType)) return { ...found, code: 'NOT_HTML' };
    if (isRobotsHeaderDenied(page.headers?.['x-robots-tag'])) return { ...found, code: 'ROBOTS_NOINDEX' };
    const html = decodeHtml(page.body, { contentType: page.contentType, isBodyTruncated: page.isBodyTruncated });
    if (deadlineSignal.aborted) return { ...found, code: 'FETCH_TIMEOUT' };
    const extracted = extractContent(html, { finalUrl: page.finalUrl });
    if (extracted.code) return { ...found, code: extracted.code };
    const truncation = extracted.isTextTruncated ? 'TEXT' : page.isBodyTruncated ? 'HTML' : 'NONE';
    return { ...found, ...extracted.meta, text: extracted.text, truncation };
  }

  return async function handler(event, context) {
    const started = now();
    const traceId = /^[a-zA-Z0-9-]{1,64}$/.test(event?.traceId || '') ? event.traceId : 'untracked';
    const url = validUrl(event?.url);
    let result;
    let cause;
    if (!url) {
      result = { code: 'INVALID_EVENT' };
    } else {
      try {
        result = await collect(url, traceId, deadlineFor(event, context));
      } catch (error) {
        cause = error?.name || 'Error';
        result = { code: 'EXTRACTION_FAILED' };
      }
    }
    const code = result.code ?? null;
    const response = {
      outcome: code == null ? 'COLLECTED' : BLOCKING_CODES.has(code) ? 'BLOCKED' : 'FAILED',
      code,
      upstreamStatus: result.upstreamStatus ?? null,
      retryAfterSeconds: result.retryAfterSeconds ?? null,
      ...EMPTY_PAGE,
      ...(code == null ? result : { finalUrl: result.finalUrl ?? null }),
      durationMs: now() - started,
    };
    log('LINK_CONTENT_RESULT', {
      traceId,
      outcome: response.outcome,
      code,
      upstreamStatus: response.upstreamStatus,
      textLength: response.text?.length ?? 0,
      truncation: response.truncation,
      durationMs: response.durationMs,
      ...(cause ? { cause } : {}),
    });
    return response;
  };
}
