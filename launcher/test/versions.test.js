'use strict';
// Multi-version support: 1.8.9 (Legacy Fabric, pre-1.13 arguments, LWJGL 2 natives), 1.16.5 (natives jars, no quick play),
// the version catalog, Java selection and per-version mods. The 1.8.9 / 1.16.5 / Legacy Fabric profiles below are trimmed
// copies of the real JSONs (same structure and values, fewer libraries).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const zlib = require('node:zlib');
const { mergeProfiles, resolveLibraries, withClassifier } = require('../src/core/versions');
const { buildLaunchCommand, isLegacyProfile, supportsQuickPlay, splitAddress } = require('../src/core/launch');
const { parseJavaMajor, javaFits } = require('../src/core/java');
const { extractZip, listEntries } = require('../src/core/unzip');
const catalog = require('../src/core/catalog');
const { syncMods } = require('../src/core/mods');

const WIN = { os: 'windows', arch: 'x86_64', osVersion: '10.0.22631' };
const MAC = { os: 'osx', arch: 'x86_64', osVersion: '21.0.0' };

const vanilla189 = {
  id: '1.8.9',
  type: 'release',
  mainClass: 'net.minecraft.client.main.Main',
  minecraftArguments: '--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userProperties ${user_properties} --userType ${user_type}',
  javaVersion: { component: 'jre-legacy', majorVersion: 8 },
  assetIndex: { id: '1.8' },
  assets: '1.8',
  logging: { client: { argument: '-Dlog4j.configurationFile=${path}', file: { id: 'client-1.7.xml', sha1: '50c9cc4af6d853d9fc137c84bcd153e2bd3a9a82', size: 966 }, type: 'log4j2-xml' } },
  libraries: [
    { name: 'com.google.guava:guava:17.0', downloads: { artifact: { path: 'com/google/guava/guava/17.0/guava-17.0.jar', url: 'https://libraries.minecraft.net/com/google/guava/guava/17.0/guava-17.0.jar', sha1: '9c6ef172e8de35fd8d4d8783e4821e57cdef7445', size: 2243036 } } },
    { name: 'org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20150209', rules: [{ action: 'allow' }, { action: 'disallow', os: { name: 'osx' } }],
      downloads: { artifact: { path: 'org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar', url: 'https://libraries.minecraft.net/x.jar', sha1: 'a', size: 1 } } },
    { name: 'org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209', rules: [{ action: 'allow' }, { action: 'disallow', os: { name: 'osx' } }],
      natives: { linux: 'natives-linux', osx: 'natives-osx', windows: 'natives-windows' }, extract: { exclude: ['META-INF/'] },
      downloads: { classifiers: {
        'natives-linux': { path: 'org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-linux.jar', url: 'https://libraries.minecraft.net/l.jar', sha1: 'b', size: 1 },
        'natives-windows': { path: 'org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-windows.jar', url: 'https://libraries.minecraft.net/w.jar', sha1: 'c', size: 1 },
      } } },
    { name: 'tv.twitch:twitch-platform:6.5', rules: [{ action: 'allow' }, { action: 'disallow', os: { name: 'linux' } }],
      natives: { linux: 'natives-linux', osx: 'natives-osx', windows: 'natives-windows-${arch}' }, extract: { exclude: ['META-INF/'] },
      downloads: { classifiers: {
        'natives-osx': { path: 'tv/twitch/twitch-platform/6.5/twitch-platform-6.5-natives-osx.jar', url: 'https://libraries.minecraft.net/to.jar', sha1: 'd', size: 1 },
        'natives-windows-32': { path: 'tv/twitch/twitch-platform/6.5/twitch-platform-6.5-natives-windows-32.jar', url: 'https://libraries.minecraft.net/t32.jar', sha1: 'e', size: 1 },
        'natives-windows-64': { path: 'tv/twitch/twitch-platform/6.5/twitch-platform-6.5-natives-windows-64.jar', url: 'https://libraries.minecraft.net/t64.jar', sha1: 'f', size: 1 },
      } } },
  ],
};

