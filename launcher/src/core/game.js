'use strict';
// Prepares everything (Java, libraries, assets, mods) and starts the selected Minecraft version with Fabric
// (Legacy Fabric on 1.8.9). Polar Client itself is added on the versions it is built for.
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { currentEnv } = require('./rules');
const { fetchVanilla, latestStableLoader, fetchFabricProfile, mergeProfiles, resolveLibraries } = require('./versions');
const { ensureRuntime, javaMajor, javaFits } = require('./java');
const { ensureAssets } = require('./assets');
const { syncMods } = require('./mods');
const { download, downloadAll } = require('./http');
const { buildLaunchCommand, redact, supportsQuickPlay, splitAddress } = require('./launch');
const { extractZip } = require('./unzip');
const { resolveAddress } = require('./servers');
const { DEFAULT_VERSION, versionInfo, prepareGameDir } = require('./catalog');
const { CLIENT_ID } = require('./auth');

/** The version Polar Client's own mod is built for (kept for older imports). */
const GAME_VERSION = DEFAULT_VERSION;

/** Fallback Java when a version JSON doesn't say (all current ones do). */
const DEFAULT_RUNTIME = { component: 'jre-legacy', majorVersion: 8 };

/** Progress is reported as stages that each own a slice of the bar. */
const STAGES = {
  versions: [0, 4, 'Checking versions'],
  java: [4, 22, 'Installing Java'],
  libraries: [22, 42, 'Downloading libraries'],
  client: [42, 48, 'Downloading Minecraft'],
  assets: [48, 86, 'Downloading assets'],
  mods: [86, 96, 'Installing mods'],
  launch: [96, 100, 'Starting game'],
};

class GameManager {
  constructor({ rootDir, bundledJar, launcherVersion, onEvent }) {
    this.rootDir = rootDir;
    this.bundledJar = bundledJar;
    this.launcherVersion = launcherVersion;
    this.emit = onEvent || (() => {});
    this.process = null;
    this.logStream = null;
  }

  /** The Polar Client jar for a version: bundledJar is either one path or a function of the version id. */
  modJar(id) {
    return typeof this.bundledJar === 'function' ? this.bundledJar(id) : this.bundledJar;
  }

  get running() {
    return Boolean(this.process);
  }

  progress(stage, fraction = 0, detail = '') {
    const [from, to, label] = STAGES[stage];
    this.emit('progress', { stage, label, detail, percent: Math.round(from + (to - from) * Math.min(1, fraction)) });
  }

  log(line) {
    this.emit('log', line);
    if (this.logStream) this.logStream.write(`${line}\n`);
  }

  /** Picks Java for this version: the custom one from Settings if it fits, otherwise Mojang's runtime. */
  async pickJava(settings, vanilla) {
    const want = vanilla.javaVersion || DEFAULT_RUNTIME;
    const need = Number(want.majorVersion) || 8;
    const custom = (settings.javaPath || '').trim();
    if (custom) {
      if (!fs.existsSync(custom)) throw new Error(`Custom Java not found: ${custom}`);
      const have = javaMajor(custom);
      if (javaFits(have, need)) return custom;
      this.log(`[launcher] Your custom Java${have ? ` (Java ${have})` : ''} can't run Minecraft ${vanilla.id}, which needs Java ${need}. Using Mojang's Java ${need} instead.`);
    }
    this.progress('java', 0, `Java ${need}`);
    return ensureRuntime(this.rootDir, want.component, {
      major: need,
      onProgress: (d, t) => this.progress('java', d / t, `Java ${need} · ${d}/${t} files`),
    });
  }

  /** Unpacks the natives jars of pre-1.19 versions (LWJGL 2/3 .dll/.so/.dylib files) into the natives folder. */
  async extractNatives(libraries, nativesDir, librariesDir) {
    const jars = libraries.filter((l) => l.native);
    if (!jars.length) return;
    const marker = path.join(nativesDir, '.polar-natives.json');
    const signature = JSON.stringify(jars.map((l) => [l.path, l.sha1 || l.size || '']));
    try {
      if ((await fsp.readFile(marker, 'utf8')) === signature) return;
    } catch {
      // not extracted yet
    }
    await fsp.rm(nativesDir, { recursive: true, force: true });
    await fsp.mkdir(nativesDir, { recursive: true });
    for (const l of jars) {
      await extractZip(path.join(librariesDir, l.path), nativesDir, { exclude: (l.extract && l.extract.exclude) || ['META-INF/'] });
    }
    await fsp.writeFile(marker, signature);
  }

