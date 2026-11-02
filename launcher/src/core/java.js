'use strict';
// Downloads Mojang's own Java runtime for each version (Java 25 for 26.x, 21 for 1.21, 8 for 1.16.5 and 1.8.9),
// so players don't need Java installed.
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { getJsonCached, downloadAll } = require('./http');

/** Major version from `java -version` output: "1.8.0_402" -> 8, "25.0.1" -> 25. */
function parseJavaMajor(output) {
  const m = String(output || '').match(/version "(\d+)(?:\.(\d+))?/);
  if (!m) return null;
  const first = Number(m[1]);
  return first === 1 && m[2] ? Number(m[2]) : first;
}

/** Runs a Java executable to find its major version, or null if it can't be run. */
function javaMajor(javaPath) {
  try {
    // javaw.exe has no console, but -version still prints to stderr
    const r = spawnSync(javaPath, ['-version'], { encoding: 'utf8', timeout: 15000, windowsHide: true });
    return parseJavaMajor(`${r.stderr || ''}\n${r.stdout || ''}`);
  } catch {
    return null;
  }
}

/**
 * Can a Java of major version `have` run a game that asks for `need`?
 * Versions that need Java 8 (1.16.5 and older) are only run on Java 8, the one they were made for.
 */
function javaFits(have, need) {
  if (!have) return false;
  return need <= 8 ? have === 8 : have >= need;
}

const RUNTIME_INDEX = 'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';

/** Mojang's platform keys in the runtime index. */
function platformKey(platform = process.platform, arch = process.arch) {
  if (platform === 'win32') return arch === 'arm64' ? 'windows-arm64' : arch === 'ia32' ? 'windows-x86' : 'windows-x64';
  if (platform === 'darwin') return arch === 'arm64' ? 'mac-os-arm64' : 'mac-os';
  if (platform === 'linux') return arch === 'ia32' ? 'linux-i386' : arch === 'x64' ? 'linux' : null;
  return null;
}

function javaExecutable(runtimeDir, platform = process.platform) {
  if (platform === 'win32') return path.join(runtimeDir, 'bin', 'javaw.exe');
  if (platform === 'darwin') return path.join(runtimeDir, 'jre.bundle', 'Contents', 'Home', 'bin', 'java');
  return path.join(runtimeDir, 'bin', 'java');
}

/** Picks the runtime entry for a component on this platform, or null if Mojang doesn't ship one. */
function pickRuntime(index, component, key = platformKey()) {
  const list = key && index[key] && index[key][component];
  return list && list.length ? list[0] : null;
}

/**
 * Makes sure the runtime is installed and returns the java executable path.
 * onProgress(done, total) during download.
 */
async function ensureRuntime(rootDir, component, { onProgress, major } = {}) {
  const key = platformKey();
  const javaName = major ? `Java ${major}` : 'Java';
  if (!key) throw new Error(`No bundled Java for ${process.platform}/${process.arch}. Set a custom ${javaName} path in Settings.`);
  const dir = path.join(rootDir, 'runtime', component, key);
  const marker = path.join(dir, '.polar-runtime.json');
  const index = await getJsonCached(RUNTIME_INDEX, path.join(rootDir, 'cache', 'java-runtime-all.json'));
  const entry = pickRuntime(index, component, key);
  if (!entry) throw new Error(`Mojang has no ${javaName} (${component}) for ${key}. Set a custom ${javaName} path in Settings.`);

  try {
    const m = JSON.parse(await fsp.readFile(marker, 'utf8'));
    if (m.sha1 === entry.manifest.sha1 && fs.existsSync(javaExecutable(dir))) return javaExecutable(dir);
  } catch {
    // not installed yet
  }

  const manifest = await getJsonCached(entry.manifest.url, path.join(rootDir, 'cache', `java-${component}-${key}.json`));
  const files = Object.entries(manifest.files);
  for (const [rel, f] of files) {
    if (f.type === 'directory') await fsp.mkdir(path.join(dir, rel), { recursive: true });
  }
  const tasks = files
    .filter(([, f]) => f.type === 'file' && f.downloads && f.downloads.raw)
    .map(([rel, f]) => ({ url: f.downloads.raw.url, dest: path.join(dir, rel), sha1: f.downloads.raw.sha1, size: f.downloads.raw.size, executable: f.executable }));
  await downloadAll(tasks, { concurrency: 16, onProgress });

  if (process.platform !== 'win32') {
    for (const t of tasks) if (t.executable) await fsp.chmod(t.dest, 0o755);
    for (const [rel, f] of files) {
      if (f.type !== 'link') continue;
      const linkPath = path.join(dir, rel);
      await fsp.rm(linkPath, { force: true });
      await fsp.mkdir(path.dirname(linkPath), { recursive: true });
      await fsp.symlink(f.target, linkPath).catch(() => {});
    }
  }
  await fsp.writeFile(marker, JSON.stringify({ component, platform: key, sha1: entry.manifest.sha1, version: entry.version && entry.version.name }));
  return javaExecutable(dir);
}

module.exports = { RUNTIME_INDEX, platformKey, javaExecutable, pickRuntime, ensureRuntime, parseJavaMajor, javaMajor, javaFits };
