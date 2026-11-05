import test from 'node:test';
import assert from 'node:assert/strict';
import { createContentHandler, deadlineFor } from '../src/content-handler.mjs';
import { decodeHtml, elementText, isRobotsHeaderDenied, normalizeText } from '../src/content-extract.mjs';
import { fetchSafe, readOnce } from '../src/network.mjs';
import { localTarget, withServer } from './support.mjs';
import { parseHTML } from 'linkedom';

const PUBLIC = [{ address: '8.8.8.8', family: 4 }];
const LONG_CONTEXT = { getRemainingTimeInMillis: () => 30_000 };

// Runs the handler against local routes; every public host resolves to a public address and is served locally.
async function collect(routes, { path = '/page', event = {}, context = LONG_CONTEXT, resolveHost } = {}) {
  const entries = [];
  return withServer(routes, async (port) => {
    const handler = createContentHandler({
      environment: 'dev',
      logger: { log: (line) => entries.push(JSON.parse(line)) },
      fetcher: (url, options) => fetchSafe(url, {
        ...options,
        resolveHost: resolveHost || (async () => PUBLIC),
        requestOnce: (target, requestOptions) =>
          readOnce(localTarget(port, `${target.url.pathname}${target.url.search}`), requestOptions),
      }),
    });
    const result = await handler({ url: `http://public.example${path}`, traceId: 'trace-1', deadlineMs: 20_000, ...event }, context);
    return { result, entries };
  });
}

const html = (body, head = '') => `<!doctype html><html lang="ko"><head><meta charset="utf-8">${head}</head><body>${body}</body></html>`;
const route = (body, { type = 'text/html; charset=utf-8', status = 200, headers = {} } = {}) => (_request, response) => {
  response.statusCode = status;
  response.setHeader('content-type', type);
  for (const [name, value] of Object.entries(headers)) response.setHeader(name, value);
  response.end(body);
};
const page = (body, head, options) => ({ '/page': route(html(body, head), options) });
// Korean sentence text with no notice words, exactly `length` UTF-16 units.
const prose = (length) => '가나다라마바사 아자차카타파하 '.repeat(Math.ceil(length / 16)).slice(0, length).replace(/ $/, '다');

for (const length of [50, 99, 100, 199]) {
  test(`a normal ${length}-character page is collected in full`, async () => {
    const { result } = await collect(page(`<article><p>${prose(length)}</p></article>`, '<title>짧은 글</title>'));
    assert.equal(result.outcome, 'COLLECTED');
    assert.equal(result.code, null);
    assert.equal(result.text, prose(length));
    assert.equal(result.text.length, length);
    assert.equal(result.truncation, 'NONE');
  });
}

test('the response carries every field, with page metadata bounded and normalized', async () => {
  const head = [
    '<title>문서 제목</title>',
    '<meta property="og:title" content="OG 제목">',
    '<meta property="og:site_name" content="에이링크">',
    `<meta name="author" content="${'저'.repeat(300)}">`,
    '<meta property="article:published_time" content="2026-03-01T09:00:00+09:00">',
    '<link rel="canonical" href="/canonical?x=1">',
  ].join('');
  const { result } = await collect(page(`<article><p>${prose(300)}</p></article>`, head));
  assert.deepEqual(Object.keys(result).sort(), [
    'author', 'canonicalUrl', 'code', 'durationMs', 'finalUrl', 'language', 'outcome', 'publishedAt', 'retryAfterSeconds',
    'siteName', 'text', 'title', 'truncation', 'upstreamStatus',
  ]);
  assert.equal(result.upstreamStatus, 200);
  assert.equal(result.retryAfterSeconds, null);
  assert.equal(result.finalUrl, 'http://public.example/page');
  assert.equal(result.canonicalUrl, 'http://public.example/canonical?x=1');
  assert.equal(result.title, 'OG 제목');
  assert.equal(result.siteName, '에이링크');
  assert.equal(result.author.length, 200);
  assert.equal(result.publishedAt, '2026-03-01T00:00:00.000Z');
  assert.equal(result.language, 'ko');
  assert.ok(result.durationMs >= 0);
});

test('JSON-LD supplies the author and date when meta tags do not', async () => {
  const ld = '<script type="application/ld+json">{"@graph":[{"@type":"NewsArticle","author":[{"name":"홍길동"}],"datePublished":"2026-01-02"}]}</script>';
  const { result } = await collect(page(`<article><p>${prose(300)}</p></article>`, ld));
  assert.equal(result.author, '홍길동');
  assert.equal(result.publishedAt, '2026-01-02T00:00:00.000Z');
});

