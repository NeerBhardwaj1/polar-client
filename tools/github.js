#!/usr/bin/env node
'use strict';
// Puts Polar Client on GitHub and releases launcher updates. Run through publish.bat and release.bat.
//   node tools/github.js publish ["what changed"]   uploads your changes (code, news.json) to GitHub
//   node tools/github.js release [1.2.3]            bumps the launcher version, tags it and uploads it; GitHub Actions
//                                                   then builds everything and publishes the release launchers update from
const { spawnSync, spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const readline = require('node:readline');

const ROOT = path.resolve(__dirname, '..');
const PKG = path.join(ROOT, 'launcher', 'package.json');
const LOCK = path.join(ROOT, 'launcher', 'package-lock.json');
const NEWS = [path.join(ROOT, 'news.json'), path.join(ROOT, 'launcher', 'src', 'news.json')];
const PENDING_WORKFLOWS = path.join(ROOT, 'tools', 'github-workflows');
const REPO = (JSON.parse(fs.readFileSync(PKG, 'utf8')).polar || {}).updateRepo || 'NeerBhardwaj1/polar-client';
const [OWNER, NAME] = REPO.split('/');
const WEB = `https://github.com/${REPO}`;
const MY_NAME = 'Neer Bhardwaj';
const FIRST_COMMIT = [
  'Polar Client',
  '',
  'The Polar Client mod for Minecraft 26.3, 1.21.11, 1.21.10, 1.16.5 and 1.8.9, its launcher, and the workflows that build',
  'and release them.',
  '',
  'Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>',
  'Claude-Session: https://claude.ai/code/session_01TyxVbCtZCGQgEh9SppRUK1',
].join('\n');

// typed lines are queued, so nothing typed (or pasted) ahead of a question is lost
const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
const typed = [];
const waiting = [];
let inputClosed = false;
rl.on('line', (line) => (waiting.length ? waiting.shift()(line) : typed.push(line)));
rl.on('close', () => {
  inputClosed = true;
  while (waiting.length) waiting.shift()(null);
});

/** The next line typed, or null when there's no more input. */
function nextLine(prompt) {
  process.stdout.write(prompt);
  if (typed.length) return Promise.resolve(typed.shift());
  if (inputClosed) return Promise.resolve(null);
  return new Promise((resolve) => waiting.push(resolve));
}

function say(s = '') {
  console.log(s);
}

function fail(msg) {
  console.error(`\n${msg}`);
  rl.close();
  process.exit(1);
}

async function ask(question, def = '') {
  const a = await nextLine(def ? `${question} [${def}]: ` : `${question}: `);
  return (a || '').trim() || def;
}

/** Several lines, ended by an empty one. */
async function askLines(question) {
  say(question);
  const lines = [];
  for (;;) {
    const line = await nextLine('> ');
    if (line === null || !line.trim()) break;
    lines.push(line.trimEnd());
  }
  return lines.join('\n').trim();
}

/** Runs git in the project folder. quiet: capture output instead of showing it. */
function git(args, { quiet = false, allowFail = false } = {}) {
  const r = spawnSync('git', args, { cwd: ROOT, encoding: 'utf8', stdio: quiet ? 'pipe' : ['inherit', 'inherit', 'pipe'] });
  if (r.error) fail("Git isn't installed. Install it from https://git-scm.com/download/win (or run: winget install --id Git.Git -e), then run this again.");
  if (!quiet && r.stderr) process.stderr.write(r.stderr);
  if (r.status !== 0 && !allowFail) fail(`git ${args[0]} failed (see above).`);
  return { ok: r.status === 0, out: (r.stdout || '').trim(), err: r.stderr || '' };
}

function openUrl(url) {
  const cmd = process.platform === 'win32' ? 'explorer.exe' : process.platform === 'darwin' ? 'open' : 'xdg-open';
  try {
    spawn(cmd, [url], { detached: true, stdio: 'ignore' }).unref();
  } catch {
    // the link is printed too
  }
}

function compareVersions(a, b) {
  const pa = a.split('.').map(Number), pb = b.split('.').map(Number);
  for (let i = 0; i < 3; i++) if (pa[i] !== pb[i]) return pa[i] > pb[i] ? 1 : -1;
  return 0;
}

const hasCommits = () => git(['rev-parse', '--verify', '-q', 'HEAD'], { quiet: true, allowFail: true }).ok;
const branch = () => git(['branch', '--show-current'], { quiet: true }).out || 'main';

function ensureRepo() {
  git(['--version'], { quiet: true });
  if (fs.existsSync(path.join(ROOT, '.git'))) return;
  say('Making this folder a Git repository...');
  if (!git(['init', '-b', 'main'], { quiet: true, allowFail: true }).ok) {
    git(['init'], { quiet: true });
    git(['symbolic-ref', 'HEAD', 'refs/heads/main'], { quiet: true });
  }
}

/**
 * Commits in this repository are always signed as you, with GitHub's private "noreply" address, so your real email and
 * any other Git identity saved on this PC stay out of the public history. Other repositories on the PC aren't affected.
 */
let myEmail = `${OWNER}@users.noreply.github.com`;
async function ensureIdentity() {
  try {
    const res = await fetch(`https://api.github.com/users/${OWNER}`, { headers: { Accept: 'application/vnd.github+json' } });
    const user = await res.json();
    if (user && user.id) myEmail = `${user.id}+${OWNER}@users.noreply.github.com`;
  } catch {
    // offline: use the address already set for this repository, or the older noreply form
    const local = git(['config', '--local', 'user.email'], { quiet: true, allowFail: true }).out;
    if (/@users\.noreply\.github\.com$/i.test(local)) myEmail = local;
  }
  const name = git(['config', '--local', 'user.name'], { quiet: true, allowFail: true }).out;
  const email = git(['config', '--local', 'user.email'], { quiet: true, allowFail: true }).out;
  if (name === MY_NAME && email === myEmail) return;
  git(['config', '--local', 'user.name', MY_NAME], { quiet: true });
  git(['config', '--local', 'user.email', myEmail], { quiet: true });
  say(`Commits here are signed as ${MY_NAME} <${myEmail}> (GitHub's private address for ${OWNER}).`);
}

/**
 * Commits signed as someone else (the Git identity saved on this PC before) are re-signed as you, so the next upload
 * replaces them on GitHub. Only before the first release: after that, people's launchers point at those commits.
 * Returns true when the history was rewritten (the upload then has to replace GitHub's copy).
 */
async function fixAuthors() {
  if (!hasCommits()) return false;
  const others = new Set();
  for (const line of git(['log', '--format=%an <%ae>%n%cn <%ce>', branch()], { quiet: true }).out.split('\n')) {
    if (line && !line.endsWith(`<${myEmail}>`) && !line.endsWith('<noreply@github.com>')) others.add(line);
  }
  if (!others.size) return false;
  if (git(['tag', '-l', 'v*'], { quiet: true }).out) {
    say(`\nSome commits are signed as ${[...others].join(', ')}; they can't be changed any more because a release was made from them.`);
    return false;
  }
  if (git(['status', '--porcelain'], { quiet: true }).out) return false;
  say(`\nEarlier commits are signed as ${[...others].join(', ')}.`);
  if (!/^y/i.test(await ask(`Re-sign them as ${MY_NAME} and replace them on GitHub? (y/n)`, 'y'))) return false;
  const r = git(['rebase', '-q', '--root', '--exec', 'git commit --amend --no-edit --reset-author --no-verify -q'], { allowFail: true });
  if (!r.ok) {
    git(['rebase', '--abort'], { quiet: true, allowFail: true });
    say('Could not re-sign them; nothing was changed.');
    return false;
  }
  say(`Re-signed every commit as ${MY_NAME}.`);
  return true;
}

/** New workflow files wait in tools/github-workflows (the folder Claude can write to) until they're moved into .github. */
function syncWorkflows() {
  if (!fs.existsSync(PENDING_WORKFLOWS)) return;
  const dest = path.join(ROOT, '.github', 'workflows');
  fs.mkdirSync(dest, { recursive: true });
  for (const f of fs.readdirSync(PENDING_WORKFLOWS).filter((x) => /\.ya?ml$/.test(x))) {
    fs.copyFileSync(path.join(PENDING_WORKFLOWS, f), path.join(dest, f));
    say(`Workflow updated: .github/workflows/${f}`);
  }
  fs.rmSync(PENDING_WORKFLOWS, { recursive: true, force: true });
}

/** Minecraft's own jars (tools/mcref, from fetch-refs.bat) must never be uploaded. */
function checkIgnored() {
  for (const p of ['tools/mcref', 'launcher/node_modules', 'launcher/dist']) {
    if (fs.existsSync(path.join(ROOT, p)) && !git(['check-ignore', '-q', p], { quiet: true, allowFail: true }).ok) {
      fail(`${p} isn't in .gitignore, so it would be uploaded. Stopping to be safe.`);
    }
  }
}

/**
 * The remote names the account (https://NeerBhardwaj1@github.com/...), so Git asks for that account's login instead of
 * using whichever GitHub login Windows saved first.
 */
const REMOTE = `https://${OWNER}@github.com/${REPO}.git`;
function ensureRemote() {
  const url = git(['remote', 'get-url', 'origin'], { quiet: true, allowFail: true });
  if (!url.ok) git(['remote', 'add', 'origin', REMOTE], { quiet: true });
  else if (/^https:\/\/github\.com\//i.test(url.out)) git(['remote', 'set-url', 'origin', REMOTE], { quiet: true });
}

/** Removes the GitHub login Windows saved for another account, so the next upload asks you to sign in again. */
function forgetLogin(user) {
  const input = `protocol=https\nhost=github.com\nusername=${user}\n\n`;
  spawnSync('git', ['credential', 'reject'], { cwd: ROOT, input, encoding: 'utf8' });
  spawnSync('git', ['credential-manager', 'github', 'logout', user], { cwd: ROOT, encoding: 'utf8' });
}

/** Pushes; when the GitHub repo doesn't exist yet, opens the page to create it and tries again. */
async function push(refs, replace = false) {
  for (let attempt = 0; attempt < 3; attempt++) {
    say(`\nUploading to ${WEB} ... (the first time, a window asks you to sign in to GitHub)`);
    // replacing re-signed commits: --force-with-lease only overwrites GitHub's copy if nobody else changed it meanwhile
    const r = git(['push', '-u', ...(replace ? ['--force-with-lease'] : []), 'origin', ...refs], { allowFail: true });
    if (r.ok) return;
    if (/not found|does not exist/i.test(r.err)) {
      const url = `https://github.com/new?owner=${OWNER}&name=${NAME}&visibility=public&description=${encodeURIComponent('Polar Client: a Minecraft client for 26.3, 1.21.11, 1.21.10, 1.16.5 and 1.8.9, and its launcher')}`;
      say(`\nThe repository ${REPO} doesn't exist on GitHub yet. Opening the page to create it:\n  ${url}`);
      say('Keep it Public, and leave "Add a README", .gitignore and license OFF (the folder already has them).');
      openUrl(url);
      await ask('Press Enter after you click "Create repository"');
      continue;
    }
    if (/rejected|fetch first|non-fast-forward/i.test(r.err)) {
      fail(`GitHub's copy has changes this folder doesn't have (for example a README made on the website).\n` +
        `If the repo is new, delete it on GitHub (Settings > Danger zone) and run this again to recreate it empty;\n` +
        `otherwise run "git pull --rebase origin ${branch()}" first.`);
    }
    const other = /denied to ([\w-]+)/i.exec(r.err);
    if (other && other[1].toLowerCase() !== OWNER.toLowerCase()) {
      say(`\nGit used a GitHub login saved on this PC for another account (${other[1]}), not ${OWNER}.`);
      if (/^y/i.test(await ask(`Forget the saved login for ${other[1]} on this PC, so you can sign in as ${OWNER}? (y/n)`, 'y'))) {
        forgetLogin(other[1]);
        say(`Forgotten. When the sign-in window opens, choose "Sign in with your browser" and allow it as ${OWNER}.`);
        continue;
      }
      fail(`Nothing uploaded. To fix it yourself: Control Panel > Credential Manager > Windows Credentials, remove the "git:https://github.com" entries, then run this again.`);
    }
    if (/authentication|403|permission/i.test(r.err)) {
      fail(`GitHub refused the upload. Check that the repository ${REPO} exists and that you signed in as ${OWNER}, then run this again.`);
    }
    fail('The upload failed (see above).');
  }
  fail('Still no repository on GitHub. Create it, then run this again.');
}

function stagedChanges() {
  return !git(['diff', '--cached', '--quiet'], { quiet: true, allowFail: true }).ok;
}

async function publish(message) {
  say('Polar Client > GitHub\n');
  ensureRepo();
  await ensureIdentity();
  syncWorkflows();
  ensureRemote();
  git(['add', '-A'], { quiet: true });
  checkIgnored();
  const first = !hasCommits();
  if (stagedChanges()) {
    const msg = first ? FIRST_COMMIT : message || (await ask('What changed? (one line)', 'Update Polar Client'));
    git(['commit', '-q', '-m', msg]);
  } else if (!first) {
    say('Nothing new to upload; checking GitHub has everything...');
  }
  const replace = await fixAuthors();
  await push([branch()], replace);
  say(`\nDone. ${WEB}`);
  say(`Every upload also runs the "build" check on GitHub: ${WEB}/actions`);
  if (first) {
    say('\nNext: run release.bat to publish the first installer (version 1.0.0), so people can download it and later updates work.');
    openUrl(WEB);
  }
}

function setVersion(v) {
  const text = fs.readFileSync(PKG, 'utf8');
  fs.writeFileSync(PKG, text.replace(/^(\s*"version":\s*")[^"]*(")/m, `$1${v}$2`));
  if (fs.existsSync(LOCK)) {
    const lock = JSON.parse(fs.readFileSync(LOCK, 'utf8'));
    lock.version = v;
    if (lock.packages && lock.packages['']) lock.packages[''].version = v;
    fs.writeFileSync(LOCK, `${JSON.stringify(lock, null, 2)}\n`);
  }
}

function addNews(v, notes) {
  const d = new Date();
  const date = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  const item = { id: `release-${v}`, date, tag: 'Update', title: `Polar Client ${v}`, body: notes.replace(/\s*\n\s*/g, ' ').slice(0, 2000), link: `${WEB}/releases/tag/v${v}`, linkLabel: 'Release notes' };
  for (const file of NEWS) {
    let data = { items: [] };
    try {
      data = JSON.parse(fs.readFileSync(file, 'utf8'));
    } catch {
      // a new file
    }
    const items = (Array.isArray(data.items) ? data.items : []).filter((it) => it && it.id !== item.id);
    fs.writeFileSync(file, `${JSON.stringify({ ...data, items: [item, ...items] }, null, 2)}\n`);
  }
}

async function release(wanted) {
  say('Polar Client > new release\n');
  if (!fs.existsSync(path.join(ROOT, '.git')) || !hasCommits()) fail('Run publish.bat first: it puts the project on GitHub.');
  ensureRepo();
  ensureRemote();
  await ensureIdentity();
  git(['fetch', '-q', '--tags', 'origin'], { quiet: true, allowFail: true });
  const current = JSON.parse(fs.readFileSync(PKG, 'utf8')).version;
  const tags = git(['tag', '-l', 'v*', '--sort=-v:refname'], { quiet: true }).out.split('\n').filter((t) => /^v\d+\.\d+\.\d+$/.test(t));
  const last = tags.length ? tags[0].slice(1) : null;
  let suggest = current;
  if (last) {
    const base = compareVersions(current, last) > 0 ? current : last;
    const [a, b, c] = base.split('.').map(Number);
    suggest = compareVersions(current, last) > 0 ? current : `${a}.${b}.${c + 1}`;
  }
  say(last ? `Last release: ${last}. The launcher in this folder says ${current}.` : `No release yet. The launcher in this folder is ${current}; release that first so people can install it.`);
  const v = String(wanted || (await ask('Version to release', suggest))).replace(/^v/i, '');
  if (!/^\d+\.\d+\.\d+$/.test(v)) fail(`"${v}" isn't a version like 1.2.3.`);
  if (tags.includes(`v${v}`) || git(['rev-parse', '-q', '--verify', `refs/tags/v${v}`], { quiet: true, allowFail: true }).ok) fail(`${v} was already released. Pick a higher version.`);
  if (last && compareVersions(v, last) <= 0) fail(`${v} isn't higher than the last release (${last}), so launchers wouldn't update to it.`);
  if (compareVersions(v, current) < 0) fail(`${v} is lower than the launcher's version here (${current}).`);
  const notes = (await askLines(`What's new in ${v}? Shown in the launcher's update banner and on the release page. Finish with an empty line.`)) || `Polar Client ${v}`;
  const news = /^y/i.test(await ask('Also post it in the News on the launcher home page? (y/n)', 'y'));

  setVersion(v);
  if (news) addNews(v, notes);
  syncWorkflows();
  git(['add', '-A'], { quiet: true });
  checkIgnored();
  if (stagedChanges()) git(['commit', '-q', '-m', `Polar Client ${v}`]);
  const replace = await fixAuthors();
  git(['tag', '-a', `v${v}`, '-m', notes], { quiet: true });
  await push([branch(), `v${v}`], replace);
  say(`\nReleased v${v} to GitHub. GitHub Actions is building the mod and the installers now (about 20-30 minutes):`);
  say(`  ${WEB}/actions`);
  say(`When it's done the installers are on ${WEB}/releases and every launcher older than ${v} offers "Update now" at its next start.`);
  openUrl(`${WEB}/actions`);
}

(async () => {
  const [cmd, arg] = process.argv.slice(2);
  try {
    if (cmd === 'publish') await publish(arg);
    else if (cmd === 'release') await release(arg);
    else fail('Use: node tools/github.js publish | release [version]');
  } catch (e) {
    fail(e && e.message ? e.message : String(e));
  }
  rl.close();
})();
