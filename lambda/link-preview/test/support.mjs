import http from 'node:http';
import sharp from 'sharp';
import { HeadObjectCommand, PutObjectCommand } from '@aws-sdk/client-s3';

export const png = await sharp({ create: { width: 2, height: 2, channels: 4, background: '#ff0000' } }).png().toBuffer();

export class FakeS3 {
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

export async function withServer(routes, run) {
  const server = http.createServer((request, response) => {
    const route = routes[new URL(request.url, 'http://localhost').pathname];
    if (route) route(request, response);
    else { response.statusCode = 404; response.end(); }
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  try {
    return await run(server.address().port);
  } finally {
    server.closeAllConnections();
    await new Promise((resolve) => server.close(resolve));
  }
}

export const localTarget = (port, path) => ({ url: new URL(`http://public.example:${port}${path}`), address: '127.0.0.1', family: 4 });
