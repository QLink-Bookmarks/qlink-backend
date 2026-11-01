import test from 'node:test';
import assert from 'node:assert/strict';
import { HeadObjectCommand } from '@aws-sdk/client-s3';
import { fetchSafe, readOnce } from '../src/network.mjs';
import { faviconHash, previewHash } from '../src/cache.mjs';
import { createHandler } from '../src/handler.mjs';
import { oembedUrl, parseOembed, youtubeVideoId } from '../src/youtube.mjs';
import { FakeS3, localTarget, png, withServer } from './support.mjs';

const ID = 'RRjy2B-bDs0';
const WATCH = `https://www.youtube.com/watch?v=${ID}`;
const OEMBED = `https://www.youtube.com/oembed?url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D${ID}&format=json`;
const HQDEFAULT = `https://i.ytimg.com/vi/${ID}/hqdefault.jpg`;
const MAXRES = `https://i.ytimg.com/vi/${ID}/maxresdefault.jpg`;
const OG_IMAGE = 'https://cdn.example.com/og.png';
const ICON = 'https://www.youtube.com/icon.png';
const PAGE_HEAD = `<meta property="og:title" content="Page title"><meta property="og:image" content="${OG_IMAGE}"><link rel="icon" href="/icon.png">`;

const thumbnailKey = (url = WATCH) => `dev-preview-${previewHash(url)}.png`;
const faviconKey = (url = WATCH) => `dev-favicon-${faviconHash(url)}.png`;
const resultEntry = (entries) => entries.find((entry) => entry.event === 'LINK_PREVIEW_RESULT');

// ---------------------------------------------------------------- youtubeVideoId

test('youtubeVideoId accepts watch URLs on the three YouTube hosts and ignores other query and fragment', () => {
  for (const url of [
    `https://www.youtube.com/watch?v=${ID}&t=42s`,
    `https://youtube.com/watch?list=PL1234567890&v=${ID}`,
    `https://m.youtube.com/watch?v=${ID}&si=share-token#t=10`,
    `https://www.youtube.com/watch/?v=${ID}&feature=youtu.be`,
  ]) {
    assert.equal(youtubeVideoId(url), ID, url);
  }
});

test('youtubeVideoId accepts shorts, live, embed and youtu.be paths with an optional trailing slash', () => {
  for (const path of ['shorts', 'live', 'embed']) {
    for (const host of ['www.youtube.com', 'youtube.com', 'm.youtube.com']) {
      assert.equal(youtubeVideoId(`https://${host}/${path}/${ID}`), ID);
      assert.equal(youtubeVideoId(`https://${host}/${path}/${ID}/?si=share#top`), ID);
    }
  }
  assert.equal(youtubeVideoId(`https://youtu.be/${ID}`), ID);
  assert.equal(youtubeVideoId(`https://youtu.be/${ID}/?si=share&t=3`), ID);
});

test('youtubeVideoId accepts http and upper-case hosts', () => {
  assert.equal(youtubeVideoId(`http://www.youtube.com/watch?v=${ID}`), ID);
  assert.equal(youtubeVideoId(`https://WWW.YouTube.COM/watch?v=${ID}`), ID);
  assert.equal(youtubeVideoId(`HTTP://YOUTU.BE/${ID}`), ID);
});

test('youtubeVideoId rejects ids that are not eleven allowed characters', () => {
  for (const id of [ID.slice(0, 10), `${ID}A`, `${ID.slice(0, 10)}!`, `${ID.slice(0, 10)}.`, `${ID.slice(0, 10)}%21`, `${ID.slice(0, 10)}=`]) {
    assert.equal(youtubeVideoId(`https://www.youtube.com/watch?v=${id}`), null, id);
    assert.equal(youtubeVideoId(`https://www.youtube.com/shorts/${id}`), null, id);
    assert.equal(youtubeVideoId(`https://youtu.be/${id}`), null, id);
  }
});

