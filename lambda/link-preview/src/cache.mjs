import { createHash } from 'node:crypto';
import { HeadObjectCommand, PutObjectCommand } from '@aws-sdk/client-s3';

const WEEK_MS = 7 * 24 * 60 * 60 * 1000;
const EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp'];

export function previewHash(url) {
  const match = /^([a-z][a-z\d+.-]*):\/\/([^/?#]*)(.*)$/i.exec(url);
  if (!match) throw new Error('invalid preview url');
  return createHash('sha256').update(`${match[1].toLowerCase()}://${match[2].toLowerCase()}${match[3]}`).digest('hex');
}

export function faviconHash(url) {
  return createHash('sha256').update(new URL(url).hostname.toLowerCase()).digest('hex');
}

export function objectKey(environment, kind, hash, extension) {
  if (!['dev', 'prod'].includes(environment) || !['preview', 'favicon'].includes(kind) ||
      !/^[a-f0-9]{64}$/.test(hash) || !EXTENSIONS.includes(extension)) {
    throw new Error('invalid object key parts');
  }
  return `${environment}-${kind}-${hash}.${extension}`;
}

function notFound(error) {
  return error?.$metadata?.httpStatusCode === 404 || ['NotFound', 'NoSuchKey'].includes(error?.name);
}

export async function findObject(s3, bucket, environment, kind, hash, now = Date.now()) {
  let newest = null;
  for (const ext of EXTENSIONS) {
    const key = objectKey(environment, kind, hash, ext);
    try {
      const head = await s3.send(new HeadObjectCommand({ Bucket: bucket, Key: key }));
      const updatedAt = head.LastModified?.getTime();
      const object = {
        key,
        etag: head.ETag,
        fresh: updatedAt != null && now - updatedAt < WEEK_MS,
        updatedAt: updatedAt ?? 0,
      };
      if (!newest || object.updatedAt > newest.updatedAt) newest = object;
    } catch (error) {
      if (!notFound(error)) throw error;
    }
  }
  return newest;
}

export async function putImage(s3, bucket, environment, kind, hash, image, existing) {
  const key = objectKey(environment, kind, hash, image.ext);
  const input = {
    Bucket: bucket,
    Key: key,
    Body: image.body,
    ContentType: image.contentType,
    CacheControl: 'public, max-age=86400',
    ...(existing?.key === key && existing.etag ? { IfMatch: existing.etag } : { IfNoneMatch: '*' }),
  };
  try {
    await s3.send(new PutObjectCommand(input));
  } catch (error) {
    if (error?.$metadata?.httpStatusCode !== 412) throw error;
    const head = await s3.send(new HeadObjectCommand({ Bucket: bucket, Key: key }));
    if (!head.ETag) throw error;
  }
  return key;
}