test('blocks become paragraphs, br becomes a line break and stripped regions never reach the text', async () => {
  const body = [
    '<nav>메뉴 항목</nav><header role="banner">배너 영역</header>',
    '<article><h1>머리말</h1><p>첫 줄<br>둘째   줄</p><ul><li>하나</li><li>둘</li></ul>',
    '<form><p>양식 안내</p><input value="secret-value"><textarea>적은 글</textarea><select><option>선택지</option></select><button>보내기</button></form>',
    '<div aria-hidden="true">숨김 글</div>',
    '<pre>코드  한 줄</pre><script>var leaked = 1</script><style>.x{}</style></article>',
    '<aside>곁가지</aside><footer>바닥글</footer>',
  ].join('');
  const { result } = await collect(page(body));
  assert.equal(result.outcome, 'COLLECTED');
  assert.equal(result.text, '머리말\n\n첫 줄\n둘째 줄\n\n하나\n\n둘\n\n양식 안내\n\n코드 한 줄');
  for (const hidden of ['메뉴', '배너', 'secret-value', '적은 글', '선택지', '보내기', '숨김', 'leaked', '곁가지', '바닥글']) {
    assert.ok(!result.text.includes(hidden), hidden);
  }
});

test('a WebForms page wrapped in one form keeps its text and drops its fields', async () => {
  const body = [
    '<form method="post" action="./View.aspx?id=1" id="form1">',
    '<input type="hidden" name="__VIEWSTATE" value="viewstate-secret">',
    `<div id="content"><h2>공지 제목</h2><p>${prose(300)}</p></div>`,
    '<input type="text" name="q" value="query-secret"><input type="submit" value="검색">',
    '</form>',
  ].join('');
  const { result } = await collect(page(body));
  assert.equal(result.outcome, 'COLLECTED');
  assert.equal(result.text, `공지 제목\n\n${prose(300)}`);
  assert.ok(!result.text.includes('viewstate-secret') && !result.text.includes('query-secret') && !result.text.includes('검색'));
});

test('a short page whose login form is unwrapped is still a login wall', async () => {
  const { result } = await collect(page('<form id="form1"><p>회원 로그인</p><input type="text"><input type="password"></form>'));
  assert.equal(result.code, 'LOGIN_REQUIRED');
});

test('preformatted text keeps its own line breaks', () => {
  const { document } = parseHTML('<html><body><pre>a\n  b</pre><p>c\n d</p></body></html>');
  assert.equal(normalizeText(elementText(document.body)), 'a\nb\n\nc d');
});

test('normalization removes control and zero-width characters and collapses repeats', () => {
  assert.equal(normalizeText('a\u0000b\u200Bc\uFEFF d\t\te\r\n\n\n\nf'), 'a bc d e\n\nf');
  assert.equal(normalizeText('반복\n\n반복\n\n반복\n\n끝'), '반복\n\n끝');
  assert.equal(normalizeText('두번\n두번\n끝'), '두번\n두번\n끝');
});

test('EUC-KR pages decode from the header, the meta tag and the Korean code page aliases', async () => {
  // ASCII stays as is and "한글" becomes its two EUC-KR byte pairs.
  const bytes = (text) => Buffer.concat(text.split('한글').flatMap((part, index) =>
    (index === 0 ? [Buffer.from(part)] : [Buffer.from([0xc7, 0xd1, 0xb1, 0xdb]), Buffer.from(part)])));
  assert.equal(decodeHtml(bytes('<p>한글</p>'), { contentType: 'text/html; charset=euc-kr' }), '<p>한글</p>');
  assert.equal(decodeHtml(bytes('<p>한글</p>'), { contentType: 'text/html; charset=cp949' }), '<p>한글</p>');
  assert.equal(decodeHtml(bytes('<meta charset="ks_c_5601-1987"><p>한글</p>'), { contentType: 'text/html' }),
    '<meta charset="ks_c_5601-1987"><p>한글</p>');
  assert.equal(decodeHtml(bytes('<meta http-equiv="Content-Type" content="text/html; charset=x-windows-949">한글')),
    '<meta http-equiv="Content-Type" content="text/html; charset=x-windows-949">한글');
  assert.equal(decodeHtml(Buffer.from('<p>ok 한글</p>'), { contentType: 'text/html; charset=no-such-label' }), '<p>ok 한글</p>');
  assert.equal(decodeHtml(Buffer.from('<meta charset="utf-16"><p>한글</p>')), '<meta charset="utf-16"><p>한글</p>');
  assert.equal(decodeHtml(Buffer.from('<meta charset="UTF-16LE"><p>한글</p>')), '<meta charset="UTF-16LE"><p>한글</p>');
  assert.equal(decodeHtml(Buffer.concat([Buffer.from([0xef, 0xbb, 0xbf]), Buffer.from('한글')]),
    { contentType: 'text/html; charset=euc-kr' }), '한글');

  const body = Buffer.concat([
    Buffer.from('<html><head><meta charset="cp949"></head><body><article><p>'),
    Buffer.from([0xc7, 0xd1, 0xb1, 0xdb]),
    Buffer.from(` ${'a'.repeat(30)}</p></article></body></html>`),
  ]);
  const { result } = await collect({ '/page': route(body, { type: 'text/html' }) });
  assert.equal(result.text, `한글 ${'a'.repeat(30)}`);
});