test('youtubeVideoId rejects other hosts, including look-alikes and other YouTube subdomains', () => {
  for (const url of [
    `https://music.youtube.com/watch?v=${ID}`,
    `https://www.youtube-nocookie.com/embed/${ID}`,
    `https://youtube.com.evil.example/watch?v=${ID}`,
    `https://notyoutube.com/watch?v=${ID}`,
    `https://gaming.youtube.com/watch?v=${ID}`,
    `https://example.com/watch?v=${ID}`,
    `https://www.youtu.be/${ID}`,
  ]) {
    assert.equal(youtubeVideoId(url), null, url);
  }
});

test('youtubeVideoId rejects watch without v, channel, playlist, search and handle paths and a bare youtu.be', () => {
  for (const url of [
    'https://www.youtube.com/watch',
    'https://www.youtube.com/watch?v=',
    `https://www.youtube.com/watch?list=${ID}`,
    `https://www.youtube.com/watch/${ID}`,
    'https://www.youtube.com/channel/UC1234567890abcdefghij',
    'https://www.youtube.com/playlist?list=PL1234567890',
    'https://www.youtube.com/results?search_query=music',
    'https://www.youtube.com/@handle',
    `https://www.youtube.com/@${ID}`,
    `https://www.youtube.com/shorts/${ID}/extra`,
    'https://www.youtube.com/shorts/',
    'https://youtu.be/',
    'https://youtu.be',
    `https://youtu.be/${ID}/extra`,
  ]) {
    assert.equal(youtubeVideoId(url), null, url);
  }
});

test('youtubeVideoId rejects explicit ports, credentials, other protocols and non-URLs', () => {
  for (const url of [
    `https://www.youtube.com:8443/watch?v=${ID}`,
    `https://www.youtube.com:443/watch?v=${ID}`,
    `http://youtu.be:80/${ID}`,
    `https://user:pass@www.youtube.com/watch?v=${ID}`,
    `https://user@youtu.be/${ID}`,
    `https://@youtu.be/${ID}`,
    `ftp://www.youtube.com/watch?v=${ID}`,
    `ws://www.youtube.com/watch?v=${ID}`,
    `www.youtube.com/watch?v=${ID}`,
    'not a url',
    '',
    null,
    undefined,
    42,
  ]) {
    assert.equal(youtubeVideoId(url), null, String(url));
  }
});

// ---------------------------------------------------------------- parseOembed

const oembedResponse = (data, { contentType = 'application/json; charset=utf-8', finalUrl = OEMBED } = {}) => ({
  body: Buffer.from(typeof data === 'string' ? data : JSON.stringify(data)),
  contentType,
  finalUrl,
});

test('parseOembed returns a trimmed title bounded to 300 characters and an i.ytimg.com thumbnail', () => {
  const parsed = parseOembed(oembedResponse({ title: `  ${'가'.repeat(310)}  `, thumbnail_url: HQDEFAULT, author_name: 'x' }));
  assert.deepEqual(parsed, { title: '가'.repeat(300), thumbnailUrl: HQDEFAULT });
  assert.deepEqual(parseOembed(oembedResponse({ title: ' Video ', thumbnail_url: MAXRES }, { contentType: 'Application/JSON' })),
    { title: 'Video', thumbnailUrl: MAXRES });
});

