'use strict';
// Resource packs and shaders from Modrinth, switching them on in options.txt / Iris, the skin library, the News feed,
// the Discord status and the game's presence reports.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const http = require('node:http');
const { EventEmitter } = require('node:events');
const zlib = require('node:zlib');
const modrinth = require('../src/core/modrinth');
const packs = require('../src/core/packs');
const { SkinLibrary, checkSkin, pngFromDataUrl } = require('../src/core/skins');
const news = require('../src/core/news');
const { DiscordPresence, activityFor, encode } = require('../src/core/discord');
const { startBridge } = require('../src/core/bridge');

const tmp = (name) => fs.mkdtempSync(path.join(os.tmpdir(), `polar-${name}-`));

// ---------------------------------------------------------------- Modrinth packs

function fakePacks(seen) {
  const files = {
    faith001: { slug: 'faithful', title: 'Faithful 32x', file: 'Faithful 32x - 1.21.zip', kind: 'resourcepack' },
    compl001: { slug: 'complementary', title: 'Complementary Shaders', file: 'ComplementaryReimagined_r5.zip', kind: 'shader' },
    evil0001: { slug: 'evil', title: 'Evil', file: '../evil.zip', kind: 'resourcepack' },
  };
  global.fetch = async (url) => {
    const u = new URL(String(url));
    seen && seen.push(u);
    if (u.pathname === '/v2/search') return new Response(JSON.stringify({ total_hits: 0, hits: [] }), { status: 200 });
    const m = u.pathname.match(/^\/v2\/project\/([^/]+)\/version$/);
    if (u.hostname === 'api.modrinth.com' && m) {
      const p = files[m[1]];
      if (!p) return new Response('[]', { status: 200 });
      const fileUrl = `https://cdn.modrinth.com/data/${m[1]}/x.zip`;
      return new Response(JSON.stringify([{ project_id: m[1], version_number: '2.0', version_type: 'release',
        files: [{ primary: true, url: fileUrl, filename: p.file, size: Buffer.byteLength(`zip:${fileUrl}`), hashes: {} }] }]), { status: 200 });
    }
    if (u.hostname === 'cdn.modrinth.com') return new Response(Buffer.from(`zip:${u.href}`), { status: 200 });
    return new Response('nope', { status: 404 });
  };
}

test('pack searches ask Modrinth for resource packs or Iris/OptiFine shaders of this version', async () => {
  const seen = [];
  fakePacks(seen);
  await modrinth.search({ gameVersion: '1.21.11', kind: 'resourcepack', category: '32x' });
  assert.deepEqual(JSON.parse(seen[0].searchParams.get('facets')), [['project_type:resourcepack'], ['versions:1.21.11'], ['categories:32x']]);
  await modrinth.search({ gameVersion: '1.21.11', kind: 'shader', category: 'not-a-category' });
  assert.deepEqual(JSON.parse(seen[1].searchParams.get('facets')), [['project_type:shader'], ['versions:1.21.11'], ['categories:iris', 'categories:optifine']]);
});

test('installing a pack downloads it into that kind of folder, lists it, and removes it again', async () => {
  const seen = [];
  fakePacks(seen);
  const gameDir = tmp('packs');
  const rp = await modrinth.installPack({ gameDir, gameVersion: '1.21.11', kind: 'resourcepack', project: { id: 'faith001', slug: 'faithful', title: 'Faithful 32x' } });
  assert.equal(rp.entry.filename, 'Faithful 32x - 1.21.zip');
  assert.ok(fs.existsSync(path.join(gameDir, 'resourcepacks', rp.entry.filename)));
  const loaders = JSON.parse(seen.find((u) => u.pathname.endsWith('/version')).searchParams.get('loaders'));
  assert.deepEqual(loaders, ['minecraft']);
  const sh = await modrinth.installPack({ gameDir, gameVersion: '1.21.11', kind: 'shader', project: { id: 'compl001', title: 'Complementary' } });
  assert.ok(fs.existsSync(path.join(gameDir, 'shaderpacks', sh.entry.filename)));
  fs.writeFileSync(path.join(gameDir, 'resourcepacks', 'My own pack.zip'), 'x');
  const list = await modrinth.listPacks({ gameDir });
  assert.deepEqual(list.resourcepack.added.map((e) => e.projectId), ['faith001']);
  assert.deepEqual(list.resourcepack.own, ['My own pack.zip']);
  assert.deepEqual(list.shader.added.map((e) => e.projectId), ['compl001']);
  await assert.rejects(modrinth.installPack({ gameDir, gameVersion: '1.21.11', kind: 'resourcepack', project: { id: 'evil0001' } }), /unexpected file name/);
  await assert.rejects(modrinth.installPack({ gameDir, gameVersion: '1.21.11', kind: 'resourcepack', project: { id: 'none0001', title: 'Nothing' } }), /no build/);
  await assert.rejects(modrinth.installPack({ gameDir, gameVersion: '1.21.11', kind: 'datapack', project: { id: 'x' } }), /Unknown kind/);
  const removed = await modrinth.removePack({ gameDir, kind: 'resourcepack', projectId: 'faith001' });
  assert.equal(removed.removed, true);
  assert.ok(!fs.existsSync(path.join(gameDir, 'resourcepacks', rp.entry.filename)));
  assert.deepEqual((await modrinth.removePack({ gameDir, kind: 'resourcepack', projectId: 'faith001' })).removed, false);
});

