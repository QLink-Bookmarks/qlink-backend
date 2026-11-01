import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import http from 'node:http';
import sharp from 'sharp';
import { HeadObjectCommand, PutObjectCommand } from '@aws-sdk/client-s3';
import { fetchSafe, isPublicIp, readOnce, validateTarget } from '../src/network.mjs';
import { parsePage } from '../src/metadata.mjs';
import { prepareImage } from '../src/image.mjs';
import { faviconHash, findObject, objectKey, previewHash, putImage } from '../src/cache.mjs';
import { createHandler } from '../src/handler.mjs';

const sha256 = (value) => createHash('sha256').update(value).digest('hex');
const png = await sharp({ create: { width: 2, height: 2, channels: 4, background: '#ff0000' } }).png().toBuffer();

class FakeS3 {
  objects = new Map();
  writes = [];
  async send(command) {
    const input = command.input;
    if (command instanceof HeadObjectCommand) {
      const found = this.objects.get(input.Key);
      if (!found) throw Object.assign(new Error('missing'), { name: 'NotFound', $metadata: { httpStatusCode: 404 } });
      return { ETag: found.etag, LastModified: found.updatedAt };
    }
    if (command instanceof PutObjectCommand) {
      const existing = this.objects.get(input.Key);
      if ((input.IfNoneMatch && existing) || (input.IfMatch && input.IfMatch !== existing?.etag)) {
        throw Object.assign(new Error('conflict'), { $metadata: { httpStatusCode: 412 } });
      }
      this.writes.push(input);
      this.objects.set(input.Key, { etag: '"new"', updatedAt: new Date() });
      return {};
    }
    throw new Error('unknown command');
  }
}

function fixture({ s3 = new FakeS3(), metadata, failures = new Set(), environment = 'dev' }) {
  const calls = [];
  const handler = createHandler({
    environment, bucket: `alink-previews-${environment}`, s3, logger: { log: () => {} },
    fetcher: async (url) => {
      calls.push(url);
      if (failures.has(url)) throw new Error('fixture failed');
      return url.endsWith('/page')
        ? { body: Buffer.from('<html/>'), finalUrl: url, contentType: 'text/html' }
        : { body: png, finalUrl: url, contentType: 'image/png' };
    },
    parser: async (_html, _url, mode) => mode === 'FAVICON_ONLY'
      ? { title: null, isVideo: false, imageUrls: [], faviconUrls: metadata.faviconUrls }
      : metadata,
  });
  return { handler, s3, calls };
}

test('URL hashes preserve path and query while normalizing scheme and host', () => {
  const url = 'HTTPS://Example.COM/Case?b=2&A=1';
  assert.equal(previewHash(url), sha256('https://example.com/Case?b=2&A=1'));
  assert.equal(faviconHash(url), sha256('example.com'));
  assert.equal(faviconHash('http://EXAMPLE.com/different?x=2'), faviconHash(url));
});

test('only downloaded HTML reaches the OG parser, and favicon mode skips it', async () => {
  const html = '<title> Local title </title><link rel="icon" href="/icon.png">';
  const calls = [];
  const ogParser = async (options) => { calls.push(options); return { result: {} }; };
  const preview = await parsePage(html, 'https://example.com/private?token=hidden', 'PREVIEW', ogParser);
  assert.equal(preview.title, 'Local title');
  assert.deepEqual(calls, [{ html, onlyGetOpenGraphInfo: true }]);
  await parsePage(html, 'https://example.com/', 'FAVICON_ONLY', ogParser);
  assert.equal(calls.length, 1);
});

test('metadata selects OG title and image before Twitter and puts Apple icon after icon', async () => {
  const html = '<html><head><title> HTML </title><meta property="og:title" content=" OG "><meta property="og:image" content="/og.png"><meta name="twitter:image" content="/tw.png"><meta property="og:type" content="video.movie"><link rel="apple-touch-icon" href="/apple.png"><link rel="icon" href="/icon.png"></head></html>';
  const result = await parsePage(html, 'https://example.com/page', 'PREVIEW');
  assert.equal(result.title, 'OG');
  assert.equal(result.isVideo, true);
  assert.deepEqual(result.imageUrls, ['https://example.com/og.png', 'https://example.com/tw.png']);
  assert.deepEqual(result.faviconUrls, ['https://example.com/icon.png', 'https://example.com/apple.png', 'https://example.com/favicon.ico']);
  const faviconOnly = await parsePage(html, 'https://example.com/page', 'FAVICON_ONLY');
  assert.equal(faviconOnly.title, null);
  assert.deepEqual(faviconOnly.imageUrls, []);
});