test('parseOembed drops a thumbnail that is not https on exactly i.ytimg.com and keeps the title', () => {
  for (const thumbnail of [
    `http://i.ytimg.com/vi/${ID}/hqdefault.jpg`,
    `https://i.ytimg.com.evil.example/vi/${ID}/hqdefault.jpg`,
    `https://img.youtube.com/vi/${ID}/hqdefault.jpg`,
    `https://evil.example/vi/${ID}/hqdefault.jpg`,
    `https://i.ytimg.com:8443/vi/${ID}/hqdefault.jpg`,
    `https://i.ytimg.com:443/vi/${ID}/hqdefault.jpg`,
    `https://user:pass@i.ytimg.com/vi/${ID}/hqdefault.jpg`,
    `https://user@i.ytimg.com/vi/${ID}/hqdefault.jpg`,
    `/vi/${ID}/hqdefault.jpg`,
    42,
    null,
    { href: HQDEFAULT },
  ]) {
    assert.deepEqual(parseOembed(oembedResponse({ title: 'Video', thumbnail_url: thumbnail })),
      { title: 'Video', thumbnailUrl: null }, JSON.stringify(thumbnail));
  }
  assert.deepEqual(parseOembed(oembedResponse({ title: 'Video' })), { title: 'Video', thumbnailUrl: null });
});

test('parseOembed treats a missing, empty or non-string title as absent and keeps the thumbnail', () => {
  for (const data of [{}, { title: '' }, { title: '   ' }, { title: 42 }, { title: null }, { title: ['Video'] }]) {
    assert.deepEqual(parseOembed(oembedResponse({ ...data, thumbnail_url: HQDEFAULT })),
      { title: null, thumbnailUrl: HQDEFAULT }, JSON.stringify(data));
  }
});

test('parseOembed fails on a non-JSON Content-Type or a body that is not a JSON object', () => {
  for (const contentType of ['text/html; charset=utf-8', 'text/plain', 'text/json', '']) {
    assert.throws(() => parseOembed(oembedResponse({ title: 'Video' }, { contentType })), undefined, contentType);
  }
  for (const body of ['not json', '{"title":', '', '[]', '[{"title":"Video"}]', 'null', '"Video"', '42', 'true']) {
    assert.throws(() => parseOembed(oembedResponse(body)), undefined, body);
  }
});

test('parseOembed fails when the final URL is not https://www.youtube.com/oembed', () => {
  for (const finalUrl of [
    'http://www.youtube.com/oembed?format=json',
    'https://youtube.com/oembed?format=json',
    'https://m.youtube.com/oembed?format=json',
    'https://www.youtube.com.evil.example/oembed?format=json',
    'https://other.example/oembed?format=json',
    'https://www.youtube.com:8443/oembed?format=json',
    'https://www.youtube.com/oembed/?format=json',
    `https://www.youtube.com/watch?v=${ID}`,
  ]) {
    assert.throws(() => parseOembed(oembedResponse({ title: 'Video', thumbnail_url: HQDEFAULT }, { finalUrl })), undefined, finalUrl);
  }
});

// ---------------------------------------------------------------- handler, injected fetcher

const json = (data) => ({ body: Buffer.from(JSON.stringify(data)), contentType: 'application/json; charset=utf-8' });
const image = { body: png, contentType: 'image/png' };
const page = (head) => ({ body: Buffer.from(`<html><head>${head}</head><body></body></html>`), contentType: 'text/html; charset=utf-8' });

function stubbed(responses, { s3 = new FakeS3() } = {}) {
  const calls = [];
  const entries = [];
  const handler = createHandler({
    environment: 'dev', bucket: 'alink-previews-dev', s3,
    logger: { log: (line) => entries.push(JSON.parse(line)) },
    fetcher: async (url, options) => {
      calls.push({ url, options });
      const host = new URL(url).hostname;
      const response = responses[url];
      if (response === undefined || response instanceof Error) {
        options.onRequest({ host, status: 'FAILED', durationMs: 1 });
        throw response || new Error('fixture failed');
      }
      options.onRequest({ host, status: 200, durationMs: 1 });
      return { finalUrl: url, ...response };
    },
  });
  return { handler, calls, entries, s3, urls: () => calls.map((call) => call.url) };
}

