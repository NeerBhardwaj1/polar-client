'use strict';
// Polar online features, from the launcher's side: signing in to the Polar server with the player's Minecraft account,
// the live connection (while it's open you count as "on Polar": your badge and cosmetics show to other Polar players),
// the cosmetics files (cached on disk, shared with the game through the bridge), friends, codes and settings sync.
//
// Signing in never sends the Minecraft login to the Polar server: the launcher tells Mojang "this player joins server
// <challenge>" (what every Minecraft server does), and the Polar server asks Mojang whether that happened.
const fs = require('node:fs');
const path = require('node:path');

const UUID = /^[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}$/i;
const FILE = /^(catalog\.json|badge\.png|(models|textures|thumbs)\/[a-z0-9_]+\.(json|png))$/;
const LOOKUP_TTL = 60 * 1000;
const PING_EVERY = 25 * 1000;
const SYNC_FILES = ['polarclient.json', 'polarclient-waypoints.json'];
const MAX_SYNC = 190 * 1024;

const dashed = (id) => {
  const hex = String(id || '').toLowerCase().replace(/-/g, '');
  return /^[0-9a-f]{32}$/.test(hex) ? `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}` : null;
};

class PolarError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.status = status;
  }
}

/**
 * options:
 *   serverUrl        the Polar server (package.json > polar.serverUrl); empty: online features are off
 *   cacheDir         where cosmetics files are kept
 *   account()        async: the selected account, with a fresh Minecraft token ({ uuid, name, type, mcToken })
 *   tokens           { get(uuid) -> {token, expires} | null, set(uuid, value|null) } (kept encrypted by the store)
 *   fetch, WebSocket the network (injected for tests)
 *   onEvent(msg)     live messages from the server (friend requests, invites) and connection changes
 */
class PolarService {
  constructor({ serverUrl, cacheDir, account, tokens, fetch = globalThis.fetch, WebSocket = globalThis.WebSocket, onEvent = () => {}, now = Date.now }) {
    this.url = String(serverUrl || '').replace(/\/+$/, '');
    this.cacheDir = cacheDir;
    this.account = account;
    this.tokens = tokens;
    this.fetch = fetch;
    this.WebSocket = WebSocket;
    this.onEvent = onEvent;
    this.now = now;
    this.lookups = new Map();
    this.checked = new Set();
    this.gameEvents = [];
    this.where = { state: 'launcher', server: null };
    this.ws = null;
    this.wsUuid = null;
    this.retry = 0;
    this.timers = { ping: null, reconnect: null };
    this.wanted = false;
  }

  get configured() {
    return Boolean(this.url);
  }

  need() {
    if (!this.configured) throw new PolarError("The Polar server isn't set up yet.", 503);
  }

  async request(method, p, { body, token, raw = false, headers = {} } = {}) {
    this.need();
    let res;
    try {
      res = await this.fetch(`${this.url}${p}`, {
        method,
        headers: { ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
        body: body === undefined ? undefined : typeof body === 'string' ? body : JSON.stringify(body),
        signal: AbortSignal.timeout(15000),
      });
    } catch {
      throw new PolarError("Can't reach the Polar server. Check your internet connection.", 0);
    }
    if (raw) return res;
    let data = null;
    try {
      data = await res.json();
    } catch {
      data = null;
    }
    if (!res.ok) throw new PolarError((data && data.error) || `The Polar server answered ${res.status}.`, res.status);
    return data;
  }

  // ---------------------------------------------------------------- signing in

  /** The Polar session of the selected account: signs in when there's no valid token. null for offline accounts. */
  async session({ signIn = true } = {}) {
    if (!this.configured) return null;
    const acc = await this.account();
    if (!acc || acc.type === 'offline') return null;
    const uuid = dashed(acc.uuid);
    const saved = this.tokens.get(uuid);
    if (saved && saved.token && saved.expires - this.now() > 24 * 3600 * 1000) return { token: saved.token, uuid, name: saved.name || acc.name };
    if (!signIn) return null;
    return this.signIn(acc);
  }

  async signIn(acc) {
    if (!acc || acc.type === 'offline') throw new PolarError('Polar cosmetics and friends need a Microsoft account.', 400);
    const { challenge } = await this.request('POST', '/v1/auth/challenge');
    let res;
    try {
      res = await this.fetch('https://sessionserver.mojang.com/session/minecraft/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ accessToken: acc.mcToken, selectedProfile: String(acc.uuid).replace(/-/g, ''), serverId: challenge }),
        signal: AbortSignal.timeout(15000),
      });
    } catch {
      throw new PolarError("Can't reach Minecraft's servers to sign you in.", 0);
    }
    if (res.status !== 204 && res.status !== 200) throw new PolarError('Minecraft refused the sign-in. Sign in to your account again in the launcher.', 401);
    const v = await this.request('POST', '/v1/auth/verify', { body: { name: acc.name, challenge } });
    const uuid = dashed(v.uuid);
    this.tokens.set(uuid, { token: v.token, expires: v.expires, name: v.name });
    return { token: v.token, uuid, name: v.name };
  }

