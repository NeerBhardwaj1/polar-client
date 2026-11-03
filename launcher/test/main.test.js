'use strict';
// Loads src/main.js with a stand-in for Electron and calls the IPC handlers the launcher window calls when it opens.
// If any of them fails, the window can't show the accounts, the version dropdown or the server list.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const Module = require('node:module');

function loadMain() {
  const appData = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-main-'));
  const handlers = {};
  const noop = () => {};
  const electron = {
    app: {
      requestSingleInstanceLock: () => true, quit: noop, getPath: () => appData, isPackaged: false,
      getVersion: () => '1.0.0', whenReady: () => new Promise(noop), on: noop,
    },
    BrowserWindow: Object.assign(function BrowserWindow() {}, { getAllWindows: () => [] }),
    ipcMain: { handle: (channel, fn) => { handlers[channel] = fn; }, on: noop },
    shell: { openExternal: noop, openPath: noop },
    dialog: {},
    safeStorage: { isEncryptionAvailable: () => false, encryptString: (s) => Buffer.from(s), decryptString: (b) => b.toString() },
    protocol: { registerSchemesAsPrivileged: noop, handle: noop },
  };
  const load = Module._load;
  Module._load = function (request, ...rest) {
    return request === 'electron' ? electron : load.call(this, request, ...rest);
  };
  try {
    require('../src/main.js');
  } finally {
    Module._load = load;
  }
  return { handlers, call: (channel, ...args) => handlers[channel]({}, ...args) };
}

test('the launcher window can load everything it shows at startup', async () => {
  const { handlers, call } = loadMain();
  for (const channel of ['app:info', 'settings:get', 'accounts:list', 'versions:list', 'mods:catalog', 'servers:list', 'game:status', 'modrinth:installed']) {
    assert.ok(handlers[channel], `${channel} is registered`);
    const res = await call(channel);
    assert.equal(res.error, undefined, `${channel} failed: ${res.error}`);
    assert.equal(res.ok, true, channel);
  }

  const info = (await call('app:info')).data;
  assert.equal(info.gameVersion, '26.3');
  assert.equal(typeof info.bundledMod, 'boolean');

  for (const channel of ['modrinth:search', 'modrinth:install', 'modrinth:remove', 'modrinth:updates', 'packs:list', 'packs:save', 'packs:apply',
    'packs:delete', 'shots:list', 'shots:thumb', 'shots:full', 'shots:copy', 'shots:reveal', 'shots:delete', 'crash:openReport', 'update:check',
    'update:install', 'modrinth:installPack', 'modrinth:removePack', 'packs:toggleResource', 'packs:useShader', 'skins:list', 'skins:saveCurrent',
    'skins:addFiles', 'skins:apply', 'skins:update', 'skins:remove', 'news:get']) assert.ok(handlers[channel], `${channel} is registered`);

  // an empty skin library, and the News shipped with the launcher when the published file can't be reached
  assert.deepEqual((await call('skins:list')).data, []);
  const fetch = global.fetch;
  global.fetch = async () => { throw new Error('offline'); };
  try {
    const feed = (await call('news:get')).data;
    assert.equal(feed.source, 'bundled');
    assert.ok(feed.items.length > 0);
  } finally {
    global.fetch = fetch;
  }
  assert.equal(typeof info.discord, 'boolean');
  // Polar online features: registered, and off (with a clear reason) until the server is set up
  for (const channel of ['polar:state', 'polar:signIn', 'polar:me', 'polar:catalog', 'polar:saveLoadout', 'polar:redeem', 'polar:friends',
    'polar:addFriend', 'polar:acceptFriend', 'polar:removeFriend', 'polar:invite', 'polar:playing']) assert.ok(handlers[channel], `${channel} is registered`);
  const polarState = await call('polar:state');
  assert.equal(polarState.ok, true, polarState.error);
  assert.equal(polarState.data.configured, Boolean(require('../package.json').polar.serverUrl));
  assert.equal(polarState.data.signedIn, false);
  assert.equal(polarState.data.sync, true, 'settings sync is on by default');
  assert.equal((await call('settings:get')).data.discordStatus, true, 'Discord status is on by default');

  // nothing saved yet: no mod profiles and no screenshots, and a dev build never offers a launcher update
  assert.deepEqual((await call('packs:list')).data.profiles, []);
  assert.deepEqual((await call('shots:list')).data, []);
  assert.equal((await call('update:check')).data, null);
  const saved = await call('packs:save', 'PvP');
  assert.equal(saved.ok, true, saved.error);
  assert.deepEqual((await call('packs:list')).data.profiles.map((p) => p.name), ['PvP']);
  assert.equal((await call('packs:save', '   ')).ok, false, 'a profile needs a name');
  assert.equal((await call('packs:delete', 'PvP')).ok, true);
  assert.deepEqual((await call('packs:list')).data.profiles, []);

  // every version in the dropdown can be picked, and app:info follows it
  const versions = (await call('versions:list')).data.versions.map((v) => v.id);
  assert.deepEqual(versions, ['26.3', '1.21.11', '1.21.10', '1.16.5', '1.8.9']);
  for (const id of versions) {
    const res = await call('versions:select', id);
    assert.equal(res.ok, true, `select ${id}: ${res.error}`);
    assert.equal(res.data.selected, id);
    assert.equal((await call('app:info')).data.gameVersion, id);
  }
});
