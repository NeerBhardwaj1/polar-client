'use strict';
const { app, BrowserWindow, ipcMain, shell, safeStorage, dialog, nativeImage, clipboard, protocol } = require('electron');
const path = require('node:path');
const fs = require('node:fs');
const { Store } = require('./core/store');
const auth = require('./core/auth');
const { GameManager } = require('./core/game');
const { OPTIONAL_MODS } = require('./core/mods');
const catalog = require('./core/catalog');
const { startBridge } = require('./core/bridge');
const servers = require('./core/servers');
const profile = require('./core/profile');
const modrinth = require('./core/modrinth');
const packs = require('./core/packs');
const crash = require('./core/crash');
const screenshots = require('./core/screenshots');
const updater = require('./core/updater');
const news = require('./core/news');
const { SkinLibrary, pngFromDataUrl } = require('./core/skins');
const { DiscordPresence, activityFor } = require('./core/discord');
const { PolarService } = require('./core/polar');

const POLAR_CONFIG = require('../package.json').polar || {};
/** GitHub repository the launcher updates itself from ("owner/name" in package.json > polar.updateRepo). */
const UPDATE_REPO = POLAR_CONFIG.updateRepo || '';
/** Where the News on the home page comes from (package.json > polar.newsUrl). */
const NEWS_URL = POLAR_CONFIG.newsUrl || '';
/** The Discord application whose name and "logo" image the status shows (package.json > polar.discordClientId). */
const discord = new DiscordPresence(POLAR_CONFIG.discordClientId);
/** The Polar server: cosmetics, the badge, friends and settings sync (package.json > polar.serverUrl, set by deploy-server.bat). */
const SERVER_URL = POLAR_CONFIG.serverUrl || '';

// cosmetics files reach the wardrobe page through this scheme, from the launcher's disk cache
protocol.registerSchemesAsPrivileged([{ scheme: 'polar-cos', privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true } }]);

if (!app.requestSingleInstanceLock()) {
  app.quit();
  process.exit(0);
}

const ROOT = path.join(app.getPath('appData'), '.polarclient');
// one Polar Client jar per Minecraft version (scripts/bundle-mod.js copies them here)
const MOD_DIR = app.isPackaged ? path.join(process.resourcesPath, 'mod') : path.join(__dirname, '..', 'resources', 'mod');
const bundledJar = (versionId) => path.join(MOD_DIR, catalog.modJarName(versionId));

const store = new Store(ROOT, {
  encrypt: (s) => (safeStorage.isEncryptionAvailable() ? `enc:${safeStorage.encryptString(s).toString('base64')}` : `raw:${s}`),
  decrypt: (s) => (s.startsWith('enc:') ? safeStorage.decryptString(Buffer.from(s.slice(4), 'base64')) : s.replace(/^raw:/, '')),
});

const polar = new PolarService({
  serverUrl: SERVER_URL,
  cacheDir: path.join(ROOT, 'cache', 'cosmetics'),
  account: async () => {
    const { selected } = store.listAccounts();
    return selected ? freshAccount(selected) : null;
  },
  tokens: { get: (uuid) => store.getPolarSession(uuid), set: (uuid, v) => store.setPolarSession(uuid, v) },
  // offline profiles' Polar keys; kept when the profile is removed, so adding it again gets the same name back
  keys: {
    get: (uuid) => (store.getPolarSession(`key:${uuid}`) || {}).key || null,
    set: (uuid, key) => store.setPolarSession(`key:${uuid}`, key ? { key } : null),
  },
  onEvent: (msg) => send('polar:event', msg),
});
const polarSync = () => store.getSettings().polarSync !== false;

let win = null;
let hiddenForGame = false;
let bridge = null;
/** The current game run, for the crash helper: its folder, start time, last log lines and whether you stopped it. */
let run = null;

function closeBridge() {
  if (bridge) {
    bridge.close();
    bridge = null;
  }
}

/** Shows the launcher's state on Discord: the version picked, or the game you're playing and where. */
function updatePresence() {
  const settings = store.getSettings();
  if (settings.discordStatus === false) return discord.stop();
  discord.set(activityFor({ version: selectedVersion().id, game: run ? { version: run.version, startedAt: run.startedAt, where: run.where } : null }));
}