test('oEmbed title and thumbnail are returned without requesting the page (regression)', async () => {
  const f = stubbed({ [OEMBED]: json({ title: ' Real title ', thumbnail_url: HQDEFAULT }), [HQDEFAULT]: image });
  const result = await f.handler({ url: WATCH, mode: 'PREVIEW', traceId: 'yt-1' });
  assert.deepEqual(result, { title: 'Real title', thumbnailKey: thumbnailKey(), faviconKey: null, isVideo: true });
  assert.deepEqual(f.urls(), [OEMBED, HQDEFAULT]);
  assert.equal(f.s3.writes.length, 1);
  assert.equal(resultEntry(f.entries).outcome, 'THUMBNAIL');
  const external = f.entries.find((entry) => entry.event === 'LINK_PREVIEW_EXTERNAL' && entry.kind === 'oembed');
  assert.deepEqual(Object.keys(external).sort(), ['durationMs', 'event', 'host', 'kind', 'status', 'traceId']);
  assert.equal(external.host, 'www.youtube.com');
  assert.ok(f.entries.every((entry) => !JSON.stringify(entry).includes(ID) && !JSON.stringify(entry).includes('Real title')));
});

test('a youtu.be URL requests exactly one canonical oEmbed URL with a 100,000-byte limit and no truncation', async () => {
  const url = `https://youtu.be/${ID}?si=share-token`;
  const f = stubbed({ [OEMBED]: json({ title: 'Real title', thumbnail_url: HQDEFAULT }), [HQDEFAULT]: image });
  const result = await f.handler({ url, mode: 'PREVIEW' });
  const oembedCalls = f.calls.filter((call) => call.url.startsWith('https://www.youtube.com/oembed'));
  assert.equal(oembedCalls.length, 1);
  assert.equal(oembedCalls[0].url, `https://www.youtube.com/oembed?url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3D${ID}&format=json`);
  assert.equal(oembedUrl(ID), oembedCalls[0].url);
  assert.equal(oembedCalls[0].options.maxBytes, 100_000);
  assert.notEqual(oembedCalls[0].options.truncate, true);
  assert.equal(result.thumbnailKey, thumbnailKey(url));
  assert.notEqual(result.thumbnailKey, thumbnailKey(WATCH));
});

test('a failed oEmbed thumbnail falls back to hqdefault with the oEmbed title', async () => {
  const f = stubbed({ [OEMBED]: json({ title: 'Real title', thumbnail_url: MAXRES }), [HQDEFAULT]: image });
  const result = await f.handler({ url: WATCH, mode: 'PREVIEW' });
  assert.deepEqual(result, { title: 'Real title', thumbnailKey: thumbnailKey(), faviconKey: null, isVideo: true });
  assert.deepEqual(f.urls(), [OEMBED, MAXRES, HQDEFAULT]);
  assert.ok(f.entries.some((entry) => entry.event === 'LINK_PREVIEW_CANDIDATE' && entry.kind === 'preview' && entry.outcome === 'FAILED'));
});

test('when the oEmbed thumbnail and hqdefault both fail the page image and then the favicon are used', async () => {
  const oembed = { [OEMBED]: json({ title: 'Real title', thumbnail_url: MAXRES }), [WATCH]: page(PAGE_HEAD) };
  const withImage = stubbed({ ...oembed, [OG_IMAGE]: image });
  const fromPage = await withImage.handler({ url: WATCH, mode: 'PREVIEW' });
  assert.deepEqual(fromPage, { title: 'Real title', thumbnailKey: thumbnailKey(), faviconKey: null, isVideo: true });
  assert.deepEqual(withImage.urls(), [OEMBED, MAXRES, HQDEFAULT, WATCH, OG_IMAGE]);

  const withIcon = stubbed({ ...oembed, [ICON]: image });
  const fromIcon = await withIcon.handler({ url: WATCH, mode: 'PREVIEW' });
  assert.deepEqual(fromIcon, { title: 'Real title', thumbnailKey: null, faviconKey: faviconKey(), isVideo: true });
  assert.deepEqual(withIcon.urls(), [OEMBED, MAXRES, HQDEFAULT, WATCH, OG_IMAGE, ICON]);
  assert.equal(resultEntry(withIcon.entries).outcome, 'FAVICON');
});

