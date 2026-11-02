'use strict';
// Evaluates the "rules" blocks in Mojang version JSONs (libraries and arguments).
const os = require('node:os');

/** Describes the machine the way version JSONs do. */
function currentEnv() {
  const platform = process.platform === 'win32' ? 'windows' : process.platform === 'darwin' ? 'osx' : 'linux';
  const arch = process.arch === 'x64' ? 'x86_64' : process.arch === 'ia32' ? 'x86' : process.arch;
  return { os: platform, arch, osVersion: os.release() };
}

/** Compares dotted versions like "10.0.17134". Returns <0, 0 or >0. */
function compareVersions(a, b) {
  const pa = String(a).split(/[.\-]/).map((n) => parseInt(n, 10) || 0);
  const pb = String(b).split(/[.\-]/).map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] || 0) - (pb[i] || 0);
    if (d !== 0) return d;
  }
  return 0;
}

function osMatches(ruleOs, env) {
  if (!ruleOs) return true;
  if (ruleOs.name && ruleOs.name !== env.os) return false;
  if (ruleOs.arch && ruleOs.arch !== env.arch) return false;
  if (ruleOs.version) {
    try {
      if (!new RegExp(ruleOs.version).test(env.osVersion)) return false;
    } catch {
      return false;
    }
  }
  if (ruleOs.versionRange) {
    const { min, max } = ruleOs.versionRange;
    // min is inclusive, max is exclusive, so the two halves of a split never both match
    if (min && compareVersions(env.osVersion, min) < 0) return false;
    if (max && compareVersions(env.osVersion, max) >= 0) return false;
  }
  return true;
}

function featuresMatch(ruleFeatures, features) {
  if (!ruleFeatures) return true;
  return Object.entries(ruleFeatures).every(([k, v]) => Boolean(features[k]) === v);
}

/** No rules = allowed. Otherwise the last matching rule decides; nothing matching = disallowed. */
function isAllowed(rules, env = currentEnv(), features = {}) {
  if (!Array.isArray(rules) || rules.length === 0) return true;
  let allowed = false;
  for (const rule of rules) {
    if (osMatches(rule.os, env) && featuresMatch(rule.features, features)) {
      allowed = rule.action === 'allow';
    }
  }
  return allowed;
}

module.exports = { currentEnv, compareVersions, isAllowed };
