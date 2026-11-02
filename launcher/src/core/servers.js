'use strict';
// The game's own server list (instance/servers.dat) and the Server List Ping that fills in ping, players and MOTD.
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const net = require('node:net');
const dns = require('node:dns').promises;
const path = require('node:path');
const nbt = require('./nbt');

const { T } = nbt;

// ---------------------------------------------------------------- servers.dat

function serversFile(gameDir) {
  return path.join(gameDir, 'servers.dat');
}

function readRoot(file) {
  try {
    return nbt.read(fs.readFileSync(file));
  } catch (e) {
    if (e.code === 'ENOENT') return { name: '', value: { servers: { type: T.LIST, value: { itemType: T.COMPOUND, items: [] } } } };
    throw new Error(`Couldn't read the server list (${e.message}).`);
  }
}

function entries(root) {
  const list = root.value.servers;
  if (!list || list.type !== T.LIST) {
    root.value.servers = { type: T.LIST, value: { itemType: T.COMPOUND, items: [] } };
  } else if (list.value.items.length === 0) {
    // the game saves an empty list with element type End (after you delete every server)
    list.value.itemType = T.COMPOUND;
  }
  return root.value.servers.value.items;
}

const strOf = (c, key) => (c[key] && c[key].type === T.STRING ? c[key].value : '');

/** Visible saved servers, in the game's order: [{ name, ip, icon }]. */
function listServers(gameDir) {
  return entries(readRoot(serversFile(gameDir)))
    .filter((c) => !(c.hidden && c.hidden.value))
    .map((c) => ({
      name: strOf(c, 'name') || strOf(c, 'ip'),
      ip: strOf(c, 'ip'),
      icon: strOf(c, 'icon') ? `data:image/png;base64,${strOf(c, 'icon')}` : null,
    }));
}

async function writeRoot(file, root) {
  await fsp.mkdir(path.dirname(file), { recursive: true });
  const tmp = `${file}.polar-tmp`;
  await fsp.writeFile(tmp, nbt.write(root));
  if (fs.existsSync(file)) await fsp.copyFile(file, `${file}_old`);
  await fsp.rename(tmp, file);
}

function cleanServer({ name, ip }) {
  const n = String(name || '').trim().slice(0, 64);
  const a = String(ip || '').trim();
  if (!a || a.length > 255 || /\s/.test(a)) throw new Error('Enter a valid server address.');
  return { name: n || a, ip: a };
}

async function addServer(gameDir, server) {
  const s = cleanServer(server);
  const file = serversFile(gameDir);
  const root = readRoot(file);
  const items = entries(root);
  const hidden = items.find((c) => strOf(c, 'ip') === s.ip && c.hidden && c.hidden.value);
  if (hidden) {
    hidden.name = { type: T.STRING, value: s.name };
    delete hidden.hidden;
  } else if (items.some((c) => strOf(c, 'ip') === s.ip)) {
    throw new Error(`${s.ip} is already in your server list.`);
  } else {
    items.push({ name: { type: T.STRING, value: s.name }, ip: { type: T.STRING, value: s.ip } });
  }
  await writeRoot(file, root);
  return listServers(gameDir);
}

async function editServer(gameDir, oldIp, server) {
  const s = cleanServer(server);
  const file = serversFile(gameDir);
  const root = readRoot(file);
  const target = entries(root).find((c) => strOf(c, 'ip') === oldIp);
  if (!target) throw new Error('That server is no longer in the list.');
  target.name = { type: T.STRING, value: s.name };
  if (s.ip !== oldIp) {
    target.ip = { type: T.STRING, value: s.ip };
    delete target.icon; // the icon belonged to the old address
  }
  await writeRoot(file, root);
  return listServers(gameDir);
}

async function removeServer(gameDir, ip) {
  const file = serversFile(gameDir);
  const root = readRoot(file);
  root.value.servers.value.items = entries(root).filter((c) => strOf(c, 'ip') !== ip);
  await writeRoot(file, root);
  return listServers(gameDir);
}

// ---------------------------------------------------------------- Server List Ping

function varint(n) {
  const out = [];
  let v = n >>> 0;
  do {
    let b = v & 0x7f;
    v >>>= 7;
    if (v !== 0) b |= 0x80;
    out.push(b);
  } while (v !== 0);
  return Buffer.from(out);
}

function readVarint(buf, offset) {
  let value = 0, shift = 0, p = offset;
  for (;;) {
    if (p >= buf.length) return null; // need more data
    const b = buf[p++];
    value |= (b & 0x7f) << shift;
    if (!(b & 0x80)) return { value: value | 0, next: p };
    shift += 7;
    if (shift > 35) throw new Error('VarInt too big');
  }
}

const packet = (id, ...parts) => {
  const body = Buffer.concat([varint(id), ...parts]);
  return Buffer.concat([varint(body.length), body]);
};
const mcString = (s) => {
  const b = Buffer.from(s, 'utf8');
  return Buffer.concat([varint(b.length), b]);
};

/** "host", "host:port" or "[v6]:port" -> { host, port, explicitPort } */
function parseAddress(address) {
  const a = String(address || '').trim();
  const v6 = a.match(/^\[([^\]]+)\](?::(\d+))?$/);
  if (v6) return { host: v6[1], port: v6[2] ? Number(v6[2]) : 25565, explicitPort: Boolean(v6[2]) };
  const m = a.match(/^([^:]+)(?::(\d+))?$/);
  if (!m) throw new Error('Invalid address');
  return { host: m[1], port: m[2] ? Number(m[2]) : 25565, explicitPort: Boolean(m[2]) };
}

