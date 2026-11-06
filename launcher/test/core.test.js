'use strict';
// Run with: npm test   (uses the real Minecraft 26.3 version JSON and Fabric Loader 0.20.1 data as fixtures)
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { isAllowed, compareVersions } = require('../src/core/rules');
const { mavenPath, mergeProfiles, resolveLibraries } = require('../src/core/versions');
const { buildLaunchCommand, processArgs, redact } = require('../src/core/launch');
const { platformKey, pickRuntime, javaExecutable } = require('../src/core/java');
const { assetTasks } = require('../src/core/assets');
const { parseRedirect, authorizeUrl, REDIRECT_URI } = require('../src/core/auth');

const vanilla = require('./fixtures/vanilla-26.3.json');
const fabric = require('./fixtures/fabric-profile.json');
const javaIndex = require('./fixtures/java-all.json');

const WIN = { os: 'windows', arch: 'x86_64', osVersion: '10.0.22631' };
const WIN_OLD = { os: 'windows', arch: 'x86_64', osVersion: '10.0.17000' };
const MAC = { os: 'osx', arch: 'arm64', osVersion: '24.1.0' };
const LINUX = { os: 'linux', arch: 'x86_64', osVersion: '6.8.0' };

test('rules: os, arch, features and version ranges', () => {
  assert.equal(isAllowed(undefined, WIN), true);
  assert.equal(isAllowed([{ action: 'allow', os: { name: 'osx' } }], WIN), false);
  assert.equal(isAllowed([{ action: 'allow', os: { name: 'osx' } }], MAC), true);
  assert.equal(isAllowed([{ action: 'allow' }, { action: 'disallow', os: { name: 'osx' } }], MAC), false);
  assert.equal(isAllowed([{ action: 'allow', features: { has_custom_resolution: true } }], WIN, {}), false);
  assert.equal(isAllowed([{ action: 'allow', features: { has_custom_resolution: true } }], WIN, { has_custom_resolution: true }), true);
  const zgc = [{ action: 'allow', os: { name: 'windows', versionRange: { min: '10.0.17134' } } }];
  const g1 = [{ action: 'allow', os: { name: 'windows', versionRange: { max: '10.0.17134' } } }];
  assert.equal(isAllowed(zgc, WIN), true);
  assert.equal(isAllowed(g1, WIN), false);
  assert.equal(isAllowed(zgc, WIN_OLD), false);
  assert.equal(isAllowed(g1, WIN_OLD), true);
  assert.ok(compareVersions('10.0.22631', '10.0.17134') > 0);
});

test('maven paths', () => {
  assert.equal(mavenPath('net.fabricmc:fabric-loader:0.20.1'), 'net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar');
  assert.equal(mavenPath('org.lwjgl:lwjgl:3.4.3:natives-windows'), 'org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar');
  assert.equal(mavenPath('a.b:c:1@zip'), 'a/b/c/1/c-1.zip');
});

test('merge keeps Fabric main class and puts Fabric libraries first', () => {
  const merged = mergeProfiles(vanilla, fabric);
  assert.equal(merged.mainClass, 'net.fabricmc.loader.impl.launch.knot.KnotClient');
  assert.equal(merged.id, 'fabric-loader-0.20.1-26.3');
  assert.equal(merged.libraries[0].name, fabric.libraries[0].name);
  assert.equal(merged.libraries.length, vanilla.libraries.length + fabric.libraries.length);
  assert.ok(merged.arguments.jvm.includes('-DFabricMcEmu= net.minecraft.client.main.Main '));
  assert.ok(merged.arguments['default-user-jvm'].length > 0);
});

test('libraries resolve per platform with Fabric Maven URLs', () => {
  const merged = mergeProfiles(vanilla, fabric);
  for (const env of [WIN, MAC, LINUX]) {
    const libs = resolveLibraries(merged, env);
    const names = libs.map((l) => l.name);
    const other = { windows: 'natives-linux', osx: 'natives-windows', linux: 'natives-macos' }[env.os];
    assert.ok(!names.some((n) => n.endsWith(other)), `${env.os} must not get ${other}`);
    assert.ok(libs.every((l) => l.url && l.path), 'every library has a url and path');
  }
  const winLibs = resolveLibraries(merged, WIN);
  assert.ok(winLibs.some((l) => l.name === 'org.lwjgl:lwjgl-sdl:3.4.3:natives-windows'));
  const loader = winLibs.find((l) => l.name === 'net.fabricmc:fabric-loader:0.20.1');
  assert.equal(loader.url, 'https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar');
  assert.ok(winLibs.find((l) => l.name.startsWith('org.ow2.asm:asm:')).sha1, 'Fabric libs keep their sha1');
});

