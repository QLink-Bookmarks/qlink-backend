import { S3Client } from '@aws-sdk/client-s3';
import { fetchSafe } from './network.mjs';
import { parsePage } from './metadata.mjs';
import { prepareImage } from './image.mjs';
import { faviconHash, findObject, previewHash, putImage } from './cache.mjs';

const MAX_HTML_BYTES = 1_000_000;
const MAX_IMAGE_BYTES = 10_000_000;
const MAX_INLINE_FAVICON_BYTES = 4 * 1024 * 1024;
const EMPTY = Object.freeze({ title: null, thumbnailKey: null, faviconKey: null, isVideo: false });

export function createHandler({
  fetcher = fetchSafe,
  parser = parsePage,
  imageProcessor = prepareImage,
  s3 = new S3Client({}),
  now = Date.now,
  logger = console,
  environment = process.env.APP_ENV,
  bucket = process.env.PREVIEW_BUCKET,
} = {}) {
  if (!['dev', 'prod'].includes(environment) || bucket !== `alink-previews-${environment}`) {
    throw new Error('invalid preview deployment configuration');
  }
  const log = (event, detail) => logger.log(JSON.stringify({ event, ...detail }));

  async function processCandidate(url, kind, hash, existing, traceId) {
    const response = await fetcher(url, {
      maxBytes: MAX_IMAGE_BYTES,
      onRequest: (entry) => log('LINK_PREVIEW_EXTERNAL', { traceId, kind, ...entry }),
    });
    const image = await imageProcessor(response.body, kind);
    const key = await putImage(s3, bucket, environment, kind, hash, image, existing);
    log('LINK_PREVIEW_S3', { traceId, action: 'PUT', kind, key });
    return key;
  }

  async function findImage(candidates, kind, hash, traceId) {
    if (candidates.length === 0) return null;
    const existing = await findObject(s3, bucket, environment, kind, hash, now());
    if (existing?.fresh) {
      log('LINK_PREVIEW_S3', { traceId, action: 'HIT', kind, key: existing.key });
      return existing.key;
    }
    for (const candidate of candidates.slice(0, 10)) {
      try {
        return await processCandidate(candidate, kind, hash, existing, traceId);
      } catch (error) {
        log('LINK_PREVIEW_CANDIDATE', { traceId, kind, outcome: 'FAILED', cause: error?.name || 'Error' });
      }
    }
    return existing?.key || null;
  }

  async function findInlineFavicon(bytes, hash, traceId) {
    const existing = await findObject(s3, bucket, environment, 'favicon', hash, now());
    if (existing?.fresh) {
      log('LINK_PREVIEW_S3', { traceId, action: 'HIT', kind: 'favicon', key: existing.key });
      return existing.key;
    }
    try {
      const image = await imageProcessor(bytes, 'favicon');
      const key = await putImage(s3, bucket, environment, 'favicon', hash, image, existing);
      log('LINK_PREVIEW_S3', { traceId, action: 'PUT', kind: 'favicon', key });
      return key;
    } catch (error) {
      log('LINK_PREVIEW_CANDIDATE', { traceId, kind: 'favicon', outcome: 'FAILED', cause: error?.name || 'Error' });
      return existing?.key || null;
    }
  }

  function decodeFavicon(input) {
    if (typeof input?.base64 !== 'string' || input.base64.length > Math.ceil(MAX_INLINE_FAVICON_BYTES / 3) * 4 ||
        input.base64.length % 4 !== 0 || !/^[A-Za-z0-9+/]*={0,2}$/.test(input.base64)) {
      throw new Error('invalid inline favicon');
    }
    const bytes = Buffer.from(input.base64, 'base64');
    if (bytes.length === 0 || bytes.length > MAX_INLINE_FAVICON_BYTES || bytes.toString('base64') !== input.base64) {
      throw new Error('invalid inline favicon');
    }
    return bytes;
  }

  return async function handler(event) {
    const started = now();
    const traceId = /^[a-zA-Z0-9-]{1,64}$/.test(event?.traceId || '') ? event.traceId : 'untracked';
    const mode = event?.mode;
    try {
      if (!['PREVIEW', 'FAVICON_ONLY'].includes(mode) || typeof event?.url !== 'string') {
        throw new Error('invalid event');
      }
      if (event.favicon != null) {
        if (mode !== 'FAVICON_ONLY') throw new Error('inline favicon requires favicon mode');
        const url = new URL(event.url);
        if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || url.username || url.password) {
          throw new Error('invalid favicon url');
        }
        const bytes = decodeFavicon(event.favicon);
        const faviconKey = await findInlineFavicon(bytes, faviconHash(event.url), traceId);
        log('LINK_PREVIEW_RESULT', { traceId, mode, outcome: faviconKey ? 'FAVICON' : 'EMPTY', durationMs: now() - started });
        return { ...EMPTY, faviconKey };
      }
      const page = await fetcher(event.url, {
        maxBytes: MAX_HTML_BYTES,
        onRequest: (entry) => log('LINK_PREVIEW_EXTERNAL', { traceId, kind: 'page', ...entry }),
      });
      if (!page.contentType?.toLowerCase().startsWith('text/html')) throw new Error('page is not html');
      const metadata = await parser(page.body.toString('utf8'), page.finalUrl, mode);
      const base = { title: metadata.title, isVideo: metadata.isVideo };
      if (mode === 'PREVIEW') {
        const thumbnailKey = await findImage(metadata.imageUrls, 'preview', previewHash(event.url), traceId);
        if (thumbnailKey) {
          log('LINK_PREVIEW_RESULT', { traceId, mode, outcome: 'THUMBNAIL', durationMs: now() - started });
          return { ...base, thumbnailKey, faviconKey: null };
        }
      }
      const faviconKey = await findImage(metadata.faviconUrls, 'favicon', faviconHash(event.url), traceId);
      log('LINK_PREVIEW_RESULT', { traceId, mode, outcome: faviconKey ? 'FAVICON' : 'EMPTY', durationMs: now() - started });
      return { ...base, thumbnailKey: null, faviconKey };
    } catch (error) {
      log('LINK_PREVIEW_RESULT', { traceId, mode, outcome: 'FAILED', cause: error?.name || 'Error', durationMs: now() - started });
      return { ...EMPTY };
    }
  };
}
