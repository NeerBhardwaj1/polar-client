'use strict';
// Discord Rich Presence over Discord's local IPC socket (the same one the official SDK uses), so no extra
// dependency is needed. Works when the Discord desktop app is running; otherwise it quietly retries.
const net = require('node:net');
const path = require('node:path');
const crypto = require('node:crypto');

const OP_HANDSHAKE = 0, OP_FRAME = 1, OP_CLOSE = 2, OP_PING = 3, OP_PONG = 4;
const RETRY_MS = 20000;

/** Socket paths Discord may listen on (stable, PTB, Canary, Flatpak and Snap builds use different folders). */
function socketPaths() {
  if (process.platform === 'win32') return Array.from({ length: 10 }, (_, i) => `\\\\?\\pipe\\discord-ipc-${i}`);
  const base = (process.env.XDG_RUNTIME_DIR || process.env.TMPDIR || process.env.TMP || process.env.TEMP || '/tmp').replace(/\/$/, '');
  const dirs = [base, path.join(base, 'app', 'com.discordapp.Discord'), path.join(base, 'snap.discord'), path.join(base, '.flatpak', 'dev.vencord.Vesktop', 'xdg-run')];
  const out = [];
  for (const d of dirs) for (let i = 0; i < 10; i++) out.push(path.join(d, `discord-ipc-${i}`));
  return out;
}

function encode(op, payload) {
  const json = Buffer.from(JSON.stringify(payload), 'utf8');
  const head = Buffer.alloc(8);
  head.writeInt32LE(op, 0);
  head.writeInt32LE(json.length, 4);
  return Buffer.concat([head, json]);
}

function connectOne(file) {
  return new Promise((resolve, reject) => {
    const sock = net.createConnection(file);
    const fail = (e) => {
      sock.destroy();
      reject(e);
    };
    sock.once('connect', () => {
      sock.removeListener('error', fail);
      resolve(sock);
    });
    sock.once('error', fail);
  });
}

class DiscordPresence {
  /** connect: (file) => Promise<socket>, for tests. */
  constructor(clientId, { connect = connectOne, paths = socketPaths } = {}) {
    this.clientId = String(clientId || '');
    this.connectFn = connect;
    this.paths = paths;
    this.sock = null;
    this.ready = false;
    this.activity = null;
    this.enabled = false;
    this.timer = null;
    this.buffer = Buffer.alloc(0);
  }

  get configured() {
    return /^\d{15,25}$/.test(this.clientId);
  }

  /** Shows this activity (or clears it with null). Connects to Discord when needed. */
  set(activity) {
    this.activity = activity;
    this.enabled = true;
    if (!this.configured) return;
    if (this.ready) this.send();
    else this.connect();
  }

  /** Clears the status and disconnects. */
  stop() {
    this.enabled = false;
    this.activity = null;
    clearTimeout(this.timer);
    this.timer = null;
    if (this.sock) {
      try {
        if (this.ready) this.sock.write(encode(OP_FRAME, { cmd: 'SET_ACTIVITY', args: { pid: process.pid, activity: null }, nonce: crypto.randomUUID() }));
        this.sock.end();
      } catch {
        // already gone
      }
    }
    this.sock = null;
    this.ready = false;
  }

  async connect() {
    if (this.sock || !this.enabled || !this.configured) return;
    for (const file of this.paths()) {
      try {
        this.sock = await this.connectFn(file);
        break;
      } catch {
        // try the next socket
      }
    }
    if (!this.sock) return this.retry();
    this.buffer = Buffer.alloc(0);
    this.sock.on('data', (d) => this.onData(d));
    this.sock.on('close', () => this.lost());
    this.sock.on('error', () => this.lost());
    this.sock.write(encode(OP_HANDSHAKE, { v: 1, client_id: this.clientId }));
  }

  retry() {
    clearTimeout(this.timer);
    if (this.enabled) this.timer = setTimeout(() => this.connect(), RETRY_MS);
  }

  lost() {
    if (!this.sock) return;
    this.sock = null;
    this.ready = false;
    this.retry();
  }

  onData(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (this.buffer.length >= 8) {
      const op = this.buffer.readInt32LE(0), len = this.buffer.readInt32LE(4);
      if (this.buffer.length < 8 + len) return;
      let msg = null;
      try {
        msg = JSON.parse(this.buffer.subarray(8, 8 + len).toString('utf8'));
      } catch {
        // ignore a bad frame
      }
      this.buffer = this.buffer.subarray(8 + len);
      if (op === OP_PING && this.sock) this.sock.write(encode(OP_PONG, msg || {}));
      else if (op === OP_CLOSE) {
        // wrong client id or Discord shutting down: stop retrying fast
        if (this.sock) this.sock.destroy();
      } else if (op === OP_FRAME && msg && msg.evt === 'READY') {
        this.ready = true;
        this.send();
      }
    }
  }

  send() {
    if (!this.sock || !this.ready) return;
    this.sock.write(encode(OP_FRAME, { cmd: 'SET_ACTIVITY', args: { pid: process.pid, activity: this.activity }, nonce: crypto.randomUUID() }));
  }
}

/**
 * The activity for the launcher's state. Discord shows the application's name ("Playing Polar Client") above these
 * two lines. game: null in the launcher, else { version, startedAt, where } where `where` comes from the mod:
 * { state: 'menu' | 'singleplayer' | 'server' | 'hidden', server? }. Versions without the mod send nothing.
 */
function activityFor({ version, game }) {
  const assets = { large_image: 'logo', large_text: 'Polar Client' };
  if (!game) return { details: 'In the launcher', state: `Minecraft ${version}`, assets };
  const w = game.where || {};
  const STATES = { menu: 'In the menus', singleplayer: 'Singleplayer', server: w.server ? `On ${w.server}` : 'Multiplayer' };
  const activity = { details: `Minecraft ${game.version}`, timestamps: { start: Math.floor((game.startedAt || Date.now()) / 1000) }, assets };
  if (STATES[w.state]) activity.state = STATES[w.state];
  return activity;
}

module.exports = { DiscordPresence, activityFor, encode, socketPaths };