// meta.legacyfabric.net/v2/versions/loader/1.8.9/0.20.1/profile/json (no "arguments", LWJGL 2 replaced with Legacy Fabric's build)
const legacyFabric = {
  id: 'fabric-loader-0.20.1-1.8.9',
  inheritsFrom: '1.8.9',
  type: 'release',
  mainClass: 'net.fabricmc.loader.impl.launch.knot.KnotClient',
  libraries: [
    { name: 'org.ow2.asm:asm:9.10.1', url: 'https://maven.fabricmc.net/', sha1: '1', size: 1 },
    { name: 'net.legacyfabric:intermediary:1.8.9', url: 'https://maven.legacyfabric.net/' },
    { name: 'net.fabricmc:fabric-loader:0.20.1', url: 'https://maven.fabricmc.net/' },
    { name: 'org.lwjgl.lwjgl:lwjgl:2.9.4+legacyfabric.17', url: 'https://maven.legacyfabric.net/' },
    { name: 'org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17', url: 'https://maven.legacyfabric.net/',
      natives: { linux: 'natives-linux', osx: 'natives-osx', windows: 'natives-windows' }, extract: { exclude: ['META-INF/'] } },
  ],
};

const vanilla1165 = {
  id: '1.16.5',
  type: 'release',
  mainClass: 'net.minecraft.client.main.Main',
  javaVersion: { component: 'jre-legacy', majorVersion: 8 },
  assetIndex: { id: '1.16' },
  arguments: {
    game: ['--username', '${auth_player_name}', '--version', '${version_name}', '--gameDir', '${game_directory}', '--assetsDir', '${assets_root}',
      '--assetIndex', '${assets_index_name}', '--uuid', '${auth_uuid}', '--accessToken', '${auth_access_token}', '--userType', '${user_type}', '--versionType', '${version_type}',
      { rules: [{ action: 'allow', features: { has_custom_resolution: true } }], value: ['--width', '${resolution_width}', '--height', '${resolution_height}'] }],
    jvm: [{ rules: [{ action: 'allow', os: { name: 'osx' } }], value: ['-XstartOnFirstThread'] }, '-Djava.library.path=${natives_directory}', '-cp', '${classpath}'],
  },
  libraries: [
    { name: 'org.lwjgl:lwjgl:3.2.2', natives: { linux: 'natives-linux', osx: 'natives-macos', windows: 'natives-windows' }, extract: { exclude: ['META-INF/'] },
      downloads: { artifact: { path: 'org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2.jar', url: 'https://libraries.minecraft.net/a.jar', sha1: 'a', size: 1 },
        classifiers: { 'natives-windows': { path: 'org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2-natives-windows.jar', url: 'https://libraries.minecraft.net/n.jar', sha1: 'n', size: 1 } } } },
  ],
};
const fabric1165 = { id: 'fabric-loader-0.18.0-1.16.5', mainClass: 'net.fabricmc.loader.impl.launch.knot.KnotClient', arguments: { game: [], jvm: ['-DFabricMcEmu= net.minecraft.client.main.Main '] },
  libraries: [{ name: 'net.fabricmc:fabric-loader:0.18.0', url: 'https://maven.fabricmc.net/' }] };

const ACCOUNT = { name: 'Steve', uuid: 'abc', mcToken: 'TOKEN', xuid: '9', type: 'msa' };
const DIRS = { game: '/g', assets: '/a', libraries: '/l', natives: '/n', clientJar: '/v/1.8.9.jar', loggingConfig: '/a/log_configs/client-1.7.xml' };
const SETTINGS = { memoryGb: 4, width: 1280, height: 720, quickJoin: '', jvmArgs: '' };

test('Legacy Fabric on 1.8.9: natives-only LWJGL 2 from Maven, the vanilla LWJGL dropped', () => {
  const merged = mergeProfiles(vanilla189, legacyFabric);
  assert.equal(merged.mainClass, 'net.fabricmc.loader.impl.launch.knot.KnotClient');
  assert.equal(merged.minecraftArguments, vanilla189.minecraftArguments);
  assert.ok(isLegacyProfile(merged));
  const libs = resolveLibraries(merged, WIN);
  const paths = libs.map((l) => l.path);
  assert.ok(paths.includes('org/lwjgl/lwjgl/lwjgl/2.9.4+legacyfabric.17/lwjgl-2.9.4+legacyfabric.17.jar'));
  assert.ok(!paths.some((p) => p.includes('nightly')), 'vanilla LWJGL 2 is replaced by Legacy Fabric\'s');
  const natives = libs.filter((l) => l.native).map((l) => [l.path, l.url]);
  assert.deepEqual(natives, [
    ['org/lwjgl/lwjgl/lwjgl-platform/2.9.4+legacyfabric.17/lwjgl-platform-2.9.4+legacyfabric.17-natives-windows.jar',
      'https://maven.legacyfabric.net/org/lwjgl/lwjgl/lwjgl-platform/2.9.4+legacyfabric.17/lwjgl-platform-2.9.4+legacyfabric.17-natives-windows.jar'],
    ['tv/twitch/twitch-platform/6.5/twitch-platform-6.5-natives-windows-64.jar', 'https://libraries.minecraft.net/t64.jar'],
  ]);
  assert.ok(!paths.includes('org/lwjgl/lwjgl/lwjgl-platform/2.9.4+legacyfabric.17/lwjgl-platform-2.9.4+legacyfabric.17.jar'), 'no main jar for a natives-only library');
  assert.equal(withClassifier('a.b:c:1@zip', 'natives-osx'), 'a.b:c:1:natives-osx@zip');
});

