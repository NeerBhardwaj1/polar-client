'use strict';
// Settings and saved accounts on disk. Tokens are encrypted with the OS keychain via Electron safeStorage.
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { OPTIONAL_MODS } = require('./mods');

/** Minecraft usernames: 3-16 letters, digits or underscores. */
const OFFLINE_NAME = /^[A-Za-z0-9_]{3,16}$/;

/** The UUID Minecraft gives an offline player: a v3 (MD5) UUID of "OfflinePlayer:<name>", without dashes. */
function offlineUuid(name) {
  const h = crypto.createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest();
  h[6] = (h[6] & 0x0f) | 0x30;
  h[8] = (h[8] & 0x3f) | 0x80;
  return h.toString('hex');
}

function defaultSettings() {
  const totalGb = Math.floor(os.totalmem() / 1024 ** 3);
  return {
    memoryGb: Math.max(2, Math.min(6, Math.floor(totalGb / 3))),
    jvmArgs: '',
    javaPath: '',
    width: 1280,
    height: 720,
    fullscreen: false,
    customResolution: false,
    closeOnLaunch: false,
    discordStatus: true,
    quickJoin: '',
    optionalMods: OPTIONAL_MODS.filter((m) => m.default).map((m) => m.id),
    // every optional mod the launcher has shown you, so ones added later can start switched on (once)
    offeredMods: OPTIONAL_MODS.map((m) => m.id),
  };
}

/** The optional mods launchers up to 1.0.5 offered, for settings saved before "offeredMods" existed. */
const OFFERED_UNTIL_1_0_5 = ['sodium', 'lithium', 'ferrite-core', 'entityculling', 'immediatelyfast', 'moreculling', 'dynamic-fps',
  'c2me-fabric', 'sodium-extra', 'iris', 'xaeros-minimap', 'xaeros-world-map', 'appleskin', 'jade', 'emi', 'replaymod',
  'simple-voice-chat', 'modmenu'];

/**
 * Saved settings plus the defaults for anything missing. Optional mods that are on by default and newer than your
 * saved choice (a performance mod added in an update) join it once; if you switch one off, it stays off.
 */
function withDefaults(saved) {
  const settings = { ...defaultSettings(), ...saved };
  if (Array.isArray(saved.optionalMods)) {
    const offered = Array.isArray(saved.offeredMods) ? saved.offeredMods : OFFERED_UNTIL_1_0_5;
    const fresh = OPTIONAL_MODS.filter((m) => m.default && !offered.includes(m.id)).map((m) => m.id);
    settings.optionalMods = [...new Set([...saved.optionalMods, ...fresh])];
  }
  settings.offeredMods = OPTIONAL_MODS.map((m) => m.id);
  return settings;
}

class Store {
  /** encrypt/decrypt: functions (string -> string) for secrets; identity when unavailable. */
  constructor(rootDir, { encrypt, decrypt } = {}) {
    this.rootDir = rootDir;
    this.settingsFile = path.join(rootDir, 'settings.json');
    this.accountsFile = path.join(rootDir, 'accounts.json');
    this.polarFile = path.join(rootDir, 'polar-sessions.json');
    this.encrypt = encrypt || ((s) => s);
    this.decrypt = decrypt || ((s) => s);
  }

  readJson(file, fallback) {
    try {
      return JSON.parse(fs.readFileSync(file, 'utf8'));
    } catch {
      return fallback;
    }
  }

  async writeJson(file, data) {
    await fsp.mkdir(path.dirname(file), { recursive: true });
    const tmp = `${file}.tmp`;
    await fsp.writeFile(tmp, JSON.stringify(data, null, 2));
    await fsp.rename(tmp, file);
  }

  getSettings() {
    return withDefaults(this.readJson(this.settingsFile, {}));
  }

  async setSettings(patch) {
    const next = { ...this.getSettings(), ...patch };
    next.memoryGb = Math.max(1, Math.min(64, Number(next.memoryGb) || 4));
    next.width = Math.max(640, Number(next.width) || 1280);
    next.height = Math.max(360, Number(next.height) || 720);
    await this.writeJson(this.settingsFile, next);
    return next;
  }

