const fs = require('fs');
const readline = require('readline');

const EXTINF_RE = /^#EXTINF:(-?\d+)\s*(.*),(.*)$/;
const ATTR_RE = /(\w[\w-]*)="([^"]*)"/g;

// SxxExx em vários formatos: S01E05, S1E5, 1x05, T01E05
const SEASON_EP_RE = /^(.*?)\s*[\[\(]?[SsTt]?(\d{1,2})\s*[xXeE]\s*(\d{1,4})[\]\)]?\s*(.*)$/i;

// Categorias que claramente NÃO são filme/série
const LIVE_ONLY_GROUP_RE = /\b(canais?|channels?|abertos?|esportes?|sports?|not[íi]cias?|news|infantil|kids|documentário|24\s*horas|ao\s*vivo|live\s*tv)\b/i;

// Tags de qualidade / áudio / extras que poluem o título
const JUNK_TAG_RE = /\b(?:
  4\s*k|uhd|ultrahd|ultra\s*h\.?d|2160\s*p?|8\s*k|
  fhd|full\s*hd|1080\s*p?|720\s*p?|480\s*p?|360\s*p?|
  hd|sd|bluray|blu[-\s]?ray|web[-\s]?dl|webrip|hdtv|dvdrip|bdrip|brrip|
  dual(?:\s*áudio|\s*audio)?|multi(?:\s*áudio|\s*audio|\s*lang)?|
  dublado|legendado|legenda|nacional|original|
  extended|director'?s?\s*cut|unrated|remaster(?:ed)?|
  hevc|x265|x264|h\.?265|h\.?264|aac|ac3|dts|
  cam|hdcam|ts|hdts|tc|scr|screener|telesync|telecine
)\b/gi;

// Prefixo de categoria no nome: "FILMES | ", "LANÇAMENTOS - ", etc.
const PREFIX_RE = /^(?:filmes?|series?|séries?|lancamentos?|lançamentos?|novidades?|cinema|vod|movies?)\s*[\|\-–:\/]\s*/i;

// Símbolos e lixo residual
const SYMBOL_RE = /[\|\[\]\{\}【】★☆●◆▪►◀▶•·_]+/g;
const MULTI_SPACE_RE = /\s{2,}/g;

/**
 * Limpa o título bruto da M3U.
 * Retorna { title, year, season, episode }
 * - year: number | null
 * - season/episode: number | null (só se for episódio)
 */
function cleanTitle(rawName) {
  let s = String(rawName || '').trim();
  if (!s) return { title: '', year: null, season: null, episode: null };

  // 1) Detectar SxxExx / 1x05 antes de limpar demais
  let season = null;
  let episode = null;
  const seMatch = s.match(SEASON_EP_RE);
  if (seMatch) {
    season = parseInt(seMatch[2], 10);
    episode = parseInt(seMatch[3], 10);
    // junta prefixo + resto depois do SxxExx
    s = `${seMatch[1]} ${seMatch[4] || ''}`.trim();
  }

  // 2) Extrair ano em vários formatos:
  //    (2024)  [2024]  .2024.  -2024-  no final ou isolado
  let year = null;
  const yearPatterns = [
    /[\(\[](\d{4})[\)\]]/,           // (2024) ou [2024]
    /(?:^|[\.\s\-_])(19\d{2}|20[0-3]\d)(?:[\.\s\-_]|$)/, // .2024. ou espaço
  ];
  for (const re of yearPatterns) {
    const m = s.match(re);
    if (m) {
      const y = parseInt(m[1], 10);
      if (y >= 1900 && y <= 2035) {
        year = y;
        s = s.replace(re, ' ').trim();
        break;
      }
    }
  }

  // 3) Remover prefixo de categoria
  s = s.replace(PREFIX_RE, '');

  // 4) Remover tags de qualidade/áudio
  s = s.replace(JUNK_TAG_RE, ' ');

  // 5) Remover símbolos e espaços extras
  s = s.replace(SYMBOL_RE, ' ');
  s = s.replace(MULTI_SPACE_RE, ' ').trim();
  // tira pontuação solta no final
  s = s.replace(/^[\s\-–:\.\/]+|[\s\-–:\.\/]+$/g, '').trim();

  return { title: s, year, season, episode };
}

function classify(entry) {
  const { groupTitle, name, url } = entry;
  const cleaned = cleanTitle(name);

  // Episódio de série
  if (cleaned.season != null && cleaned.episode != null && cleaned.title) {
    return {
      kind: 'episode',
      baseTitle: cleaned.title,
      season: cleaned.season,
      episode: cleaned.episode,
      year: cleaned.year,
    };
  }

  const looksLikeMovieUrl = /\/movie\//i.test(url || '');
  const isExplicitlyLiveGroup = LIVE_ONLY_GROUP_RE.test(groupTitle || '');

  // Filme: precisa de ANO. Sem ano = não cadastra (evita match errado no TMDB).
  if (cleaned.year && cleaned.title) {
    if (looksLikeMovieUrl || !isExplicitlyLiveGroup) {
      return {
        kind: 'movie',
        title: cleaned.title,
        year: cleaned.year,
      };
    }
  }

  // URL de filme sem ano → ainda classifica como movie mas year=null
  // (shouldKeepMovie / sync vão rejeitar depois)
  if (looksLikeMovieUrl && cleaned.title) {
    return {
      kind: 'movie',
      title: cleaned.title,
      year: null,
    };
  }

  return { kind: 'live' };
}

