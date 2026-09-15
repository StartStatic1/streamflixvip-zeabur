#!/usr/bin/env node
/**
 * Lê o bucket Cloudflare R2 (não o candidates.json) e tenta cadastrar
 * cada .mp4 em vip_sources. Não apaga fonte existente.
 *
 *   cd /root/streamflix
 *   set -a; . ./.env; set +a
 *   DRY_RUN=1 node scripts/sync-r2-bucket-to-vip-sources.js
 *   node scripts/sync-r2-bucket-to-vip-sources.js
 */

const fs = require('fs');
const path = require('path');
const { S3Client, ListObjectsV2Command } = require('@aws-sdk/client-s3');

function loadDotEnv() {
  const envPath = path.join(process.cwd(), '.env');
  if (!fs.existsSync(envPath)) return;
  for (const line of fs.readFileSync(envPath, 'utf8').split(/\n/)) {
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

const SUPABASE_URL = String(process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co').replace(/\/+$/, '');
const SERVICE_KEY = String(process.env.SUPABASE_SERVICE_ROLE_KEY || '').trim();
const TMDB_KEY = String(process.env.TMDB_API_KEY || '').trim();
const {
  R2_ACCOUNT_ID,
  R2_ACCESS_KEY_ID,
  R2_SECRET_ACCESS_KEY,
  R2_BUCKET_NAME,
  R2_PUBLIC_BASE_URL,
} = process.env;
const PUBLIC_BASE = String(R2_PUBLIC_BASE_URL || 'https://pub-90cce3ad488f4a52ac089b9496083787.r2.dev').replace(/\/+$/, '');
const DRY_RUN = /^(1|true|yes)$/i.test(String(process.env.DRY_RUN || ''));

const ALIASES = [
  { re: /noite\s*dos\s*dem.?nios\s*3|noitedosdem.?nios.?3/i, tmdb: 11978, title: 'A Noite dos Demônios 3', year: 1997 },
  { re: /noite\s*dos\s*dem.?nios\s*2|noitedosdem.?nios.?2/i, tmdb: 26725, title: 'A Noite dos Demônios 2', year: 1994 },
  { re: /noite\s*dos\s*dem.?nios|noitedosdem.?nios/i, tmdb: 24924, title: 'A Noite dos Demônios', year: 1988 },
  { re: /goonies/i, tmdb: 9340, title: 'Os Goonies', year: 1985 },
  { re: /est.?ria\s*de\s*piera|storia\s*di\s*piera/i, tmdb: 43004, title: 'A Estória de Piera', year: 1983 },
  { re: /homem\s*da\s*terra/i, tmdb: 11287, title: 'O Homem da Terra', year: 2007 },
];

if (!SERVICE_KEY) {
  console.error('ERRO: SUPABASE_SERVICE_ROLE_KEY ausente');
  process.exit(2);
}
if (!R2_ACCOUNT_ID || !R2_ACCESS_KEY_ID || !R2_SECRET_ACCESS_KEY || !R2_BUCKET_NAME) {
  console.error('ERRO: credenciais R2 ausentes no .env (R2_ACCOUNT_ID / KEY / SECRET / BUCKET)');
  process.exit(2);
}

const sbHeaders = {
  apikey: SERVICE_KEY,
  Authorization: `Bearer ${SERVICE_KEY}`,
  Accept: 'application/json',
  'Content-Type': 'application/json',
};

function normalize(value) {
  return String(value || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

function parseKey(key) {
  const file = String(key || '').split('/').pop() || '';
  const noExt = file.replace(/\.(mp4|mkv|avi|m4v|webm)$/i, '');
  const noTs = noExt.replace(/^\d{10,}-/, '');
  const yearMatch = noTs.match(/(?:^|[^0-9])((?:19|20)\d{2})(?:[^0-9]|$)/);
  const year = yearMatch ? Number(yearMatch[1]) : null;
  const title = noTs
    .replace(/[_-]+/g, ' ')
    .replace(/\b(19|20)\d{2}\b/g, ' ')
    .replace(/\b(1080p|720p|2160p|4k|uhd|hdr|leg|dublado|dual|webrip|bluray|bdrip|hdrip|rms)\b/ig, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return { file, title, year, raw: noTs };
}

function aliasMatch(parsed) {
  const blob = `${parsed.raw} ${parsed.title}`;
  return ALIASES.find((row) => row.re.test(blob)) || null;
}

async function listBucket() {
  const s3 = new S3Client({
    region: 'auto',
    endpoint: `https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com`,
    credentials: {
      accessKeyId: R2_ACCESS_KEY_ID,
      secretAccessKey: R2_SECRET_ACCESS_KEY,
    },
  });
  const keys = [];
  let token;
  do {
    const out = await s3.send(new ListObjectsV2Command({
      Bucket: R2_BUCKET_NAME,
      ContinuationToken: token,
      MaxKeys: 1000,
    }));
    for (const obj of out.Contents || []) {
      if (obj.Key && /\.(mp4|mkv|avi|m4v|webm)$/i.test(obj.Key)) keys.push(obj.Key);
    }
    token = out.IsTruncated ? out.NextContinuationToken : undefined;
  } while (token);
  return keys;
}

async function countVipSources() {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?select=id`, {
    headers: { ...sbHeaders, Prefer: 'count=exact', Range: '0-0' },
  });
  const range = r.headers.get('content-range') || '';
  const total = Number((range.split('/')[1] || '').trim());
  return Number.isFinite(total) ? total : 0;
}

async function existingUrls(tmdbId) {
  const r = await fetch(
    `${SUPABASE_URL}/rest/v1/vip_sources?tmdb_id=eq.${encodeURIComponent(tmdbId)}&media_type=eq.movie&select=source_url`,
    { headers: sbHeaders },
  );
  if (!r.ok) throw new Error('vip_sources GET ' + r.status);
  const rows = await r.json();
  return new Set((Array.isArray(rows) ? rows : []).map((row) => String(row.source_url || '').trim()));
}

async function tmdbSearch(title, year) {
  if (!TMDB_KEY || !title) return null;
  const u = new URL('https://api.themoviedb.org/3/search/movie');
  u.searchParams.set('api_key', TMDB_KEY);
  u.searchParams.set('query', title);
  u.searchParams.set('language', 'pt-BR');
  if (year) u.searchParams.set('year', String(year));
  const r = await fetch(u);
  if (!r.ok) return null;
  const data = await r.json();
  const results = Array.isArray(data.results) ? data.results : [];
  if (!results.length) return null;
  const nTitle = normalize(title);
  const scored = results.map((row) => {
    const n = normalize(row.title || row.original_title || '');
    let score = 0;
    if (n === nTitle) score += 8;
    else if (n.includes(nTitle) || nTitle.includes(n)) score += 5;
    const ry = String(row.release_date || '').slice(0, 4);
    if (year && ry === String(year)) score += 4;
    return { row, score };
  }).sort((a, b) => b.score - a.score);
  const best = scored[0];
  if (!best || best.score < 5) return null;
  return {
    tmdb: best.row.id,
    title: best.row.title || title,
    year: Number(String(best.row.release_date || '').slice(0, 4)) || year,
  };
}

async function insertSource(payload) {
  const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources`, {
    method: 'POST',
    headers: { ...sbHeaders, Prefer: 'return=minimal' },
    body: JSON.stringify(payload),
  });
  if (r.ok || r.status === 409) return;
  throw new Error('insert ' + r.status + ' ' + (await r.text()).slice(0, 200));
}

(async () => {
  const bank = await countVipSources();
  const keys = await listBucket();
  console.log(`vip_sources no banco: ${bank}`);
  console.log(`arquivos de video no bucket: ${keys.length}`);
  if (DRY_RUN) console.log('DRY_RUN=1 — nenhum insert');

  let matched = 0;
  let created = 0;
  let skipped = 0;
  let unmatched = 0;

  for (const key of keys) {
    const parsed = parseKey(key);
    const hit = aliasMatch(parsed) || await tmdbSearch(parsed.title, parsed.year);
    const publicUrl = `${PUBLIC_BASE}/${key}`;
    if (!hit) {
      unmatched += 1;
      console.log(`? sem TMDB  ${key}`);
      continue;
    }
    matched += 1;
    const have = await existingUrls(hit.tmdb);
    if (have.has(publicUrl)) {
      skipped += 1;
      continue;
    }
    console.log(`${DRY_RUN ? '+' : 'ok'} ${hit.tmdb} ${hit.title} <- ${key}`);
    if (DRY_RUN) {
      created += 1;
      continue;
    }
    await insertSource({
      tmdb_id: hit.tmdb,
      media_type: 'movie',
      title: hit.title,
      poster_path: null,
      season: null,
      episode: null,
      source_url: publicUrl,
      source_label: 'R2 Dev',
      priority: 0,
      is_active: true,
    });
    created += 1;
  }

  console.log(`casados=${matched} novos=${created} ja_existiam=${skipped} sem_tmdb=${unmatched}`);
})().catch((err) => {
  console.error('ERRO:', err.message);
  process.exit(1);
});