  signOut(uuid) {
    this.tokens.set(dashed(uuid), null);
    if (this.wsUuid === dashed(uuid)) this.disconnect();
  }

  /** An API call as the selected account; a token the server no longer accepts is replaced once. */
  async api(method, p, body) {
    this.need();
    let s = await this.session();
    if (!s) throw new PolarError('Polar cosmetics and friends need a Microsoft account.', 400);
    try {
      return await this.request(method, p, { body, token: s.token });
    } catch (e) {
      if (e.status !== 401) throw e;
      this.tokens.set(s.uuid, null);
      s = await this.session();
      return this.request(method, p, { body, token: s.token });
    }
  }

  // ---------------------------------------------------------------- live connection

  /** Keeps the live connection open for the selected account (call again after switching accounts). */
  async connect() {
    this.wanted = true;
    if (!this.configured || !this.WebSocket) return;
    let s;
    try {
      s = await this.session();
    } catch (e) {
      this.onEvent({ type: 'status', online: false, error: e.message });
      return this.scheduleReconnect();
    }
    if (!s) return this.disconnect(true);
    if (this.ws && this.wsUuid === s.uuid && this.ws.readyState <= 1) return;
    this.close();
    const ws = new this.WebSocket(`${this.url.replace(/^http/, 'ws')}/v1/live?token=${encodeURIComponent(s.token)}`);
    this.ws = ws;
    this.wsUuid = s.uuid;
    ws.onopen = () => {
      this.retry = 0;
      this.sendWhere();
      this.onEvent({ type: 'status', online: true, uuid: s.uuid, name: s.name });
      this.timers.ping = setInterval(() => {
        try {
          ws.send('ping');
        } catch {
          // closing
        }
      }, PING_EVERY);
    };
    ws.onmessage = (e) => {
      let msg;
      try {
        msg = JSON.parse(String(e.data));
      } catch {
        return;
      }
      if (!msg || (msg.type !== 'invite' && msg.type !== 'friends')) return;
      this.gameEvents.push(msg);
      if (this.gameEvents.length > 20) this.gameEvents.shift();
      this.onEvent(msg);
    };
    ws.onclose = (e) => {
      if (this.ws !== ws) return;
      clearInterval(this.timers.ping);
      this.ws = null;
      this.onEvent({ type: 'status', online: false });
      if (e && e.code === 4003) return; // blocked by an admin
      if (e && e.code === 4000) return; // replaced by a newer connection (another launcher on the same account)
      if (this.wanted) this.scheduleReconnect();
    };
    ws.onerror = () => {};
  }

  scheduleReconnect() {
    clearTimeout(this.timers.reconnect);
    const delay = Math.min(60000, 2000 * 2 ** this.retry++);
    this.timers.reconnect = setTimeout(() => this.connect(), delay);
    if (this.timers.reconnect.unref) this.timers.reconnect.unref();
  }

  close() {
    clearInterval(this.timers.ping);
    clearTimeout(this.timers.reconnect);
    if (this.ws) {
      const ws = this.ws;
      this.ws = null;
      try {
        ws.close(1000, 'bye');
      } catch {
        // already closed
      }
    }
  }

  disconnect(keepWanted = false) {
    this.wanted = keepWanted && this.wanted;
    this.close();
    this.wsUuid = null;
  }

  get online() {
    return Boolean(this.ws && this.ws.readyState === 1);
  }

  /** Where you are, for friends: 'launcher', 'menu', 'singleplayer', 'server' (with its address) or 'hidden'. */
  setWhere(where) {
    this.where = { state: where.state, server: where.state === 'server' ? where.server || null : null };
    this.sendWhere();
  }

  sendWhere() {
    if (!this.online) return;
    try {
      this.ws.send(JSON.stringify({ type: 'where', ...this.where }));
    } catch {
      // closing
    }
  }

  /** Friend requests and invites for the game to show (each one once). */
  drainGameEvents() {
    return this.gameEvents.splice(0, this.gameEvents.length);
  }

  // ---------------------------------------------------------------- cosmetics files

  /** A cosmetics file (catalog.json, models/…, textures/…, thumbs/…): from the disk cache, checked once per session. */
  async file(rel) {
    if (!FILE.test(rel)) throw new PolarError('No such file', 404);
    const file = path.join(this.cacheDir, ...rel.split('/'));
    const meta = `${file}.etag`;
    const cached = fs.existsSync(file);
    if (cached && this.checked.has(rel)) return fs.promises.readFile(file);
    if (!this.configured) {
      if (cached) return fs.promises.readFile(file);
      this.need();
    }
    const headers = {};
    if (cached && fs.existsSync(meta)) headers['If-None-Match'] = fs.readFileSync(meta, 'utf8').trim();
    let res;
    try {
      res = await this.request('GET', `/cosmetics/${rel}`, { raw: true, headers });
    } catch (e) {
      if (cached) return fs.promises.readFile(file); // offline: what we have
      throw e;
    }
    if (res.status === 304 && cached) {
      this.checked.add(rel);
      return fs.promises.readFile(file);
    }
    if (!res.ok) throw new PolarError(res.status === 404 ? 'No such file' : `The Polar server answered ${res.status}.`, res.status);
    const buf = Buffer.from(await res.arrayBuffer());
    await fs.promises.mkdir(path.dirname(file), { recursive: true });
    await fs.promises.writeFile(`${file}.part`, buf);
    await fs.promises.rename(`${file}.part`, file);
    const etag = res.headers.get('etag');
    if (etag) await fs.promises.writeFile(meta, etag);
    this.checked.add(rel);
    return buf;
  }

