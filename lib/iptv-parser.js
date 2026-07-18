// parser.js
// Faz streaming-parse de um M3U grande sem carregar tudo em memória.
// Classifica cada entrada em: live (canal TV), movie (filme) ou episode (série).

const fs = require('fs');
const readline = require('readline');

// Regex pra extrair os campos do #EXTINF
// Ex: #EXTINF:-1 tvg-id="AE.br" tvg-name="A&E FHD" tvg-logo="http://..." group-title="VARIEDADES",A&E FHD
const EXTINF_RE = /^#EXTINF:(-?\d+)\s*(.*),(.*)$/;
const ATTR_RE = /(\w[\w-]*)="([^"]*)"/g;

// Detecta "Título (Ano)" — permite sufixos depois do ano (ex: "[Dual]",
// "[L]", "HD", "Full HD"), que são muito comuns nas playlists reais e
// antes faziam o ano não ser reconhecido (ficava NULL), impedindo o
// casamento seguro com o TMDB mesmo pra filmes populares e corretamente
// anotados na fonte, como "Rambo IV (2008) [Dual]".
const YEAR_RE = /^(.*?)\s*\((\d{4})\)\s*(?:\[[^\]]*\]|\b(?:HD|FHD|Full\s*HD|SD|4K|Dual|Legendado|Dublado|L)\b)*\s*$/i;

// Detecta "Título SxxEyy" (com variações de espaçamento/zero-padding).
// Episódio aceita até 4 dígitos (não 3) porque séries/animes longevos
// passam de 999 episódios na numeração absoluta — ex: "One Piece
// S21E1000". Com limite de 3 dígitos esses episódios não batiam na
// regex, caíam no fallback "live" por padrão e sumiam silenciosamente
// da sincronização (o sync nunca via erro, só nunca cadastrava esses
// títulos).
const SEASON_EP_RE = /^(.*?)\s+S(\d{1,2})E(\d{1,4})\s*$/i;

// Categorias que consideramos "TV ao vivo" (heurística por group-title)
// Filmes/séries usam "Filmes | ..." (com pipe) ou nomes de streaming (Netflix¹ etc).
// Canais ao vivo tipo "FILMES E SÉRIES" (sem pipe) são canais 24h, não itens individuais.
// Qualquer coisa que NÃO bate com filme/série cai em "live" por padrão.
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
  // "Filmes | Categoria" (com pipe) = subcategoria de filme individual.
  // "FILMES E SÉRIES" sozinho (sem pipe) = nome de canal 24h ao vivo, não bate aqui.
  const looksLikeMovieGroup = /filmes?\s*\|/i.test(groupTitle || '');
  const looksLikeStreamingGroup = /netflix|amazon|prime|globoplay|max¹|disney|paramount|apple\s*tv|crunchyroll|star\s*plus|hbo|claro\s*video/i.test(groupTitle || '');

  if (yearMatch && (looksLikeMovieGroup || looksLikeStreamingGroup || url.includes('/movie/'))) {
    return {
      kind: 'movie',
      title: yearMatch[1].trim(),
      year: parseInt(yearMatch[2], 10),
    };
  }

  // Fallback extra: URL de VOD /movie/ mesmo sem ano no nome (raro, mas existe)
  if (url.includes('/movie/') && !yearMatch) {
    return { kind: 'movie', title: name.trim(), year: null };
  }

  // Sem ano e sem SxxExx -> assume canal ao vivo
  return { kind: 'live' };
}

// Marcadores de "legendado" que aparecem no nome (ex: "Nuremberg [L] (2025)")
// ou no group-title ("Filmes | Legendados¹")
const SUBTITLED_RE = /\[l\]|legendad/i;
// Marcadores de 4K no nome ou grupo
const FOUR_K_RE = /4k|\bfhd4k\b/i;
// Conteúdo adulto — descartado por padrão
const ADULT_RE = /xxx|adulto/i;
// Gravações piratas de baixa qualidade feitas dentro do cinema (CAM/TS/TC/SCR).
// Diferente de um link quebrado: o vídeo toca normalmente, só que a
// qualidade é ruim por natureza (tremido, câmera na mão, gente passando na
// frente). Fallback automático não ajuda aqui, porque o arquivo "funciona"
// tecnicamente — o problema é descartar antes de cadastrar.
const BOOTLEG_RE = /\bcam\b|\bhdcam\b|\bts\b|\bhdts\b|\btc\b|\bscr\b|\bscreener\b|\btelesync\b|\btelecine\b(?!.*dublado)/i;

/**
 * Decide se um item de FILME deve ser mantido, segundo as regras:
 * - remove legendados
 * - remove 4K (fica só HD/FHD "normal")
 * - remove adultos
 * Retorna true se deve manter.
 */
function shouldKeepMovie(entry) {
  const { name, groupTitle } = entry;
  if (SUBTITLED_RE.test(name) || SUBTITLED_RE.test(groupTitle)) return false;
  if (FOUR_K_RE.test(name) || FOUR_K_RE.test(groupTitle)) return false;
  if (ADULT_RE.test(name) || ADULT_RE.test(groupTitle)) return false;
  if (BOOTLEG_RE.test(name) || BOOTLEG_RE.test(groupTitle)) return false;
  return true;
}

// ─── Filtro de canais ao vivo por categoria desejada ───
// Você quer: Esportes, PPV, Discovery, Combate, TV aberta somente.
// Mapeamento por group-title (categorias exatas da playlist) OU por nome
// do canal (porque "Discovery"/"Combate" aparecem dentro do NOME, com
// group-title genérico tipo "DOCUMENTÁRIOS"/"ESPORTE" — não dá pra pegar
// só pelo grupo).
const WANTED_LIVE_GROUPS = new Set([
  'PPV',
  'ESPN', 'SPORTV', 'SPORTV+', 'ESPORTE', 'ESPORTES ESTADUAIS', 'NBA',
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

/**
 * Faz streaming-parse do arquivo M3U.
 * onEntry(entry) é chamado pra cada item encontrado.
 * entry = { name, groupTitle, logo, tvgId, url, classification }
 */
async function parseM3U(filePath, onEntry, { limit = Infinity, dedupe = false } = {}) {
  const fileStream = fs.createReadStream(filePath, { encoding: 'utf8' });
  const rl = readline.createInterface({ input: fileStream, crlfDelay: Infinity });

  let pendingExtinf = null;
  let count = 0;
  let lineNo = 0;
  const seenUrls = dedupe ? new Set() : null;

  for await (const rawLine of rl) {
    lineNo++;
    const line = rawLine.trim();
    if (!line) continue;

    if (line.startsWith('#EXTM3U') || line.startsWith('#EXT-X-')) continue;

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

    if (line.startsWith('#')) continue; // outras diretivas, ignora

    // Linha de URL — fecha a entrada pendente
    if (pendingExtinf) {
      const entry = { ...pendingExtinf, url: line };

      if (dedupe) {
        if (seenUrls.has(entry.url)) {
          pendingExtinf = null;
          continue;
        }
        seenUrls.add(entry.url);
      }

      entry.classification = classify(entry);
      onEntry(entry);
      count++;
      pendingExtinf = null;
      if (count >= limit) {
        rl.close();
        fileStream.destroy();
        break;
      }
    }
  }

  return count;
}

module.exports = { parseM3U, classify, shouldKeepMovie, shouldKeepLiveChannel };
