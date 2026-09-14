#!/usr/bin/env node
/**
 * Audita fontes Cloudflare R2 já cadastradas manualmente no painel.
 *
 * Importante: este script NÃO lê candidates.json e NÃO cria fontes novas.
 * O fluxo correto do projeto é cadastrar manualmente o filme e a URL R2
 * diretamente em vip_sources pelo painel administrativo.
 *
 * Uso:
 *   SUPABASE_SERVICE_ROLE_KEY=... node scripts/audit-r2-vip-sources.js
 *   SUPABASE_SERVICE_ROLE_KEY=... TMDB_ID=25239 node scripts/audit-r2-vip-sources.js
 *
 * Variáveis:
 *   SUPABASE_URL                  opcional; possui default do projeto
 *   SUPABASE_SERVICE_ROLE_KEY     obrigatória; nunca use a anon key
 *   TMDB_ID                       opcional; audita um título específico
 *   MEDIA_TYPE                    opcional; movie (default) ou tv
 */

const SUPABASE_URL = String(
  process.env.SUPABASE_URL || 'https://gkujbjpvphuvrejpvvtz.supabase.co',
).replace(/\/+$/, '');
const SERVICE_KEY = String(process.env.SUPABASE_SERVICE_ROLE_KEY || '').trim();
const targetTmdbId = String(process.env.TMDB_ID || '').trim();
const targetMediaType = String(process.env.MEDIA_TYPE || 'movie').trim().toLowerCase();

if (!SERVICE_KEY) {
  console.error('ERRO: defina SUPABASE_SERVICE_ROLE_KEY no ambiente.');
  process.exit(2);
}
if (targetMediaType !== 'movie' && targetMediaType !== 'tv') {
  console.error('ERRO: MEDIA_TYPE deve ser movie ou tv.');
  process.exit(2);
}

const headers = {
  apikey: SERVICE_KEY,
  Authorization: `Bearer ${SERVICE_KEY}`,
  Accept: 'application/json',
};

function isR2Url(value) {
  return /(?:r2\.dev|r2\.cloudflarestorage\.com|\.r2\.)/i.test(String(value || ''));
}

function isR2Row(row) {
  const label = String(row?.source_label || '').toLowerCase();
  return isR2Url(row?.source_url) || /\br2\b|cloudflare|streamflix\s*r2/.test(label);
}

function encode(value) {
  return encodeURIComponent(String(value));
}

async function getRows() {
  const rows = [];
  const pageSize = 1000;
  for (let offset = 0; ; offset += pageSize) {
    const params = new URLSearchParams({
      select: 'id,tmdb_id,media_type,season,episode,title,source_url,source_label,priority,is_active,created_at',
      order: 'tmdb_id.asc,media_type.asc,season.asc.nullslast,episode.asc.nullslast,priority.asc',
      limit: String(pageSize),
      offset: String(offset),
    });
    const response = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?${params}`, { headers });
    const body = await response.text();
    if (!response.ok) {
      throw new Error(`Supabase ${response.status}: ${body.slice(0, 500)}`);
    }
    const page = JSON.parse(body);
    if (!Array.isArray(page)) throw new Error('Resposta inesperada do Supabase.');
    rows.push(...page);
    if (page.length < pageSize) break;
  }
  return rows;
}

function formatRow(row) {
  return {
    id: row.id,
    tmdb_id: row.tmdb_id,
    media_type: row.media_type,
    season: row.season ?? null,
    episode: row.episode ?? null,
    title: row.title || null,
    source_label: row.source_label || null,
    source_url: row.source_url || null,
    priority: row.priority ?? null,
    is_active: row.is_active,
  };
}

function printDiagnosis(rows) {
  const r2Rows = rows.filter(isR2Row);
  console.log(`Fontes totais em vip_sources: ${rows.length}`);
  console.log(`Fontes identificadas como R2: ${r2Rows.length}`);

  if (r2Rows.length) {
    console.log('\n--- Fontes R2 cadastradas ---');
    for (const row of r2Rows) console.log(JSON.stringify(formatRow(row)));
  }

  if (!targetTmdbId) return;

  const titleRows = rows.filter((row) =>
    String(row.tmdb_id) === targetTmdbId && String(row.media_type).toLowerCase() === targetMediaType,
  );
  const exactMovieRows = titleRows.filter((row) => targetMediaType !== 'movie' || row.season == null);
  const exactR2Rows = exactMovieRows.filter(isR2Row);
  const activeExactR2Rows = exactR2Rows.filter((row) => row.is_active === true);

  console.log(`\n--- Diagnóstico TMDB ${targetTmdbId} (${targetMediaType}) ---`);
  console.log(`Linhas do título: ${titleRows.length}`);
  console.log(`Linhas R2 do título: ${exactR2Rows.length}`);
  console.log(`Linhas R2 ativas e compatíveis: ${activeExactR2Rows.length}`);

  if (!titleRows.length) {
    console.log('CAUSA PROVÁVEL: o filme não está cadastrado em vip_sources com esse TMDB/media_type.');
  } else if (!exactR2Rows.length) {
    console.log('CAUSA PROVÁVEL: há fonte para o título, mas a URL/label não é reconhecida como R2.');
  } else if (!activeExactR2Rows.length) {
    console.log('CAUSA PROVÁVEL: a fonte R2 existe, mas está inativa ou com season preenchido incorretamente.');
  } else {
    console.log('OK: existe pelo menos uma fonte R2 ativa para o título.');
  }

  for (const row of titleRows) console.log(JSON.stringify(formatRow(row)));
}

(async () => {
  try {
    const rows = await getRows();
    printDiagnosis(rows);
  } catch (error) {
    console.error(`ERRO: ${error.message}`);
    process.exitCode = 1;
  }
})();