test('a page over 2MB is read up to the limit and a character cut at the limit is dropped', async () => {
  const paragraph = `<p>${prose(300)}</p>`;
  const filler = paragraph.repeat(Math.ceil(2_100_000 / Buffer.byteLength(paragraph)));
  const { result } = await collect(page(`<article>${filler}</article>`));
  assert.equal(result.outcome, 'COLLECTED');
  assert.equal(result.truncation, 'HTML');
  assert.ok(!result.text.includes('\uFFFD'));
  const cut = Buffer.from('글자').subarray(0, 4);
  assert.equal(decodeHtml(cut, { isBodyTruncated: true }), '글');
});

test('text over 100,000 characters is cut without splitting a surrogate pair', async () => {
  const text = `${'가'.repeat(99_999)}😀${'나'.repeat(10)}`;
  const { result } = await collect(page(`<article><p>${text}</p></article>`));
  assert.equal(result.truncation, 'TEXT');
  assert.equal(result.text.length, 99_999);
  assert.ok(result.text.endsWith('가'));
});

test('X-Robots-Tag counts only unprefixed and googlebot directives, one header line at a time', () => {
  assert.equal(isRobotsHeaderDenied('noindex'), true);
  assert.equal(isRobotsHeaderDenied('NoArchive, nofollow'), true);
  assert.equal(isRobotsHeaderDenied('googlebot: noindex'), true);
  assert.equal(isRobotsHeaderDenied('bingbot: noindex, nofollow'), false);
  assert.equal(isRobotsHeaderDenied('bingbot: nofollow, googlebot: noarchive'), true);
  assert.equal(isRobotsHeaderDenied('bingbot: nofollow\nnoindex'), true);
  assert.equal(isRobotsHeaderDenied('unavailable_after: 25 Jun 2030 15:00:00 PST, max-snippet: 20'), false);
  assert.equal(isRobotsHeaderDenied('nosnippet'), false);
  assert.equal(isRobotsHeaderDenied(undefined), false);
});

test('an X-Robots-Tag aimed at another crawler does not block the page', async () => {
  const { result } = await collect(page(`<p>${prose(300)}</p>`, '', { headers: { 'x-robots-tag': 'bingbot: noindex' } }));
  assert.equal(result.outcome, 'COLLECTED');
});

test('noindex in the X-Robots-Tag header or the robots meta blocks the page', async () => {
  const header = await collect(page(`<p>${prose(300)}</p>`, '', { headers: { 'x-robots-tag': 'googlebot: noarchive' } }));
  assert.equal(header.result.outcome, 'BLOCKED');
  assert.equal(header.result.code, 'ROBOTS_NOINDEX');
  assert.equal(header.result.text, null);
  const meta = await collect(page(`<p>${prose(300)}</p>`, '<meta name="robots" content="noindex, follow">'));
  assert.equal(meta.result.code, 'ROBOTS_NOINDEX');
});

test('upstream statuses are classified and Retry-After is passed on', async () => {
  const cases = [
    [401, 'LOGIN_REQUIRED', 'BLOCKED'],
    [403, 'ACCESS_DENIED', 'BLOCKED'],
    [404, 'UPSTREAM_NOT_FOUND', 'FAILED'],
    [410, 'UPSTREAM_NOT_FOUND', 'FAILED'],
    [429, 'UPSTREAM_RATE_LIMITED', 'FAILED'],
    [503, 'UPSTREAM_SERVER_ERROR', 'FAILED'],
    [418, 'UPSTREAM_OTHER_STATUS', 'FAILED'],
  ];
  for (const [status, code, outcome] of cases) {
    const { result } = await collect(page('<p>오류 본문</p>', '', { status, headers: { 'retry-after': '120' } }));
    assert.equal(result.code, code, String(status));
    assert.equal(result.outcome, outcome, String(status));
    assert.equal(result.upstreamStatus, status);
    assert.equal(result.retryAfterSeconds, 120);
    assert.equal(result.text, null);
  }
});