test('an oEmbed network error or timeout falls back to the page and is not recorded as a failed preview', async () => {
  for (const error of [new Error('request timed out'), Object.assign(new Error('socket hang up'), { name: 'ECONNRESET' })]) {
    const f = stubbed({ [OEMBED]: error, [WATCH]: page(PAGE_HEAD), [OG_IMAGE]: image });
    const result = await f.handler({ url: WATCH, mode: 'PREVIEW' });
    assert.deepEqual(result, { title: 'Page title', thumbnailKey: thumbnailKey(), faviconKey: null, isVideo: true });
    assert.deepEqual(f.urls(), [OEMBED, WATCH, OG_IMAGE]);
    assert.equal(resultEntry(f.entries).outcome, 'THUMBNAIL');
    assert.ok(f.entries.some((entry) => entry.event === 'LINK_PREVIEW_CANDIDATE' && entry.kind === 'oembed' && entry.outcome === 'FAILED'));
    assert.ok(f.entries.some((entry) => entry.event === 'LINK_PREVIEW_EXTERNAL' && entry.kind === 'oembed' && entry.status === 'FAILED'));
  }
});

test('an oEmbed title without a thumbnail uses the page og:image', async () => {
  const f = stubbed({ [OEMBED]: json({ title: 'Real title' }), [WATCH]: page(PAGE_HEAD), [OG_IMAGE]: image, [HQDEFAULT]: image });
  const result = await f.handler({ url: WATCH, mode: 'PREVIEW' });
  assert.deepEqual(result, { title: 'Real title', thumbnailKey: thumbnailKey(), faviconKey: null, isVideo: true });
  assert.deepEqual(f.urls(), [OEMBED, WATCH, OG_IMAGE]);
});

test('an oEmbed thumbnail without a title uses the page title and stores the oEmbed thumbnail first', async () => {
  const f = stubbed({ [OEMBED]: json({ thumbnail_url: MAXRES }), [WATCH]: page(PAGE_HEAD), [MAXRES]: image, [OG_IMAGE]: image });
  const result = await f.handler({ url: WATCH, mode: 'PREVIEW' });
  assert.deepEqual(result, { title: 'Page title', thumbnailKey: thumbnailKey(), faviconKey: null, isVideo: true });
  assert.deepEqual(f.urls(), [OEMBED, WATCH, MAXRES]);
});

test('an oEmbed thumbnail outside the allow list is never requested and the page image and hqdefault follow', async () => {
  const evil = `https://evil.example/vi/${ID}/hqdefault.jpg`;
  const f = stubbed({ [OEMBED]: json({ title: 'Real title', thumbnail_url: evil }), [WATCH]: page(PAGE_HEAD), [evil]: image, [HQDEFAULT]: image });
  const result = await f.handler({ url: WATCH, mode: 'PREVIEW' });
  assert.deepEqual(result, { title: 'Real title', thumbnailKey: thumbnailKey(), faviconKey: null, isVideo: true });
  assert.deepEqual(f.urls(), [OEMBED, WATCH, OG_IMAGE, HQDEFAULT]);
  assert.equal(f.urls().includes(evil), false);
});

test('a failed oEmbed and a stripped "- YouTube" page store hqdefault with no title (regression)', async () => {
  for (const title of ['- YouTube', '  - YouTube  ']) {
    const stripped = page(`<title>${title}</title><link rel="icon" href="/icon.png">`);
    const f = stubbed({ [OEMBED]: new Error('upstream status'), [WATCH]: stripped, [HQDEFAULT]: image, [ICON]: image });
    const result = await f.handler({ url: WATCH, mode: 'PREVIEW' });
    assert.deepEqual(result, { title: null, thumbnailKey: thumbnailKey(), faviconKey: null, isVideo: true });
    assert.deepEqual(f.urls(), [OEMBED, WATCH, HQDEFAULT]);
    assert.equal(resultEntry(f.entries).outcome, 'THUMBNAIL');
  }
});

