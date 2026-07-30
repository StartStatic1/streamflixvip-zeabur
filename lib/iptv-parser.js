const fs = require('fs');
const readline = require('readline');

const EXTINF_RE = /^#EXTINF:(-?\d+)\s*(.*),(.*)$/;
const ATTR_RE = /(\w[\w-]*)="([^"]*)"/g;
const YEAR_RE = /^(.*?)\s*\((\d{4})\)\s*(?:\[[^\]]*\]|\b(?:HD|FHD|Full\s*HD|SD|4K|Dual|Legendado|Dublado|L)\b)*\s*$/i;
const SEASON_EP_RE = /^(.*?)\s+S(\d{1,2})\s*E(\d{1,4})(?:\s+.*)?$/i;

// Categorias que claramente NÃO são filme/série
const LIVE_ONLY_GROUP_RE = /\b(canais?|channels?|abertos?|esportes?|sports?|not[íi]cias?|news|infantil|kids|documentário|24\s*horas|ao\s*vivo|live\s*tv)\b/i;

function classify(entry) {
  const { groupTitle, name, url } = entry;
  
  // 1. Tentar padrão S01E01 (mais comum para séries e animes)
  const seasonEpMatch = name.match(SEASON_EP_RE);
  if (seasonEpMatch) {
    let kind = 'episode';
    // Se o grupo mencionar anime ou crunchyroll, classificamos como anime para facilitar filtros futuros
    if (/anime|crunchyroll/i.test(groupTitle || '')) kind = 'anime';
    
    return {
      kind: kind,
      baseTitle: seasonEpMatch[1].trim(),
      season: parseInt(seasonEpMatch[2], 10),
      episode: parseInt(seasonEpMatch[3], 10),
    };
  }

  // 2. Tentar detectar episódio pela URL Xtream (/series/user/pass/id.ext)
  // Alguns animes e séries não têm S01E01 no nome, mas a URL confirma que é série
  const isSeriesUrl = /\/series\//i.test(url);
  if (isSeriesUrl) {
    let kind = 'episode';
    if (/anime|crunchyroll/i.test(groupTitle || '')) kind = 'anime';
    
    const yearMatch = name.match(YEAR_RE);
    const baseTitle = yearMatch ? yearMatch[1].trim() : name.trim();
    
    return {
      kind: kind,
      baseTitle: baseTitle,
      season: 1, // Fallback para primeira temporada
      episode: 1, // O casamento com TMDB tentará resolver o episódio real
      isUnformattedEpisode: true
    };
  }

  // 3. Tentar detectar Filmes
  const looksLikeMovieUrl = /\/movie\//i.test(url);
  const yearMatch = name.match(YEAR_RE);
  const isExplicitlyLiveGroup = LIVE_ONLY_GROUP_RE.test(groupTitle || '');

  if (looksLikeMovieUrl) {
    return yearMatch
      ? { kind: 'movie', title: yearMatch[1].trim(), year: parseInt(yearMatch[2], 10) }
      : { kind: 'movie', title: name.trim(), year: null };
  }

  if (yearMatch && !isExplicitlyLiveGroup) {
    return { kind: 'movie', title: yearMatch[1].trim(), year: parseInt(yearMatch[2], 10) };
  }

  return { kind: 'live' };
}

const ADULT_RE = /xxx|adulto/i;
const BOOTLEG_RE = /\bcam\b|\bhdcam\b|\bts\b|\bhdts\b|\btc\b|\bscr\b|\bscreener\b|\btelesync\b|\btelecine\b(?!.*dublado)/i;

function shouldKeepMovie(entry) {
  const { name, groupTitle, classification } = entry;
  
  // OTIMIZAÇÃO: Não descartamos mais legendados ([L]) ou 4K, 
  // pois o usuário quer esses conteúdos no app e site.
  
  // Mantemos o bloqueio de conteúdo adulto e gravações de cinema (CAM/TS)
  if (ADULT_RE.test(name) || ADULT_RE.test(groupTitle)) return false;
  if (BOOTLEG_RE.test(name) || BOOTLEG_RE.test(groupTitle)) return false;
  
  return true;
}

const WANTED_LIVE_GROUPS = new Set([
  'PPV', 'ESPN', 'SPORTV', 'SPORTV+', 'ESPORTE', 'ESPORTES ESTADUAIS', 'NBA',
  'ELEVEN', 'DAZN', 'SPORTYNET', 'UFC', 'PREMIERE',
  'GLOBO SUDESTE', 'GLOBO NORDESTE', 'GLOBO SUL', 'GLOBO NORTE', 'GLOBO SUL', 'GLOBO NORTE', 'GLOBO CENTRO-OESTE',
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

module.exports = { parseM3U, classify, shouldKeepMovie, shouldKeepLiveChannel };