test('a non-HTML response is not extracted', async () => {
  const { result } = await collect({ '/page': route('{"a":1}', { type: 'application/json' }) });
  assert.equal(result.code, 'NOT_HTML');
  assert.equal(result.outcome, 'FAILED');
});

test('a redirect to a private address is denied before it is requested', async () => {
  const routes = { '/page': (_request, response) => { response.statusCode = 302; response.setHeader('location', 'http://private.example/x'); response.end(); } };
  const resolveHost = async (host) => (host === 'private.example' ? [{ address: '10.0.0.1', family: 4 }] : PUBLIC);
  const { result, entries } = await collect(routes, { resolveHost });
  assert.equal(result.code, 'TARGET_DENIED');
  assert.ok(entries.some((entry) => entry.event === 'LINK_CONTENT_EXTERNAL' && entry.host === 'private.example' && entry.status === 'BLOCKED'));
});

test('too many redirects fail with their own code', async () => {
  const routes = { '/page': (_request, response) => { response.statusCode = 302; response.setHeader('location', '/page'); response.end(); } };
  const { result } = await collect(routes);
  assert.equal(result.code, 'TOO_MANY_REDIRECTS');
});

test('the deadline covers the whole fetch and ends a slow page as a timeout', async () => {
  const routes = { '/page': (_request, response) => { response.setHeader('content-type', 'text/html'); response.write('<p>'); } };
  const started = Date.now();
  const { result } = await collect(routes, { event: { deadlineMs: 150 } });
  assert.equal(result.code, 'FETCH_TIMEOUT');
  assert.ok(Date.now() - started < 3_000);
});

test('the deadline is the smallest of the request, the remaining time less 5 seconds, and 20 seconds', () => {
  const remaining = (ms) => ({ getRemainingTimeInMillis: () => ms });
  assert.equal(deadlineFor({ deadlineMs: 20_000 }, remaining(30_000)), 20_000);
  assert.equal(deadlineFor({ deadlineMs: 60_000 }, remaining(60_000)), 20_000);
  assert.equal(deadlineFor({ deadlineMs: 20_000 }, remaining(12_000)), 7_000);
  assert.equal(deadlineFor({ deadlineMs: 1_000 }, remaining(30_000)), 1_000);
  assert.equal(deadlineFor({ deadlineMs: 'soon' }, remaining(30_000)), 20_000);
  assert.equal(deadlineFor({ deadlineMs: 20_000 }, remaining(3_000)), 0);
});

test('a short page with a password field is a login wall, a long one is content', async () => {
  const login = await collect(page('<main><p>로그인이 필요합니다</p><form><input type="password"></form></main>'));
  assert.equal(login.result.code, 'LOGIN_REQUIRED');
  assert.equal(login.result.outcome, 'BLOCKED');
  const article = await collect(page(`<article><p>${prose(400)}</p></article><form><input type="password"></form>`));
  assert.equal(article.result.outcome, 'COLLECTED');
});

test('a short article that loads reCAPTCHA for its comment form is content', async () => {
  const body = [
    `<article><p>${prose(120)}</p></article>`,
    '<script src="https://www.google.com/recaptcha/api.js" async defer></script>',
    '<form><div class="g-recaptcha" data-sitekey="key"></div><button>댓글 등록</button></form>',
  ].join('');
  const { result } = await collect(page(body));
  assert.equal(result.outcome, 'COLLECTED');
  assert.equal(result.text, prose(120));
});

test('a short page whose visible text asks for a captcha is a bot wall', async () => {
  const { result } = await collect(page('<main><p>계속하려면 CAPTCHA를 완료하세요</p></main>'));
  assert.equal(result.code, 'BOT_WALL');
});

test('a short bot challenge page is a bot wall', async () => {
  const { result } = await collect(page('<div id="cf-chl-widget"><p>사람인지 확인하는 중</p></div>', '<title>Just a moment...</title>'));
  assert.equal(result.code, 'BOT_WALL');
  assert.equal(result.outcome, 'BLOCKED');
});

test('JSON-LD marking the page as not free is a paywall', async () => {
  const ld = '<script type="application/ld+json">{"@type":"NewsArticle","isAccessibleForFree":"False"}</script>';
  const { result } = await collect(page(`<article><p>${prose(400)}</p></article>`, ld));
  assert.equal(result.code, 'PAYWALL');
  assert.equal(result.outcome, 'BLOCKED');
});