async function resolve(address) {
  const { host, port, explicitPort } = parseAddress(address);
  if (!explicitPort && net.isIP(host) === 0) {
    try {
      const srv = await dns.resolveSrv(`_minecraft._tcp.${host}`);
      if (srv.length) return { host: srv[0].name, port: srv[0].port, handshakeHost: host };
    } catch {
      // no SRV record: use the name as-is
    }
  }
  return { host, port, handshakeHost: host };
}

const COLORS = {
  black: '#000000', dark_blue: '#0000AA', dark_green: '#00AA00', dark_aqua: '#00AAAA', dark_red: '#AA0000',
  dark_purple: '#AA00AA', gold: '#FFAA00', gray: '#AAAAAA', dark_gray: '#555555', blue: '#5555FF', green: '#55FF55',
  aqua: '#55FFFF', red: '#FF5555', light_purple: '#FF55FF', yellow: '#FFFF55', white: '#FFFFFF',
};
const CODES = '0123456789abcdef'.split('');
const CODE_COLORS = ['black', 'dark_blue', 'dark_green', 'dark_aqua', 'dark_red', 'dark_purple', 'gold', 'gray', 'dark_gray', 'blue', 'green', 'aqua', 'red', 'light_purple', 'yellow', 'white'];

/** MOTD (string with § codes, or a chat component) -> [{ text, color, bold, italic }] */
function motdSegments(desc) {
  const out = [];
  const pushLegacy = (text, base) => {
    let style = { ...base };
    for (const part of String(text).split(/(§[0-9a-fk-or])/i)) {
      const m = part.match(/^§([0-9a-fk-or])$/i);
      if (m) {
        const c = m[1].toLowerCase();
        if (CODES.includes(c)) style = { color: COLORS[CODE_COLORS[CODES.indexOf(c)]] };
        else if (c === 'l') style.bold = true;
        else if (c === 'o') style.italic = true;
        else if (c === 'r') style = { ...base };
      } else if (part) out.push({ text: part, ...style });
    }
  };
  const walk = (node, inherited) => {
    if (node == null) return;
    if (typeof node === 'string') return pushLegacy(node, inherited);
    if (Array.isArray(node)) return node.forEach((n) => walk(n, inherited));
    const style = { ...inherited };
    if (node.color) style.color = COLORS[node.color] || (/^#[0-9a-f]{6}$/i.test(node.color) ? node.color : style.color);
    if (node.bold != null) style.bold = Boolean(node.bold);
    if (node.italic != null) style.italic = Boolean(node.italic);
    if (node.text != null) pushLegacy(node.text, style);
    if (node.translate && node.text == null) pushLegacy(node.translate, style);
    if (Array.isArray(node.extra)) node.extra.forEach((n) => walk(n, style));
  };
  walk(desc, {});
  return out;
}

/** Pings a server: { online, ping, version, protocol, players: {online, max, sample}, motd: [...], favicon } */
function ping(address, { timeout = 5000 } = {}) {
  return resolve(address).then(({ host, port, handshakeHost }) => new Promise((resolvePing, reject) => {
    const socket = net.createConnection({ host, port });
    let buf = Buffer.alloc(0);
    let status = null;
    let sentAt = 0;
    let finished = false;
    const done = (err, value) => {
      if (finished) return;
      finished = true;
      socket.destroy();
      clearTimeout(timer);
      if (err) reject(err);
      else resolvePing(value);
    };
    const timer = setTimeout(() => done(new Error('Timed out')), timeout);
    socket.on('error', (e) => done(new Error(e.code === 'ENOTFOUND' ? "Can't find that server" : e.code === 'ECONNREFUSED' ? 'Connection refused' : e.message)));
    socket.on('connect', () => {
      const portBuf = Buffer.alloc(2);
      portBuf.writeUInt16BE(port);
      socket.write(packet(0x00, varint(-1), mcString(handshakeHost), portBuf, varint(1)));
      socket.write(packet(0x00));
    });
    socket.on('data', (chunk) => {
      buf = Buffer.concat([buf, chunk]);
      for (;;) {
        const len = readVarint(buf, 0);
        if (!len || buf.length < len.next + len.value) return;
        const body = buf.subarray(len.next, len.next + len.value);
        buf = buf.subarray(len.next + len.value);
        const id = readVarint(body, 0);
        if (id.value === 0x00 && !status) {
          const sl = readVarint(body, id.next);
          try {
            status = JSON.parse(body.subarray(sl.next, sl.next + sl.value).toString('utf8'));
          } catch {
            return done(new Error('The server sent an invalid status'));
          }
          const payload = Buffer.alloc(8);
          payload.writeBigInt64BE(BigInt(Date.now()));
          sentAt = performance.now();
          socket.write(packet(0x01, payload));
        } else if (id.value === 0x01 && status) {
          const players = status.players || {};
          return done(null, {
            online: true,
            ping: Math.max(1, Math.round(performance.now() - sentAt)),
            version: status.version ? String(status.version.name || '') : '',
            protocol: status.version ? Number(status.version.protocol) : 0,
            players: {
              online: Number(players.online) || 0,
              max: Number(players.max) || 0,
              sample: Array.isArray(players.sample) ? players.sample.map((p) => String(p.name || '')).slice(0, 12) : [],
            },
            motd: motdSegments(status.description),
            favicon: typeof status.favicon === 'string' && status.favicon.startsWith('data:image/png;base64,') ? status.favicon : null,
          });
        }
      }
    });
    socket.on('close', () => {
      if (status) done(null, { online: true, ping: 0, version: '', protocol: 0, players: { online: 0, max: 0, sample: [] }, motd: motdSegments(status.description), favicon: null });
    });
  }));
}

module.exports = { serversFile, listServers, addServer, editServer, removeServer, ping, parseAddress, resolveAddress: resolve, motdSegments, varint, readVarint };