test('1.8.9 launch command: legacy JVM defaults, minecraftArguments, --server for quick join', () => {
  const merged = mergeProfiles(vanilla189, legacyFabric);
  const libraries = resolveLibraries(merged, WIN);
  const cmd = buildLaunchCommand({ profile: merged, env: WIN, java: 'java', dirs: DIRS, libraries, account: ACCOUNT,
    settings: { ...SETTINGS, quickJoin: 'mc.hypixel.net', customResolution: true }, server: { host: 'mc.hypixel.net', port: 25565 } });
  const a = cmd.args;
  const main = a.indexOf('net.fabricmc.loader.impl.launch.knot.KnotClient');
  const jvm = a.slice(0, main), game = a.slice(main + 1);
  assert.ok(jvm.includes('-Djava.library.path=/n'));
  assert.ok(jvm.includes('-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump'));
  assert.ok(jvm.includes('-Dlog4j.configurationFile=/a/log_configs/client-1.7.xml'));
  const cp = jvm[jvm.indexOf('-cp') + 1].split(';');
  assert.equal(cp[cp.length - 1], '/v/1.8.9.jar');
  assert.ok(!cp.some((p) => p.includes('natives')), 'natives jars are not on the classpath');
  assert.deepEqual(game.slice(0, 4), ['--username', 'Steve', '--version', 'fabric-loader-0.20.1-1.8.9']);
  assert.equal(game[game.indexOf('--userProperties') + 1], '{}');
  assert.equal(game[game.indexOf('--accessToken') + 1], 'TOKEN');
  assert.equal(game[game.indexOf('--assetIndex') + 1], '1.8');
  assert.deepEqual(game.slice(game.indexOf('--width'), game.indexOf('--width') + 4), ['--width', '1280', '--height', '720']);
  assert.deepEqual(game.slice(game.indexOf('--server'), game.indexOf('--server') + 4), ['--server', 'mc.hypixel.net', '--port', '25565']);
  assert.ok(!game.some((x) => x.includes('${')), 'every placeholder is filled');

  const mac = buildLaunchCommand({ profile: merged, env: MAC, java: 'java', dirs: DIRS, libraries: resolveLibraries(merged, MAC), account: ACCOUNT, settings: SETTINGS });
  assert.ok(mac.args.includes('-XstartOnFirstThread'));
  assert.ok(!mac.args.includes('--server'));
});

test('1.16.5: natives jars from classifiers, --server instead of quick play, modern arguments kept', () => {
  const merged = mergeProfiles(vanilla1165, fabric1165);
  assert.ok(!isLegacyProfile(merged));
  assert.ok(!supportsQuickPlay(merged));
  const libraries = resolveLibraries(merged, WIN);
  assert.deepEqual(libraries.filter((l) => l.native).map((l) => l.path), ['org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2-natives-windows.jar']);
  assert.ok(libraries.some((l) => !l.native && l.path === 'org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2.jar'));
  const cmd = buildLaunchCommand({ profile: merged, env: WIN, java: 'java', dirs: { ...DIRS, clientJar: '/v/1.16.5.jar' }, libraries, account: ACCOUNT,
    settings: { ...SETTINGS, quickJoin: 'play.example.net:25570' } });
  const a = cmd.args;
  assert.ok(a.includes('-DFabricMcEmu= net.minecraft.client.main.Main '));
  assert.deepEqual(a.slice(a.indexOf('--server'), a.indexOf('--server') + 4), ['--server', 'play.example.net', '--port', '25570']);
  assert.ok(!a.includes('--quickPlayMultiplayer'));
  assert.ok(!a.includes('--userProperties'));
});

test('26.3 still joins with quick play, not --server', () => {
  const vanilla = require('./fixtures/vanilla-26.3.json');
  const fabric = require('./fixtures/fabric-profile.json');
  const merged = mergeProfiles(vanilla, fabric);
  assert.ok(supportsQuickPlay(merged));
  const cmd = buildLaunchCommand({ profile: merged, env: WIN, java: 'java', dirs: DIRS, libraries: resolveLibraries(merged, WIN), account: ACCOUNT,
    settings: { ...SETTINGS, quickJoin: 'play.example.net' } });
  assert.equal(cmd.args[cmd.args.indexOf('--quickPlayMultiplayer') + 1], 'play.example.net');
  assert.ok(!cmd.args.includes('--server'));
});

