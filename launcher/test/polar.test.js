'use strict';
// The launcher's side of the Polar server: signing in through Mojang, tokens, the live connection, the cosmetics
// cache, lookups for the game, friends and settings sync. The server and Mojang are faked.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { PolarService, dashed } = require('../src/core/polar');
const { startBridge } = require('../src/core/bridge');

const ACC = { uuid: '0f7b6a5c4d3e2f1a0b9c8d7e6f5a4b3c', name: 'Neer', type: 'msa', mcToken: 'mc-token' };
const UUID = dashed(ACC.uuid);

function fakeNet() {
  const calls = [];
  const net = {
    calls,
    joined: new Set(),
    token: 'tok-1',
    rejectToken: null,
    files: { 'catalog.json': JSON.stringify({ items: [{ id: 'cape_aurora', type: 'cape' }] }), 'textures/cape_aurora.png': 'PNGDATA' },
    etag: '"v1"',
    down: false,
    sync: { data: null, updated: 0 },
    players: {},
  };
  net.fetch = async (url, init = {}) => {
    const u = new URL(url);
    const method = init.method || 'GET';
    const auth = (init.headers || {}).Authorization;
    calls.push(`${method} ${u.host}${u.pathname}`);
    if (net.down) throw new Error('offline');
    const json = (v, status = 200) => new Response(JSON.stringify(v), { status, headers: { 'Content-Type': 'application/json' } });
    if (u.host === 'sessionserver.mojang.com') {
      const b = JSON.parse(init.body);
      if (b.accessToken !== 'mc-token' || b.selectedProfile !== ACC.uuid) return new Response(null, { status: 403 });
      net.joined.add(b.serverId);
      return new Response(null, { status: 204 });
    }
    if (u.pathname === '/v1/auth/challenge') return json({ challenge: 'c'.repeat(32) });
    if (u.pathname === '/v1/auth/verify') {
      const b = JSON.parse(init.body);
      if (!net.joined.has(b.challenge)) return json({ error: 'no' }, 401);
      return json({ token: net.token, uuid: UUID, name: 'Neer', expires: Date.now() + 30 * 864e5 });
    }
    if (u.pathname.startsWith('/cosmetics/')) {
      const rel = u.pathname.slice('/cosmetics/'.length);
      if (!(rel in net.files)) return new Response('nope', { status: 404 });
      if ((init.headers || {})['If-None-Match'] === net.etag) return new Response(null, { status: 304 });
      return new Response(net.files[rel], { status: 200, headers: { ETag: net.etag } });
    }
    if (auth !== `Bearer ${net.token}` || auth === `Bearer ${net.rejectToken}`) return json({ error: 'Sign in again.' }, 401);
    if (u.pathname === '/v1/me') return json({ uuid: UUID, name: 'Neer', loadout: { slots: {}, hideBadge: false }, owned: [] });
    if (u.pathname === '/v1/players') {
      const b = JSON.parse(init.body);
      const players = {};
      for (const id of b.uuids) if (net.players[id]) players[id] = net.players[id];
      return json({ players, ttl: 120 });
    }
    if (u.pathname === '/v1/sync' && method === 'GET') return json(net.sync);
    if (u.pathname === '/v1/sync' && method === 'PUT') {
      net.sync = { data: JSON.parse(init.body), updated: Date.now() };
      return json({ updated: net.sync.updated });
    }
    return json({ error: 'Not found' }, 404);
  };
  return net;
}

class FakeWS {
  static all = [];
  constructor(url) {
    this.url = url;
    this.readyState = 0;
    this.sent = [];
    FakeWS.all.push(this);
    setImmediate(() => {
      this.readyState = 1;
      if (this.onopen) this.onopen();
    });
  }
  send(m) { this.sent.push(m); }
  close(code) {
    this.readyState = 3;
    if (this.onclose) this.onclose({ code });
  }
  receive(msg) { this.onmessage({ data: JSON.stringify(msg) }); }
}

function service(net, extra = {}) {
  const saved = new Map();
  const events = [];
  const cacheDir = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-cache-'));
  const svc = new PolarService({
    serverUrl: 'https://polar.example.workers.dev/', cacheDir,
    account: async () => extra.account || ACC,
    tokens: { get: (u) => saved.get(u) || null, set: (u, v) => (v ? saved.set(u, v) : saved.delete(u)) },
    fetch: net.fetch, WebSocket: FakeWS, onEvent: (e) => events.push(e),
  });
  return { svc, saved, events, cacheDir };
}

