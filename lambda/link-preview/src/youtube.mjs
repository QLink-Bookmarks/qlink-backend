const VIDEO_HOSTS = new Set(['youtube.com', 'www.youtube.com', 'm.youtube.com']);
const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/;
const MAX_TITLE_LENGTH = 300;

// `new URL` drops a default port and an empty user, so the raw authority is checked as well.
function plainUrl(value, protocols) {
  if (typeof value !== 'string') return null;
  let url;
  try { url = new URL(value); } catch { return null; }
  if (!protocols.includes(url.protocol) || url.port || url.username || url.password) return null;
  const authority = /^[\s\0-\x1f]*[a-z][a-z\d+.-]*:[\\/]*([^/\\?#]*)/i.exec(value)?.[1] ?? '';
  if (authority.includes('@') || /:\d*$/.test(authority)) return null;
  return url;
}

export function youtubeVideoId(input) {
  const url = plainUrl(input, ['http:', 'https:']);
  if (!url) return null;
  const path = url.pathname.length > 1 && url.pathname.endsWith('/') ? url.pathname.slice(0, -1) : url.pathname;
  let id = null;
  if (url.hostname === 'youtu.be') {
    id = /^\/([^/]+)$/.exec(path)?.[1];
  } else if (VIDEO_HOSTS.has(url.hostname)) {
    id = path === '/watch' ? url.searchParams.get('v') : /^\/(?:shorts|live|embed)\/([^/]+)$/.exec(path)?.[1];
  }
  return typeof id === 'string' && VIDEO_ID.test(id) ? id : null;
}

export function oembedUrl(videoId) {
  return `https://www.youtube.com/oembed?url=${encodeURIComponent(`https://www.youtube.com/watch?v=${videoId}`)}&format=json`;
}

export function hqdefaultUrl(videoId) {
  return `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;
}

// Throws when the response is not a JSON object from www.youtube.com/oembed.
// A missing or unusable title or thumbnail is returned as null.
export function parseOembed({ body, contentType, finalUrl }) {
  const final = new URL(finalUrl);
  if (final.origin !== 'https://www.youtube.com' || final.pathname !== '/oembed') {
    throw new Error('oembed final url denied');
  }
  if (!String(contentType || '').toLowerCase().startsWith('application/json')) {
    throw new Error('oembed is not json');
  }
  const data = JSON.parse(Buffer.from(body).toString('utf8'));
  if (data === null || typeof data !== 'object' || Array.isArray(data)) throw new Error('oembed is not an object');
  const title = typeof data.title === 'string' ? data.title.trim().slice(0, MAX_TITLE_LENGTH) || null : null;
  const thumbnail = plainUrl(data.thumbnail_url, ['https:']);
  return { title, thumbnailUrl: thumbnail?.hostname === 'i.ytimg.com' ? thumbnail.href : null };
}
