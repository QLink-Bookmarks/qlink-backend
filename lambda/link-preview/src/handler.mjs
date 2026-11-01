import { S3Client } from '@aws-sdk/client-s3';
import { fetchSafe } from './network.mjs';
import { parsePage } from './metadata.mjs';
import { prepareImage } from './image.mjs';
import { faviconHash, findObject, previewHash, putImage } from './cache.mjs';
import { hqdefaultUrl, oembedUrl, parseOembed, youtubeVideoId } from './youtube.mjs';

const MAX_HTML_BYTES = 2_000_000;
const MAX_IMAGE_BYTES = 10_000_000;
const MAX_INLINE_FAVICON_BYTES = 4 * 1024 * 1024;
const MAX_OEMBED_BYTES = 100_000;
const MAX_CANDIDATES = 10;
const YOUTUBE_PLACEHOLDER_TITLE = '- YouTube';
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

  async function findFresh(kind, hash, traceId) {
    const existing = await findObject(s3, bucket, environment, kind, hash, now());
    if (existing?.fresh) log('LINK_PREVIEW_S3', { traceId, action: 'HIT', kind, key: existing.key });
    return existing;
  }

  async function storeFirst(candidates, kind, hash, existing, traceId) {
    for (const candidate of candidates) {
      try {
        return await processCandidate(candidate, kind, hash, existing, traceId);
      } catch (error) {
        log('LINK_PREVIEW_CANDIDATE', { traceId, kind, outcome: 'FAILED', cause: error?.name || 'Error' });
      }
    }
    return null;
  }

  async function findImage(candidates, kind, hash, traceId) {
    if (candidates.length === 0) return null;
    const existing = await findFresh(kind, hash, traceId);
    if (existing?.fresh) return existing.key;
    return await storeFirst(candidates.slice(0, MAX_CANDIDATES), kind, hash, existing, traceId) || existing?.key || null;
  }

  async function readPage(url, mode, traceId) {
    const page = await fetcher(url, {
      maxBytes: MAX_HTML_BYTES,
      truncate: true,
      onRequest: (entry) => log('LINK_PREVIEW_EXTERNAL', { traceId, kind: 'page', ...entry }),
    });
    if (!page.contentType?.toLowerCase().startsWith('text/html')) throw new Error('page is not html');
    return parser(page.body.toString('utf8'), page.finalUrl, mode);
  }

  async function readOembed(videoId, traceId) {
    try {
      const response = await fetcher(oembedUrl(videoId), {
        maxBytes: MAX_OEMBED_BYTES,
        onRequest: (entry) => log('LINK_PREVIEW_EXTERNAL', { traceId, kind: 'oembed', ...entry }),
      });
      return parseOembed(response);
    } catch (error) {
      log('LINK_PREVIEW_CANDIDATE', { traceId, kind: 'oembed', outcome: 'FAILED', cause: error?.name || 'Error' });
      return { title: null, thumbnailUrl: null };
    }
  }

  // Order: oEmbed thumbnail, page images, hqdefault. hqdefault is always last and always within the limit.
  function youtubeCandidates(thumbnailUrl, pageImageUrls, hqdefault, tried) {
    const front = [...new Set([thumbnailUrl, ...pageImageUrls].filter(Boolean))]
      .filter((url) => url !== hqdefault && !tried.includes(url));
    if (tried.includes(hqdefault)) return front.slice(0, MAX_CANDIDATES);
    return [...front.slice(0, MAX_CANDIDATES - 1), hqdefault];
  }

  async function previewYoutube(event, videoId, traceId) {
    const oembed = await readOembed(videoId, traceId);
    const hash = previewHash(event.url);
    const hqdefault = hqdefaultUrl(videoId);
    const existing = await findFresh('preview', hash, traceId);
    let tried = [];
    if (oembed.title && oembed.thumbnailUrl) {
      tried = [...new Set([oembed.thumbnailUrl, hqdefault])];
      const thumbnailKey = existing?.fresh ? existing.key : await storeFirst(tried, 'preview', hash, existing, traceId);
      if (thumbnailKey) return { title: oembed.title, thumbnailKey, faviconKey: null, isVideo: true };
    }
    let metadata = { title: null, imageUrls: [], faviconUrls: [] };
    try {
      metadata = await readPage(event.url, 'PREVIEW', traceId);
    } catch (error) {
      log('LINK_PREVIEW_CANDIDATE', { traceId, kind: 'page', outcome: 'FAILED', cause: error?.name || 'Error' });
    }
    const pageTitle = metadata.title?.trim() === YOUTUBE_PLACEHOLDER_TITLE ? null : metadata.title;
    const title = oembed.title || pageTitle || null;
    let thumbnailKey = existing?.fresh ? existing.key : null;
    if (!thumbnailKey) {
      const candidates = youtubeCandidates(oembed.thumbnailUrl, metadata.imageUrls, hqdefault, tried);
      thumbnailKey = await storeFirst(candidates, 'preview', hash, existing, traceId) || existing?.key || null;
    }
    if (thumbnailKey) return { title, thumbnailKey, faviconKey: null, isVideo: true };
    const faviconKey = await findImage(metadata.faviconUrls, 'favicon', faviconHash(event.url), traceId);
    return { title, thumbnailKey: null, faviconKey, isVideo: true };
  }

  function outcome(result) {
    if (result.thumbnailKey) return 'THUMBNAIL';
    return result.faviconKey ? 'FAVICON' : 'EMPTY';
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
      const videoId = mode === 'PREVIEW' ? youtubeVideoId(event.url) : null;
      if (videoId) {
        const result = await previewYoutube(event, videoId, traceId);
        log('LINK_PREVIEW_RESULT', { traceId, mode, outcome: outcome(result), durationMs: now() - started });
        return result;
      }
      const metadata = await readPage(event.url, mode, traceId);
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