test('when oEmbed, the page and hqdefault all fail every key is null, isVideo is true and the result is EMPTY', async () => {
  const f = stubbed({});
  const result = await f.handler({ url: WATCH, mode: 'PREVIEW', traceId: 'yt-empty' });
  assert.deepEqual(result, { title: null, thumbnailKey: null, faviconKey: null, isVideo: true });
  assert.deepEqual(f.urls(), [OEMBED, WATCH, HQDEFAULT]);
  assert.equal(resultEntry(f.entries).outcome, 'EMPTY');
  assert.ok(f.entries.some((entry) => entry.event === 'LINK_PREVIEW_CANDIDATE' && entry.kind === 'page' && entry.outcome === 'FAILED'));
});

test('ten or more failing page images still leave room for hqdefault within the ten-candidate limit', async () => {
  const pageImages = Array.from({ length: 12 }, (_value, index) => `https://cdn.example.com/${index}.png`);
  const head = pageImages.map((url) => `<meta property="og:image" content="${url}">`).join('');
  const f = stubbed({ [OEMBED]: new Error('upstream status'), [WATCH]: page(head), [HQDEFAULT]: image });
  const result = await f.handler({ url: WATCH, mode: 'PREVIEW' });
  assert.equal(result.thumbnailKey, thumbnailKey());
  assert.equal(result.isVideo, true);
  assert.deepEqual(f.urls(), [OEMBED, WATCH, ...pageImages.slice(0, 9), HQDEFAULT]);

  const withThumbnail = stubbed({ [OEMBED]: json({ thumbnail_url: MAXRES }), [WATCH]: page(head), [HQDEFAULT]: image });
  assert.equal((await withThumbnail.handler({ url: WATCH, mode: 'PREVIEW' })).thumbnailKey, thumbnailKey());
  assert.deepEqual(withThumbnail.urls(), [OEMBED, WATCH, MAXRES, ...pageImages.slice(0, 8), HQDEFAULT]);
});

test('a preview object younger than seven days is reused with the oEmbed title and no image download', async () => {
  const s3 = new FakeS3();
  const key = `dev-preview-${previewHash(WATCH)}.jpg`;
  s3.objects.set(key, { etag: '"cached"', updatedAt: new Date(Date.now() - 6 * 86400_000) });
  const f = stubbed({ [OEMBED]: json({ title: 'Real title', thumbnail_url: HQDEFAULT }), [HQDEFAULT]: image }, { s3 });
  const result = await f.handler({ url: WATCH, mode: 'PREVIEW' });
  assert.deepEqual(result, { title: 'Real title', thumbnailKey: key, faviconKey: null, isVideo: true });
  assert.deepEqual(f.urls(), [OEMBED]);
  assert.equal(s3.writes.length, 0);
  assert.ok(f.entries.some((entry) => entry.event === 'LINK_PREVIEW_S3' && entry.action === 'HIT'));

  const failed = stubbed({ [OEMBED]: new Error('upstream status'), [WATCH]: page(PAGE_HEAD), [OG_IMAGE]: image, [HQDEFAULT]: image }, { s3 });
  assert.deepEqual(await failed.handler({ url: WATCH, mode: 'PREVIEW' }),
    { title: 'Page title', thumbnailKey: key, faviconKey: null, isVideo: true });
  assert.deepEqual(failed.urls(), [OEMBED, WATCH]);
  assert.equal(s3.writes.length, 0);
});