  /** Accounts without secrets, for the UI. */
  listAccounts() {
    const data = this.readJson(this.accountsFile, { selected: null, accounts: [] });
    return {
      selected: data.selected,
      accounts: data.accounts.map(({ uuid, name, type }) => ({ uuid, name, type: type || 'msa' })),
      offlineUnlocked: true,
    };
  }

  hasMicrosoftAccount() {
    const data = this.readJson(this.accountsFile, { selected: null, accounts: [] });
    return data.accounts.some((a) => (a.type || 'msa') === 'msa');
  }

  async addOfflineAccount(rawName) {
    const name = String(rawName || '').trim();
    if (!OFFLINE_NAME.test(name)) throw new Error('Use 3-16 letters, numbers or underscores for the name.');
    const data = this.readJson(this.accountsFile, { selected: null, accounts: [] });
    const taken = data.accounts.find((a) => a.type === 'offline' && a.name.toLowerCase() === name.toLowerCase());
    if (taken) throw new Error(`There's already an offline profile called ${taken.name}.`);
    const uuid = offlineUuid(name);
    data.accounts.push({ uuid, name, type: 'offline' });
    data.selected = uuid;
    await this.writeJson(this.accountsFile, data);
  }

  getAccount(uuid) {
    const data = this.readJson(this.accountsFile, { selected: null, accounts: [] });
    const a = data.accounts.find((x) => x.uuid === uuid);
    if (!a) return null;
    if (a.type === 'offline') return { uuid: a.uuid, name: a.name, type: 'offline', xuid: '0', mcToken: '0', mcExpiresAt: Infinity };
    return {
      uuid: a.uuid,
      name: a.name,
      type: 'msa',
      xuid: a.xuid,
      msRefreshToken: a.msRefreshToken ? this.decrypt(a.msRefreshToken) : null,
      mcToken: a.mcToken ? this.decrypt(a.mcToken) : null,
      mcExpiresAt: a.mcExpiresAt || 0,
    };
  }

  async saveAccount(acc, select = true) {
    const data = this.readJson(this.accountsFile, { selected: null, accounts: [] });
    const entry = {
      type: 'msa',
      uuid: acc.uuid,
      name: acc.name,
      xuid: acc.xuid,
      msRefreshToken: acc.msRefreshToken ? this.encrypt(acc.msRefreshToken) : null,
      mcToken: acc.mcToken ? this.encrypt(acc.mcToken) : null,
      mcExpiresAt: acc.mcExpiresAt,
    };
    data.accounts = [...data.accounts.filter((x) => x.uuid !== acc.uuid), entry];
    if (select || !data.selected) data.selected = acc.uuid;
    await this.writeJson(this.accountsFile, data);
  }

  async selectAccount(uuid) {
    const data = this.readJson(this.accountsFile, { selected: null, accounts: [] });
    if (data.accounts.some((a) => a.uuid === uuid)) data.selected = uuid;
    await this.writeJson(this.accountsFile, data);
  }

  /** The Polar server sign-in of an account ({ token, expires, name }), kept encrypted like the Minecraft tokens. */
  getPolarSession(uuid) {
    const v = this.readJson(this.polarFile, {})[uuid];
    if (!v) return null;
    try {
      return JSON.parse(this.decrypt(v));
    } catch {
      return null;
    }
  }

  setPolarSession(uuid, value) {
    const data = this.readJson(this.polarFile, {});
    if (value) data[uuid] = this.encrypt(JSON.stringify(value));
    else delete data[uuid];
    fs.mkdirSync(path.dirname(this.polarFile), { recursive: true });
    fs.writeFileSync(`${this.polarFile}.tmp`, JSON.stringify(data, null, 2));
    fs.renameSync(`${this.polarFile}.tmp`, this.polarFile);
  }

  async removeAccount(uuid) {
    const data = this.readJson(this.accountsFile, { selected: null, accounts: [] });
    data.accounts = data.accounts.filter((a) => a.uuid !== uuid);
    if (data.selected === uuid) data.selected = data.accounts.length ? data.accounts[0].uuid : null;
    await this.writeJson(this.accountsFile, data);
  }
}

module.exports = { Store, defaultSettings, offlineUuid, OFFLINE_NAME };
