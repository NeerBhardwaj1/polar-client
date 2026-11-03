'use strict';
// Lets the running game list the launcher's accounts and switch to one without restarting.
// Listens on 127.0.0.1 only, on a random port, and every request must carry a random secret that is handed to
// the game process through an environment variable (never on the command line).
const http = require('node:http');
const crypto = require('node:crypto');

const MAX_BODY = 16 * 1024;
const FILE_TYPES = { json: 'application/json', png: 'image/png' };
const PRESENCE_STATES = ['menu', 'singleplayer', 'server', 'hidden'];

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.setEncoding('utf8');
    req.on('data', (chunk) => {
      data += chunk;
      if (data.length > MAX_BODY) {
        reject(Object.assign(new Error('Request too large'), { status: 413 }));
        req.destroy();
      }
    });
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}

function sameSecret(a, b) {
  const x = Buffer.from(String(a || ''));
  const y = Buffer.from(String(b || ''));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

/**
 * handlers: { listAccounts(): {selected, accounts}, session(uuid): Promise<{uuid,name,type,accessToken,xuid}>,
 *   presence?({state, server}) (where the game is, for the Discord status and friends),
 *   polar?: { state(), file(rel) -> Buffer, lookup(uuids) -> {uuid: {badge, slots}}, events() -> [...] } (Polar cosmetics,
 *   badge and friends: the game asks the launcher, which talks to the Polar server and keeps the files) }
 * Resolves with { port, token, env, close() }.
 */
async function startBridge(handlers) {
  const token = crypto.randomBytes(24).toString('hex');
  const server = http.createServer(async (req, res) => {
    const reply = (status, body) => {
      res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      res.end(JSON.stringify(body));
    };
    try {
      if (!sameSecret(req.headers['x-polar-token'], token)) return reply(403, { error: 'Forbidden' });
      if (req.method === 'GET' && req.url === '/accounts') {
        const { selected, accounts } = handlers.listAccounts();
        return reply(200, { selected, accounts: accounts.map(({ uuid, name, type }) => ({ uuid, name, type })) });
      }
      if (req.method === 'POST' && req.url === '/session') {
        const { uuid } = JSON.parse((await readBody(req)) || '{}');
        if (typeof uuid !== 'string' || !/^[0-9a-f-]{32,36}$/i.test(uuid)) return reply(400, { error: 'Bad account id' });
        return reply(200, await handlers.session(uuid));
      }
      if (req.method === 'POST' && req.url === '/presence') {
        const { state, server } = JSON.parse((await readBody(req)) || '{}');
        if (!PRESENCE_STATES.includes(state)) return reply(400, { error: 'Bad state' });
        // a host name or address with an optional port; anything else is dropped rather than shown on Discord
        const host = typeof server === 'string' && /^[A-Za-z0-9.\-:[\]_]{1,100}$/.test(server) ? server : null;
        if (handlers.presence) handlers.presence({ state, server: state === 'server' ? host : null });
        return reply(200, { ok: true });
      }
      if (handlers.polar && req.url.startsWith('/polar/')) {
        const url = new URL(req.url, 'http://bridge');
        if (req.method === 'GET' && url.pathname === '/polar/state') return reply(200, await handlers.polar.state());
        if (req.method === 'GET' && url.pathname === '/polar/events') return reply(200, { events: handlers.polar.events() });
        if (req.method === 'POST' && url.pathname === '/polar/players') {
          const { uuids } = JSON.parse((await readBody(req)) || '{}');
          return reply(200, { players: await handlers.polar.lookup(Array.isArray(uuids) ? uuids.slice(0, 200) : []) });
        }
        if (req.method === 'GET' && url.pathname === '/polar/file') {
          const rel = String(url.searchParams.get('path') || '');
          const buf = await handlers.polar.file(rel);
          res.writeHead(200, { 'Content-Type': FILE_TYPES[rel.split('.').pop()] || 'application/octet-stream', 'Content-Length': buf.length, 'Cache-Control': 'no-store' });
          return res.end(buf);
        }
      }
      return reply(404, { error: 'Not found' });
    } catch (e) {
      return reply(e.status || 500, { error: e && e.message ? e.message : String(e) });
    }
  });
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  const { port } = server.address();
  return {
    port,
    token,
    env: { POLAR_BRIDGE: `${port}:${token}` },
    close: () => new Promise((resolve) => server.close(() => resolve())),
  };
}

module.exports = { startBridge };