  async catalog() {
    return JSON.parse((await this.file('catalog.json')).toString('utf8'));
  }

  /** Which of these players are on Polar now and what they wear (for the game), cached for a minute. */
  async lookup(uuids) {
    const ids = [...new Set((Array.isArray(uuids) ? uuids : []).map(dashed).filter(Boolean))].slice(0, 200);
    const t = this.now();
    const out = {};
    const ask = [];
    for (const id of ids) {
      const c = this.lookups.get(id);
      if (c && t - c.at < LOOKUP_TTL) {
        if (c.value) out[id] = c.value;
      } else {
        ask.push(id);
      }
    }
    if (ask.length) {
      const r = await this.api('POST', '/v1/players', { uuids: ask });
      for (const id of ask) {
        const value = (r.players || {})[id] || null;
        this.lookups.set(id, { at: t, value });
        if (value) out[id] = value;
      }
      if (this.lookups.size > 5000) this.lookups.clear();
    }
    return out;
  }

  /** Your own profile: what you wear and the special cosmetics you were given. */
  me() {
    return this.api('GET', '/v1/me');
  }

  async saveLoadout(loadout) {
    const r = await this.api('PUT', '/v1/me/loadout', loadout);
    const s = await this.session({ signIn: false });
    if (s) this.lookups.delete(s.uuid);
    return r;
  }

  redeem(code) {
    return this.api('POST', '/v1/redeem', { code: String(code || '') });
  }

  // ---------------------------------------------------------------- friends

  friends() {
    return this.api('GET', '/v1/friends');
  }

  addFriend(name) {
    return this.api('POST', '/v1/friends/add', { name: String(name || '').trim() });
  }

  acceptFriend(uuid) {
    return this.api('POST', '/v1/friends/accept', { uuid });
  }

  removeFriend(uuid) {
    return this.api('POST', '/v1/friends/remove', { uuid });
  }

  invite(uuid, server) {
    return this.api('POST', '/v1/friends/invite', { uuid, server });
  }

  // ---------------------------------------------------------------- settings sync (the Polar mod's settings per version)

  /**
   * Before the game starts: if the copy on the server is newer than this PC's files, use it.
   * Returns the files written (names).
   */
  async pullSettings(versionId, gameDir) {
    const r = await this.api('GET', '/v1/sync');
    const copy = r && r.data && r.data.versions && r.data.versions[versionId];
    if (!copy || !copy.files) return [];
    const dir = path.join(gameDir, 'config');
    const written = [];
    for (const name of SYNC_FILES) {
      if (typeof copy.files[name] !== 'string') continue;
      const file = path.join(dir, name);
      const local = fs.existsSync(file) ? fs.statSync(file).mtimeMs : 0;
      if (local >= (copy.saved || 0)) continue; // this PC's file is newer
      await fs.promises.mkdir(dir, { recursive: true });
      await fs.promises.writeFile(file, copy.files[name]);
      written.push(name);
    }
    return written;
  }

  /** After the game closes: uploads this version's settings when they changed. */
  async pushSettings(versionId, gameDir) {
    const dir = path.join(gameDir, 'config');
    const files = {};
    let newest = 0;
    for (const name of SYNC_FILES) {
      const file = path.join(dir, name);
      if (!fs.existsSync(file)) continue;
      files[name] = await fs.promises.readFile(file, 'utf8');
      newest = Math.max(newest, fs.statSync(file).mtimeMs);
    }
    if (!Object.keys(files).length) return false;
    const r = await this.api('GET', '/v1/sync');
    const data = (r && r.data && typeof r.data === 'object' && r.data) || {};
    data.versions = data.versions && typeof data.versions === 'object' ? data.versions : {};
    const old = data.versions[versionId];
    if (old && old.files && SYNC_FILES.every((n) => old.files[n] === files[n])) return false;
    data.versions[versionId] = { files, saved: Math.round(newest) };
    let text = JSON.stringify(data);
    if (text.length > MAX_SYNC) {
      // waypoints can get big: keep the main settings everywhere, drop waypoints from other versions first
      for (const v of Object.keys(data.versions)) if (v !== versionId) delete data.versions[v].files['polarclient-waypoints.json'];
      text = JSON.stringify(data);
      if (text.length > MAX_SYNC) throw new PolarError('Your Polar settings are too big to sync.', 413);
    }
    await this.api('PUT', '/v1/sync', data);
    return true;
  }
}

module.exports = { PolarService, PolarError, dashed, UUID };
