'use strict';
// GameManager.prepare() end to end for 1.8.9 against a fake network: version JSON, Legacy Fabric, Java 8 runtime,
// libraries, natives unpacking, assets, Legacy Fabric API, the per-version folder and the launch command.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
const { GameManager } = require('../src/core/game');
const { MANIFEST_URL } = require('../src/core/versions');
const { RUNTIME_INDEX, platformKey } = require('../src/core/java');
const { buildLaunchCommand } = require('../src/core/launch');

const sha1 = (b) => crypto.createHash('sha1').update(b).digest('hex');

function zipOf(files) {
  const parts = [];
  const central = [];
  let offset = 0;
  for (const [name, text] of Object.entries(files)) {
    const raw = Buffer.from(text);
    const data = zlib.deflateRawSync(raw);
    const n = Buffer.from(name);
    const l = Buffer.alloc(30);
    l.writeUInt32LE(0x04034b50, 0); l.writeUInt16LE(8, 8); l.writeUInt32LE(data.length, 18); l.writeUInt32LE(raw.length, 22); l.writeUInt16LE(n.length, 26);
    const c = Buffer.alloc(46);
    c.writeUInt32LE(0x02014b50, 0); c.writeUInt16LE(8, 10); c.writeUInt32LE(data.length, 20); c.writeUInt32LE(raw.length, 24); c.writeUInt16LE(n.length, 28); c.writeUInt32LE(offset, 42);
    parts.push(l, n, data);
    central.push(c, n);
    offset += 30 + n.length + data.length;
  }
  const cd = Buffer.concat(central);
  const e = Buffer.alloc(22);
  e.writeUInt32LE(0x06054b50, 0); e.writeUInt16LE(Object.keys(files).length, 8); e.writeUInt16LE(Object.keys(files).length, 10); e.writeUInt32LE(cd.length, 12); e.writeUInt32LE(offset, 16);
  return Buffer.concat([...parts, cd, e]);
}

