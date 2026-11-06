'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const net = require('node:net');
const path = require('node:path');
const nbt = require('../src/core/nbt');
const servers = require('../src/core/servers');

const tmp = () => fs.mkdtempSync(path.join(os.tmpdir(), 'polar-servers-'));

test('NBT round trip keeps every tag', () => {
  const { T } = nbt;
  const root = { name: '', value: {
    servers: { type: T.LIST, value: { itemType: T.COMPOUND, items: [
      { name: { type: T.STRING, value: 'Ünïcode ☃ server' }, ip: { type: T.STRING, value: 'play.example.net' }, acceptTextures: { type: T.BYTE, value: 1 } },
    ] } },
    n: { type: T.INT, value: -5 }, l: { type: T.LONG, value: 1234567890123n }, d: { type: T.DOUBLE, value: 1.5 },
    a: { type: T.INT_ARRAY, value: [1, 2, 3] }, b: { type: T.BYTE_ARRAY, value: Buffer.from([1, 2]) },
  } };
  const again = nbt.read(nbt.write(root));
  assert.equal(again.value.servers.value.items[0].name.value, 'Ünïcode ☃ server');
  assert.equal(again.value.l.value, 1234567890123n);
  assert.deepEqual(again.value.a.value, [1, 2, 3]);
  assert.deepEqual(nbt.write(again), nbt.write(root));
});

test('servers.dat: add, edit, remove, keeps unknown fields, hides hidden entries', async () => {
  const dir = tmp();
  assert.deepEqual(servers.listServers(dir), []);
  await servers.addServer(dir, { name: 'Polar SMP', ip: 'smp.example.net' });
  await servers.addServer(dir, { name: '', ip: 'pvp.example.net:25570' });
  await assert.rejects(servers.addServer(dir, { name: 'dupe', ip: 'smp.example.net' }), /already/);
  await assert.rejects(servers.addServer(dir, { name: 'bad', ip: 'has space' }), /valid/);
  // simulate what the game writes: an icon, an extra flag, and a hidden direct-connect entry
  const file = servers.serversFile(dir);
  const root = nbt.read(fs.readFileSync(file));
  root.value.servers.value.items[0].icon = { type: nbt.T.STRING, value: 'iVBORw0KGgo=' };
  root.value.servers.value.items[0].acceptTextures = { type: nbt.T.BYTE, value: 1 };
  root.value.servers.value.items.push({ name: { type: nbt.T.STRING, value: 'x' }, ip: { type: nbt.T.STRING, value: 'hidden.example.net' }, hidden: { type: nbt.T.BYTE, value: 1 } });
  fs.writeFileSync(file, nbt.write(root));

  let list = servers.listServers(dir);
  assert.deepEqual(list.map((s) => [s.name, s.ip]), [['Polar SMP', 'smp.example.net'], ['pvp.example.net:25570', 'pvp.example.net:25570']]);
  assert.equal(list[0].icon, 'data:image/png;base64,iVBORw0KGgo=');

  list = await servers.editServer(dir, 'smp.example.net', { name: 'Polar SMP 2', ip: 'smp.example.net' });
  assert.equal(list[0].name, 'Polar SMP 2');
  assert.equal(nbt.read(fs.readFileSync(file)).value.servers.value.items[0].acceptTextures.value, 1, 'unknown fields survive');
  list = await servers.addServer(dir, { name: 'Now visible', ip: 'hidden.example.net' });
  assert.equal(list.length, 3, 'adding a hidden server un-hides it');
  list = await servers.removeServer(dir, 'pvp.example.net:25570');
  assert.deepEqual(list.map((s) => s.ip), ['smp.example.net', 'hidden.example.net']);
});

test('servers.dat: adding to the empty list the game writes after you delete every server', async () => {
  const dir = tmp();
  // root compound "" { servers: list of End, length 0 }
  fs.writeFileSync(servers.serversFile(dir), Buffer.from('0a000009000773657276657273000000000000', 'hex'));
  assert.deepEqual(servers.listServers(dir), []);
  const list = await servers.addServer(dir, { name: 'Hypixel', ip: 'mc.hypixel.net' });
  assert.deepEqual(list.map((s) => s.ip), ['mc.hypixel.net']);
  assert.equal(nbt.read(fs.readFileSync(servers.serversFile(dir))).value.servers.value.itemType, nbt.T.COMPOUND);
});

test('MOTD parsing: legacy codes and chat components', () => {
  assert.deepEqual(servers.motdSegments('§aHello §lworld'), [{ text: 'Hello ', color: '#55FF55' }, { text: 'world', color: '#55FF55', bold: true }]);
  assert.deepEqual(servers.motdSegments({ text: '', extra: [{ text: 'Polar', color: 'aqua' }, ' SMP'] }), [{ text: 'Polar', color: '#55FFFF' }, { text: ' SMP' }]);
});

test('Server List Ping against a fake server', async () => {
  const status = { version: { name: '26.3', protocol: 999 }, players: { online: 7, max: 100, sample: [{ name: 'Frosty', id: 'x' }] }, description: { text: 'Welcome!' } };
  const srv = net.createServer((sock) => {
    let buf = Buffer.alloc(0);
    let stage = 0;
    sock.on('data', (chunk) => {
      buf = Buffer.concat([buf, chunk]);
      for (;;) {
        const len = servers.readVarint(buf, 0);
        if (!len || buf.length < len.next + len.value) return;
        const body = buf.subarray(len.next, len.next + len.value);
        buf = buf.subarray(len.next + len.value);
        const id = servers.readVarint(body, 0).value;
        if (stage === 0) { stage = 1; continue; } // handshake
        if (id === 0x00) {
          const json = Buffer.from(JSON.stringify(status));
          const payload = Buffer.concat([servers.varint(0x00), servers.varint(json.length), json]);
          sock.write(Buffer.concat([servers.varint(payload.length), payload]));
        } else if (id === 0x01) {
          const payload = Buffer.concat([servers.varint(0x01), body.subarray(1)]);
          sock.write(Buffer.concat([servers.varint(payload.length), payload]));
        }
      }
    });
  });
  await new Promise((r) => srv.listen(0, '127.0.0.1', r));
  try {
    const r = await servers.ping(`127.0.0.1:${srv.address().port}`);
    assert.equal(r.online, true);
    assert.equal(r.version, '26.3');
    assert.deepEqual(r.players, { online: 7, max: 100, sample: ['Frosty'] });
    assert.deepEqual(r.motd, [{ text: 'Welcome!' }]);
    assert.ok(r.ping >= 1);
    await assert.rejects(servers.ping('127.0.0.1:1', { timeout: 2000 }));
  } finally {
    srv.close();
  }
});