test('addresses split like the game does', () => {
  assert.deepEqual(splitAddress('a.net'), { host: 'a.net', port: 25565 });
  assert.deepEqual(splitAddress('a.net:1234'), { host: 'a.net', port: 1234 });
  assert.deepEqual(splitAddress('[::1]:25566'), { host: '::1', port: 25566 });
});

test('Java versions: parse `java -version` and only run old versions on Java 8', () => {
  assert.equal(parseJavaMajor('java version "1.8.0_51"\nJava(TM) SE Runtime Environment'), 8);
  assert.equal(parseJavaMajor('openjdk version "25.0.1" 2025-10-21'), 25);
  assert.equal(parseJavaMajor('openjdk version "21" 2023-09-19'), 21);
  assert.equal(parseJavaMajor('nothing'), null);
  assert.equal(javaFits(8, 8), true);
  assert.equal(javaFits(17, 8), false);
  assert.equal(javaFits(25, 21), true);
  assert.equal(javaFits(21, 25), false);
  assert.equal(javaFits(null, 21), false);
});

/** Minimal zip writer for the test (the reader ignores CRCs). */
function makeZip(files) {
  const locals = [];
  const centrals = [];
  let offset = 0;
  for (const [name, text, deflate] of files) {
    const raw = Buffer.from(text);
    const data = deflate ? zlib.deflateRawSync(raw) : raw;
    const n = Buffer.from(name);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(deflate ? 8 : 0, 8);
    local.writeUInt32LE(data.length, 18);
    local.writeUInt32LE(raw.length, 22);
    local.writeUInt16LE(n.length, 26);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(deflate ? 8 : 0, 10);
    central.writeUInt32LE(data.length, 20);
    central.writeUInt32LE(raw.length, 24);
    central.writeUInt16LE(n.length, 28);
    central.writeUInt32LE(offset, 42);
    locals.push(local, n, data);
    centrals.push(central, n);
    offset += 30 + n.length + data.length;
  }
  const cd = Buffer.concat(centrals);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(files.length, 8);
  end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(cd.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, cd, end]);
}

test('natives jars unpack without META-INF and never outside the folder', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-natives-'));
  const jar = path.join(dir, 'natives.jar');
  fs.writeFileSync(jar, makeZip([
    ['META-INF/MANIFEST.MF', 'Manifest-Version: 1.0', false],
    ['lwjgl64.dll', 'dll bytes '.repeat(50), true],
    ['sub/', '', false],
    ['sub/OpenAL64.dll', 'openal', false],
    ['../evil.dll', 'nope', false],
  ]));
  assert.equal(listEntries(fs.readFileSync(jar)).length, 5);
  const out = path.join(dir, 'out');
  const got = await extractZip(jar, out, { exclude: ['META-INF/'] });
  assert.deepEqual(got.sort(), ['lwjgl64.dll', 'sub/OpenAL64.dll']);
  assert.equal(fs.readFileSync(path.join(out, 'lwjgl64.dll'), 'utf8'), 'dll bytes '.repeat(50));
  assert.ok(!fs.existsSync(path.join(dir, 'evil.dll')));
  assert.ok(!fs.existsSync(path.join(out, 'META-INF')));
  fs.rmSync(dir, { recursive: true, force: true });
});

test('catalog: the five versions, folders per version, servers copied into a new folder', async () => {
  assert.deepEqual(catalog.VERSIONS.map((v) => v.id), ['26.3', '1.21.11', '1.21.10', '1.16.5', '1.8.9']);
  assert.equal(catalog.versionInfo(undefined).id, '26.3');
  assert.equal(catalog.versionInfo('1.12.2').id, '26.3');
  assert.equal(catalog.versionInfo('1.8.9').loader, 'legacy-fabric');
  assert.equal(catalog.versionInfo('1.8.9').loaderName, 'Legacy Fabric');
  assert.equal(catalog.VERSIONS.filter((v) => v.polar).length, 5, 'Polar Client is built for every version');
  assert.equal(catalog.modJarName('1.8.9'), 'polarclient-1.8.9.jar');
  assert.equal(catalog.modJarName('nope'), 'polarclient-26.3.jar');
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-root-'));
  assert.equal(catalog.gameDirFor(root, '26.3'), path.join(root, 'instance'));
  assert.equal(catalog.gameDirFor(root, '1.16.5'), path.join(root, 'instances', '1.16.5'));
  fs.mkdirSync(path.join(root, 'instance'));
  fs.writeFileSync(path.join(root, 'instance', 'servers.dat'), 'NBT');
  const dir = await catalog.prepareGameDir(root, '1.8.9');
  assert.equal(fs.readFileSync(path.join(dir, 'servers.dat'), 'utf8'), 'NBT');
  fs.writeFileSync(path.join(dir, 'servers.dat'), 'MINE');
  await catalog.prepareGameDir(root, '1.8.9');
  assert.equal(fs.readFileSync(path.join(dir, 'servers.dat'), 'utf8'), 'MINE', 'an existing list is never replaced');
  assert.equal(catalog.listVersions(root).find((v) => v.id === '1.8.9').installed, false);
  fs.rmSync(root, { recursive: true, force: true });
});