test('prepare() installs and lays out Minecraft 1.8.9 with Legacy Fabric', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-game-'));
  const os_ = process.platform === 'win32' ? 'windows' : process.platform === 'darwin' ? 'osx' : 'linux';
  const routes = new Map();
  const serve = (url, body) => { routes.set(url, Buffer.isBuffer(body) ? body : Buffer.from(typeof body === 'string' ? body : JSON.stringify(body))); return routes.get(url); };
  const file = (url, body) => { const b = serve(url, body); return { url, sha1: sha1(b), size: b.length }; };

  const client = file('https://dl.test/client-1.8.9.jar', 'client jar');
  const logCfg = file('https://dl.test/client-1.7.xml', '<Configuration/>');
  const assetIndex = file('https://dl.test/indexes/1.8.json', { objects: { 'minecraft/lang/en_US.lang': { hash: sha1(Buffer.from('lang')), size: 4 } } });
  serve(`https://resources.download.minecraft.net/${sha1(Buffer.from('lang')).slice(0, 2)}/${sha1(Buffer.from('lang'))}`, 'lang');
  const guava = file('https://libraries.minecraft.net/com/google/guava/guava/17.0/guava-17.0.jar', 'guava');
  const jinputNatives = file('https://libraries.minecraft.net/jinput-natives.jar', zipOf({ 'META-INF/MANIFEST.MF': 'x', 'jinput-raw_64.dll': 'jinput native' }));

  const vanilla = {
    id: '1.8.9', type: 'release', mainClass: 'net.minecraft.client.main.Main',
    minecraftArguments: '--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userProperties ${user_properties} --userType ${user_type}',
    javaVersion: { component: 'jre-legacy', majorVersion: 8 },
    assetIndex: { id: '1.8', ...assetIndex }, assets: '1.8',
    downloads: { client },
    logging: { client: { argument: '-Dlog4j.configurationFile=${path}', file: { id: 'client-1.7.xml', ...logCfg }, type: 'log4j2-xml' } },
    libraries: [
      { name: 'com.google.guava:guava:17.0', downloads: { artifact: { path: 'com/google/guava/guava/17.0/guava-17.0.jar', ...guava } } },
      { name: 'net.java.jinput:jinput-platform:2.0.5', natives: { linux: 'natives-linux', osx: 'natives-osx', windows: 'natives-windows' }, extract: { exclude: ['META-INF/'] },
        downloads: { classifiers: { [`natives-${os_}`]: { path: `net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-natives-${os_}.jar`, ...jinputNatives } } } },
    ],
  };
  const versionJson = file('https://meta.test/1.8.9.json', vanilla);
  serve(MANIFEST_URL, { versions: [{ id: '1.8.9', type: 'release', url: versionJson.url }] });

  // Legacy Fabric
  serve('https://meta.legacyfabric.net/v2/versions/loader/1.8.9', [{ loader: { version: '0.20.1', stable: true }, intermediary: { version: '1.8.9' } }]);
  serve('https://meta.legacyfabric.net/v2/versions/loader/1.8.9/0.20.1/profile/json', {
    id: 'fabric-loader-0.20.1-1.8.9', inheritsFrom: '1.8.9', mainClass: 'net.fabricmc.loader.impl.launch.knot.KnotClient',
    libraries: [
      { name: 'net.fabricmc:fabric-loader:0.20.1', url: 'https://maven.fabricmc.net/' },
      { name: 'org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17', url: 'https://maven.legacyfabric.net/',
        natives: { linux: 'natives-linux', osx: 'natives-osx', windows: 'natives-windows' }, extract: { exclude: ['META-INF/'] } },
    ],
  });
  serve('https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar', 'loader');
  serve(`https://maven.legacyfabric.net/org/lwjgl/lwjgl/lwjgl-platform/2.9.4+legacyfabric.17/lwjgl-platform-2.9.4+legacyfabric.17-natives-${os_}.jar`,
    zipOf({ 'META-INF/MANIFEST.MF': 'x', 'lwjgl64.dll': 'lwjgl native', 'OpenAL64.dll': 'openal native' }));

  // Mojang's Java 8 for this platform
  const javaRel = process.platform === 'win32' ? 'bin/javaw.exe' : process.platform === 'darwin' ? 'jre.bundle/Contents/Home/bin/java' : 'bin/java';
  const javaBin = file('https://dl.test/java', '#!/bin/sh\n');
  const runtimeManifest = file('https://dl.test/jre-legacy.json', { files: { [javaRel]: { type: 'file', executable: true, downloads: { raw: javaBin } } } });
  serve(RUNTIME_INDEX, { [platformKey()]: { 'jre-legacy': [{ manifest: runtimeManifest, version: { name: '8u51' } }] } });

  // Modrinth
  const lfapi = file('https://cdn.modrinth.test/legacy-fabric-api-1.13.5.jar', 'lfapi');
  const asked = [];
  global.fetch = async (url) => {
    const u = String(url);
    if (u.startsWith('https://api.modrinth.com/')) {
      asked.push(u);
      const body = u.includes('/project/legacy-fabric-api/') && u.includes('legacy-fabric')
        ? [{ version_number: '1.13.5', version_type: 'release', files: [{ primary: true, url: lfapi.url, filename: 'legacy-fabric-api-1.13.5.jar', size: lfapi.size, hashes: { sha1: lfapi.sha1 } }] }]
        : [];
      return new Response(JSON.stringify(body), { status: 200 });
    }
    const b = routes.get(u);
    return b ? new Response(b, { status: 200 }) : new Response(`no route ${u}`, { status: 404 });
  };

  // an existing 26.3 server list is copied into the new 1.8.9 folder
  fs.mkdirSync(path.join(root, 'instance'), { recursive: true });
  fs.writeFileSync(path.join(root, 'instance', 'servers.dat'), 'servers');

  const logs = [];
  // the launcher ships one Polar jar per version; 1.8.9 must get its own
  const jars = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-jars-'));
  for (const v of ['26.3', '1.8.9']) fs.writeFileSync(path.join(jars, `polarclient-${v}.jar`), `polar for ${v}`);
  const gm = new GameManager({ rootDir: root, bundledJar: (id) => path.join(jars, `polarclient-${id}.jar`), launcherVersion: '1.0.0', onEvent: (t, d) => { if (t === 'log') logs.push(d); } });
  const settings = { gameVersion: '1.8.9', memoryGb: 3, width: 1280, height: 720, javaPath: '', jvmArgs: '', optionalMods: ['sodium'], quickJoin: '' };
  const p = await gm.prepare(settings);

  assert.equal(p.version.id, '1.8.9');
  assert.equal(p.dirs.game, path.join(root, 'instances', '1.8.9'));
  assert.equal(fs.readFileSync(path.join(p.dirs.game, 'servers.dat'), 'utf8'), 'servers');
  assert.equal(p.java, path.join(root, 'runtime', 'jre-legacy', platformKey(), ...javaRel.split('/')));
  assert.ok(fs.existsSync(p.java));
  assert.ok(fs.existsSync(path.join(root, 'versions', '1.8.9', '1.8.9.jar')));
  assert.ok(fs.existsSync(path.join(root, 'assets', 'indexes', '1.8.json')));
  // natives unpacked (both jars), META-INF skipped
  const natives = fs.readdirSync(p.dirs.natives).sort();
  for (const f of ['OpenAL64.dll', 'jinput-raw_64.dll', 'lwjgl64.dll']) assert.ok(natives.includes(f), `${f} unpacked`);
  assert.ok(!natives.includes('META-INF'));
  // mods: Legacy Fabric API (asked for with the legacy-fabric loader) and the 1.8.9 Polar jar
  assert.deepEqual(fs.readdirSync(path.join(p.dirs.game, 'mods')).sort(), ['.polar-managed.json', 'legacy-fabric-api-1.13.5.jar', 'polarclient.jar']);
  assert.equal(fs.readFileSync(path.join(p.dirs.game, 'mods', 'polarclient.jar'), 'utf8'), 'polar for 1.8.9');
  assert.equal(asked.length, 1);
  assert.ok(logs.some((l) => l.includes('Minecraft 1.8.9 with Legacy Fabric Loader 0.20.1')));

  const cmd = buildLaunchCommand({ ...p, account: { name: 'Alex', uuid: 'u', mcToken: 't', type: 'offline' }, settings, launcherVersion: '1.0.0' });
  assert.equal(cmd.cwd, p.dirs.game);
  assert.ok(cmd.args.includes('net.fabricmc.loader.impl.launch.knot.KnotClient'));
  assert.ok(cmd.args.includes(`-Djava.library.path=${p.dirs.natives}`));
  assert.ok(cmd.args.includes('--userProperties'));
  assert.equal(cmd.args[cmd.args.indexOf('--gameDir') + 1], p.dirs.game);

  // second run: nothing re-extracted when the natives didn't change
  fs.writeFileSync(path.join(p.dirs.natives, 'marker.txt'), 'keep');
  await gm.prepare(settings);
  assert.ok(fs.existsSync(path.join(p.dirs.natives, 'marker.txt')));
  fs.rmSync(root, { recursive: true, force: true });
  fs.rmSync(jars, { recursive: true, force: true });
});