// --- Filtros de qualidade ---
// Legendado AGORA É PERMITIDO (clássicos, nunca dublados, ou as duas versões na lista).
// Bloqueia: 4K/UHD/8K, SD, adulto, cam/ts.
const FOUR_K_RE = /4\s*k|\bfhd4k\b|\buhd\b|2160\s*p?|\bultra\s*h\.?d\b|\bultrahd\b|3840\s*[x×]\s*2160|\b8\s*k\b/i;
const SD_RE = /\b(?:sd|480\s*p?|360\s*p?|240\s*p?)\b/i;
const ADULT_RE = /xxx|adulto/i;
const BOOTLEG_RE = /\bcam\b|\bhdcam\b|\bts\b|\bhdts\b|\btc\b|\bscr\b|\bscreener\b|\btelesync\b|\btelecine\b(?!.*dublado)/i;

/** true = pode cadastrar; false = descarta. */
function shouldKeepMovie(entry) {
  const { name, groupTitle, classification } = entry;
  const blob = `${name || ''} ${groupTitle || ''}`;

  // Sem ano → não cadastra (evita conteúdo errado no TMDB)
  if (classification && classification.kind === 'movie' && !classification.year) {
    return false;
  }

  if (FOUR_K_RE.test(blob)) return false;
  if (SD_RE.test(blob)) return false;
  if (ADULT_RE.test(blob)) return false;
  if (BOOTLEG_RE.test(blob)) return false;
  return true;
}

/** Mesma regra só com string (API Xtream sem groupTitle). */
function shouldKeepMovieTitle(title) {
  return shouldKeepMovie({ name: title || '', groupTitle: '', classification: null });
}

const WANTED_LIVE_GROUPS = new Set([
  'PPV', 'ESPN', 'SPORTV', 'SPORTV+', 'ESPORTE', 'ESPORTES ESTADUAIS', 'NBA',
  'ELEVEN', 'DAZN', 'SPORTYNET', 'UFC', 'PREMIERE',
  'GLOBO SUDESTE', 'GLOBO NORDESTE', 'GLOBO SUL', 'GLOBO NORTE', 'GLOBO CENTRO-OESTE',
  'SBT', 'RECORD', 'BAND', 'ABERTOS',
]);
const WANTED_LIVE_NAME_RE = /discovery|combate/i;

function shouldKeepLiveChannel(entry) {
  const { name, groupTitle } = entry;
  if (WANTED_LIVE_GROUPS.has(groupTitle)) return true;
  if (WANTED_LIVE_NAME_RE.test(name)) return true;
  return false;
}

function parseAttrs(attrString) {
  const attrs = {};
  let m;
  ATTR_RE.lastIndex = 0;
  while ((m = ATTR_RE.exec(attrString)) !== null) {
    attrs[m[1]] = m[2];
  }
  return attrs;
}

async function parseM3U(filePath, onEntry, { limit = Infinity, dedupe = false } = {}) {
  const fileStream = fs.createReadStream(filePath, { encoding: 'utf8' });
  const rl = readline.createInterface({ input: fileStream, crlfDelay: Infinity });
  let pendingExtinf = null;
  let count = 0;
  const seenUrls = dedupe ? new Set() : null;
  for await (const rawLine of rl) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#EXTM3U') || line.startsWith('#EXT-X-')) continue;
    if (line.startsWith('#EXTINF:')) {
      const m = line.match(EXTINF_RE);
      if (!m) { pendingExtinf = null; continue; }
      const attrs = parseAttrs(m[2]);
      pendingExtinf = {
        name: m[3].trim(),
        groupTitle: attrs['group-title'] || '',
        logo: attrs['tvg-logo'] || '',
        tvgId: attrs['tvg-id'] || '',
      };
      continue;
    }
    if (line.startsWith('#')) continue;
    if (pendingExtinf) {
      const entry = { ...pendingExtinf, url: line };
      if (dedupe) {
        if (seenUrls.has(entry.url)) { pendingExtinf = null; continue; }
        seenUrls.add(entry.url);
      }
      entry.classification = classify(entry);
      onEntry(entry);
      count++;
      pendingExtinf = null;
      if (count >= limit) { rl.close(); fileStream.destroy(); break; }
    }
  }
  return count;
}

module.exports = {
  parseM3U,
  classify,
  cleanTitle,
  shouldKeepMovie,
  shouldKeepMovieTitle,
  shouldKeepLiveChannel,
  FOUR_K_RE,
  SD_RE,
};
