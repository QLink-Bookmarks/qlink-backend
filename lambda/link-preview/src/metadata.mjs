import ogs from 'open-graph-scraper';
import { load } from 'cheerio';

function absoluteCandidates(values, baseUrl) {
  return [...new Set(values.map((value) => {
    try { return new URL(value, baseUrl).href; } catch { return null; }
  }).filter(Boolean))];
}

export async function parsePage(html, pageUrl, mode, ogParser = ogs) {
  const $ = load(html);
  const baseHref = $('base[href]').first().attr('href');
  let baseUrl = pageUrl;
  try { if (baseHref) baseUrl = new URL(baseHref, pageUrl).href; } catch { /* use page URL */ }

  const icons = [];
  const appleIcons = [];
  $('link[rel][href]').each((_index, element) => {
    const rel = ($(element).attr('rel') || '').toLowerCase().split(/\s+/);
    const href = $(element).attr('href');
    if (rel.includes('icon')) icons.push(href);
    else if (rel.includes('apple-touch-icon') || rel.includes('apple-touch-icon-precomposed')) appleIcons.push(href);
  });
  const faviconUrls = [
    ...absoluteCandidates([...icons, ...appleIcons], baseUrl),
    ...absoluteCandidates(['/favicon.ico'], pageUrl),
  ];
  if (mode === 'FAVICON_ONLY') {
    return { title: null, isVideo: false, imageUrls: [], faviconUrls };
  }

  const parsed = await ogParser({ html, onlyGetOpenGraphInfo: true });
  const ogTitle = typeof parsed.result?.ogTitle === 'string' ? parsed.result.ogTitle : null;
  const htmlTitle = $('title').first().text();
  const title = (ogTitle?.trim() || htmlTitle.trim()).slice(0, 300) || null;
  const meta = (name) => $('meta').filter((_index, element) =>
    ($(element).attr('property') || $(element).attr('name') || '').toLowerCase() === name
  ).map((_index, element) => $(element).attr('content')).get().filter(Boolean);
  const ogType = meta('og:type')[0]?.toLowerCase() || '';
  const isVideo = ogType.startsWith('video') || meta('og:video').length > 0 ||
    meta('twitter:card').some((card) => card.toLowerCase() === 'player');
  const imageUrls = absoluteCandidates([...meta('og:image'), ...meta('twitter:image')], baseUrl);
  return { title, isVideo, imageUrls, faviconUrls };
}