test('resource packs are switched on and off in options.txt, the old 1.8 way too', async () => {
  const dir = tmp('options');
  assert.equal(await packs.setResourcePack(dir, 'A.zip', true, false), false, 'no options.txt before the first start');
  fs.writeFileSync(path.join(dir, 'options.txt'), 'version:4440\nresourcePacks:["vanilla","file/Old.zip"]\nincompatibleResourcePacks:["file/A.zip"]\nfov:0.0\n');
  assert.equal(await packs.setResourcePack(dir, 'A.zip', true, false), true);
  let text = fs.readFileSync(path.join(dir, 'options.txt'), 'utf8');
  assert.match(text, /^resourcePacks:\["vanilla","file\/Old.zip","file\/A.zip"\]$/m, 'goes on top');
  assert.match(text, /^incompatibleResourcePacks:\[\]$/m, 'no longer marked incompatible');
  assert.match(text, /^fov:0\.0$/m, 'other options kept');
  assert.deepEqual(await packs.enabledResourcePacks(dir, false), ['Old.zip', 'A.zip']);
  await packs.setResourcePack(dir, 'Old.zip', false, false);
  assert.deepEqual(await packs.enabledResourcePacks(dir, false), ['A.zip']);

  const legacy = tmp('options189');
  fs.writeFileSync(path.join(legacy, 'options.txt'), 'invertYMouse:false\n');
  await packs.setResourcePack(legacy, 'Pvp Pack.zip', true, true);
  text = fs.readFileSync(path.join(legacy, 'options.txt'), 'utf8');
  assert.match(text, /^resourcePacks:\["Pvp Pack.zip"\]$/m, '1.8 lists bare file names');
  assert.deepEqual(await packs.enabledResourcePacks(legacy, true), ['Pvp Pack.zip']);
});

test('the shader pack is picked in Iris\'s settings, keeping the rest of them', async () => {
  const dir = tmp('iris');
  assert.equal(await packs.currentShader(dir), null);
  await packs.setShader(dir, 'Complementary: v5=new.zip');
  assert.equal(await packs.currentShader(dir), 'Complementary: v5=new.zip', 'special characters survive');
  const file = path.join(dir, 'config', 'iris.properties');
  fs.appendFileSync(file, 'maxShadowRenderDistance=32\n');
  await packs.setShader(dir, null);
  assert.equal(await packs.currentShader(dir), null, 'shaders off');
  const text = fs.readFileSync(file, 'utf8');
  assert.match(text, /^maxShadowRenderDistance=32$/m);
  assert.match(text, /^enableShaders=false$/m);
  await packs.setShader(dir, 'BSL.zip');
  assert.equal(await packs.currentShader(dir), 'BSL.zip');
});

// ---------------------------------------------------------------- skin library