test('private addresses, mixed DNS answers and prohibited ports are denied', async () => {
  for (const address of ['127.0.0.1', '169.254.169.254', '10.0.0.1', '100.64.0.1', '::1', '::ffff:127.0.0.1']) {
    assert.equal(isPublicIp(address), false, address);
  }
  assert.equal(isPublicIp('8.8.8.8'), true);
  await assert.rejects(validateTarget('http://127.1/'));
  await assert.rejects(validateTarget('https://example.com:8443/', async () => [{ address: '8.8.8.8', family: 4 }]));
  await assert.rejects(validateTarget('https://example.com/', async () => [{ address: '8.8.8.8', family: 4 }, { address: '10.0.0.1', family: 4 }]));
});

test('redirect destination is checked before another request', async () => {
  let requests = 0;
  const logs = [];
  await assert.rejects(fetchSafe('https://example.com/', {
    maxBytes: 100,
    resolveHost: async () => [{ address: '8.8.8.8', family: 4 }],
    requestOnce: async () => { requests += 1; return { status: 302, location: 'http://169.254.169.254/latest/meta-data/' }; },
    onRequest: (entry) => logs.push(entry),
  }));
  assert.equal(requests, 1);
  assert.equal(logs.at(-1).status, 'BLOCKED');
  assert.equal(logs.at(-1).host, '169.254.169.254');
});

