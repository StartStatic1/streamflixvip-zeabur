#!/usr/bin/env node
/**
 * Cadastra em vip_sources as URLs públicas do Cloudflare R2 que já existem
 * em movie-fetcher/candidates.json. Não apaga fonte nenhuma.
 *
 * No VPS:
 *   cd /root/streamflix
 *   set -a; . ./.env; set +a
 *   DRY_RUN=1 node scripts/sync-r2-to-vip-sources.js
 *   node scripts/sync-r2-to-vip-sources.js
 */

const fs = require('fs');
const path = require('path');

function loadDotEnv() {
  const envPath = path.join(process.cwd(), '.env');
  if (!fs.existsSync(envPath)) return;
  const text = fs.readFileSync(envPath, 'utf8');
  for (const line of text.split(/\n/)) {
    const t = line.trim();
    if (!t || t.startsWith('#')) continue;
    const i = t.indexOf('=');
    if (i < 1) continue;
    const key = t.slice(0, i).trim();
    let val = t.slice(i + 1).trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
      val = val.slice(1, -1);
    }
    if (!process.env[key]) process.env[key] = val;
  }
}

loadDotEnv();

const SUPABASE_URL = String(
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co',
).replace(/\/+$/, '');
const SERVICE_KEY = String(process.env.SUPABASE_SERVICE_ROLE_KEY || '').trim();
const R2_PUBLIC_BASE_URL = String(
  process.env.R2_PUBLIC_BASE_URL ||
    'https://pub-90cce3ad488f4a52ac089b9496083787.r2.dev',
).replace(/\/+$/, '');
const CATALOG_URL = `${R2_PUBLIC_BASE_URL}/movie-fetcher/candidates.json`;
const DRY_RUN = /^(1|true|yes)$/i.test(String(process.env.DRY_RUN || ''));

if (!SERVICE_KEY) {
  console.error('ERRO: SUPABASE_SERVICE_ROLE_KEY ausente. Rode a partir de /root/streamflix com o .env.');
  process.exit(2);
}

const headers = {
  apikey: SERVICE_KEY,
  Authorization: `Bearer ${SERVICE_KEY}`,
  Accept: 'application/json',
  'Content-Type': 'application/json',
};

function isR2Url(value) {
  return /(?:r2\.dev|r2\.cloudflarestorage\.com|\.r2\.)/i.test(String(value || ''));
}

function pickR2Url(row) {
  for (const raw of [row && row.r2_url, row && row.source_url, row && row.url]) {
    const url = String(raw || '').trim();
    if (/^https?:\/\//i.test(url) && isR2Url(url)) return url;
  }
  return '';
}

function posterPath(row) {
  const raw = String((row && (row.poster_path || row.poster_url)) || '');
  const m = raw.match(/\/([a-zA-Z0-9]+\.(?:jpg|jpeg|png|webp))$/i);
  if (m) return '/' + m[1];
  if (raw.startsWith('/')) return raw;
  return null;
}

async function fetchCatalog() {
  const r = await fetch(CATALOG_URL + '?v=' + Date.now(), { headers: { Accept: 'application/json' } });
  if (!r.ok) throw new Error('catalogo R2 HTTP ' + r.status);
  const data = await r.json();
  if (!Array.isArray(data)) throw new Error('catalogo R2 nao e lista');
  return data;
}

async function existingUrls(tmdbId) {
  const url =
    `${SUPABASE_URL}/rest/v1/vip_sources?tmdb_id=eq.${encodeURIComponent(tmdbId)}` +
    `&media_type=eq.movie&select=source_url`;
  const r = await fetch(url, { headers });
  if (!r.ok) throw new Error('vip_sources GET ' + r.status + ' ' + (await r.text()).slice(0, 180));
  const rows = await r.json();
  return new Set((Array.isArray(rows) ? rows : []).map((row) => String(row.source_url || '').trim()));
}

async function insertSource(payload) {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources`, {
    method: 'POST',
    headers: { ...headers, Prefer: 'return=minimal' },
    body: JSON.stringify(payload),
  });
  if (r.ok || r.status === 409) return r.status;
  const body = await r.text();
  throw new Error('insert ' + r.status + ' ' + body.slice(0, 240));
}

(async () => {
  const catalog = await fetchCatalog();
  const ready = catalog
    .map((row) => ({
      tmdb_id: Number(row && row.tmdb_id),
      title: String((row && row.title) || '').trim(),
      source_url: pickR2Url(row),
      poster_path: posterPath(row),
    }))
    .filter((row) => Number.isFinite(row.tmdb_id) && row.tmdb_id > 0 && row.source_url);

  console.log(`Catalogo: ${catalog.length} itens`);
  console.log(`Com URL R2: ${ready.length}`);
  if (DRY_RUN) console.log('DRY_RUN=1 — nenhum insert');

  let created = 0;
  let skipped = 0;
  let failed = 0;

  for (const row of ready) {
    try {
      const have = await existingUrls(row.tmdb_id);
      if (have.has(row.source_url)) {
        skipped += 1;
        continue;
      }
      if (DRY_RUN) {
        console.log(`+ ${row.tmdb_id} ${row.title} ${row.source_url}`);
        created += 1;
        continue;
      }
      await insertSource({
        tmdb_id: row.tmdb_id,
        media_type: 'movie',
        title: row.title || null,
        poster_path: row.poster_path,
        season: null,
        episode: null,
        source_url: row.source_url,
        source_label: 'R2 Dev',
        priority: 0,
        is_active: true,
      });
      created += 1;
      console.log(`ok ${row.tmdb_id} ${row.title}`);
    } catch (err) {
      failed += 1;
      console.error(`falhou ${row.tmdb_id} ${row.title}: ${err.message}`);
    }
  }

  console.log(`criados=${created} ja_existiam=${skipped} falhou=${failed}`);
})().catch((err) => {
  console.error('ERRO:', err.message);
  process.exit(1);
});