const tick = () => new Promise((r) => setImmediate(r));

test('signs in through Mojang once and keeps the token', async () => {
  const net = fakeNet();
  const { svc, saved } = service(net);
  const me = await svc.me();
  assert.equal(me.name, 'Neer');
  assert.deepEqual(net.calls.slice(0, 3), ['POST polar.example.workers.dev/v1/auth/challenge', 'POST sessionserver.mojang.com/session/minecraft/join', 'POST polar.example.workers.dev/v1/auth/verify']);
  assert.equal(saved.get(UUID).token, 'tok-1');
  net.calls.length = 0;
  await svc.me();
  assert.deepEqual(net.calls, ['GET polar.example.workers.dev/v1/me'], 'no second sign-in');
  // a token the server stopped accepting is replaced once
  net.token = 'tok-2';
  net.calls.length = 0;
  await svc.me();
  assert.ok(net.calls.includes('POST polar.example.workers.dev/v1/auth/verify'));
  assert.equal(saved.get(UUID).token, 'tok-2');
});

test('offline profiles and a missing server get clear answers', async () => {
  const net = fakeNet();
  const { svc } = service(net, { account: { uuid: 'x', name: 'Off', type: 'offline' } });
  assert.equal(await svc.session(), null);
  await assert.rejects(svc.me(), /Microsoft account/);
  const off = new PolarService({ serverUrl: '', cacheDir: os.tmpdir(), account: async () => ACC, tokens: { get: () => null, set: () => {} }, fetch: net.fetch });
  assert.equal(off.configured, false);
  await assert.rejects(off.me(), /isn't set up yet/);
});

test('cosmetics files are cached, revalidated once per session and still work offline', async () => {
  const net = fakeNet();
  const { svc, cacheDir } = service(net);
  assert.equal((await svc.file('textures/cape_aurora.png')).toString(), 'PNGDATA');
  assert.equal(fs.readFileSync(path.join(cacheDir, 'textures', 'cape_aurora.png'), 'utf8'), 'PNGDATA');
  net.calls.length = 0;
  await svc.file('textures/cape_aurora.png');
  assert.deepEqual(net.calls, [], 'checked already this session');
  // a new session asks with the ETag and gets 304
  const again = new PolarService({ ...svc, serverUrl: svc.url, cacheDir, account: svc.account, tokens: svc.tokens, fetch: net.fetch });
  await again.file('textures/cape_aurora.png');
  assert.deepEqual(net.calls, ['GET polar.example.workers.dev/cosmetics/textures/cape_aurora.png']);
  assert.equal((await svc.catalog()).items[0].id, 'cape_aurora');
  // offline: the cached copy
  net.down = true;
  const offline = new PolarService({ serverUrl: svc.url, cacheDir, account: svc.account, tokens: svc.tokens, fetch: net.fetch });
  assert.equal((await offline.file('textures/cape_aurora.png')).toString(), 'PNGDATA');
  await assert.rejects(offline.file('textures/never.png'), /Can't reach/);
  // only cosmetics paths
  await assert.rejects(svc.file('../secrets.json'), /No such file/);
  await assert.rejects(svc.file('textures/../../x.png'), /No such file/);
});

test('player lookups for the game are batched and cached for a minute', async () => {
  const net = fakeNet();
  const { svc } = service(net);
  const other = '11112222-3333-4444-5555-666677778888';
  net.players[other] = { badge: true, slots: { cape: { id: 'cape_aurora' } } };
  let r = await svc.lookup([other.replace(/-/g, ''), UUID, 'junk']);
  assert.deepEqual(r, { [other]: net.players[other] });
  net.calls.length = 0;
  r = await svc.lookup([other, UUID]);
  assert.deepEqual(Object.keys(r), [other]);
  assert.deepEqual(net.calls, [], 'cached, including who is not on Polar');
});

test('the live connection reports where you are and passes on invites', async () => {
  FakeWS.all = [];
  const net = fakeNet();
  const { svc, events } = service(net);
  await svc.connect();
  await tick();
  const ws = FakeWS.all[0];
  assert.match(ws.url, /^wss:\/\/polar\.example\.workers\.dev\/v1\/live\?token=tok-1$/);
  assert.deepEqual(JSON.parse(ws.sent[0]), { type: 'where', state: 'launcher', server: null });
  assert.equal(svc.online, true);
  svc.setWhere({ state: 'server', server: 'play.example.net' });
  assert.deepEqual(JSON.parse(ws.sent.at(-1)), { type: 'where', state: 'server', server: 'play.example.net' });
  svc.setWhere({ state: 'menu', server: 'leaked.example' });
  assert.deepEqual(JSON.parse(ws.sent.at(-1)), { type: 'where', state: 'menu', server: null });
  ws.receive({ type: 'invite', from: { uuid: 'x', name: 'Alice' }, server: 'play.example.net' });
  ws.receive({ type: 'something-else' });
  assert.equal(events.filter((e) => e.type === 'invite').length, 1);
  assert.deepEqual(svc.drainGameEvents().map((e) => e.type), ['invite']);
  assert.deepEqual(svc.drainGameEvents(), [], 'each event goes to the game once');
  // switching to the same account keeps the connection; signing out closes it
  await svc.connect();
  assert.equal(FakeWS.all.length, 1);
  svc.signOut(UUID);
  assert.equal(svc.online, false);
  svc.disconnect();
});

test('settings sync: newer copies come down before playing, changes go up after', async () => {
  const net = fakeNet();
  const { svc } = service(net);
  const pcA = fs.mkdtempSync(path.join(os.tmpdir(), 'pc-a-'));
  const pcB = fs.mkdtempSync(path.join(os.tmpdir(), 'pc-b-'));
  fs.mkdirSync(path.join(pcA, 'config'));
  fs.writeFileSync(path.join(pcA, 'config', 'polarclient.json'), '{"zoom":true}');
  assert.equal(await svc.pushSettings('1.21.11', pcA), true);
  assert.equal(await svc.pushSettings('1.21.11', pcA), false, 'nothing changed');
  assert.deepEqual(Object.keys(net.sync.data.versions), ['1.21.11']);
  // another PC picks it up
  assert.deepEqual(await svc.pullSettings('1.21.11', pcB), ['polarclient.json']);
  assert.equal(fs.readFileSync(path.join(pcB, 'config', 'polarclient.json'), 'utf8'), '{"zoom":true}');
  assert.deepEqual(await svc.pullSettings('1.21.11', pcB), [], 'already up to date');
  assert.deepEqual(await svc.pullSettings('1.8.9', pcB), [], 'nothing saved for that version');
  // a file edited here after the upload wins over the server copy
  fs.writeFileSync(path.join(pcB, 'config', 'polarclient.json'), '{"zoom":false}');
  assert.deepEqual(await svc.pullSettings('1.21.11', pcB), []);
  assert.equal(await svc.pushSettings('1.21.11', pcB), true);
  assert.match(net.sync.data.versions['1.21.11'].files['polarclient.json'], /false/);
});

test('the bridge gives the game the Polar state, files, lookups and events', async () => {
  const bridge = await startBridge({
    listAccounts: () => ({ selected: null, accounts: [] }),
    session: async () => ({}),
    polar: {
      state: async () => ({ configured: true, signedIn: true, uuid: UUID, name: 'Neer', online: true }),
      file: async (rel) => {
        if (rel !== 'textures/a.png') throw Object.assign(new Error('No such file'), { status: 404 });
        return Buffer.from([1, 2, 3]);
      },
      lookup: async (uuids) => ({ [uuids[0]]: { badge: true, slots: {} } }),
      events: () => [{ type: 'invite' }],
    },
  });
  const base = `http://127.0.0.1:${bridge.port}`;
  const h = { 'x-polar-token': bridge.token };
  try {
    assert.equal((await fetch(`${base}/polar/state`)).status, 403, 'needs the secret');
    assert.equal((await (await fetch(`${base}/polar/state`, { headers: h })).json()).signedIn, true);
    const f = await fetch(`${base}/polar/file?path=textures/a.png`, { headers: h });
    assert.equal(f.headers.get('content-type'), 'image/png');
    assert.deepEqual([...new Uint8Array(await f.arrayBuffer())], [1, 2, 3]);
    assert.equal((await fetch(`${base}/polar/file?path=nope.png`, { headers: h })).status, 404);
    const p = await (await fetch(`${base}/polar/players`, { method: 'POST', headers: h, body: JSON.stringify({ uuids: [UUID] }) })).json();
    assert.deepEqual(p.players[UUID], { badge: true, slots: {} });
    assert.deepEqual((await (await fetch(`${base}/polar/events`, { headers: h })).json()).events, [{ type: 'invite' }]);
  } finally {
    await bridge.close();
  }
});