function send(channel, data) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, data);
}

const game = new GameManager({
  rootDir: ROOT,
  bundledJar,
  launcherVersion: app.getVersion(),
  onEvent: (type, data) => {
    if (type === 'log' && run) {
      run.log.push(String(data));
      if (run.log.length > 600) run.log.splice(0, run.log.length - 600);
    }
    if (type === 'exit' && run) {
      const finished = run;
      if (polarSync() && polar.configured) polar.pushSettings(finished.version, finished.dir).catch(() => {});
      polar.setWhere({ state: 'launcher' });
      reportCrash(run, data).catch(() => {});
    }
    send(`game:${type}`, data);
    if (type === 'exit') {
      closeBridge();
      updatePresence();
    }
    if (type === 'exit' && hiddenForGame && win) {
      hiddenForGame = false;
      win.show();
    }
  },
});

function createWindow() {
  win = new BrowserWindow({
    width: 1320,
    height: 860,
    minWidth: 1080,
    minHeight: 720,
    frame: false,
    show: false,
    backgroundColor: '#060E1E',
    title: 'Polar Client',
    icon: path.join(__dirname, '..', 'build', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  win.once('ready-to-show', () => win.show());
  win.on('maximize', () => send('win:state', { maximized: true }));
  win.on('unmaximize', () => send('win:state', { maximized: false }));
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://')) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e) => e.preventDefault());
}

/** After the game closes with an error: finds the new crash report and explains the likely cause to the window. */
async function reportCrash(r, data) {
  run = null;
  if (r.killed || !data || data.code === 0 || data.code === null) return;
  let report = '';
  let reportPath = null;
  try {
    const dir = path.join(r.dir, 'crash-reports');
    const files = fs.readdirSync(dir).map((f) => ({ f, t: fs.statSync(path.join(dir, f)).mtimeMs })).filter((x) => x.t >= r.startedAt - 2000).sort((a, b) => b.t - a.t);
    if (files.length) {
      reportPath = path.join(dir, files[0].f);
      report = fs.readFileSync(reportPath, 'utf8').slice(0, 200000);
    }
  } catch {
    // no crash report: the log is all we have
  }
  const log = r.log.join('\n');
  send('game:crash', { code: data.code, version: r.version, analysis: crash.analyze(log, report), report: reportPath ? path.basename(reportPath) : null, log: log.slice(-20000) });
  r.reportPath = reportPath;
  lastCrash = r;
}
let lastCrash = null;

/** Opens Microsoft's sign-in page in a separate window and resolves with the OAuth code. */
function microsoftSignIn() {
  return new Promise((resolve, reject) => {
    const loginWin = new BrowserWindow({
      width: 500,
      height: 660,
      parent: win,
      modal: true,
      title: 'Sign in with Microsoft',
      autoHideMenuBar: true,
      backgroundColor: '#FFFFFF',
      webPreferences: { partition: 'polar-msa', contextIsolation: true, nodeIntegration: false, sandbox: true },
    });
    let finished = false;
    const check = (url) => {
      const result = auth.parseRedirect(url);
      if (!result || finished) return;
      finished = true;
      loginWin.destroy();
      if (result.error) reject(new Error(result.error));
      else resolve(result.code);
    };
    // newer Electron passes details objects; older passes the URL as the 2nd argument
    loginWin.webContents.on('will-redirect', (e, url) => check((e && e.url) || url));
    loginWin.webContents.on('will-navigate', (e, url) => check((e && e.url) || url));
    loginWin.webContents.on('did-navigate', (e, url) => check(url));
    loginWin.on('closed', () => {
      if (!finished) reject(new Error('Sign-in was cancelled.'));
    });
    loginWin.loadURL(auth.authorizeUrl());
  });
}

async function freshAccount(uuid) {
  const acc = store.getAccount(uuid);
  if (!acc) throw new Error('Add an account first.');
  if (acc.type === 'offline') {
    return acc;
  }
  if (acc.mcToken && acc.mcExpiresAt - Date.now() > 10 * 60 * 1000) return acc;
  if (!acc.msRefreshToken) throw new Error('Please sign in again.');
  const fresh = await auth.refreshAccount(acc.msRefreshToken);
  await store.saveAccount(fresh, false);
  return { ...fresh, type: 'msa' };
}

/** Wraps IPC handlers so errors reach the UI as { error } instead of an unhandled rejection. */
function handle(channel, fn) {
  ipcMain.handle(channel, async (_e, ...args) => {
    try {
      return { ok: true, data: await fn(...args) };
    } catch (e) {
      return { ok: false, error: e && e.message ? e.message : String(e) };
    }
  });
}

/** The Minecraft version picked in the launcher's version dropdown. */
const selectedVersion = () => catalog.versionInfo(store.getSettings().gameVersion);
const versionsState = () => ({ selected: selectedVersion().id, versions: catalog.listVersions(ROOT) });

handle('app:info', () => ({ version: app.getVersion(), gameVersion: selectedVersion().id, platform: process.platform, root: ROOT, bundledMod: fs.existsSync(bundledJar(selectedVersion().id)), discord: discord.configured }));
handle('versions:list', () => versionsState());
handle('versions:select', async (id) => {
  if (!catalog.VERSIONS.some((v) => v.id === id)) throw new Error(`Minecraft ${id} isn't offered by this launcher.`);
  await store.setSettings({ gameVersion: id });
  await catalog.prepareGameDir(ROOT, id);
  send('servers:changed'); // every version has its own server list
  if (!run) updatePresence();
  return versionsState();
});
handle('accounts:list', () => store.listAccounts());
handle('accounts:login', async () => {
  const code = await microsoftSignIn();
  const acc = await auth.loginWithCode(code);
  await store.saveAccount(acc, true);
  polar.connect();
  return store.listAccounts();
});
handle('accounts:addOffline', async (name) => {
  await store.addOfflineAccount(name);
  return store.listAccounts();
});
handle('accounts:select', async (uuid) => {
  await store.selectAccount(uuid);
  polar.connect();
  return store.listAccounts();
});
handle('accounts:remove', async (uuid) => {
  polar.signOut(uuid);
  await store.removeAccount(uuid);
  polar.connect();
  return store.listAccounts();
});
handle('settings:get', () => store.getSettings());
handle('settings:set', (patch) => {
  // the launcher itself keeps the recent-server list and the version choice; don't let a stale page copy overwrite them
  const { recentServers, gameVersion, modProfiles, activeModProfile, ...rest } = patch || {};
  const saved = store.setSettings(rest);
  if ('discordStatus' in rest) Promise.resolve(saved).then(updatePresence, () => {});
  return saved;
});
handle('mods:catalog', () => OPTIONAL_MODS);

// ---------------------------------------------------------------- Modrinth browser (mods for the selected version)
const browserVersion = () => {
  const v = selectedVersion();
  return { gameVersion: v.id, loader: v.loader === 'legacy-fabric' ? 'legacy-fabric' : 'fabric' };
};
const isLegacy = (versionId) => /^1\.8/.test(versionId);
async function browserState() {
  const v = selectedVersion();
  const dir = await catalog.prepareGameDir(ROOT, v.id);
  return {
    ...(await modrinth.list({ gameDir: dir })),
    packs: await modrinth.listPacks({ gameDir: dir }),
    enabledPacks: await packs.enabledResourcePacks(dir, isLegacy(v.id)),
    shader: await packs.currentShader(dir),
    shadersSupported: v.loader !== 'legacy-fabric',
    optional: store.getSettings().optionalMods || [],
    version: v.id,
  };
}
handle('modrinth:search', async (params = {}) => {
  const { gameVersion, loader } = browserVersion();
  const kind = params.kind === 'resourcepack' || params.kind === 'shader' ? params.kind : 'mod';
  const res = await modrinth.search({ ...params, kind, gameVersion, loader });
  return { ...res, kind, gameVersion, loader, categories: kind === 'mod' ? modrinth.CATEGORIES : modrinth.PACK_CATEGORIES[kind] };
});
/** Installs a resource pack or shader pack and switches it on for the next start (when the game is closed). */
handle('modrinth:installPack', async (project = {}) => {
  const v = selectedVersion();
  const kind = project.kind;
  if (kind === 'shader' && v.loader === 'legacy-fabric') throw new Error("Shader packs need Iris, which doesn't run on Minecraft 1.8.9.");
  const dir = await catalog.prepareGameDir(ROOT, v.id);
  const { entry, replaced } = await modrinth.installPack({ gameDir: dir, gameVersion: v.id, kind, project });
  let applied = false;
  const enabled = [];
  if (kind === 'shader') {
    // shaders need Iris (which brings Sodium); it's installed with the other mods on the next Play
    const current = store.getSettings().optionalMods || [];
    if (!current.includes('iris')) {
      await store.setSettings({ optionalMods: [...current, 'iris'] });
      enabled.push('Iris Shaders');
    }
    if (!game.running) {
      await packs.setShader(dir, entry.filename);
      applied = true;
    }
  } else if (!game.running) {
    if (replaced) await packs.setResourcePack(dir, replaced, false, isLegacy(v.id));
    applied = await packs.setResourcePack(dir, entry.filename, true, isLegacy(v.id));
  }
  return { entry, applied, enabled, state: await browserState() };
});
handle('modrinth:removePack', async ({ kind, projectId } = {}) => {
  const v = selectedVersion();
  const dir = await catalog.prepareGameDir(ROOT, v.id);
  const res = await modrinth.removePack({ gameDir: dir, kind, projectId });
  if (res.removed && !game.running) {
    if (kind === 'resourcepack') await packs.setResourcePack(dir, res.filename, false, isLegacy(v.id));
    else if ((await packs.currentShader(dir)) === res.filename) await packs.setShader(dir, null);
  }
  return { ...res, state: await browserState() };
});
/** Switches a resource pack on or off, or picks the shader pack (null = shaders off). The game must be closed. */
handle('packs:toggleResource', async ({ filename, on } = {}) => {
  if (game.running) throw new Error('Close Minecraft first, or switch packs in game (Options > Resource Packs).');
  const v = selectedVersion();
  const dir = await catalog.prepareGameDir(ROOT, v.id);
  if (!fs.existsSync(path.join(dir, 'resourcepacks', path.basename(String(filename))))) throw new Error('That pack is gone from the folder.');
  const ok = await packs.setResourcePack(dir, path.basename(String(filename)), !!on, isLegacy(v.id));
  if (!ok) throw new Error(`Start Minecraft ${v.id} once first, then you can switch packs here (or in game).`);
  return browserState();
});
handle('packs:useShader', async (filename) => {
  if (game.running) throw new Error('Close Minecraft first, or pick a shader in game (Options > Video Settings > Shader Packs).');
  const v = selectedVersion();
  const dir = await catalog.prepareGameDir(ROOT, v.id);
  const name = filename ? path.basename(String(filename)) : null;
  if (name && !fs.existsSync(path.join(dir, 'shaderpacks', name))) throw new Error('That shader pack is gone from the folder.');
  await packs.setShader(dir, name);
  if (name) {
    const current = store.getSettings().optionalMods || [];
    if (!current.includes('iris')) await store.setSettings({ optionalMods: [...current, 'iris'] });
  }
  return browserState();
});
handle('modrinth:installed', () => browserState());
handle('modrinth:install', async (project) => {
  if (!project || typeof project !== 'object' || (!project.id && !project.slug)) throw new Error('Pick a mod to install.');
  const { gameVersion, loader } = browserVersion();
  const dir = await catalog.prepareGameDir(ROOT, gameVersion);
  const res = await modrinth.install({ gameDir: dir, gameVersion, loader, project, log: (m) => send('game:log', `[mods] ${m}`) });
  if (res.enable.length) {
    const current = store.getSettings().optionalMods || [];
    await store.setSettings({ optionalMods: [...new Set([...current, ...res.enable])] });
  }
  const names = res.enable.map((id) => (OPTIONAL_MODS.find((m) => m.id === id) || { name: id }).name);
  return { installed: res.installed, enabled: names, state: await browserState() };
});
handle('modrinth:updates', async () => {
  const { gameVersion, loader } = browserVersion();
  const dir = await catalog.prepareGameDir(ROOT, gameVersion);
  return { version: gameVersion, updates: await modrinth.checkUpdates({ gameDir: dir, gameVersion, loader }) };
});
const profilesFor = (version) => ((store.getSettings().modProfiles || {})[version] || []);
async function saveProfiles(version, list) {
  const all = { ...(store.getSettings().modProfiles || {}) };
  all[version] = list;
  await store.setSettings({ modProfiles: all });
}
handle('packs:list', () => ({ version: selectedVersion().id, profiles: profilesFor(selectedVersion().id), active: (store.getSettings().activeModProfile || {})[selectedVersion().id] || null }));
handle('packs:save', async (rawName) => {
  const name = String(rawName || '').trim().replace(/\s+/g, ' ').slice(0, 32);
  if (!name) throw new Error('Give the profile a name.');
  const version = selectedVersion().id;
  const dir = await catalog.prepareGameDir(ROOT, version);
  const added = (await modrinth.list({ gameDir: dir })).added;
  const profile = { name, savedAt: Date.now(), optionalMods: store.getSettings().optionalMods || [],
    mods: added.map((e) => ({ projectId: e.projectId, slug: e.slug, title: e.title, icon: e.icon, author: e.author })) };
  const list = profilesFor(version).filter((p) => p.name.toLowerCase() !== name.toLowerCase());
  if (list.length >= 20) throw new Error('You can keep up to 20 profiles per version.');
  await saveProfiles(version, [...list, profile]);
  await store.setSettings({ activeModProfile: { ...(store.getSettings().activeModProfile || {}), [version]: name } });
  return { profile };
});
handle('packs:apply', async (name) => {
  if (game.running) throw new Error('Close Minecraft before switching mod profiles.');
  const { gameVersion, loader } = browserVersion();
  const profile = profilesFor(gameVersion).find((p) => p.name === name);
  if (!profile) throw new Error('That profile is gone.');
  const dir = await catalog.prepareGameDir(ROOT, gameVersion);
  const res = await modrinth.applyProfile({ gameDir: dir, gameVersion, loader, profile, log: (m) => send('game:log', `[mods] ${m}`) });
  await store.setSettings({ optionalMods: [...new Set([...(profile.optionalMods || []), ...res.enable])],
    activeModProfile: { ...(store.getSettings().activeModProfile || {}), [gameVersion]: name } });
  return { ...res, state: await browserState() };
});
handle('packs:delete', async (name) => {
  const version = selectedVersion().id;
  await saveProfiles(version, profilesFor(version).filter((p) => p.name !== name));
  const active = { ...(store.getSettings().activeModProfile || {}) };
  if (active[version] === name) delete active[version];
  await store.setSettings({ activeModProfile: active });
});
handle('modrinth:remove', async (projectId) => {
  const dir = await catalog.prepareGameDir(ROOT, selectedVersion().id);
  const res = await modrinth.remove({ gameDir: dir, projectId });
  return { ...res, state: await browserState() };
});
/** Each version has its own game folder (26.3 keeps the original "instance" folder); created on first use. */
const gameDir = () => catalog.prepareGameDir(ROOT, selectedVersion().id);

/** Keeps the last 8 servers joined from the launcher, newest first. */
function recordRecent(list, ip, name) {
  const entry = { ip, name: name || ip, at: Date.now() };
  return [entry, ...(Array.isArray(list) ? list : []).filter((r) => r && r.ip !== ip)].slice(0, 8);
}

handle('game:launch', async (opts = {}) => {
  if (game.running) throw new Error('The game is already running.');
  const { selected } = store.listAccounts();
  const account = await freshAccount(selected);
  let settings = store.getSettings();
  // "Play" on a server card joins that server for this launch only
  const server = opts && typeof opts.server === 'string' ? opts.server.trim() : '';
  if (server) settings = { ...settings, quickJoin: server };
  if (settings.quickJoin) {
    await store.setSettings({ recentServers: recordRecent(settings.recentServers, settings.quickJoin, opts && opts.name) });
    send('servers:changed');
  }
  closeBridge();
  // lets the game's account menu switch between the launcher's accounts while it runs (Polar Client versions only)
  if (selectedVersion().polar) {
    bridge = await startBridge({
      listAccounts: () => store.listAccounts(),
      session: async (uuid) => {
        const acc = await freshAccount(uuid);
        await store.selectAccount(uuid);
        send('accounts:changed', store.listAccounts());
        polar.connect(); // be on Polar as the account you switched to
        return { uuid: acc.uuid, name: acc.name, type: acc.type, accessToken: acc.mcToken, xuid: acc.xuid || '0' };
      },
      presence: (where) => {
        if (!run) return;
        run.where = where;
        polar.setWhere(where);
        updatePresence();
      },
      polar: {
        state: async () => {
          const s = await polar.session({ signIn: false }).catch(() => null);
          return { configured: polar.configured, signedIn: Boolean(s), uuid: s ? s.uuid : null, name: s ? s.name : null, online: polar.online };
        },
        file: (rel) => polar.file(rel),
        lookup: (uuids) => polar.lookup(uuids),
        events: () => polar.drainGameEvents(),
      },
    });
  }
  // settings sync: if this version's Polar settings were changed on another PC since, use those
  if (polarSync() && selectedVersion().polar && (await polar.session({ signIn: false }).catch(() => null))) {
    const dir = await gameDir();
    await Promise.race([polar.pullSettings(selectedVersion().id, dir), new Promise((r) => setTimeout(r, 6000))]).catch(() => {});
  }
  let result;
  run = { dir: await gameDir(), version: selectedVersion().id, startedAt: Date.now(), log: [], killed: false };
  try {
    result = await game.launch(settings, account, bridge ? bridge.env : {});
  } catch (e) {
    closeBridge();
    run = null;
    throw e;
  }
  updatePresence();
  if (settings.closeOnLaunch && win) {
    hiddenForGame = true;
    win.hide();
  }
  return result;
});
handle('game:kill', () => {
  if (run) run.killed = true;
  return game.kill();
});
handle('crash:openReport', async () => {
  if (!lastCrash || !lastCrash.reportPath || !fs.existsSync(lastCrash.reportPath)) throw new Error('No crash report was written this time.');
  const err = await shell.openPath(lastCrash.reportPath);
  if (err) throw new Error(err);
});

// ---------------------------------------------------------------- screenshots (every version's screenshots folder)
const thumbs = new Map();
handle('shots:list', () => screenshots.list(ROOT));
handle('shots:thumb', (id) => {
  const file = screenshots.resolve(ROOT, id);
  if (!file) throw new Error('That screenshot is gone.');
  const key = `${file}:${fs.statSync(file).mtimeMs}`;
  if (!thumbs.has(key)) {
    const img = nativeImage.createFromPath(file);
    if (img.isEmpty()) throw new Error('Couldn\'t read that screenshot.');
    thumbs.set(key, img.resize({ width: 360, quality: 'good' }).toDataURL());
    if (thumbs.size > 300) thumbs.delete(thumbs.keys().next().value);
  }
  return thumbs.get(key);
});
handle('shots:full', (id) => {
  const file = screenshots.resolve(ROOT, id);
  if (!file) throw new Error('That screenshot is gone.');
  const img = nativeImage.createFromPath(file);
  if (img.isEmpty()) throw new Error('Couldn\'t read that screenshot.');
  const { width } = img.getSize();
  return (width > 2560 ? img.resize({ width: 2560, quality: 'good' }) : img).toDataURL();
});
handle('shots:copy', (id) => {
  const file = screenshots.resolve(ROOT, id);
  if (!file) throw new Error('That screenshot is gone.');
  clipboard.writeImage(nativeImage.createFromPath(file));
});
handle('shots:reveal', (id) => {
  const file = screenshots.resolve(ROOT, id);
  if (!file) throw new Error('That screenshot is gone.');
  shell.showItemInFolder(file);
});
handle('shots:delete', async (id) => {
  const file = screenshots.resolve(ROOT, id);
  if (!file) throw new Error('That screenshot is gone.');
  await shell.trashItem(file); // to the Recycle Bin, so it can be restored
  return screenshots.list(ROOT);
});

// ---------------------------------------------------------------- launcher updates (GitHub releases)
let pendingUpdate = null;
handle('update:check', async () => {
  if (!app.isPackaged) return null; // dev builds don't update themselves
  pendingUpdate = await updater.check({ repo: UPDATE_REPO, currentVersion: app.getVersion() });
  return pendingUpdate;
});
handle('update:install', async () => {
  if (!pendingUpdate) throw new Error('No update is waiting.');
  if (game.running) throw new Error('Close Minecraft first, then update.');
  const file = await updater.fetchInstaller(pendingUpdate);
  if (process.platform === 'win32') {
    require('node:child_process').spawn(file, [], { detached: true, stdio: 'ignore' }).unref();
    setTimeout(() => app.quit(), 500);
  } else {
    shell.showItemInFolder(file);
  }
  return { file: path.basename(file) };
});

// ---------------------------------------------------------------- servers (the game's own list, instance/servers.dat)
const notWhileRunning = () => {
  if (game.running) throw new Error('Close Minecraft first - it saves its own server list when it exits.');
};
handle('servers:list', async () => ({ servers: servers.listServers(await gameDir()), recent: store.getSettings().recentServers || [], version: selectedVersion().id }));
handle('servers:add', async (server) => {
  notWhileRunning();
  return servers.addServer(await gameDir(), server || {});
});
handle('servers:edit', async (oldIp, server) => {
  notWhileRunning();
  return servers.editServer(await gameDir(), String(oldIp || ''), server || {});
});
handle('servers:remove', async (ip) => {
  notWhileRunning();
  return servers.removeServer(await gameDir(), String(ip || ''));
});
handle('servers:ping', async (address) => {
  try {
    return await servers.ping(String(address || ''));
  } catch (e) {
    return { online: false, error: e.message };
  }
});
handle('services:status', () => profile.servicesOnline());

// ---------------------------------------------------------------- cosmetics: skin and capes of the selected Microsoft account
async function profileToken() {
  const { selected } = store.listAccounts();
  const acc = await freshAccount(selected);
  if (acc.type === 'offline') throw new Error('Skins and capes need a Microsoft account. Offline profiles always use the default skin.');
  return acc.mcToken;
}
handle('profile:get', async () => profile.getProfile(await profileToken()));
handle('skin:upload', async (variant) => {
  const token = await profileToken();
  const r = await dialog.showOpenDialog(win, { title: 'Choose a skin (64×64 PNG)', properties: ['openFile'], filters: [{ name: 'Skin', extensions: ['png'] }] });
  if (r.canceled || !r.filePaths[0]) return null;
  return profile.uploadSkin(token, r.filePaths[0], variant);
});
handle('skin:reset', async () => profile.resetSkin(await profileToken()));

// ---------------------------------------------------------------- skin library
const skinLibrary = new SkinLibrary(path.join(ROOT, 'skins'));
handle('skins:list', () => skinLibrary.list());
handle('skins:saveCurrent', async () => {
  const p = await profile.getProfile(await profileToken());
  if (!p.skin || !p.skin.data) throw new Error("Couldn't download your current skin.");
  await skinLibrary.add(pngFromDataUrl(p.skin.data), { name: `${p.name}'s skin`, variant: p.skin.variant === 'SLIM' ? 'slim' : 'classic' });
  return skinLibrary.list();
});
handle('skins:addFiles', async (variant) => {
  const r = await dialog.showOpenDialog(win, { title: 'Add skins (64×64 PNG)', properties: ['openFile', 'multiSelections'], filters: [{ name: 'Skin', extensions: ['png'] }] });
  if (r.canceled) return { list: await skinLibrary.list(), added: 0, errors: [] };
  const errors = [];
  let added = 0;
  for (const f of r.filePaths.slice(0, 30)) {
    try {
      await skinLibrary.add(await fs.promises.readFile(f), { name: path.basename(f, path.extname(f)), variant });
      added++;
    } catch (e) {
      errors.push(`${path.basename(f)}: ${e.message}`);
    }
  }
  return { list: await skinLibrary.list(), added, errors };
});
handle('skins:apply', async (id) => {
  const entry = (await skinLibrary.entries()).find((e) => e.id === id);
  if (!entry) throw new Error('That skin is gone from the library.');
  return profile.uploadSkin(await profileToken(), skinLibrary.file(id), entry.variant);
});
handle('skins:update', async ({ id, name, variant } = {}) => {
  await skinLibrary.update(id, { name, variant });
  return skinLibrary.list();
});
handle('skins:remove', async (id) => {
  await skinLibrary.remove(id);
  return skinLibrary.list();
});

// ---------------------------------------------------------------- Polar: wardrobe, codes, friends (the Polar server)
async function polarState() {
  const { selected, accounts } = store.listAccounts();
  const acc = accounts.find((a) => a.uuid === selected);
  const s = await polar.session({ signIn: false }).catch(() => null);
  return { configured: polar.configured, account: acc ? { name: acc.name, type: acc.type } : null, signedIn: Boolean(s), uuid: s ? s.uuid : null, name: s ? s.name : null, online: polar.online, sync: polarSync() };
}
handle('polar:state', () => polarState());
handle('polar:signIn', async () => {
  const { selected } = store.listAccounts();
  if (!selected) throw new Error('Add an account first.');
  try {
    await polar.signIn(await freshAccount(selected));
  } catch (e) {
    // an offline profile's name that another PC uses on Polar: the page offers to paste that PC's key
    if (e.code === 'name-taken') return { ...(await polarState()), taken: e.message };
    throw e;
  }
  polar.connect();
  return polarState();
});
handle('polar:offlineKey', (uuid) => {
  const acc = store.getAccount(uuid);
  if (!acc || acc.type !== 'offline') throw new Error('Polar keys are for offline profiles.');
  return polar.offlineKey(acc);
});
handle('polar:useOfflineKey', async (key) => {
  const { selected } = store.listAccounts();
  await polar.useOfflineKey(selected ? store.getAccount(selected) : null, key);
  polar.connect();
  return polarState();
});
handle('polar:me', () => polar.me());
handle('polar:catalog', () => polar.catalog());
handle('polar:saveLoadout', (loadout) => polar.saveLoadout(loadout));
handle('polar:redeem', (code) => polar.redeem(code));
handle('polar:friends', () => polar.friends());
handle('polar:addFriend', (name) => polar.addFriend(name));
handle('polar:acceptFriend', (uuid) => polar.acceptFriend(uuid));
handle('polar:removeFriend', (uuid) => polar.removeFriend(uuid));
handle('polar:invite', (uuid) => polar.invite(uuid, run && run.where && run.where.state === 'server' ? run.where.server : ''));
handle('polar:playing', () => (run && run.where && run.where.state === 'server' ? run.where.server : null));

// ---------------------------------------------------------------- news
handle('news:get', () => news.load({ url: NEWS_URL, cacheFile: path.join(ROOT, 'news-cache.json'), bundledFile: path.join(__dirname, 'news.json') }));
handle('cape:set', async (capeId) => profile.setCape(await profileToken(), capeId || null));
handle('game:status', () => ({ running: game.running }));
handle('folder:open', async (which) => {
  const sub = { mods: 'mods', resourcepacks: 'resourcepacks', shaderpacks: 'shaderpacks' }[which];
  const dir = which === 'logs' ? path.join(ROOT, 'logs') : sub ? path.join(await gameDir(), sub) : await gameDir();
  fs.mkdirSync(dir, { recursive: true });
  const err = await shell.openPath(dir);
  if (err) throw new Error(err);
});
handle('java:pick', async () => {
  const r = await dialog.showOpenDialog(win, {
    title: 'Choose a Java executable',
    properties: ['openFile'],
    filters: process.platform === 'win32' ? [{ name: 'Java', extensions: ['exe'] }] : [],
  });
  return r.canceled ? null : r.filePaths[0];
});
handle('link:open', async (url) => {
  if (typeof url === 'string' && url.startsWith('https://')) await shell.openExternal(url);
});

ipcMain.on('win:minimize', () => win && win.minimize());
ipcMain.on('win:maximize', () => win && (win.isMaximized() ? win.unmaximize() : win.maximize()));
ipcMain.on('win:close', () => win && win.close());

app.on('second-instance', () => {
  if (win) {
    if (win.isMinimized()) win.restore();
    win.show();
    win.focus();
  }
});
app.whenReady().then(() => {
  protocol.handle('polar-cos', async (req) => {
    const cors = { 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'no-store' };
    try {
      const rel = decodeURIComponent(new URL(req.url).pathname.replace(/^\/+/, ''));
      const buf = await polar.file(rel);
      return new Response(buf, { headers: { ...cors, 'Content-Type': rel.endsWith('.png') ? 'image/png' : 'application/json' } });
    } catch (e) {
      return new Response(String((e && e.message) || e), { status: (e && e.status) || 404, headers: cors });
    }
  });
  createWindow();
  updatePresence();
  polar.connect();
});
app.on('before-quit', () => {
  discord.stop();
  polar.disconnect();
});
app.on('window-all-closed', () => {
  if (!game.running) app.quit();
});
app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});
