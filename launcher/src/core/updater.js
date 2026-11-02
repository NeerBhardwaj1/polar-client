'use strict';
// Launcher self-update from GitHub releases: a newer tag with an installer for this OS is offered, downloaded and run.
const path = require('node:path');
const os = require('node:os');
const fsp = require('node:fs/promises');
const { getJson, download } = require('./http');

/** 1.2.10 > 1.2.9; "v1.3.0" works too; pre-release suffixes are ignored. */
function compareVersions(a, b) {
  const pa = String(a).replace(/^v/i, '').split(/[.+-]/).map((n) => parseInt(n, 10) || 0);
  const pb = String(b).replace(/^v/i, '').split(/[.+-]/).map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < 3; i++) {
    if ((pa[i] || 0) !== (pb[i] || 0)) return (pa[i] || 0) > (pb[i] || 0) ? 1 : -1;
  }
  return 0;
}

const PATTERNS = { win32: /setup.*\.exe$/i, darwin: /\.dmg$/i, linux: /\.AppImage$/i };

/** Asks GitHub for the latest release of `repo` ("owner/name"). Returns null when there's nothing newer. */
async function check({ repo, currentVersion, platform = process.platform }) {
  if (!repo || !/^[\w.-]+\/[\w.-]+$/.test(repo)) return null;
  const rel = await getJson(`https://api.github.com/repos/${repo}/releases/latest`, { headers: { Accept: 'application/vnd.github+json' } });
  if (!rel || rel.draft || rel.prerelease || !rel.tag_name) return null;
  if (compareVersions(rel.tag_name, currentVersion) <= 0) return null;
  const pattern = PATTERNS[platform];
  const asset = (rel.assets || []).find((a) => pattern && pattern.test(a.name));
  if (!asset || !/^https:\/\//.test(asset.browser_download_url || '')) return null;
  return {
    version: String(rel.tag_name).replace(/^v/i, ''),
    notes: String(rel.body || '').slice(0, 2000),
    url: asset.browser_download_url,
    name: path.basename(asset.name),
    size: asset.size || 0,
    page: rel.html_url || null,
  };
}

/** Downloads the installer to a temp folder and returns its path. */
async function fetchInstaller(info) {
  const dir = path.join(os.tmpdir(), 'polar-client-update');
  await fsp.mkdir(dir, { recursive: true });
  const file = path.join(dir, path.basename(info.name));
  await download(info.url, file, { size: info.size || undefined });
  return file;
}

module.exports = { compareVersions, check, fetchInstaller };
