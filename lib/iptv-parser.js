const fs = require('fs');
const readline = require('readline');

const EXTINF_RE = /^#EXTINF:(-?\d+)\s*(.*),(.*)$/;
const ATTR_RE = /(\w[\w-]*)="([^"]*)"/g;
const YEAR_RE = /^(.*?)\s*\((\d{4})\)\s*(?:\[[^\]]*\]|\b(?:HD|FHD|Full\s*HD|SD|4K|Dual|Legendado|Dublado|L)\b)*\s*$/i;
const SEASON_EP_RE = /^(.*?)\s+S(\d{1,2})\s*E(\d{1,4})\s*$/i;

function classify(entry) {
  const { groupTitle, name, url } = entry;
  const seasonEpMatch = name.match(SEASON_EP_RE);
  if (seasonEpMatch) {
    return {
      kind: 'episode',
      baseTitle: seasonEpMatch[1].trim(),
      season: parseInt(seasonEpMatch[2], 10),
      episode: parseInt(seasonEpMatch[3], 10),
    };
  }
  const yearMatch = name.match(YEAR_RE);
  const looksLikeMovieGroup = /filmes?\s*[:|]/i.test(groupTitle || '');
  const looksLikeStreamingGroup = /netflix|amazon|prime|globoplay|max¹|disney|paramount|apple\s*tv|crunchyroll|star\s*plus|hbo|claro\s*video/i.test(groupTitle || '');
  if (yearMatch && (looksLikeMovieGroup || looksLikeStreamingGroup || url.includes('/movie/'))) {
    return {
      kind: 'movie',
      title: yearMatch[1].trim(),
      year: parseInt(yearMatch[2], 10),
    };
  }
  if (url.includes('/movie/') && !yearMatch && (looksLikeMovieGroup || looksLikeStreamingGroup)) {
    return { kind: 'movie', title: name.trim(), year: null };
  }
  return { kind: 'live' };
}

const SUBTITLED_RE = /\[l\]|legendad/i;
const FOUR_K_RE = /4k|\bfhd4k\b/i;
const ADULT_RE = /xxx|adulto/i;
const BOOTLEG_RE = /\bcam\b|\bhdcam\b|\bts\b|\bhdts\b|\btc\b|\bscr\b|\bscreener\b|\btelesync\b|\btelecine\b(?!.*dublado)/i;

function shouldKeepMovie(entry) {
  const { name, groupTitle } = entry;
  if (SUBTITLED_RE.test(name) || SUBTITLED_RE.test(groupTitle)) return false;
  if (FOUR_K_RE.test(name) || FOUR_K_RE.test(groupTitle)) return false;
  if (ADULT_RE.test(name) || ADULT_RE.test(groupTitle)) return false;
  if (BOOTLEG_RE.test(name) || BOOTLEG_RE.test(groupTitle)) return false;
  return true;
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

module.exports = { parseM3U, classify, shouldKeepMovie, shouldKeepLiveChannel };