test('launch command for Windows', () => {
  const merged = mergeProfiles(vanilla, fabric);
  const libraries = resolveLibraries(merged, WIN);
  const dirs = { game: 'C:/pc/instance', assets: 'C:/pc/assets', libraries: 'C:/pc/libraries', natives: 'C:/pc/natives/26.3', clientJar: 'C:/pc/versions/26.3/26.3.jar', loggingConfig: 'C:/pc/assets/log_configs/client.xml' };
  const account = { name: 'Frosty', uuid: 'abc123', mcToken: 'SECRET', xuid: '42' };
  const settings = { memoryGb: 6, jvmArgs: '-XX:+UseStringDeduplication', quickJoin: 'play.example.net', customResolution: true, width: 1600, height: 900 };
  const { args, java, cwd } = buildLaunchCommand({ profile: merged, env: WIN, java: 'javaw.exe', libraries, dirs, account, settings, clientId: 'cid' });
  assert.equal(java, 'javaw.exe');
  assert.equal(cwd, dirs.game);
  assert.equal(args[0], '-Xms2G');
  assert.equal(args[1], '-Xmx6G');
  assert.ok(!args.includes('-Xmx4G'), "Mojang's default -Xmx is replaced by ours");
  assert.ok(args.includes('-XX:+UseZGC'), 'Windows 10 1803+ gets ZGC from default-user-jvm');
  assert.ok(!args.includes('-XstartOnFirstThread'), 'mac-only flag stays off on Windows');
  const cp = args[args.indexOf('-cp') + 1];
  assert.ok(cp.includes(';') && cp.endsWith(dirs.clientJar));
  assert.ok(cp.includes('fabric-loader-0.20.1.jar'));
  const main = args.indexOf('net.fabricmc.loader.impl.launch.knot.KnotClient');
  assert.ok(main > args.indexOf('-cp'), 'main class comes after JVM args');
  const game = args.slice(main + 1);
  const val = (flag) => game[game.indexOf(flag) + 1];
  assert.equal(val('--username'), 'Frosty');
  assert.equal(val('--accessToken'), 'SECRET');
  assert.equal(val('--assetIndex'), vanilla.assetIndex.id);
  assert.equal(val('--width'), '1600');
  assert.equal(val('--quickPlayMultiplayer'), 'play.example.net');
  assert.ok(!game.includes('--demo'));
  assert.ok(!game.some((a) => /\$\{/.test(a)), `no unfilled placeholders: ${game.filter((a) => /\$\{/.test(a))}`);
  assert.ok(!args.some((a) => /\$\{/.test(a)), 'no unfilled placeholders in JVM args');
  assert.ok(args.includes(`-Djava.library.path=${dirs.natives}/java`));
  assert.equal(redact(args)[args.indexOf('--accessToken') + 1], '<hidden>');
});

test('launch command for macOS adds -XstartOnFirstThread and skips custom size when off', () => {
  const merged = mergeProfiles(vanilla, fabric);
  const { args } = buildLaunchCommand({ profile: merged, env: MAC, java: 'java', libraries: resolveLibraries(merged, MAC), dirs: { game: '/g', assets: '/a', libraries: '/l', natives: '/n', clientJar: '/c.jar' }, account: { name: 'x', uuid: 'u', mcToken: 't' }, settings: { memoryGb: 4 } });
  assert.ok(args.includes('-XstartOnFirstThread'));
  assert.ok(!args.includes('--width'));
  assert.ok(!args.includes('--quickPlayMultiplayer'));
  assert.ok(args[args.indexOf('-cp') + 1].includes(':'));
});

test('JVM defaults: Polar picks smooth GC flags for versions without Mojang\'s own', () => {
  // a 1.21-style profile: no "default-user-jvm" in its arguments
  const merged = mergeProfiles(vanilla, fabric);
  const bare = { ...merged, arguments: { ...merged.arguments, 'default-user-jvm': undefined } };
  const dirs = { game: 'g', assets: 'a', libraries: 'l', natives: 'n', clientJar: 'c.jar' };
  const account = { name: 'x', uuid: 'u', mcToken: 't' };
  const run = (env, javaMajor, settings) => buildLaunchCommand({ profile: bare, env, java: 'java', javaMajor, libraries: resolveLibraries(bare, env), dirs, account, settings }).args;

  const z21 = run(WIN, 21, { memoryGb: 6 });
  assert.ok(z21.includes('-XX:+UseZGC') && z21.includes('-XX:+ZGenerational'), 'Java 21 with 6 GB: generational ZGC');
  assert.ok(!z21.includes('-XX:+UseG1GC'));
  assert.ok(z21.includes('-XX:+PerfDisableSharedMem'));
  const z25 = run(LINUX, 25, { memoryGb: 4 });
  assert.ok(z25.includes('-XX:+UseZGC') && !z25.includes('-XX:+ZGenerational'), 'ZGenerational only where it is still needed');
  const oldWin = run(WIN_OLD, 21, { memoryGb: 6 });
  assert.ok(oldWin.includes('-XX:+UseG1GC') && !oldWin.includes('-XX:+UseZGC'), 'Windows before 1803: G1, like Mojang');
  const small = run(WIN, 21, { memoryGb: 3 });
  assert.ok(small.includes('-XX:+UseG1GC') && !small.includes('-XX:+UseZGC'), 'small heaps: G1');
  const java8 = run(WIN, 8, { memoryGb: 6 });
  assert.ok(java8.includes('-XX:+UseG1GC') && java8.includes('-XX:MaxGCPauseMillis=50') && !java8.includes('-XX:+UseZGC'), 'Java 8: tuned G1');
  assert.ok(run(WIN, undefined, { memoryGb: 6 }).includes('-XX:+UseG1GC'), 'unknown Java: the safe choice');

  // your own collector replaces ours, and two collectors never meet on one command line
  const own = run(WIN, 21, { memoryGb: 6, jvmArgs: '-XX:+UseShenandoahGC' });
  assert.ok(own.includes('-XX:+UseShenandoahGC'));
  assert.ok(!own.some((a) => a === '-XX:+UseZGC' || a === '-XX:+UseG1GC' || a === '-XX:+ZGenerational' || a.startsWith('-XX:G1')));
  assert.ok(own.includes('-XX:+PerfDisableSharedMem'), 'flags for any collector stay');

  // 26.x keeps Mojang\'s defaults, minus their collector when you pick one
  const mojang = buildLaunchCommand({ profile: merged, env: WIN, java: 'java', javaMajor: 25, libraries: resolveLibraries(merged, WIN), dirs, account, settings: { memoryGb: 4, jvmArgs: '-XX:+UseG1GC' } }).args;
  assert.ok(!mojang.includes('-XX:+UseZGC') && mojang.includes('-XX:+UseG1GC') && mojang.includes('-XX:+AlwaysPreTouch'));
  assert.ok(!mojang.includes('-XX:+PerfDisableSharedMem'), "Polar's flags don't stack onto Mojang's");
});

test('processArgs handles string and list values', () => {
  const out = processArgs(['--a', '${x}', { rules: [{ action: 'allow', os: { name: 'linux' } }], value: ['--b', '${x}'] }], { env: LINUX, vars: { x: 'y' } });
  assert.deepEqual(out, ['--a', 'y', '--b', 'y']);
});

test('java runtime selection', () => {
  assert.equal(platformKey('win32', 'x64'), 'windows-x64');
  assert.equal(platformKey('darwin', 'arm64'), 'mac-os-arm64');
  assert.equal(platformKey('linux', 'x64'), 'linux');
  assert.equal(platformKey('linux', 'arm64'), null);
  const comp = vanilla.javaVersion.component;
  for (const key of ['windows-x64', 'mac-os', 'mac-os-arm64', 'linux']) {
    const entry = pickRuntime(javaIndex, comp, key);
    assert.ok(entry && entry.manifest.url, `${comp} available for ${key}`);
    assert.match(entry.version.name, /^25\./);
  }
  assert.equal(javaExecutable('/r', 'darwin'), path.join('/r', 'jre.bundle', 'Contents', 'Home', 'bin', 'java'));
  assert.equal(javaExecutable('/r', 'win32'), path.join('/r', 'bin', 'javaw.exe'));
});

test('asset tasks dedupe hashes and use 2-char folders', () => {
  const index = { objects: { a: { hash: 'abcdef', size: 1 }, b: { hash: 'abcdef', size: 1 }, c: { hash: '123456', size: 2 } } };
  const tasks = assetTasks(index, '/assets');
  assert.equal(tasks.length, 2);
  assert.equal(tasks[0].url, 'https://resources.download.minecraft.net/ab/abcdef');
  assert.equal(tasks[0].dest, path.join('/assets', 'objects', 'ab', 'abcdef'));
});

test('Microsoft redirect parsing', () => {
  assert.equal(parseRedirect('https://login.live.com/oauth20_authorize.srf?x=1'), null);
  assert.deepEqual(parseRedirect(`${REDIRECT_URI}?code=M.C123&lc=1033`), { code: 'M.C123' });
  assert.deepEqual(parseRedirect(`${REDIRECT_URI}?error=access_denied&error_description=The+user+cancelled`), { error: 'The user cancelled' });
  const url = new URL(authorizeUrl());
  assert.equal(url.searchParams.get('scope'), 'XboxLive.signin offline_access');
  assert.equal(url.searchParams.get('response_type'), 'code');
});
