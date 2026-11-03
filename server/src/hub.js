// The Hub: one Durable Object with Polar's data (its built-in SQLite database) and the open connections of the players
// who are on Polar right now. Being connected is what "online" means: those players get the Polar badge, and their
// cosmetics are shown to other Polar players.
import catalog from './catalog.js';
import { signToken, readToken, sameSecret, randomHex } from './token.js';

export const SLOTS = ['cape', 'wings', 'head', 'back', 'aura'];
let ITEMS = new Map(catalog.items.map((i) => [i.id, i]));

/** Swaps the cosmetics catalogue (the tests use a small one). */
export function useCatalog(c) {
  ITEMS = new Map(c.items.map((i) => [i.id, i]));
}
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const NAME = /^[A-Za-z0-9_]{1,16}$/;
const COLOR = /^#[0-9a-f]{6}$/;
const CHALLENGE_TTL = 2 * 60 * 1000;
const TOKEN_TTL = 30 * 24 * 3600 * 1000;
const MAX_SYNC = 200 * 1024;
const MAX_LOOKUP = 200;
const MAX_SOCKETS = 4; // per player: the launcher and the game, with room for a reconnect
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no 0/O or 1/I

const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, first_seen INTEGER NOT NULL DEFAULT 0,
     last_seen INTEGER NOT NULL DEFAULT 0, banned INTEGER NOT NULL DEFAULT 0)`,
  'CREATE INDEX IF NOT EXISTS players_name ON players (name COLLATE NOCASE)',
  'CREATE TABLE IF NOT EXISTS challenges (id TEXT PRIMARY KEY, created INTEGER NOT NULL)',
  'CREATE TABLE IF NOT EXISTS loadouts (uuid TEXT PRIMARY KEY, data TEXT NOT NULL, updated INTEGER NOT NULL)',
  'CREATE TABLE IF NOT EXISTS grants (uuid TEXT NOT NULL, item TEXT NOT NULL, source TEXT, created INTEGER NOT NULL, PRIMARY KEY (uuid, item))',
  `CREATE TABLE IF NOT EXISTS codes (code TEXT PRIMARY KEY, item TEXT NOT NULL, uses_left INTEGER NOT NULL, uses INTEGER NOT NULL,
     note TEXT, created INTEGER NOT NULL)`,
  'CREATE TABLE IF NOT EXISTS redemptions (code TEXT NOT NULL, uuid TEXT NOT NULL, created INTEGER NOT NULL, PRIMARY KEY (code, uuid))',
  'CREATE TABLE IF NOT EXISTS sync (uuid TEXT PRIMARY KEY, data TEXT NOT NULL, updated INTEGER NOT NULL)',
  // one row per direction: state is 'friends', 'out' (uuid asked other) or 'in' (other asked uuid)
  `CREATE TABLE IF NOT EXISTS friends (uuid TEXT NOT NULL, other TEXT NOT NULL, state TEXT NOT NULL, created INTEGER NOT NULL,
     PRIMARY KEY (uuid, other))`,
];
const MAX_FRIENDS = 200;

export class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' } });
}

async function body(request, max = 16 * 1024) {
  const text = await request.text();
  if (text.length > max) throw new HttpError(413, 'Too much data');
  try {
    const v = JSON.parse(text || '{}');
    if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error();
    return v;
  } catch {
    throw new HttpError(400, 'Expected JSON');
  }
}

/** Minecraft ids come with or without dashes; we keep them dashed and lowercase. */
export function dashed(id) {
  const hex = String(id || '').toLowerCase().replace(/-/g, '');
  if (!/^[0-9a-f]{32}$/.test(hex)) return null;
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export class Hub {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.sql = ctx.storage.sql;
    for (const s of SCHEMA) this.sql.exec(s);
    this.limits = new Map();
    // clients ping every so often; the runtime answers without waking this object, so idle connections cost nothing
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair('ping', 'pong'));
  }

  async fetch(request) {
    try {
      return await this.route(request, new URL(request.url));
    } catch (e) {
      if (e instanceof HttpError) return json({ error: e.message }, e.status);
      console.error(e && e.stack ? e.stack : e);
      return json({ error: 'Server error' }, 500);
    }
  }

  async route(request, url) {
    const p = url.pathname, m = request.method;
    if (p === '/v1/health') return json({ ok: true, items: ITEMS.size, configured: Boolean(this.env.TOKEN_SECRET) });
    if (p === '/v1/auth/challenge' && m === 'POST') return this.challenge(request);
    if (p === '/v1/auth/verify' && m === 'POST') return this.verify(request);
    if (p === '/v1/live') return this.live(request, url);
    if (p.startsWith('/v1/admin/')) return this.admin(request, url);
    const me = await this.auth(request.headers.get('Authorization'));
    if (p === '/v1/me' && m === 'GET') return json(this.profile(me));
    if (p === '/v1/me/loadout' && m === 'PUT') return json(this.setLoadout(me, await body(request)));
    if (p === '/v1/players' && m === 'POST') return json(this.lookup(await body(request, 64 * 1024)));
    if (p === '/v1/redeem' && m === 'POST') return json(this.redeem(me, await body(request)));
    if (p === '/v1/sync' && m === 'GET') return json(this.getSync(me));
    if (p === '/v1/sync' && m === 'PUT') return json(this.putSync(me, await request.text()));
    if (p === '/v1/friends' && m === 'GET') return json(this.friends(me));
    if (p === '/v1/friends/add' && m === 'POST') return json(await this.addFriend(me, await body(request)));
    if (p === '/v1/friends/accept' && m === 'POST') return json(this.acceptFriend(me, await body(request)));
    if (p === '/v1/friends/remove' && m === 'POST') return json(this.removeFriend(me, await body(request)));
    if (p === '/v1/friends/invite' && m === 'POST') return json(this.invite(me, await body(request)));
    throw new HttpError(404, 'Not found');
  }

  /** Allows `max` calls per minute for a key; throws 429 beyond that. */
  limit(key, max) {
    const now = Date.now();
    let b = this.limits.get(key);
    if (!b || now - b.start > 60000) {
      b = { start: now, n: 0 };
      this.limits.set(key, b);
      if (this.limits.size > 50000) this.limits.clear();
    }
    if (++b.n > max) throw new HttpError(429, 'Too many requests. Wait a minute.');
  }

  ip(request) {
    return request.headers.get('CF-Connecting-IP') || 'local';
  }

  secret() {
    if (!this.env.TOKEN_SECRET) throw new HttpError(503, 'The server is missing its TOKEN_SECRET (run deploy-server.bat).');
    return this.env.TOKEN_SECRET;
  }

  // ------------------------------------------------------------------ sign-in

  /**
   * Step 1: a one-time challenge. The launcher tells Mojang "this player joins server <challenge>" with the player's
   * own Minecraft login, which never comes here.
   */
  challenge(request) {
    this.limit(`c:${this.ip(request)}`, 30);
    const id = randomHex(16);
    const now = Date.now();
    this.sql.exec('DELETE FROM challenges WHERE created < ?', now - CHALLENGE_TTL);
    this.sql.exec('INSERT INTO challenges (id, created) VALUES (?, ?)', id, now);
    return json({ challenge: id, expiresIn: CHALLENGE_TTL / 1000 });
  }

  /** Step 2: we ask Mojang whether that player really joined the challenge; if so, a Polar token for 30 days. */
  async verify(request) {
    this.limit(`v:${this.ip(request)}`, 30);
    const { name, challenge } = await body(request);
    if (!NAME.test(String(name)) || !/^[0-9a-f]{32}$/.test(String(challenge))) throw new HttpError(400, 'Bad sign-in request');
    const used = this.sql.exec('DELETE FROM challenges WHERE id = ? AND created >= ? RETURNING id', challenge, Date.now() - CHALLENGE_TTL).toArray();
    if (!used.length) throw new HttpError(401, 'The sign-in took too long. Try again.');
    const res = await fetch(`https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(name)}&serverId=${challenge}`);
    if (res.status !== 200) throw new HttpError(401, "Minecraft didn't confirm this sign-in. Sign in again in the launcher.");
    const profile = await res.json();
    const uuid = dashed(profile.id);
    if (!uuid || !NAME.test(String(profile.name))) throw new HttpError(502, 'Unexpected answer from Minecraft');
    const now = Date.now();
    this.sql.exec(`INSERT INTO players (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?)
      ON CONFLICT (uuid) DO UPDATE SET name = excluded.name, last_seen = excluded.last_seen,
      first_seen = CASE WHEN players.first_seen = 0 THEN excluded.first_seen ELSE players.first_seen END`, uuid, profile.name, now, now);
    if (this.player(uuid).banned) throw new HttpError(403, 'This account is blocked from Polar features.');
    const expires = now + TOKEN_TTL;
    const token = await signToken(this.secret(), { u: uuid, n: profile.name, e: expires });
    return json({ token, uuid, name: profile.name, expires });
  }

  async auth(header) {
    const m = /^Bearer (.+)$/.exec(header || '');
    const t = m && (await readToken(this.secret(), m[1]));
    if (!t || !UUID.test(t.u) || !(t.e > Date.now())) throw new HttpError(401, 'Sign in again.');
    const p = this.player(t.u);
    if (!p || p.banned) throw new HttpError(403, 'This account is blocked from Polar features.');
    this.limit(`u:${t.u}`, 240);
    return { uuid: t.u, name: p.name };
  }

  player(uuid) {
    return this.sql.exec('SELECT uuid, name, first_seen, last_seen, banned FROM players WHERE uuid = ?', uuid).toArray()[0] || null;
  }

  // ------------------------------------------------------------------ presence

  /** The live connection. While it's open the player counts as on Polar: badge and cosmetics for everyone to see. */
  async live(request, url) {
    if (request.headers.get('Upgrade') !== 'websocket') throw new HttpError(426, 'Expected a WebSocket');
    const me = await this.auth(`Bearer ${url.searchParams.get('token') || ''}`);
    const tag = `u:${me.uuid}`;
    const open = this.ctx.getWebSockets(tag);
    for (const old of open.slice(0, Math.max(0, open.length - MAX_SOCKETS + 1))) {
      try {
        old.close(4000, 'Replaced by a newer connection');
      } catch {
        // already gone
      }
    }
    const [client, server] = Object.values(new WebSocketPair());
    this.ctx.acceptWebSocket(server, [tag]);
    server.serializeAttachment({ uuid: me.uuid, since: Date.now(), where: null });
    this.sql.exec('UPDATE players SET last_seen = ? WHERE uuid = ?', Date.now(), me.uuid);
    return new Response(null, { status: 101, webSocket: client });
  }

  /** Clients say where they are ({ type: "where", state, server }), kept for the friends list. */
  webSocketMessage(ws, message) {
    if (typeof message !== 'string' || message.length > 512) return;
    let msg;
    try {
      msg = JSON.parse(message);
    } catch {
      return;
    }
    if (msg && msg.type === 'where') {
      const a = ws.deserializeAttachment() || {};
      a.where = { state: String(msg.state || '').slice(0, 16), server: msg.server ? String(msg.server).slice(0, 100) : null };
      a.whereAt = Date.now();
      ws.serializeAttachment(a);
    }
  }

  webSocketClose(ws, code, reason) {
    const a = ws.deserializeAttachment();
    if (a && a.uuid) this.sql.exec('UPDATE players SET last_seen = ? WHERE uuid = ?', Date.now(), a.uuid);
    try {
      ws.close(1000, 'Bye');
    } catch {
      // already closed
    }
  }

  webSocketError() {}

  online(uuid) {
    return this.ctx.getWebSockets(`u:${uuid}`).length > 0;
  }

  // ------------------------------------------------------------------ cosmetics

  owned(uuid) {
    return new Set(this.sql.exec('SELECT item FROM grants WHERE uuid = ?', uuid).toArray().map((r) => r.item));
  }

  /** May this player wear the item? Free items yes; special ones when they were given it. */
  allowed(item, owned) {
    return item && (!item.special || owned.has(item.id));
  }

  /** What a player wears, minus anything that no longer exists or was taken back. */
  loadout(uuid, owned = this.owned(uuid)) {
    const row = this.sql.exec('SELECT data FROM loadouts WHERE uuid = ?', uuid).toArray()[0];
    let data = {};
    try {
      data = row ? JSON.parse(row.data) : {};
    } catch {
      data = {};
    }
    const slots = {};
    for (const slot of SLOTS) {
      const v = data.slots && data.slots[slot];
      const item = v && ITEMS.get(v.id);
      if (item && item.type === slot && this.allowed(item, owned)) slots[slot] = v.color ? { id: v.id, color: v.color } : { id: v.id };
    }
    return { slots, hideBadge: Boolean(data.hideBadge) };
  }

  profile(me) {
    const owned = this.owned(me.uuid);
    return { uuid: me.uuid, name: me.name, loadout: this.loadout(me.uuid, owned), owned: [...owned].filter((id) => ITEMS.has(id)) };
  }

  setLoadout(me, b) {
    const owned = this.owned(me.uuid);
    const slots = {};
    const want = b.slots && typeof b.slots === 'object' ? b.slots : {};
    for (const slot of SLOTS) {
      let v = want[slot];
      if (v === undefined || v === null || v === '') continue;
      if (typeof v === 'string') v = { id: v };
      const item = ITEMS.get(String(v.id));
      if (!item || item.type !== slot) throw new HttpError(400, `There's no ${slot} called ${v.id}.`);
      if (!this.allowed(item, owned)) throw new HttpError(403, `${item.name} is a special cosmetic you don't have.`);
      const color = typeof v.color === 'string' ? v.color.toLowerCase() : '';
      slots[slot] = item.tint && COLOR.test(color) ? { id: item.id, color } : { id: item.id };
    }
    const data = { slots, hideBadge: Boolean(b.hideBadge) };
    this.sql.exec(`INSERT INTO loadouts (uuid, data, updated) VALUES (?, ?, ?)
      ON CONFLICT (uuid) DO UPDATE SET data = excluded.data, updated = excluded.updated`, me.uuid, JSON.stringify(data), Date.now());
    return { loadout: data };
  }

  /** Which of these players are on Polar right now, and what they wear. Asked by the game for the players it sees. */
  lookup(b) {
    const ids = [...new Set((Array.isArray(b.uuids) ? b.uuids : []).slice(0, MAX_LOOKUP).map(dashed).filter(Boolean))];
    const players = {};
    for (const uuid of ids) {
      if (!this.online(uuid)) continue;
      const l = this.loadout(uuid);
      players[uuid] = { badge: !l.hideBadge, slots: l.slots };
    }
    return { players, ttl: 120 };
  }

  redeem(me, b) {
    this.limit(`r:${me.uuid}`, 10);
    const code = String(b.code || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (code.length < 6 || code.length > 32) throw new HttpError(400, "That doesn't look like a code.");
    return this.ctx.storage.transactionSync(() => {
      const row = this.sql.exec('SELECT code, item, uses_left FROM codes WHERE code = ?', code).toArray()[0];
      if (!row) throw new HttpError(404, "That code doesn't exist.");
      const item = ITEMS.get(row.item);
      if (!item) throw new HttpError(410, 'That cosmetic is no longer available.');
      if (this.sql.exec('SELECT 1 FROM redemptions WHERE code = ? AND uuid = ?', code, me.uuid).toArray().length) {
        throw new HttpError(409, 'You already used this code.');
      }
      if (row.uses_left <= 0) throw new HttpError(410, 'That code has been used up.');
      if (this.owned(me.uuid).has(item.id)) throw new HttpError(409, `You already have ${item.name}.`);
      const now = Date.now();
      this.sql.exec('INSERT INTO redemptions (code, uuid, created) VALUES (?, ?, ?)', code, me.uuid, now);
      this.sql.exec('INSERT OR IGNORE INTO grants (uuid, item, source, created) VALUES (?, ?, ?, ?)', me.uuid, item.id, `code:${code}`, now);
      this.sql.exec('UPDATE codes SET uses_left = uses_left - 1 WHERE code = ?', code);
      return { item: item.id, name: item.name };
    });
  }

  // ------------------------------------------------------------------ friends

  /** Sends a message to every open connection of a player (the launcher and the game). */
  tell(uuid, msg) {
    const text = JSON.stringify(msg);
    for (const ws of this.ctx.getWebSockets(`u:${uuid}`)) {
      try {
        ws.send(text);
      } catch {
        // closing
      }
    }
  }

  /** Where a player is right now, from the connection that reported most recently. */
  where(uuid) {
    let best = null;
    for (const ws of this.ctx.getWebSockets(`u:${uuid}`)) {
      const a = ws.deserializeAttachment() || {};
      if (a.where && (!best || (a.whereAt || 0) > best.at)) best = { at: a.whereAt || 0, where: a.where };
    }
    return best ? best.where : null;
  }

  friends(me) {
    const rows = this.sql.exec(`SELECT f.other AS uuid, f.state AS state, p.name AS name, p.last_seen AS lastSeen
      FROM friends f JOIN players p ON p.uuid = f.other WHERE f.uuid = ? ORDER BY p.name COLLATE NOCASE`, me.uuid).toArray();
    const out = { friends: [], incoming: [], outgoing: [] };
    for (const r of rows) {
      if (r.state === 'friends') {
        const online = this.online(r.uuid);
        out.friends.push({ uuid: r.uuid, name: r.name, online, where: online ? this.where(r.uuid) : null, lastSeen: r.lastSeen });
      } else {
        (r.state === 'in' ? out.incoming : out.outgoing).push({ uuid: r.uuid, name: r.name });
      }
    }
    out.friends.sort((a, b) => Number(b.online) - Number(a.online));
    return out;
  }

  state(a, b) {
    const r = this.sql.exec('SELECT state FROM friends WHERE uuid = ? AND other = ?', a, b).toArray()[0];
    return r ? r.state : null;
  }

  async addFriend(me, b) {
    this.limit(`f:${me.uuid}`, 20);
    const name = String(b.name || '').trim();
    if (!NAME.test(name)) throw new HttpError(400, 'Type a Minecraft name.');
    const them = this.sql.exec('SELECT uuid, name, first_seen, banned FROM players WHERE name = ? COLLATE NOCASE', name).toArray()[0];
    if (!them || !them.first_seen) throw new HttpError(404, `${name} hasn't used Polar yet. Ask them to sign in to the Polar launcher first.`);
    if (them.uuid === me.uuid) throw new HttpError(400, "That's you!");
    const st = this.state(me.uuid, them.uuid);
    if (st === 'friends') throw new HttpError(409, `You're already friends with ${them.name}.`);
    if (st === 'out') throw new HttpError(409, `You already asked ${them.name}. They need to accept.`);
    const now = Date.now();
    if (st === 'in') {
      this.link(me.uuid, them.uuid, 'friends', 'friends', now);
      this.tell(them.uuid, { type: 'friends', event: 'accepted', from: { uuid: me.uuid, name: me.name } });
      return { ...this.friends(me), message: `You and ${them.name} are now friends.` };
    }
    const count = Object.values(this.sql.exec('SELECT COUNT(*) FROM friends WHERE uuid = ?', me.uuid).toArray()[0])[0];
    if (count >= MAX_FRIENDS) throw new HttpError(409, `You can have up to ${MAX_FRIENDS} friends and requests.`);
    if (them.banned) throw new HttpError(404, `${name} hasn't used Polar yet. Ask them to sign in to the Polar launcher first.`);
    this.link(me.uuid, them.uuid, 'out', 'in', now);
    this.tell(them.uuid, { type: 'friends', event: 'request', from: { uuid: me.uuid, name: me.name } });
    return { ...this.friends(me), message: `Friend request sent to ${them.name}.` };
  }

  link(a, b, stateA, stateB, now) {
    const up = `INSERT INTO friends (uuid, other, state, created) VALUES (?, ?, ?, ?)
      ON CONFLICT (uuid, other) DO UPDATE SET state = excluded.state`;
    this.sql.exec(up, a, b, stateA, now);
    this.sql.exec(up, b, a, stateB, now);
  }

  acceptFriend(me, b) {
    const other = dashed(b.uuid);
    if (!other || this.state(me.uuid, other) !== 'in') throw new HttpError(404, "That friend request isn't there any more.");
    this.link(me.uuid, other, 'friends', 'friends', Date.now());
    this.tell(other, { type: 'friends', event: 'accepted', from: { uuid: me.uuid, name: me.name } });
    return this.friends(me);
  }

  /** Unfriend, decline a request or cancel one you sent. */
  removeFriend(me, b) {
    const other = dashed(b.uuid);
    if (!other) throw new HttpError(400, 'Pick a friend.');
    this.sql.exec('DELETE FROM friends WHERE (uuid = ? AND other = ?) OR (uuid = ? AND other = ?)', me.uuid, other, other, me.uuid);
    this.tell(other, { type: 'friends', event: 'removed', from: { uuid: me.uuid, name: me.name } });
    return this.friends(me);
  }

  /** Invites a friend to the server you're on: their game and launcher show it. */
  invite(me, b) {
    this.limit(`i:${me.uuid}`, 20);
    const other = dashed(b.uuid);
    if (!other || this.state(me.uuid, other) !== 'friends') throw new HttpError(404, 'You can only invite friends.');
    if (!this.online(other)) throw new HttpError(409, "They're not on Polar right now.");
    const server = String(b.server || (this.where(me.uuid) || {}).server || '').slice(0, 100);
    if (!server) throw new HttpError(400, "Join a server first, then invite your friend to it.");
    this.tell(other, { type: 'invite', from: { uuid: me.uuid, name: me.name }, server });
    return { sent: true, server };
  }

  // ------------------------------------------------------------------ settings sync

  getSync(me) {
    const row = this.sql.exec('SELECT data, updated FROM sync WHERE uuid = ?', me.uuid).toArray()[0];
    return row ? { data: JSON.parse(row.data), updated: row.updated } : { data: null, updated: 0 };
  }

  putSync(me, text) {
    if (text.length > MAX_SYNC) throw new HttpError(413, 'Your settings are too big to sync.');
    let data;
    try {
      data = JSON.parse(text);
    } catch {
      throw new HttpError(400, 'Expected JSON');
    }
    const updated = Date.now();
    this.sql.exec(`INSERT INTO sync (uuid, data, updated) VALUES (?, ?, ?)
      ON CONFLICT (uuid) DO UPDATE SET data = excluded.data, updated = excluded.updated`, me.uuid, JSON.stringify(data), updated);
    return { updated };
  }

  // ------------------------------------------------------------------ admin (the /admin page)

  async admin(request, url) {
    const m = /^Bearer (.+)$/.exec(request.headers.get('Authorization') || '');
    if (!this.env.ADMIN_TOKEN || !m || !sameSecret(m[1], this.env.ADMIN_TOKEN)) {
      this.limit(`a:${this.ip(request)}`, 20);
      throw new HttpError(401, 'Wrong admin password.');
    }
    const p = url.pathname.slice('/v1/admin/'.length), method = request.method;
    if (p === 'stats' && method === 'GET') return json(this.stats());
    if (p === 'player' && method === 'GET') return json(await this.adminPlayer(url.searchParams.get('q')));
    if (p === 'grant' && method === 'POST') return json(await this.adminGrant(await body(request), true));
    if (p === 'revoke' && method === 'POST') return json(await this.adminGrant(await body(request), false));
    if (p === 'ban' && method === 'POST') return json(await this.adminBan(await body(request)));
    if (p === 'codes' && method === 'GET') return json(this.listCodes());
    if (p === 'codes' && method === 'POST') return json(this.makeCodes(await body(request)));
    if (p === 'codes' && method === 'DELETE') {
      this.sql.exec('DELETE FROM codes WHERE code = ?', String(url.searchParams.get('code') || '').toUpperCase().replace(/[^A-Z0-9]/g, ''));
      return json(this.listCodes());
    }
    throw new HttpError(404, 'Not found');
  }

  stats() {
    const one = (q) => Object.values(this.sql.exec(q).toArray()[0])[0];
    const online = new Set(this.ctx.getWebSockets().map((ws) => (ws.deserializeAttachment() || {}).uuid).filter(Boolean));
    return {
      players: one('SELECT COUNT(*) FROM players WHERE first_seen > 0'),
      online: online.size,
      today: one(`SELECT COUNT(*) FROM players WHERE last_seen > ${Date.now() - 86400000}`),
      grants: one('SELECT COUNT(*) FROM grants'),
      codes: one('SELECT COUNT(*) FROM codes WHERE uses_left > 0'),
      items: ITEMS.size,
      special: [...ITEMS.values()].filter((i) => i.special).length,
    };
  }

  /** A player by name or id: from our records, or from Mojang if they never signed in to Polar. */
  async resolve(q) {
    const s = String(q || '').trim();
    const uuid = dashed(s);
    if (uuid) return this.player(uuid) || { uuid, name: s, first_seen: 0, last_seen: 0, banned: 0 };
    if (!NAME.test(s)) throw new HttpError(400, 'Type a Minecraft name or id.');
    const known = this.sql.exec('SELECT uuid, name, first_seen, last_seen, banned FROM players WHERE name = ? COLLATE NOCASE', s).toArray()[0];
    if (known) return known;
    const res = await fetch(`https://api.minecraftservices.com/minecraft/profile/lookup/name/${encodeURIComponent(s)}`);
    if (res.status !== 200) throw new HttpError(404, `No Minecraft account is called ${s}.`);
    const prof = await res.json();
    const id = dashed(prof.id);
    if (!id) throw new HttpError(502, 'Unexpected answer from Minecraft');
    this.sql.exec('INSERT OR IGNORE INTO players (uuid, name) VALUES (?, ?)', id, prof.name || s);
    return this.player(id);
  }

  async adminPlayer(q) {
    const p = await this.resolve(q);
    const grants = this.sql.exec('SELECT item, source, created FROM grants WHERE uuid = ? ORDER BY created DESC', p.uuid).toArray();
    return { uuid: p.uuid, name: p.name, signedIn: p.first_seen > 0, lastSeen: p.last_seen, banned: Boolean(p.banned), online: this.online(p.uuid), grants, loadout: this.loadout(p.uuid) };
  }

  async adminGrant(b, give) {
    const item = ITEMS.get(String(b.item || ''));
    if (!item) throw new HttpError(400, 'Pick a cosmetic.');
    const p = await this.resolve(b.player);
    if (give) this.sql.exec('INSERT OR IGNORE INTO grants (uuid, item, source, created) VALUES (?, ?, ?, ?)', p.uuid, item.id, 'admin', Date.now());
    else this.sql.exec('DELETE FROM grants WHERE uuid = ? AND item = ?', p.uuid, item.id);
    return this.adminPlayer(p.uuid);
  }

  async adminBan(b) {
    const p = await this.resolve(b.player);
    this.sql.exec('UPDATE players SET banned = ? WHERE uuid = ?', b.banned ? 1 : 0, p.uuid);
    if (b.banned) {
      for (const ws of this.ctx.getWebSockets(`u:${p.uuid}`)) {
        try {
          ws.close(4003, 'Blocked');
        } catch {
          // already gone
        }
      }
    }
    return this.adminPlayer(p.uuid);
  }

  listCodes() {
    return { codes: this.sql.exec('SELECT code, item, uses_left, uses, note, created FROM codes ORDER BY created DESC LIMIT 300').toArray() };
  }

  makeCodes(b) {
    const item = ITEMS.get(String(b.item || ''));
    if (!item) throw new HttpError(400, 'Pick a cosmetic.');
    const uses = Math.min(100000, Math.max(1, Math.floor(Number(b.uses) || 1)));
    const count = Math.min(100, Math.max(1, Math.floor(Number(b.count) || 1)));
    const note = String(b.note || '').slice(0, 80);
    const made = [];
    for (let i = 0; i < count; i++) {
      const bytes = crypto.getRandomValues(new Uint8Array(8));
      const raw = Array.from(bytes, (x) => CODE_CHARS[x % CODE_CHARS.length]).join('');
      const code = `POLAR${raw}`;
      this.sql.exec('INSERT INTO codes (code, item, uses_left, uses, note, created) VALUES (?, ?, ?, ?, ?, ?)', code, item.id, uses, uses, note, Date.now());
      made.push(code);
    }
    return { made: made.map((c) => `${c.slice(0, 5)}-${c.slice(5, 9)}-${c.slice(9)}`), ...this.listCodes() };
  }
}
