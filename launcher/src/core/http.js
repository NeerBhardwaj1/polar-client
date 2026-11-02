'use strict';
// Small fetch/download helpers: retries, timeouts, SHA-1 verification and parallel downloads.
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const { Readable } = require('node:stream');
const { pipeline } = require('node:stream/promises');

const USER_AGENT = 'polar-client-launcher/1.0.0 (+https://github.com/NeerBhardwaj1/polar-client)';

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function withRetries(fn, tries = 3) {
  let lastError;
  for (let i = 0; i < tries; i++) {
    try {
      return await fn();
    } catch (e) {
      lastError = e;
      if (e && e.noRetry) break;
      await sleep(400 * (i + 1) ** 2);
    }
  }
  throw lastError;
}

async function request(url, options = {}) {
  const res = await fetch(url, {
    ...options,
    headers: { 'User-Agent': USER_AGENT, ...(options.headers || {}) },
    signal: AbortSignal.timeout(options.timeout || 30000),
  });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    const err = new Error(`${options.method || 'GET'} ${url} failed: HTTP ${res.status} ${body.slice(0, 200)}`);
    err.status = res.status;
    err.body = body;
    if (res.status >= 400 && res.status < 500 && res.status !== 429) err.noRetry = true;
    throw err;
  }
  return res;
}

async function getJson(url, options = {}) {
  return withRetries(async () => (await request(url, options)).json());
}

async function postJson(url, body, headers = {}) {
  const res = await request(url, {
    method: 'POST',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...headers },
  });
  return res.json();
}

async function postForm(url, form) {
  const res = await request(url, {
    method: 'POST',
    body: new URLSearchParams(form).toString(),
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });
  return res.json();
}

/** Fetches JSON and keeps a copy on disk; falls back to the copy when offline. */
async function getJsonCached(url, cacheFile, options = {}) {
  try {
    const data = await getJson(url, options);
    await fsp.mkdir(path.dirname(cacheFile), { recursive: true });
    await fsp.writeFile(cacheFile, JSON.stringify(data));
    return data;
  } catch (e) {
    try {
      return JSON.parse(await fsp.readFile(cacheFile, 'utf8'));
    } catch {
      throw e;
    }
  }
}

async function sha1File(file) {
  const hash = crypto.createHash('sha1');
  await pipeline(fs.createReadStream(file), hash);
  return hash.digest('hex');
}

/** True if the file exists and (when a hash is given) matches it. */
async function isValid(file, sha1, size) {
  try {
    const st = await fsp.stat(file);
    if (!st.isFile()) return false;
    if (size != null && st.size !== size) return false;
    if (sha1) return (await sha1File(file)) === sha1.toLowerCase();
    return true;
  } catch {
    return false;
  }
}

/** Downloads to a temp file, verifies, then moves into place. Skips files that are already valid. */
async function download(url, dest, { sha1, size } = {}) {
  if (await isValid(dest, sha1, size)) return false;
  await fsp.mkdir(path.dirname(dest), { recursive: true });
  await withRetries(async () => {
    const tmp = `${dest}.${process.pid}.part`;
    const res = await request(url, { timeout: 120000 });
    await pipeline(Readable.fromWeb(res.body), fs.createWriteStream(tmp));
    if (sha1) {
      const got = await sha1File(tmp);
      if (got !== sha1.toLowerCase()) {
        await fsp.rm(tmp, { force: true });
        throw new Error(`Checksum mismatch for ${path.basename(dest)} (expected ${sha1}, got ${got})`);
      }
    }
    await fsp.rename(tmp, dest);
  });
  return true;
}

/**
 * Runs download tasks with limited parallelism.
 * tasks: [{ url, dest, sha1?, size? }]. onProgress(done, total, task).
 */
async function downloadAll(tasks, { concurrency = 12, onProgress } = {}) {
  let next = 0;
  let done = 0;
  const failures = [];
  async function worker() {
    while (next < tasks.length) {
      const task = tasks[next++];
      try {
        await download(task.url, task.dest, task);
      } catch (e) {
        failures.push({ task, error: e });
      }
      done++;
      if (onProgress) onProgress(done, tasks.length, task);
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, tasks.length) }, worker));
  if (failures.length) {
    const first = failures[0];
    const err = new Error(`${failures.length} download(s) failed. First: ${path.basename(first.task.dest)} — ${first.error.message}`);
    err.failures = failures;
    throw err;
  }
}

module.exports = { USER_AGENT, request, getJson, getJsonCached, postJson, postForm, download, downloadAll, isValid, sha1File };