function fakeModrinth(byLoader) {
  const asked = [];
  global.fetch = async (url) => {
    const u = new URL(String(url));
    if (u.hostname === 'api.modrinth.com') {
      const slug = decodeURIComponent(u.pathname.split('/')[3]);
      const loader = JSON.parse(u.searchParams.get('loaders'))[0];
      asked.push(`${loader}:${slug}`);
      const v = (byLoader[loader] || {})[slug];
      const fileUrl = `https://cdn.test/${slug}-${v}.jar`;
      const body = v ? [{ version_number: v, version_type: 'release', files: [{ primary: true, url: fileUrl, filename: `${slug}-${v}.jar`, size: Buffer.byteLength(`jar:${fileUrl}`), hashes: {} }] }] : [];
      return new Response(JSON.stringify(body), { status: 200 });
    }
    if (u.hostname === 'cdn.test') return new Response(Buffer.from(`jar:${url}`), { status: 200 });
    return new Response('nope', { status: 404 });
  };
  return asked;
}

test('mods: Legacy Fabric API on 1.8.9, no Polar jar on other versions, missing builds skipped', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-mods-'));
  const bundled = path.join(dir, 'polarclient.jar');
  fs.writeFileSync(bundled, 'polar');

  const asked = fakeModrinth({ 'legacy-fabric': { 'legacy-fabric-api': '1.13.5' } });
  let res = await syncMods({ gameDir: dir, gameVersion: '1.8.9', loader: 'legacy-fabric', enabledOptional: ['sodium'], bundledJar: null, polar: false });
  assert.deepEqual(asked, ['legacy-fabric:legacy-fabric-api'], 'no performance pack for Legacy Fabric');
  assert.deepEqual(fs.readdirSync(path.join(dir, 'mods')).sort(), ['.polar-managed.json', 'legacy-fabric-api-1.13.5.jar']);
  assert.deepEqual(res.installed, ['Legacy Fabric API 1.13.5']);

  fakeModrinth({});
  res = await syncMods({ gameDir: dir, gameVersion: '1.8.9', loader: 'legacy-fabric', enabledOptional: [], bundledJar: null, polar: false });
  assert.deepEqual(res.skipped, ['Legacy Fabric API'], 'a missing Legacy Fabric API build is not fatal');

  // 1.16.5: Fabric API + the performance mods that exist; the Polar jar from an earlier 26.3 sync is removed
  const dir2 = fs.mkdtempSync(path.join(os.tmpdir(), 'polar-mods-'));
  fakeModrinth({ fabric: { 'fabric-api': '0.169.0', sodium: '0.8.0' } });
  await syncMods({ gameDir: dir2, gameVersion: '26.3', enabledOptional: ['sodium'], bundledJar: bundled, polar: true });
  assert.ok(fs.existsSync(path.join(dir2, 'mods', 'polarclient.jar')));
  fakeModrinth({ fabric: { 'fabric-api': '0.42.0+1.16', sodium: 'mc1.16.5-0.2.0' } });
  res = await syncMods({ gameDir: dir2, gameVersion: '1.16.5', enabledOptional: ['sodium', 'immediatelyfast'], bundledJar: null, polar: false });
  assert.deepEqual(fs.readdirSync(path.join(dir2, 'mods')).sort(), ['.polar-managed.json', 'fabric-api-0.42.0+1.16.jar', 'sodium-mc1.16.5-0.2.0.jar']);
  assert.deepEqual(res.skipped, ['ImmediatelyFast']);
  fs.rmSync(dir, { recursive: true, force: true });
  fs.rmSync(dir2, { recursive: true, force: true });
});