  async prepare(settings) {
    const root = this.rootDir;
    const version = versionInfo(settings.gameVersion);
    const dirs = {
      versions: path.join(root, 'versions'),
      libraries: path.join(root, 'libraries'),
      game: await prepareGameDir(root, version.id),
    };
    const env = currentEnv();

    this.progress('versions', 0, `Minecraft ${version.id}`);
    const vanilla = await fetchVanilla(version.id, dirs.versions);
    const loader = await latestStableLoader(version.id, dirs.versions, version.loader);
    const fabric = await fetchFabricProfile(version.id, loader, dirs.versions, version.loader);
    const profile = mergeProfiles(vanilla, fabric);
    this.log(`[launcher] Minecraft ${version.id} with ${version.loaderName} Loader ${loader}`);
    this.progress('versions', 1);

    const java = await this.pickJava(settings, vanilla);
    this.progress('java', 1);

    const libraries = resolveLibraries(profile, env);
    await downloadAll(libraries.map((l) => ({ url: l.url, dest: path.join(dirs.libraries, l.path), sha1: l.sha1, size: l.size })), {
      onProgress: (d, t) => this.progress('libraries', d / t, `${d}/${t}`),
    });

    const clientJar = path.join(dirs.versions, vanilla.id, `${vanilla.id}.jar`);
    this.progress('client', 0);
    await download(vanilla.downloads.client.url, clientJar, { sha1: vanilla.downloads.client.sha1, size: vanilla.downloads.client.size });
    let loggingConfig = null;
    if (vanilla.logging && vanilla.logging.client && vanilla.logging.client.file) {
      const f = vanilla.logging.client.file;
      loggingConfig = path.join(root, 'assets', 'log_configs', f.id);
      await download(f.url, loggingConfig, { sha1: f.sha1, size: f.size });
    }
    this.progress('client', 1);

    const { assetsDir } = await ensureAssets(root, vanilla.assetIndex, {
      onProgress: (d, t) => this.progress('assets', d / t, `${d}/${t} files`),
    });

    this.progress('mods', 0);
    const mods = await syncMods({
      gameDir: dirs.game,
      gameVersion: version.id,
      loader: version.loader,
      enabledOptional: settings.optionalMods || [],
      bundledJar: version.polar ? this.modJar(version.id) : null,
      polar: version.polar,
      log: (m) => this.log(`[launcher] ${m}`),
    });
    this.log(`[launcher] Mods: ${mods.installed.join(', ') || 'none'}`);
    this.progress('mods', 1);

    const natives = path.join(root, 'natives', version.id);
    await this.extractNatives(libraries, natives, dirs.libraries);
    for (const sub of ['java', 'jna', 'lwjgl', 'netty']) await fsp.mkdir(path.join(natives, sub), { recursive: true });

    // versions before 1.20 can't quick-play: --server needs the real host and port, so look up SRV records here
    let server = null;
    const quickJoin = (settings.quickJoin || '').trim();
    if (quickJoin && !supportsQuickPlay(profile)) {
      server = await resolveAddress(quickJoin).then((r) => ({ host: r.host, port: r.port })).catch(() => splitAddress(quickJoin));
    }

    return { version, profile, env, java, libraries, server, dirs: { ...dirs, assets: assetsDir, natives, clientJar, loggingConfig }, mods };
  }

  /** extraEnv: variables for the game process only (e.g. the account bridge address). */
  async launch(settings, account, extraEnv = {}) {
    if (this.process) throw new Error('The game is already running.');
    await fsp.mkdir(path.join(this.rootDir, 'logs'), { recursive: true });
    this.logStream = fs.createWriteStream(path.join(this.rootDir, 'logs', 'latest-launch.log'));
    const prepared = await this.prepare(settings);
    this.progress('launch', 0);
    const cmd = buildLaunchCommand({
      ...prepared,
      account,
      settings,
      launcherVersion: this.launcherVersion,
      clientId: CLIENT_ID,
    });
    this.log(`[launcher] ${cmd.java} ${redact(cmd.args).join(' ')}`);
    const child = spawn(cmd.java, cmd.args, { cwd: cmd.cwd, windowsHide: false, env: { ...process.env, ...extraEnv } });
    this.process = child;
    const pipe = (stream) => {
      let buf = '';
      stream.setEncoding('utf8');
      stream.on('data', (chunk) => {
        buf += chunk;
        const lines = buf.split(/\r?\n/);
        buf = lines.pop();
        for (const l of lines) this.log(l);
      });
    };
    pipe(child.stdout);
    pipe(child.stderr);
    child.on('error', (e) => {
      this.log(`[launcher] Failed to start Java: ${e.message}`);
      this.process = null;
      this.emit('exit', { code: -1, error: e.message });
    });
    child.on('exit', (code) => {
      this.log(`[launcher] Game exited with code ${code}`);
      this.process = null;
      if (this.logStream) this.logStream.end();
      this.emit('exit', { code });
    });
    this.progress('launch', 1);
    this.emit('started', { pid: child.pid, version: prepared.version.id, polar: prepared.version.polar });
    return { pid: child.pid, version: prepared.version.id };
  }

  kill() {
    if (this.process) this.process.kill();
  }
}

module.exports = { GameManager, GAME_VERSION, STAGES };