test('a storage error is still a failed preview that returns the empty result', async () => {
  const s3 = new FakeS3();
  s3.send = async (command) => {
    if (command instanceof HeadObjectCommand) throw Object.assign(new Error('denied'), { name: 'AccessDenied', $metadata: { httpStatusCode: 403 } });
    throw new Error('unexpected command');
  };
  const f = stubbed({ [OEMBED]: json({ title: 'Real title', thumbnail_url: HQDEFAULT }), [HQDEFAULT]: image }, { s3 });
  const result = await f.handler({ url: WATCH, mode: 'PREVIEW' });
  assert.deepEqual(result, { title: null, thumbnailKey: null, faviconKey: null, isVideo: false });
  assert.equal(resultEntry(f.entries).outcome, 'FAILED');
});

test('URLs that are not YouTube videos, including music.youtube.com, never request oEmbed', async () => {
  for (const url of [`https://music.youtube.com/watch?v=${ID}`, `https://example.com/watch?v=${ID}`, 'https://www.youtube.com/@handle']) {
    const f = stubbed({ [url]: page(PAGE_HEAD), [OG_IMAGE]: image });
    const result = await f.handler({ url, mode: 'PREVIEW' });
    assert.deepEqual(result, { title: 'Page title', thumbnailKey: thumbnailKey(url), faviconKey: null, isVideo: false });
    assert.deepEqual(f.urls(), [url, OG_IMAGE]);
    const stripped = stubbed({ [url]: page('<title>- YouTube</title>') });
    assert.deepEqual(await stripped.handler({ url, mode: 'PREVIEW' }),
      { title: '- YouTube', thumbnailKey: null, faviconKey: null, isVideo: false });
    assert.equal(stripped.urls().some((called) => called.includes('/oembed') || called.includes('i.ytimg.com')), false);
  }
});

test('favicon-only YouTube requests skip oEmbed and keep the existing favicon flow', async () => {
  const f = stubbed({ [OEMBED]: json({ title: 'Real title', thumbnail_url: HQDEFAULT }), [WATCH]: page(PAGE_HEAD), [ICON]: image, [HQDEFAULT]: image });
  const result = await f.handler({ url: WATCH, mode: 'FAVICON_ONLY' });
  assert.deepEqual(result, { title: null, thumbnailKey: null, faviconKey: faviconKey(), isVideo: false });
  assert.deepEqual(f.urls(), [WATCH, ICON]);
});

// ---------------------------------------------------------------- handler, local HTTP server and real fetchSafe

// Each request is served from `/<host><path>` so routes can tell www.youtube.com, i.ytimg.com and others apart.
async function previewThroughServer(routes, { timeoutMs } = {}) {
  const requested = [];
  const entries = [];
  const pngRoute = (_request, response) => { response.setHeader('content-type', 'image/png'); response.end(png); };
  const pageRoute = (_request, response) => {
    response.setHeader('content-type', 'text/html; charset=utf-8');
    response.end(`<html><head>${PAGE_HEAD}</head></html>`);
  };
  return withServer({ '/www.youtube.com/watch': pageRoute, '/cdn.example.com/og.png': pngRoute, [`/i.ytimg.com/vi/${ID}/hqdefault.jpg`]: pngRoute, ...routes }, async (port) => {
    const handler = createHandler({
      environment: 'dev', bucket: 'alink-previews-dev', s3: new FakeS3(),
      logger: { log: (line) => entries.push(JSON.parse(line)) },
      fetcher: (url, options) => fetchSafe(url, {
        ...options,
        resolveHost: async () => [{ address: '8.8.8.8', family: 4 }],
        requestOnce: (target, requestOptions) => {
          const path = `/${target.url.hostname}${target.url.pathname}`;
          requested.push(path);
          return readOnce(localTarget(port, `${path}${target.url.search}`), { ...requestOptions, ...(timeoutMs && { timeoutMs }) });
        },
      }),
    });
    const result = await handler({ url: WATCH, mode: 'PREVIEW', traceId: 'yt-server' });
    return { result, entries, requested };
  });
}

