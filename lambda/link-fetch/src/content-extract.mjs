import { parseHTML } from 'linkedom';
import { Readability } from '@mozilla/readability';

export const MAX_TEXT_LENGTH = 100_000;
// Counted as elements (linkedom `querySelectorAll('*')`), not all DOM nodes.
const MAX_ELEMENTS = 200_000;
// Readability's own `maxElemsToParse` never trips under linkedom (`getElementsByTagName('*')` is empty), so the limit
// is checked here.
const MAX_READABILITY_ELEMENTS = 100_000;
const SHORT_TEXT_LENGTH = 200;
const META_PRESCAN_BYTES = 2048;
const META_LIMITS = { title: 500, siteName: 200, author: 200, language: 16 };

const KOREAN_ALIASES = new Set(['cp949', 'ms949', 'ks_c_5601-1987', 'ks_c_5601-1989', 'x-windows-949']);
const STRIPPED = [
  'script', 'style', 'noscript', 'template', 'iframe', 'svg', 'canvas', 'input', 'textarea', 'select', 'button',
  '[aria-hidden=true]', 'nav', 'footer', 'aside',
  '[role=navigation]', '[role=banner]', '[role=contentinfo]', '[role=complementary]',
].join(',');
const BLOCKS = new Set(['P', 'DIV', 'LI', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'BLOCKQUOTE', 'PRE', 'TR', 'SECTION', 'ARTICLE']);
// Bot challenge markers by where they count: challenge ids in the page source, the interstitial title, and "captcha" only
// in the visible text (a reCAPTCHA script on a comment form is not a wall).
const BOT_SOURCE_MARKERS = /cf-chl|challenge-form|cf-browser-verification/i;
const BOT_TITLE_MARKERS = /just a moment/i;
const BOT_TEXT_MARKERS = /captcha/i;
// A short text is not content when every line is one of these notices: soft 404, loading shell, cookie consent,
// region restriction. Tuned with sample pages.
const NOTICE_PATTERNS = /찾을 수 없|존재하지 않|not found|404|page unavailable|loading|잠시만 기다|redirect|access denied|not available in your|이용할 수 없는 지역|쿠키|cookie|동의/i;
const ROBOTS_DENY = /\b(noindex|noarchive)\b/i;
// Directives that carry their own `name: value`, so their name is not a user-agent prefix.
const VALUED_ROBOTS_DIRECTIVES = new Set(['unavailable_after', 'max-snippet', 'max-image-preview', 'max-video-preview']);

// --- Encoding -------------------------------------------------------------------------------------------------------