const notices = {
  'soft 404 page': '<main><h1>페이지를 찾을 수 없습니다</h1><p>요청하신 페이지가 존재하지 않습니다.</p></main>',
  'SPA loading shell': '<div id="root"><p>Loading...</p></div>',
  'cookie consent only': '<div><p>이 사이트는 쿠키를 사용합니다.</p><p>계속하려면 동의해 주세요.</p></div>',
  'region restriction': '<main><p>This content is not available in your country.</p></main>',
};
for (const [name, body] of Object.entries(notices)) {
  test(`a ${name} is not content`, async () => {
    const { result } = await collect(page(body));
    assert.equal(result.code, 'NOT_CONTENT');
    assert.equal(result.outcome, 'FAILED');
    assert.equal(result.text, null);
  });
}

test('a short body that only repeats the title is not content', async () => {
  const { result } = await collect(page('<main><p>에이링크 소개</p></main>', '<title>에이링크 소개</title>'));
  assert.equal(result.code, 'NOT_CONTENT');
});

test('a page with no text is empty', async () => {
  const { result } = await collect(page('<nav>메뉴</nav><main><script>render()</script></main>'));
  assert.equal(result.code, 'CONTENT_EMPTY');
});

test('a page with too many nodes is not extracted', async () => {
  const { result } = await collect(page(`<p>${prose(300)}</p>${'<i></i>'.repeat(200_001)}`));
  assert.equal(result.code, 'EXTRACTION_FAILED');
});

test('when Readability gives up, the main element is used and without it extraction fails', async () => {
  const crowd = `<div>${'<span></span>'.repeat(100_001)}</div>`;
  const withMain = await collect(page(`${crowd}<main><p>${prose(300)}</p></main>`));
  assert.equal(withMain.result.outcome, 'COLLECTED');
  assert.equal(withMain.result.text, prose(300));
  const without = await collect(page(`${crowd}<div><p>${prose(300)}</p></div>`));
  assert.equal(without.result.code, 'EXTRACTION_FAILED');
});

test('an invalid event fails without any request', async () => {
  let requested = false;
  const handler = createContentHandler({ environment: 'dev', logger: { log: () => {} }, fetcher: async () => { requested = true; } });
  for (const event of [{}, { url: 42 }, { url: 'ftp://example.com/' }, { url: 'not a url' }, null]) {
    const result = await handler(event, LONG_CONTEXT);
    assert.equal(result.code, 'INVALID_EVENT');
    assert.equal(result.outcome, 'FAILED');
  }
  assert.equal(requested, false);
});

test('an unexpected error is an extraction failure', async () => {
  const handler = createContentHandler({
    environment: 'dev', logger: { log: () => {} },
    fetcher: async () => ({ status: 200, contentType: 'text/html', body: null, finalUrl: 'https://example.com/' }),
  });
  const result = await handler({ url: 'https://example.com/' }, LONG_CONTEXT);
  assert.equal(result.code, 'EXTRACTION_FAILED');
});

test('logs carry the outcome without the body, headers, path or query', async () => {
  const { result, entries } = await collect(page(`<article><p>${prose(300)} 비밀본문</p></article>`), { path: '/page?token=top-secret' });
  assert.equal(result.outcome, 'COLLECTED');
  const summary = entries.find((entry) => entry.event === 'LINK_CONTENT_RESULT');
  assert.deepEqual(summary, {
    event: 'LINK_CONTENT_RESULT', traceId: 'trace-1', outcome: 'COLLECTED', code: null, upstreamStatus: 200,
    textLength: result.text.length, truncation: 'NONE', durationMs: summary.durationMs,
  });
  assert.ok(entries.some((entry) => entry.event === 'LINK_CONTENT_EXTERNAL' && entry.host === 'public.example' && entry.status === 200));
  for (const entry of entries) {
    const line = JSON.stringify(entry);
    assert.ok(!line.includes('top-secret') && !line.includes('/page') && !line.includes('비밀본문'), line);
  }
});

test('the content request asks for HTML in Korean first', async () => {
  let headers;
  const routes = { '/page': (request, response) => { headers = request.headers; route(html(`<p>${prose(300)}</p>`))(request, response); } };
  await collect(routes);
  assert.match(headers.accept, /^text\/html/);
  assert.match(headers['accept-language'], /^ko-KR/);
  assert.equal(headers['accept-encoding'], 'identity');
});

test('only dev and prod deployments start', () => {
  assert.throws(() => createContentHandler({ environment: 'staging' }));
  assert.doesNotThrow(() => createContentHandler({ environment: 'prod' }));
});
