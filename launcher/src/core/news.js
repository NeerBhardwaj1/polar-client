'use strict';
// The launcher's News: a small JSON file you publish (news.json in the GitHub repo) with your own announcements and
// changelog. Fetched at start-up, kept for offline use, and the copy shipped inside the launcher is the fallback.
const fsp = require('node:fs/promises');
const { request } = require('./http');

const MAX_ITEMS = 30;
const str = (v, max) => (typeof v === 'string' ? v.trim().slice(0, max) : '');
const https = (v) => (typeof v === 'string' && /^https:\/\/[^\s"'<>]+$/.test(v.trim()) ? v.trim() : '');

/** Keeps only well-formed items, newest first: { id, date, title, body, tag, image, link, linkLabel }. */
function clean(data) {
  const raw = data && Array.isArray(data.items) ? data.items : Array.isArray(data) ? data : [];
  const out = [];
  for (const it of raw) {
    if (!it || typeof it !== 'object') continue;
    const title = str(it.title, 120);
    const date = /^\d{4}-\d{2}-\d{2}$/.test(String(it.date || '')) ? it.date : '';
    if (!title || !date) continue;
    out.push({
      id: str(it.id, 80) || `${date}-${title}`.toLowerCase().replace(/[^a-z0-9]+/g, '-'),
      date, title,
      body: str(it.body, 2000),
      tag: str(it.tag, 24),
      image: https(it.image),
      link: https(it.link),
      linkLabel: str(it.linkLabel, 40) || 'Read more',
    });
  }
  return out.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0)).slice(0, MAX_ITEMS);
}

async function readItems(file) {
  try {
    return clean(JSON.parse(await fsp.readFile(file, 'utf8')));
  } catch {
    return null;
  }
}

/** Live news when the URL answers, else the last copy saved, else the bundled file. Returns { items, source }. */
async function load({ url, cacheFile, bundledFile }) {
  if (url) {
    try {
      const res = await request(url, { timeout: 8000, headers: { Accept: 'application/json' } });
      const items = clean(JSON.parse(await res.text()));
      if (items.length) {
        await fsp.writeFile(cacheFile, JSON.stringify({ items }, null, 2)).catch(() => {});
        return { items, source: 'live' };
      }
    } catch {
      // offline or not published yet
    }
  }
  const cached = await readItems(cacheFile);
  if (cached && cached.length) return { items: cached, source: 'cache' };
  return { items: (await readItems(bundledFile)) || [], source: 'bundled' };
}

module.exports = { load, clean };