function normalizeLabel(label) {
  const lower = label?.trim().toLowerCase().replace(/^["']|["']$/g, '');
  if (!lower) return null;
  return KOREAN_ALIASES.has(lower) ? 'euc-kr' : lower;
}

const charsetIn = (text) => /charset\s*=\s*["']?\s*([\w:.-]+)/i.exec(text || '')?.[1];

function sniffLabel(bytes, contentType) {
  if (bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) return 'utf-8';
  if (bytes[0] === 0xfe && bytes[1] === 0xff) return 'utf-16be';
  if (bytes[0] === 0xff && bytes[1] === 0xfe) return 'utf-16le';
  const fromHeader = normalizeLabel(charsetIn(contentType));
  if (fromHeader) return fromHeader;
  const head = bytes.subarray(0, META_PRESCAN_BYTES).toString('latin1');
  for (const [tag] of head.matchAll(/<meta\b[^>]*>/gi)) {
    const label = normalizeLabel(charsetIn(tag));
    // As in browsers, a <meta> cannot switch a BOM-less byte stream to UTF-16.
    if (label) return encodingOf(label)?.startsWith('utf-16') ? 'utf-8' : label;
  }
  return 'utf-8';
}

function encodingOf(label) {
  try {
    return new TextDecoder(label).encoding;
  } catch {
    return null;
  }
}

function decoderFor(label) {
  try {
    return new TextDecoder(label, { fatal: false });
  } catch {
    return new TextDecoder('utf-8', { fatal: false });
  }
}

// BOM, then Content-Type charset, then a <meta> charset in the first 2KB, then UTF-8. Unknown labels fall back to UTF-8.
// A body cut at a byte limit may end inside a character, so the replacement characters at its end are dropped.
export function decodeHtml(bytes, { contentType = '', isBodyTruncated = false } = {}) {
  let text = decoderFor(sniffLabel(bytes, contentType)).decode(bytes);
  if (isBodyTruncated) text = text.replace(/\uFFFD+$/, '');
  return text.normalize('NFC');
}

// --- Text ------------------------------------------------------------------------------------------------------------

function collectText(node, parts, inPre) {
  for (const child of node.childNodes) {
    if (child.nodeType === 3) {
      parts.push(inPre ? child.textContent : child.textContent.replace(/\s+/g, ' '));
    } else if (child.nodeType === 1) {
      const tag = child.tagName.toUpperCase();
      if (tag === 'BR') {
        parts.push('\n');
        continue;
      }
      const block = BLOCKS.has(tag);
      if (block) parts.push('\n\n');
      collectText(child, parts, inPre || tag === 'PRE');
      if (block) parts.push('\n\n');
    }
  }
}

export function elementText(element) {
  const parts = [];
  collectText(element, parts, element.tagName?.toUpperCase() === 'PRE');
  return parts.join('');
}

function dropRepeatedLines(lines) {
  const kept = [];
  let index = 0;
  while (index < lines.length) {
    const line = lines[index];
    if (line === '') {
      kept.push(line);
      index += 1;
      continue;
    }
    // A run is the same line repeated, with only blank lines between the copies.
    let end = index + 1;
    let copies = 1;
    let next = end;
    while (next < lines.length) {
      while (next < lines.length && lines[next] === '') next += 1;
      if (lines[next] !== line) break;
      copies += 1;
      next += 1;
      end = next;
    }
    if (copies >= 3) kept.push(line);
    else kept.push(...lines.slice(index, end));
    index = end;
  }
  return kept;
}

// Removes control and zero-width characters (keeping line breaks), collapses spaces within a line, keeps at most one
// blank line between blocks and keeps a single copy of a line repeated three or more times.
export function normalizeText(raw) {
  const cleaned = raw
    .replace(/\r\n?/g, '\n')
    .replace(/[\u200B-\u200D\u2060\uFEFF]/g, '')
    .replace(/[\u0000-\u0008\u000B-\u001F\u007F-\u009F]/g, ' ');
  const lines = cleaned.split('\n').map((line) => line.replace(/\s+/g, ' ').trim());
  return dropRepeatedLines(lines).join('\n').replace(/\n{3,}/g, '\n\n').trim();
}

function cutKeepingSurrogates(text, limit) {
  if (text.length <= limit) return text;
  const code = text.charCodeAt(limit - 1);
  return text.slice(0, code >= 0xd800 && code <= 0xdbff ? limit - 1 : limit);
}

const bounded = (value, limit) => {
  const text = typeof value === 'string' ? value.replace(/\s+/g, ' ').trim() : '';
  return text ? cutKeepingSurrogates(text, limit) : null;
};

// An X-Robots-Tag value (one header per line) denies storing when a directive with no user-agent prefix, or with the
// `googlebot` prefix, is noindex or noarchive. A prefix applies to the directives after it on the same header line.
export function isRobotsHeaderDenied(value) {
  return (value || '').split('\n').some((line) => {
    let agent = null;
    return line.split(',').some((part) => {
      const prefixed = /^\s*([\w-]+)\s*:(.*)$/.exec(part);
      let directive = part;
      if (prefixed && !VALUED_ROBOTS_DIRECTIVES.has(prefixed[1].toLowerCase())) {
        agent = prefixed[1].toLowerCase();
        directive = prefixed[2];
      }
      return (agent == null || agent === 'googlebot') && ROBOTS_DENY.test(directive);
    });
  });
}

// --- Page facts ------------------------------------------------------------------------------------------------------

function jsonLdNodes(document) {
  const nodes = [];
  const visit = (value, depth) => {
    if (!value || typeof value !== 'object' || depth > 6 || nodes.length > 200) return;
    if (Array.isArray(value)) {
      value.forEach((item) => visit(item, depth + 1));
      return;
    }
    nodes.push(value);
    if (value['@graph']) visit(value['@graph'], depth + 1);
    if (value.hasPart) visit(value.hasPart, depth + 1);
  };
  for (const script of document.querySelectorAll('script[type="application/ld+json"]')) {
    try {
      visit(JSON.parse(script.textContent), 0);
    } catch {
      // A broken JSON-LD block is ignored.
    }
  }
  return nodes;
}

const authorName = (value) => {
  const first = Array.isArray(value) ? value[0] : value;
  return typeof first === 'string' ? first : first?.name;
};

function isoDate(value) {
  if (typeof value !== 'string' || value.trim() === '') return null;
  const time = Date.parse(value.trim());
  return Number.isNaN(time) ? null : new Date(time).toISOString();
}

function httpUrl(href, base) {
  try {
    const url = new URL(href, base);
    return ['http:', 'https:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

function pageFacts(document, html, finalUrl) {
  const meta = (selector) => document.querySelector(selector)?.getAttribute('content') ?? null;
  const ld = jsonLdNodes(document);
  const robots = [...document.querySelectorAll('meta[name=robots i], meta[name=googlebot i]')]
    .map((element) => element.getAttribute('content') || '').join(' ');
  const documentTitle = document.querySelector('title')?.textContent ?? null;
  const language = document.documentElement?.getAttribute('lang')?.trim();
  const canonical = document.querySelector('link[rel=canonical i]')?.getAttribute('href');
  return {
    isNoIndex: ROBOTS_DENY.test(robots),
    isPaywalled: ld.some((node) => node.isAccessibleForFree === false || /^false$/i.test(String(node.isAccessibleForFree))),
    hasPassword: document.querySelector('input[type=password i]') != null,
    hasBotMarker: BOT_SOURCE_MARKERS.test(html) || BOT_TITLE_MARKERS.test(documentTitle || ''),
    titles: [meta('meta[property="og:title"]'), documentTitle].map((value) => bounded(value, META_LIMITS.title)).filter(Boolean),
    meta: {
      canonicalUrl: canonical ? httpUrl(canonical, finalUrl) : null,
      title: bounded(meta('meta[property="og:title"]') ?? meta('meta[name="twitter:title"]') ?? documentTitle, META_LIMITS.title),
      siteName: bounded(meta('meta[property="og:site_name"]'), META_LIMITS.siteName),
      author: bounded(meta('meta[name=author i]') ?? meta('meta[property="article:author"]') ??
        authorName(ld.find((node) => node.author)?.author), META_LIMITS.author),
      publishedAt: isoDate(meta('meta[property="article:published_time"]') ??
        ld.find((node) => node.datePublished)?.datePublished),
      language: language && /^[a-z]{2,3}(-[a-z0-9]{1,8})*$/i.test(language) && language.length <= META_LIMITS.language
        ? language : null,
    },
  };
}

// --- Extraction ------------------------------------------------------------------------------------------------------

// A <form> is unwrapped rather than removed: ASP.NET WebForms pages wrap the whole body in one. Its fields are already
// removed above, and the password and paywall facts are read from the original document.
function strippedCopy(document) {
  const copy = document.cloneNode(true);
  copy.querySelectorAll(STRIPPED).forEach((element) => element.remove());
  copy.querySelectorAll('form').forEach((form) => form.replaceWith(...form.childNodes));
  return copy;
}

function readableText(document) {
  const copy = strippedCopy(document);
  let article = null;
  if (copy.querySelectorAll('*').length <= MAX_READABILITY_ELEMENTS) {
    article = new Readability(copy, { charThreshold: 1 }).parse();
  }
  if (article?.content) {
    const { document: content } = parseHTML(`<!doctype html><html><body>${article.content}</body></html>`);
    return elementText(content.body);
  }
  const fallback = strippedCopy(document).querySelector('article, main');
  return fallback ? elementText(fallback) : null;
}

const sameText = (a, b) => a.replace(/\s+/g, ' ').trim().toLowerCase() === b.replace(/\s+/g, ' ').trim().toLowerCase();

function isNotice(text, titles) {
  if (titles.some((title) => sameText(text, title))) return true;
  return text.split('\n').filter(Boolean).every((line) => NOTICE_PATTERNS.test(line));
}

// Turns decoded HTML into `{ code }` for a page that is not stored, or `{ text, isTextTruncated, meta }`.
export function extractContent(html, { finalUrl }) {
  const { document } = parseHTML(html);
  if (document.querySelectorAll('*').length > MAX_ELEMENTS) return { code: 'EXTRACTION_FAILED' };
  const facts = pageFacts(document, html, finalUrl);
  if (facts.isNoIndex) return { code: 'ROBOTS_NOINDEX' };
  if (facts.isPaywalled) return { code: 'PAYWALL' };

  const raw = readableText(document);
  if (raw == null) return { code: 'EXTRACTION_FAILED' };
  const text = normalizeText(raw);
  if (text === '') return { code: 'CONTENT_EMPTY' };
  if (text.length < SHORT_TEXT_LENGTH) {
    if (facts.hasPassword) return { code: 'LOGIN_REQUIRED' };
    if (facts.hasBotMarker || BOT_TEXT_MARKERS.test(text)) return { code: 'BOT_WALL' };
    if (isNotice(text, facts.titles)) return { code: 'NOT_CONTENT' };
  }
  const cut = cutKeepingSurrogates(text, MAX_TEXT_LENGTH);
  return { text: cut, isTextTruncated: cut.length < text.length, meta: facts.meta };
}
