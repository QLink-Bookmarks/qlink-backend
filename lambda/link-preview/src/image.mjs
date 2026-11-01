import sharp from 'sharp';
import { extractLargestImageAsPng } from '@humanwhocodes/ico-to-png';

const MAX_PIXELS = 16_000_000;
const PNG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

export function imageType(bytes) {
  if (bytes.subarray(0, 8).equals(PNG)) return { ext: 'png', contentType: 'image/png' };
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) {
    return { ext: 'jpg', contentType: 'image/jpeg' };
  }
  if (bytes.subarray(0, 6).toString('ascii').match(/^GIF8[79]a$/)) {
    return { ext: 'gif', contentType: 'image/gif' };
  }
  if (bytes.subarray(0, 4).toString('ascii') === 'RIFF' && bytes.subarray(8, 12).toString('ascii') === 'WEBP') {
    return { ext: 'webp', contentType: 'image/webp' };
  }
  if (bytes.length >= 4 && bytes.readUInt32LE(0) === 0x00010000) return { ext: 'ico' };
  if (/^\s*(?:<\?xml[^>]*>\s*)?<svg(?:\s|>)/i.test(bytes.toString('utf8', 0, 512))) return { ext: 'svg' };
  return null;
}

async function validateRaster(bytes) {
  const metadata = await sharp(bytes, { limitInputPixels: MAX_PIXELS }).metadata();
  if (!metadata.width || !metadata.height || metadata.width * metadata.height > MAX_PIXELS) {
    throw new Error('image dimensions denied');
  }
}

export async function prepareImage(bytes, kind) {
  const type = imageType(bytes);
  if (!type) throw new Error('image format denied');
  if (kind === 'preview' && (type.ext === 'ico' || type.ext === 'svg')) {
    throw new Error('thumbnail format denied');
  }
  if (type.ext === 'ico') {
    const image = extractLargestImageAsPng(bytes);
    if (!image || image.width * image.height > MAX_PIXELS) throw new Error('icon dimensions denied');
    const output = Buffer.from(image.data);
    await validateRaster(output);
    return { body: output, ext: 'png', contentType: 'image/png' };
  }
  if (type.ext === 'svg') {
    const text = bytes.toString('utf8');
    if (/<!(?:DOCTYPE|ENTITY)|<\s*(?:script|image|foreignObject|style)\b|\b(?:href|xlink:href|style|on\w+)\s*=|@import|url\s*\(|&/i.test(text)) {
      throw new Error('unsafe svg denied');
    }
    const metadata = await sharp(bytes, { limitInputPixels: MAX_PIXELS }).metadata();
    if (!metadata.width || !metadata.height || metadata.width * metadata.height > MAX_PIXELS) {
      throw new Error('svg dimensions denied');
    }
    const output = await sharp(bytes, { limitInputPixels: MAX_PIXELS }).png().toBuffer();
    await validateRaster(output);
    return { body: output, ext: 'png', contentType: 'image/png' };
  }
  await validateRaster(bytes);
  return { body: bytes, ext: type.ext, contentType: type.contentType };
}