/** A real PNG of the given size (the library only reads the header, but the bytes are a valid image). */
function png(w, h, seed = 0) {
  const crcTable = Array.from({ length: 256 }, (_, n) => {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    return c >>> 0;
  });
  const crc = (buf) => {
    let c = 0xffffffff;
    for (const b of buf) c = crcTable[(c ^ b) & 255] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
  };
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type), data]);
    const c = Buffer.alloc(4);
    c.writeUInt32BE(crc(td));
    return Buffer.concat([len, td, c]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const raw = Buffer.alloc((w * 4 + 1) * h, seed);
  for (let y = 0; y < h; y++) raw[y * (w * 4 + 1)] = 0;
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

test('skin library: saves skins once, renames, switches arm style and deletes', async () => {
  assert.throws(() => checkSkin(Buffer.from('hello')), /isn't a PNG/);
  assert.throws(() => checkSkin(png(128, 128)), /64×64/);
  checkSkin(png(64, 32));
  const lib = new SkinLibrary(tmp('skins'));
  const a = await lib.add(png(64, 64, 1), { name: '  Winter   coat ', variant: 'slim' });
  const again = await lib.add(png(64, 64, 1), { name: 'Same image', variant: 'classic' });
  assert.equal(again, a, 'the same picture is stored once');
  const b = await lib.add(png(64, 64, 2), { name: '' });
  let list = await lib.list();
  assert.equal(list.length, 2);
  assert.equal(list.find((s) => s.id === a).name, 'Same image');
  assert.equal(list.find((s) => s.id === b).name, 'Skin 2');
  assert.ok(list[0].data.startsWith('data:image/png;base64,'));
  await lib.update(b, { name: 'PvP', variant: 'slim' });
  list = await lib.list();
  assert.deepEqual(list.find((s) => s.id === b), { ...list.find((s) => s.id === b), name: 'PvP', variant: 'slim' });
  assert.throws(() => lib.file('../../etc/passwd'), /Unknown skin/);
  await lib.remove(a);
  assert.deepEqual((await lib.list()).map((s) => s.id), [b]);
  assert.equal(pngFromDataUrl(`data:image/png;base64,${png(64, 64).toString('base64')}`).readUInt32BE(16), 64);
  assert.throws(() => pngFromDataUrl('data:text/html,hi'), /Couldn't read/);
});

// ---------------------------------------------------------------- news

test('news keeps well-formed items newest first and falls back to the saved or bundled copy', async () => {
  const items = news.clean({ items: [
    { title: 'Old', date: '2026-01-02', body: 'x' },
    { title: 'New', date: '2026-11-02', link: 'javascript:alert(1)', image: 'https://example.com/a.png', tag: 'Update' },
    { title: 'No date' },
    { date: '2026-05-05' },
  ] });
  assert.deepEqual(items.map((i) => i.title), ['New', 'Old']);
  assert.equal(items[0].link, '', 'only https links');
  assert.equal(items[0].image, 'https://example.com/a.png');
  assert.equal(items[0].linkLabel, 'Read more');

  const dir = tmp('news');
  const bundled = path.join(dir, 'bundled.json');
  const cache = path.join(dir, 'cache.json');
  fs.writeFileSync(bundled, JSON.stringify({ items: [{ title: 'Shipped', date: '2026-10-01' }] }));
  global.fetch = async () => new Response(JSON.stringify({ items: [{ title: 'Live', date: '2026-11-01' }] }), { status: 200 });
  let r = await news.load({ url: 'https://example.com/news.json', cacheFile: cache, bundledFile: bundled });
  assert.equal(r.source, 'live');
  assert.equal(r.items[0].title, 'Live');
  global.fetch = async () => { throw new Error('offline'); };
  r = await news.load({ url: 'https://example.com/news.json', cacheFile: cache, bundledFile: bundled });
  assert.equal(r.source, 'cache');
  assert.equal(r.items[0].title, 'Live');
  fs.rmSync(cache);
  r = await news.load({ url: '', cacheFile: cache, bundledFile: bundled });
  assert.deepEqual([r.source, r.items[0].title], ['bundled', 'Shipped']);
  // the copy shipped with the launcher and the one published in the repo are valid
  for (const f of ['../src/news.json', '../../news.json']) {
    const shipped = news.clean(JSON.parse(fs.readFileSync(path.join(__dirname, f), 'utf8')));
    assert.ok(shipped.length > 0, `${f} has items`);
  }
});

// ---------------------------------------------------------------- Discord

test('Discord activity: launcher, menus, singleplayer, a server, and a hidden server', () => {
  assert.deepEqual(activityFor({ version: '1.21.11', game: null }).details, 'In the launcher');
  const startedAt = Date.UTC(2026, 10, 2, 12);
  const on = (where) => activityFor({ version: '1.8.9', game: { version: '1.21.11', startedAt, where } });
  assert.equal(on({ state: 'server', server: 'play.example.net' }).state, 'On play.example.net');
  assert.equal(on({ state: 'server', server: 'play.example.net' }).details, 'Minecraft 1.21.11');
  assert.equal(on({ state: 'server', server: 'play.example.net' }).timestamps.start, startedAt / 1000);
  assert.equal(on({ state: 'server' }).state, 'Multiplayer');
  assert.equal(on({ state: 'singleplayer' }).state, 'Singleplayer');
  assert.equal(on({ state: 'menu' }).state, 'In the menus');
  assert.equal(on({ state: 'hidden' }).state, undefined);
  assert.equal(on(undefined).state, undefined, 'versions without the mod just show the version');
});

class FakeSocket extends EventEmitter {
  constructor() {
    super();
    this.frames = [];
  }

  write(buf) {
    this.frames.push({ op: buf.readInt32LE(0), body: JSON.parse(buf.subarray(8).toString('utf8')) });
  }

  end() {
    this.ended = true;
  }

  destroy() {
    this.emit('close');
  }

  reply(op, body) {
    this.emit('data', encode(op, body));
  }
}

test('Discord IPC: handshake, the status after READY, pings and a clean stop', async () => {
  const notConfigured = new DiscordPresence('', { connect: () => assert.fail('no client id: never connects') });
  notConfigured.set({ details: 'x' });
  assert.equal(notConfigured.configured, false);

  const sock = new FakeSocket();
  const tried = [];
  const p = new DiscordPresence('123456789012345678', {
    paths: () => ['a', 'b'],
    connect: async (file) => {
      tried.push(file);
      if (file === 'a') throw new Error('ENOENT');
      return sock;
    },
  });
  p.set({ details: 'In the launcher' });
  await new Promise((r) => setImmediate(r));
  assert.deepEqual(tried, ['a', 'b'], 'tries each socket');
  assert.deepEqual(sock.frames[0], { op: 0, body: { v: 1, client_id: '123456789012345678' } });
  // the reply may arrive in pieces
  const ready = encode(1, { cmd: 'DISPATCH', evt: 'READY', data: {} });
  sock.emit('data', ready.subarray(0, 5));
  sock.emit('data', ready.subarray(5));
  assert.equal(sock.frames[1].body.cmd, 'SET_ACTIVITY');
  assert.equal(sock.frames[1].body.args.activity.details, 'In the launcher');
  p.set({ details: 'Minecraft 1.21.11', state: 'On play.example.net' });
  assert.equal(sock.frames[2].body.args.activity.state, 'On play.example.net', 'updates go straight out');
  sock.reply(3, { n: 1 });
  assert.deepEqual(sock.frames[3], { op: 4, body: { n: 1 } }, 'pong');
  p.stop();
  assert.equal(sock.frames[4].body.args.activity, null, 'clears the status');
  assert.ok(sock.ended);
});

// ---------------------------------------------------------------- the game's presence reports

function post(port, token, pathname, body) {
  return new Promise((resolve, reject) => {
    const req = http.request({ host: '127.0.0.1', port, path: pathname, method: 'POST', headers: { 'x-polar-token': token, 'Content-Type': 'application/json' } }, (res) => {
      let data = '';
      res.on('data', (c) => (data += c));
      res.on('end', () => resolve({ status: res.statusCode, body: data ? JSON.parse(data) : null }));
    });
    req.on('error', reject);
    req.end(JSON.stringify(body));
  });
}

test('the game reports where it is through the launcher bridge', async () => {
  const seen = [];
  const bridge = await startBridge({ listAccounts: () => ({ selected: null, accounts: [] }), session: async () => ({}), presence: (w) => seen.push(w) });
  try {
    assert.equal((await post(bridge.port, bridge.token, '/presence', { state: 'server', server: 'play.example.net:25565' })).status, 200);
    assert.equal((await post(bridge.port, bridge.token, '/presence', { state: 'server', server: '<script>' })).status, 200);
    assert.equal((await post(bridge.port, bridge.token, '/presence', { state: 'singleplayer', server: 'x.net' })).status, 200);
    assert.equal((await post(bridge.port, bridge.token, '/presence', { state: 'dancing' })).status, 400);
    assert.equal((await post(bridge.port, 'wrong', '/presence', { state: 'menu' })).status, 403);
    assert.deepEqual(seen, [
      { state: 'server', server: 'play.example.net:25565' },
      { state: 'server', server: null },
      { state: 'singleplayer', server: null },
    ]);
  } finally {
    await bridge.close();
  }
});