const oembedRoute = (body, { status = 200, contentType = 'application/json; charset=utf-8', headers = {} } = {}) => (_request, response) => {
  response.statusCode = status;
  response.setHeader('content-type', contentType);
  for (const [name, value] of Object.entries(headers)) response.setHeader(name, value);
  if (typeof body === 'string') { response.end(body); return; }
  for (const part of body) response.write(part);
  response.end();
};

const validOembed = JSON.stringify({ title: 'Real title', thumbnail_url: HQDEFAULT });
const pageFallback = { title: 'Page title', thumbnailKey: thumbnailKey(), faviconKey: null, isVideo: true };

test('oEmbed 401, 404, non-JSON, broken JSON and off-host redirects each fall back to the page through fetchSafe', async () => {
  const cases = {
    401: oembedRoute(validOembed, { status: 401 }),
    404: oembedRoute(validOembed, { status: 404 }),
    html: oembedRoute(validOembed, { contentType: 'text/html; charset=utf-8' }),
    broken: oembedRoute('{"title":"Real title",'),
    redirect: oembedRoute('', { status: 302, headers: { location: 'https://other.example/oembed?format=json' } }),
  };
  for (const [name, route] of Object.entries(cases)) {
    const { result, entries, requested } = await previewThroughServer({
      '/www.youtube.com/oembed': route,
      '/other.example/oembed': oembedRoute(validOembed),
    });
    assert.deepEqual(result, pageFallback, name);
    assert.deepEqual(requested.slice(-2), ['/www.youtube.com/watch', '/cdn.example.com/og.png'], name);
    assert.equal(resultEntry(entries).outcome, 'THUMBNAIL', name);
    assert.ok(entries.some((entry) => entry.event === 'LINK_PREVIEW_CANDIDATE' && entry.kind === 'oembed' && entry.outcome === 'FAILED'), name);
    if (name === 'redirect') assert.deepEqual(requested.slice(0, 2), ['/www.youtube.com/oembed', '/other.example/oembed']);
  }
});

test('an oEmbed connection reset or timeout through fetchSafe falls back to the page', async () => {
  const reset = (request) => request.socket.destroy();
  const stalled = () => {};
  for (const [route, options] of [[reset, {}], [stalled, { timeoutMs: 200 }]]) {
    const { result, entries, requested } = await previewThroughServer({ '/www.youtube.com/oembed': route }, options);
    assert.deepEqual(result, pageFallback);
    assert.deepEqual(requested, ['/www.youtube.com/oembed', '/www.youtube.com/watch', '/cdn.example.com/og.png']);
    assert.equal(resultEntry(entries).outcome, 'THUMBNAIL');
    assert.ok(entries.some((entry) => entry.event === 'LINK_PREVIEW_EXTERNAL' && entry.kind === 'oembed' && entry.status === 'FAILED'));
  }
});

test('an oEmbed body of exactly 100,000 bytes is used and 100,001 bytes falls back to the page', async () => {
  const padded = (bytes) => `${validOembed.slice(0, -1)}${' '.repeat(bytes - validOembed.length)}}`;
  assert.equal(Buffer.byteLength(padded(100_000)), 100_000);

  const exact = await previewThroughServer({ '/www.youtube.com/oembed': oembedRoute(padded(100_000)) });
  assert.deepEqual(exact.result, { title: 'Real title', thumbnailKey: thumbnailKey(), faviconKey: null, isVideo: true });
  assert.deepEqual(exact.requested, ['/www.youtube.com/oembed', `/i.ytimg.com/vi/${ID}/hqdefault.jpg`]);

  const over = padded(100_001);
  for (const body of [over, [over.slice(0, 60_000), over.slice(60_000)]]) {
    const large = await previewThroughServer({ '/www.youtube.com/oembed': oembedRoute(body) });
    assert.deepEqual(large.result, pageFallback);
    assert.deepEqual(large.requested, ['/www.youtube.com/oembed', '/www.youtube.com/watch', '/cdn.example.com/og.png']);
  }
});
