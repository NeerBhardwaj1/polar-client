// A local Polar server for trying the admin page and the launcher without Cloudflare:
//   node test/dev-server.mjs [port]      (admin password: dev-admin, Mojang is faked: any name signs in)
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { makeCtx } from './shim.js';
import { Hub } from '../src/hub.js';

const port = Number(process.argv[2] || 8787);
const root = path.join(path.dirname(new URL(import.meta.url).pathname), '..', 'public');
const realFetch = globalThis.fetch;
// fake Mojang: every name exists and every sign-in is confirmed
globalThis.fetch = async (url, init) => {
  const u = new URL(String(url));
  const fakeId = (name) => Buffer.from(name.toLowerCase().padEnd(16, '_')).toString('hex').slice(0, 32);
  if (u.hostname === 'sessionserver.mojang.com') {
    const name = u.searchParams.get('username');
    return new Response(JSON.stringify({ id: fakeId(name), name }), { status: 200 });
  }
  if (u.hostname === 'api.minecraftservices.com') {
    const name = decodeURIComponent(u.pathname.split('/').pop());
    return new Response(JSON.stringify({ id: fakeId(name), name }), { status: 200 });
  }
  return realFetch(url, init);
};
const hub = new Hub(makeCtx(), { TOKEN_SECRET: 'dev-secret-0123456789', ADMIN_TOKEN: 'dev-admin' });
const types = { '.html': 'text/html', '.json': 'application/json', '.png': 'image/png', '.js': 'text/javascript', '.css': 'text/css' };

http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  if (url.pathname.startsWith('/v1/')) {
    const chunks = [];
    for await (const c of req) chunks.push(c);
    const r = await hub.fetch(new Request(url, { method: req.method, headers: req.headers, body: ['GET', 'HEAD'].includes(req.method) ? undefined : Buffer.concat(chunks) }));
    res.writeHead(r.status === 101 ? 200 : r.status, Object.fromEntries(r.headers));
    res.end(r.status === 101 ? '' : Buffer.from(await r.arrayBuffer()));
    return;
  }
  let file = path.join(root, decodeURIComponent(url.pathname));
  if (!file.startsWith(root)) return res.writeHead(403).end();
  if (fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, 'index.html');
  if (!fs.existsSync(file)) return res.writeHead(404).end('Not found');
  res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
}).listen(port, () => console.log(`Polar dev server on http://localhost:${port} (admin password: dev-admin)`));