test('pinned requests enforce streamed byte and time limits for every content type', async () => {
  const server = http.createServer((request, response) => {
    if (request.url === '/slow') {
      setTimeout(() => response.end('late'), 300);
    } else if (request.url === '/stream') {
      response.write('12345');
      response.end('678901');
    } else {
      response.end('1234567890');
    }
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    const target = (path) => ({ url: new URL(`http://public.example:${server.address().port}${path}`), address: '127.0.0.1', family: 4 });
    assert.equal((await readOnce(target('/exact'), { maxBytes: 10, timeoutMs: 1000 })).body.length, 10);
    await assert.rejects(readOnce(target('/stream'), { maxBytes: 10, timeoutMs: 1000 }), /too large/);
    await assert.rejects(readOnce(target('/slow'), { maxBytes: 10, timeoutMs: 40 }), /timed out/);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});

test('thumbnail is conditionally stored and reused within seven days', async () => {
  const url = 'https://example.com/page';
  const metadata = { title: 'Example', isVideo: false, imageUrls: ['https://cdn.example.com/og.png'], faviconUrls: ['https://example.com/icon.png'] };
  const f = fixture({ metadata });
  const first = await f.handler({ url, mode: 'PREVIEW', traceId: 'test-1' });
  assert.equal(first.thumbnailKey, `dev-preview-${previewHash(url)}.png`);
  assert.equal(first.faviconKey, null);
  assert.equal(f.s3.writes[0].IfNoneMatch, '*');
  assert.equal(f.s3.writes[0].CacheControl, 'public, max-age=86400');
  const second = await f.handler({ url, mode: 'PREVIEW', traceId: 'test-2' });
  assert.equal(second.thumbnailKey, first.thumbnailKey);
  assert.equal(f.s3.writes.length, 1);
  assert.equal(f.calls.filter((value) => value.endsWith('/og.png')).length, 1);
  assert.equal(f.calls.includes('https://example.com/icon.png'), false);
});

test('stale image refresh uses ETag and keeps old object on failure', async () => {
  const url = 'https://example.com/page';
  const key = `dev-preview-${previewHash(url)}.png`;
  const s3 = new FakeS3();
  s3.objects.set(key, { etag: '"old"', updatedAt: new Date(Date.now() - 8 * 86400_000) });
  const metadata = { title: null, isVideo: false, imageUrls: ['https://cdn.example.com/og.png'], faviconUrls: [] };
  assert.equal((await fixture({ s3, metadata }).handler({ url, mode: 'PREVIEW' })).thumbnailKey, key);
  assert.equal(s3.writes[0].IfMatch, '"old"');
  s3.objects.set(key, { etag: '"old"', updatedAt: new Date(Date.now() - 8 * 86400_000) });
  assert.equal((await fixture({ s3, metadata, failures: new Set(metadata.imageUrls) }).handler({ url, mode: 'PREVIEW' })).thumbnailKey, key);
  assert.equal(s3.objects.get(key).etag, '"old"');
});

test('exactly seven-day-old objects are stale and a losing conditional write reuses the winner', async () => {
  const s3 = new FakeS3();
  const hash = previewHash('https://example.com/page');
  const key = objectKey('dev', 'preview', hash, 'png');
  const now = Date.now();
  s3.objects.set(key, { etag: '"winner"', updatedAt: new Date(now - 7 * 86400_000) });
  const existing = await findObject(s3, 'alink-previews-dev', 'dev', 'preview', hash, now);
  assert.equal(existing.fresh, false);
  assert.equal(await putImage(s3, 'alink-previews-dev', 'dev', 'preview', hash,
    { body: png, ext: 'png', contentType: 'image/png' }, { ...existing, etag: '"loser"' }), key);
  assert.equal(s3.writes.length, 0);
  assert.equal(s3.objects.get(key).etag, '"winner"');
  assert.equal((await findObject(s3, 'alink-previews-dev', 'dev', 'preview', hash, now - 1)).fresh, true);
});

test('favicon-only skips thumbnail work and keys by hostname', async () => {
  const url = 'https://example.com/page';
  const metadata = { title: 'ignored', isVideo: true, imageUrls: ['https://cdn.example.com/og.png'], faviconUrls: ['https://example.com/apple.png'] };
  const f = fixture({ metadata });
  const result = await f.handler({ url, mode: 'FAVICON_ONLY' });
  assert.equal(result.title, null);
  assert.equal(result.isVideo, false);
  assert.equal(result.faviconKey, `dev-favicon-${faviconHash(url)}.png`);
  assert.equal(f.calls.includes(metadata.imageUrls[0]), false);
});

test('SVG external references are rejected before rasterization', async () => {
  await assert.rejects(prepareImage(Buffer.from('<svg width="10" height="10"><image href="https://example.com/a.png"/></svg>'), 'favicon'));
  await assert.rejects(prepareImage(Buffer.from('<!DOCTYPE svg><svg width="10" height="10"></svg>'), 'favicon'));
  await assert.rejects(prepareImage(Buffer.from('<svg width="10" height="10"><text>&entity;</text></svg>'), 'favicon'));
  await assert.rejects(prepareImage(Buffer.from('<svg width="5000" height="5000"></svg>'), 'favicon'));
  const output = await prepareImage(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10" fill="red"/></svg>'), 'favicon');
  assert.equal(output.ext, 'png');
});

test('image type follows bytes and all four root key forms use their environment bucket', async () => {
  const url = 'https://example.com/page';
  const metadata = { title: null, isVideo: false, imageUrls: ['https://cdn.example.com/og.jpg'], faviconUrls: ['https://example.com/icon.png'] };
  for (const environment of ['dev', 'prod']) {
    const preview = fixture({ metadata, environment });
    const previewResult = await preview.handler({ url, mode: 'PREVIEW' });
    assert.equal(previewResult.thumbnailKey, `${environment}-preview-${previewHash(url)}.png`);
    assert.equal(preview.s3.writes[0].Bucket, `alink-previews-${environment}`);
    assert.equal(preview.s3.writes[0].ContentType, 'image/png');
    const icon = fixture({ metadata, environment });
    const iconResult = await icon.handler({ url, mode: 'FAVICON_ONLY' });
    assert.equal(iconResult.faviconKey, `${environment}-favicon-${faviconHash(url)}.png`);
    assert.equal(icon.s3.writes[0].Bucket, `alink-previews-${environment}`);
    assert.ok(!icon.s3.writes[0].CacheControl.includes('immutable'));
    assert.ok(!previewResult.thumbnailKey.includes('/'));
    assert.ok(!iconResult.faviconKey.includes('/'));
  }
});

test('failed extraction and storage return empty keys with safe result logs', async () => {
  const entries = [];
  const s3 = new FakeS3();
  const originalSend = s3.send.bind(s3);
  s3.send = async (command) => {
    if (command instanceof PutObjectCommand) throw new Error('storage failed with private response body');
    return originalSend(command);
  };
  const handler = createHandler({
    environment: 'dev', bucket: 'alink-previews-dev', s3,
    logger: { log: (line) => entries.push(JSON.parse(line)) },
    fetcher: async (url, { maxBytes, onRequest }) => {
      assert.equal(maxBytes, url.endsWith('/page?token=private') ? 1_000_000 : 10_000_000);
      onRequest({ host: new URL(url).hostname, status: 200, durationMs: 3 });
      return url.endsWith('/page?token=private')
        ? { body: Buffer.from('<html/>'), finalUrl: url, contentType: 'text/html' }
        : { body: png, finalUrl: url, contentType: 'image/png' };
    },
    parser: async () => ({ title: null, isVideo: false, imageUrls: ['https://cdn.example.com/og.png'], faviconUrls: [] }),
  });
  const result = await handler({ url: 'https://example.com/page?token=private', mode: 'PREVIEW', traceId: 'trace-123' });
  assert.equal(result.thumbnailKey, null);
  assert.equal(result.faviconKey, null);
  assert.ok(entries.some((entry) => entry.event === 'LINK_PREVIEW_RESULT' && entry.outcome === 'EMPTY' && entry.traceId === 'trace-123' && entry.durationMs >= 0));
  assert.ok(entries.every((entry) => !JSON.stringify(entry).includes('private') && !JSON.stringify(entry).includes('body')));
});

test('HTML title is trimmed and bounded, video metadata is recognized', async () => {
  const title = `  ${'A'.repeat(310)}  `;
  const html = `<html><head><title>${title}</title><meta name="twitter:card" content="player"></head></html>`;
  const result = await parsePage(html, 'https://example.com/', 'PREVIEW');
  assert.equal(result.title, 'A'.repeat(300));
  assert.equal(result.isVideo, true);
  assert.deepEqual(result.imageUrls, []);
  assert.equal((await parsePage('<title>   </title>', 'https://example.com/', 'PREVIEW')).title, null);
});

test('favicon fallback uses the page origin even with a cross-origin base tag', async () => {
  const html = '<base href="https://cdn.example.net/assets/"><link rel="icon" href="icon.png">';
  const result = await parsePage(html, 'https://example.com/story', 'FAVICON_ONLY');
  assert.deepEqual(result.faviconUrls, ['https://cdn.example.net/assets/icon.png', 'https://example.com/favicon.ico']);
});

test('failed thumbnail candidates fall back to an Apple icon', async () => {
  const url = 'https://example.com/page';
  const metadata = { title: 'Fallback', isVideo: false, imageUrls: ['https://cdn.example.com/og.png'], faviconUrls: ['https://example.com/apple.png'] };
  const f = fixture({ metadata, failures: new Set(metadata.imageUrls) });
  const result = await f.handler({ url, mode: 'PREVIEW' });
  assert.equal(result.thumbnailKey, null);
  assert.equal(result.faviconKey, `dev-favicon-${faviconHash(url)}.png`);
  assert.deepEqual(f.calls, [url, metadata.imageUrls[0], metadata.faviconUrls[0]]);
});

test('unsafe responses do not expose full URL or query in logs', async () => {
  const entries = [];
  const handler = createHandler({
    environment: 'dev', bucket: 'alink-previews-dev', s3: new FakeS3(),
    logger: { log: (line) => entries.push(line) },
    fetcher: async (_url, { onRequest }) => {
      onRequest({ host: 'example.com', status: 'BLOCKED', durationMs: 0 });
      throw new Error('blocked');
    },
  });
  const result = await handler({ url: 'https://example.com/private?secret=top-secret', mode: 'PREVIEW', traceId: 'trace-1' });
  assert.equal(result.thumbnailKey, null);
  assert.equal(result.isVideo, false);
  assert.ok(entries.every((line) => !line.includes('top-secret') && !line.includes('/private')));
  assert.ok(entries.some((line) => line.includes('trace-1')));
});

test('DNS choice is passed to the request layer and redirects are bounded', async () => {
  const seen = [];
  await assert.rejects(fetchSafe('https://example.com/', {
    maxBytes: 100,
    resolveHost: async () => [{ address: '8.8.8.8', family: 4 }],
    requestOnce: async (target) => {
      seen.push(target.address);
      return { status: 302, location: '/again' };
    },
  }));
  assert.deepEqual(seen, ['8.8.8.8', '8.8.8.8', '8.8.8.8', '8.8.8.8']);
});

test('ICO icons become PNG and thumbnails reject ICO', async () => {
  const header = Buffer.alloc(22);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(1, 4);
  header[6] = 2;
  header[7] = 2;
  header.writeUInt16LE(1, 10);
  header.writeUInt16LE(32, 12);
  header.writeUInt32LE(png.length, 14);
  header.writeUInt32LE(22, 18);
  const ico = Buffer.concat([header, png]);
  const converted = await prepareImage(ico, 'favicon');
  assert.equal(converted.ext, 'png');
  assert.equal(converted.contentType, 'image/png');
  await assert.rejects(prepareImage(ico, 'preview'));
});

test('inline favicon bytes skip every external request and upload a domain-keyed object', async () => {
  const s3 = new FakeS3();
  const requests = [];
  const handler = createHandler({
    environment: 'dev', bucket: 'alink-previews-dev', s3, logger: { log: () => {} },
    fetcher: async (url) => { requests.push(url); throw new Error('must not download'); },
  });
  const url = 'https://example.com/path?token=private';
  const result = await handler({ url, mode: 'FAVICON_ONLY', favicon: { base64: png.toString('base64') } });
  assert.equal(result.faviconKey, `dev-favicon-${faviconHash(url)}.png`);
  assert.equal(result.thumbnailKey, null);
  assert.equal(result.title, null);
  assert.equal(result.isVideo, false);
  assert.deepEqual(requests, []);
  assert.equal(s3.writes[0].ContentType, 'image/png');
  assert.equal(s3.writes[0].CacheControl, 'public, max-age=86400');
  const again = await handler({ url, mode: 'FAVICON_ONLY', favicon: { base64: png.toString('base64') } });
  assert.equal(again.faviconKey, result.faviconKey);
  assert.equal(s3.writes.length, 1);
});

test('inline favicon rejects malformed or oversized payloads without downloading', async () => {
  const s3 = new FakeS3();
  const requests = [];
  const handler = createHandler({
    environment: 'prod', bucket: 'alink-previews-prod', s3, logger: { log: () => {} },
    fetcher: async (url) => { requests.push(url); throw new Error('must not download'); },
  });
  for (const favicon of [{ base64: '%%%=' }, { base64: 'A'.repeat(5_600_000) }]) {
    const result = await handler({ url: 'https://example.com/', mode: 'FAVICON_ONLY', favicon });
    assert.equal(result.faviconKey, null);
  }
  assert.deepEqual(requests, []);
  assert.equal(s3.writes.length, 0);
});

test('production favicon bytes use only the production bucket and key prefix', async () => {
  const s3 = new FakeS3();
  const handler = createHandler({
    environment: 'prod', bucket: 'alink-previews-prod', s3, logger: { log: () => {} },
    fetcher: async () => { throw new Error('must not download'); },
  });
  const result = await handler({ url: 'https://example.com/', mode: 'FAVICON_ONLY', favicon: { base64: png.toString('base64') } });
  assert.equal(result.faviconKey, `prod-favicon-${faviconHash('https://example.com/')}.png`);
  assert.equal(s3.writes[0].Bucket, 'alink-previews-prod');
});
