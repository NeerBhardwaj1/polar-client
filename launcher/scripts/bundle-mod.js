'use strict';
// Copies the built Polar Client mod jars (one per Minecraft version, ../mod/<version>/build/libs) into resources/mod
// as polarclient-<version>.jar, so the launcher ships them and installs the right one for the version you play.
const fs = require('node:fs');
const path = require('node:path');
const { VERSIONS, modJarName } = require('../src/core/catalog');

const modRoot = path.join(__dirname, '..', '..', 'mod');
const outDir = path.join(__dirname, '..', 'resources', 'mod');

/** Newest built jar of a version project (not the sources jar). */
function builtJar(version) {
  const libs = path.join(modRoot, version, 'build', 'libs');
  if (!fs.existsSync(libs)) return null;
  const jar = fs.readdirSync(libs)
    .filter((f) => /^polarclient-.*\.jar$/.test(f) && !/-(sources|dev)\.jar$/.test(f))
    .map((f) => ({ f, t: fs.statSync(path.join(libs, f)).mtimeMs }))
    .sort((a, b) => b.t - a.t)[0];
  return jar ? path.join(libs, jar.f) : null;
}

fs.mkdirSync(outDir, { recursive: true });
// the single jar older launchers shipped (26.3 only)
fs.rmSync(path.join(outDir, 'polarclient.jar'), { force: true });

const missing = [];
for (const v of VERSIONS.filter((x) => x.polar)) {
  const jar = builtJar(v.id);
  const dest = path.join(outDir, modJarName(v.id));
  if (!jar) {
    missing.push(v.id);
    fs.rmSync(dest, { force: true });
    continue;
  }
  fs.copyFileSync(jar, dest);
  console.log(`Bundled ${path.relative(modRoot, jar)} -> resources/mod/${modJarName(v.id)}`);
}
if (missing.length) {
  console.error(`No mod build for Minecraft ${missing.join(', ')}. Build it first:  cd ../mod/<version> && ./gradlew build`);
  process.exit(1);
}
