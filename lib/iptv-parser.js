const fs = require('fs');
const readline = require('readline');

const EXTINF_RE = /^#EXTINF:(-?\d+)\s*(.*),(.*)$/;
const ATTR_RE = /(\w[\w-]*)="([^"]*)"/g;
const YEAR_RE = /^(.*?)\s*\((\d{4})\)\s*(?:\[[^\]]*\]|\b(?:HD|FHD|Full\s*HD|SD|4K|Dual|Legendado|Dublado|L)\b)*\s*$/i;
const SEASON_EP_RE = /^(.*?)\s+S(\d{1,2})\s*E(\d{1,4})\s*$/i;

// Categorias que claramente NÃO são filme/série, mesmo que o nome do item
// tenha um ano "(2024)" por coincidência (ex: um canal chamado "Rede X
// (2024)" não deve virar filme). Esta lista é uma EXCLUSÃO, não uma
// permissão — ao contrário da versão antiga, que exigia que o group-title
// batesse com uma lista fixa de palavras ("filme:", "netflix", etc) para
// aceitar o item. Listas M3U variam muito de nomenclatura de categoria
// entre provedores (ex: "LANÇAMENTOS", "AÇÃO/AVENTURA", "TOP 10"), e a
// versão antiga descartava tudo isso silenciosamente como "live".
const LIVE_ONLY_GROUP_RE = /\b(canais?|channels?|abertos?|esportes?|sports?|not[íi]cias?|news|infantil|kids|documentário|24\s*horas|ao\s*vivo|live\s*tv)\b/i;

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

  // URL no padrão Xtream de filme (/movie/user/pass/id.ext) é o sinal mais
  // forte que existe — nenhum canal ao vivo usa esse padrão de URL. Se
  // bater, é filme, independente do texto da categoria.
  const looksLikeMovieUrl = /\/movie\//i.test(url);

  const yearMatch = name.match(YEAR_RE);
  const isExplicitlyLiveGroup = LIVE_ONLY_GROUP_RE.test(groupTitle || '');

  if (looksLikeMovieUrl) {
    return yearMatch
      ? { kind: 'movie', title: yearMatch[1].trim(), year: parseInt(yearMatch[2], 10) }
      : { kind: 'movie', title: name.trim(), year: null };
  }

  // Sem URL de filme explícita: um ano no nome ("Nome do Filme (2024)") é
  // um indício forte de filme/série, MAS só aceitamos se a categoria não
  // for explicitamente de canal ao vivo (evita falso positivo tipo canal
  // "TV Cidade 24 Horas (2024)" virando filme por coincidência de texto).
  if (yearMatch && !isExplicitlyLiveGroup) {
    return { kind: 'movie', title: yearMatch[1].trim(), year: parseInt(yearMatch[2], 10) };
  }

  return { kind: 'live' };
}

const SUBTITLED_RE = /\[l\]|legendad/i;
// Bloqueia 4K/UHD/8K — celular trava ou dá DECODING_FAILED com bitstream
// pesado. Aceita SD/HD/FHD/Full HD normalmente. Nomes comuns em IPTV BR:
// "Filme 4K", "UHD", "2160p", "ULTRA HD", categoria "FILMES 4K".
const FOUR_K_RE = /4\s*k|\bfhd4k\b|\buhd\b|2160\s*p?|\bultra\s*h\.?d\b|\bultrahd\b|3840\s*[x×]\s*2160|\b8\s*k\b/i;
const ADULT_RE = /xxx|adulto/i;
const BOOTLEG_RE = /\bcam\b|\bhdcam\b|\bts\b|\bhdts\b|\btc\b|\bscr\b|\bscreener\b|\btelesync\b|\btelecine\b(?!.*dublado)/i;

/** true = pode cadastrar; false = descarta (legendado, 4K, adulto, cam). */
function shouldKeepMovie(entry) {
  const { name, groupTitle } = entry;
  const blob = `${name || ''} ${groupTitle || ''}`;
  if (SUBTITLED_RE.test(blob)) return false;
  if (FOUR_K_RE.test(blob)) return false;
  if (ADULT_RE.test(blob)) return false;
  if (BOOTLEG_RE.test(blob)) return false;
  return true;
}

/** Mesma regra de qualidade, só com string (API Xtream não tem groupTitle). */
function shouldKeepMovieTitle(title) {
  return shouldKeepMovie({ name: title || '', groupTitle: '' });
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

module.exports = { parseM3U, classify, shouldKeepMovie, shouldKeepMovieTitle, shouldKeepLiveChannel, FOUR_K_RE };
