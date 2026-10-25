// lib/live-categories.js
//
// Organiza a TV ao vivo em poucas categorias fixas (mesma lista para o app de
// TV e o de celular). Cada canal vai para um "balde" pelo NOME do canal
// primeiro (ex.: "Telecine Pipoca" cai em Telecine mesmo vindo de "4K Canais")
// e depois pelo nome da categoria da fonte. Canais 24h (programacao em loop)
// e "series avulsas" saem da lista.

function norm(s) {
  return String(s || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[\u00b9\u00b2\u00b3\u2070-\u209f]/g, ' ')
    .replace(/[^a-z0-9+]+/g, ' ')
    .trim();
}

// Ordem em que aparecem no app.
const BUCKETS = [
  ['canais', 'Canais'],
  ['regionais', 'Canais Regionais'],
  ['capitais', 'Capitais'],
  ['hbo', 'HBO'],
  ['telecine', 'Telecine'],
  ['filmes-series', 'Filmes e Séries'],
  ['noticias', 'Notícias'],
  ['premiere', 'Premiere'],
  ['sportv', 'SporTV'],
  ['espn', 'ESPN'],
  ['ppv-esportes', 'Outros PPV de Esportes'],
  ['discovery', 'Discovery'],
  ['infantil', 'Infantil'],
  ['documentarios', 'Documentários'],
  ['variedades', 'Variedades'],
  ['religiosos', 'Religiosos'],
  ['internacionais', 'Internacionais'],
  ['outros', 'Outros'],
];
const BUCKET_ORDER = new Map(BUCKETS.map(([id], i) => [id, i]));
const BUCKET_NAME = new Map(BUCKETS);

const LOOP_RE = /\b24\s*h(rs|oras)?\b|\b24\s*horas\b|\b24 7\b|\b24x7\b/;
const AVULSAS_RE = /\bavuls/;

/** Programacao em loop (24h) ou "series avulsas": nao entra na TV ao vivo. */
function isRemoved(channelName, rawCategory) {
  const c = norm(rawCategory);
  const n = norm(channelName);
  if (LOOP_RE.test(c) || AVULSAS_RE.test(c) || /\bcameras?\b/.test(c)) return true;
  if (/\bherois sem limites\b|\bsuper sentai\b/.test(c)) return true;
  if (LOOP_RE.test(n) || AVULSAS_RE.test(n)) return true;
  if (!/[a-z]/.test(n.replace(/\b(tv|hd|fhd|sd)\b/g, ''))) return true; // "(23)", "HD 2"
  if (/\bteste?\b/.test(n)) return true;
  if (/\bcam \d+\b|\bcamera \d+\b/.test(n)) return true; // cameras 24h (A Fazenda Cam 1...)
  return false;
}

const NETWORK_RE = /\b(globo|eptv|rbs|nsc|inter ?tv|tv tem|vanguarda|integracao|anhanguera|mirante|tv bahia|amazonica|tapajos|liberal|verdes mares|cabo branco|asa branca|centro america|tv morena|rio sul|tv sergipe|tv alagoas|tv clube|tv gazeta|band|bandeirantes|sbt|alterosa|jangadeiro|aratu|record|recordtv|ric|redetv|rede tv)\b/;
const NATIONAL_LEFTOVER_RE = /^(rede|tv|nacional|brasil|br|canal|oficial|ao vivo|\s)*$/;
const CAPITAL_RE = /\b(sp|rj|bh|df|bsb|brasilia|sao paulo|rio de janeiro|rio|minas|belo horizonte|bahia|salvador|recife|pernambuco|pe|fortaleza|ceara|ce|belem|para|pa|manaus|amazonas|am|curitiba|parana|pr|porto alegre|rs|goiania|goias|go|florianopolis|floripa|sc|vitoria|es|capixaba|natal|rn|joao pessoa|paraiba|pb|maceio|alagoas|al|aracaju|sergipe|se|teresina|piaui|pi|sao luis|sao luiz|maranhao|ma|cuiaba|mt|campo grande|ms|palmas|tocantins|to|porto velho|rondonia|ro|rio branco|acre|macapa|amapa|boa vista|roraima)\b/;
const INTERIOR_RE = /\b(eptv|tv tem|vanguarda|integracao|inter ?tv|intertv|campinas|ribeirao|bauru|sorocaba|sao carlos|sao jose|rio preto|uberlandia|uberaba|juiz de fora|divinopolis|araxa|taubate|santos|londrina|maringa|cascavel|foz|joinville|blumenau|chapeco|joacaba|caxias|pelotas|passo fundo|santa maria|santa rosa|santa cruz|cruz alta|erechim|uruguaiana|bage|rio grande|petrolina|caruaru|campina grande|mossoro|imperatriz|juazeiro|feira de santana|conquista|ilheus|itabuna|montes claros|valadares|ipatinga|campos|goytacazes|cabo frio|friburgo|alto litoral|serra mar|grande minas|dos vales|noroeste|norte|sul de minas|centro oeste|interior|planicie|sertao|picos|guajara|itapetininga|presidente prudente|araraquara|piracicaba|jundiai|litoral|vale|tribuna|taroba|barriga verde|naipi|allamanda|bolsas|cabralia|remanso|atalaia|pajucara|guaiba|itapoan|correio|arapuan|tv rio preto)\b/;

function networkOf(n) {
  if (/\bglobo\b|eptv|\brbs\b|\bnsc\b|inter ?tv|tv tem|vanguarda|integracao|anh?anguera|mirante|serra dourada|tv goiania|tv bahia|amazonica|tapajos/.test(n)) return 'globo';
  if (/\bsbt\b|alterosa|jangadeiro|aratu/.test(n)) return 'sbt';
  if (/\brecord(tv)?\b|\bric\b/.test(n)) return 'record';
  if (/\bband(eirantes)?\b/.test(n)) return 'band';
  if (/\bredetv\b|\brede tv\b/.test(n)) return 'redetv';
  return null;
}

/** Globo/SBT/Record/Band: nacional -> Canais, capital -> Capitais, resto -> Regionais. */
function networkBucket(n) {
  const net = networkOf(n);
  const rest = n
    .replace(/\b(rede|tv|globo|sbt|record|recordtv|band|bandeirantes|redetv|br|hd|fhd|sd|4k|uhd|h265|ao vivo|oficial)\b/g, ' ')
    .replace(/\b\d+\b/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  if (net && (rest === '' || NATIONAL_LEFTOVER_RE.test(rest) || /^(nacional|internacional)$/.test(rest))) return 'canais';
  if (INTERIOR_RE.test(rest)) return 'regionais';
  if (CAPITAL_RE.test(rest)) return 'capitais';
  return 'regionais';
}

function byChannelName(n) {
  if (/\btelecine\b|^tc (action|cult|fun|pipoca|premium|touch)\b/.test(n)) return 'telecine';
  if (/\bpremiere\b/.test(n) && !/\buniversal\b/.test(n)) return 'premiere';
  if (/\bsport tv\b/.test(n)) return 'internacionais'; // SPORT TV de Portugal
  if (/\bsportv\b/.test(n)) return 'sportv';
  if (/\bespn\b/.test(n)) return 'espn';
  if (/\bdisney ?(\+|plus)|\bprime video\b|\bparamount ?(\+|plus)|\bband ?sports?\b|\bge\b|\bfutebol\b|\bbrasileirao\b|\bzapping\b|\bpremier club|\bapple tv\b|\bdazn\b|\bufc\b|\bcombate\b|\bnba\b|\bnfl\b|\bnsports\b|\bcaze ?tv\b|\bgoat\b|\bonefootball\b|\bone football\b|\beleven sports\b|\bfifa\b|\bppv\b|\bpay per view\b/.test(n)) return 'ppv-esportes';
  if (/\bdiscovery kids\b|\bcartoon\b|\bnick( jr|elodeon)?\b|\bgloob(inho)?\b|\bdisney (channel|junior|jr)\b|^disney$|\bboomerang\b|\bbaby ?tv\b|\btooncast\b|\bzoomoo\b|\bdreamworks\b|\bgospel kids\b/.test(n)) return 'infantil';
  if (/\b(hbo|cinemax)\b|^max( \d+)?$|\bhbo max\b/.test(n)) return 'hbo';
  if (/\bdiscovery\b|\binvestigacao discovery\b|^id\b/.test(n)) return 'discovery';
  if (/\bglobo ?news\b|\bcnn\b|\bband ?news\b|\brecord news\b|\bjovem pan\b|\bbloomberg\b|\bcnbc\b|\btimes brasil\b|\bsbt news\b|\bbbc world\b|\bnhk\b|\bcbn\b/.test(n)) return 'noticias';
  if (/^(viva|multishow|gnt|bis|off|globoplay|mais globosat|lifetime|food ?network|tlc|hgtv|e!|comedy central|mtv)\b/.test(n)) return 'variedades';
  if (/^(usa|warner|tnt|axn|sony|space|amc|tcm|megapix|universal|a e|fx|paramount|canal sony|syfy|cinespanto|run ?time|canal brasil|studio universal|film arts|cine sky|runtime|star channel|cinecanal|tnt series|tnt novelas)\b/.test(n)) return 'filmes-series';
  if (/\bhistory\b|\bh2\b|\bnat ?geo\b|\bnational geographic\b|\banimal planet\b|\bcuriosity\b|\bsmithsonian\b/.test(n)) return 'documentarios';
  return null;
}

function byCategoryName(c) {
  if (!c) return null;
  if (/\bhbo\b|^max\b/.test(c)) return 'hbo';
  if (/\btelecine\b/.test(c)) return 'telecine';
  if (/\bpremiere\b/.test(c)) return 'premiere';
  if (/\bsportv\b/.test(c)) return 'sportv';
  if (/\bespn\b/.test(c)) return 'espn';
  if (/\bdiscovery\b/.test(c)) return 'discovery';
  if (/\bnoticia|\bnews\b|\bjornal/.test(c)) return 'noticias';
  if (/\bppv\b|pay per view|\besporte|\bsport|\bjogos?\b|\beventos?\b|\bfutebol\b|\bconcacaf\b|\bcopa\b|\bnba\b|\bnfl\b|\bufc\b|\bcombate\b|\blutas?\b|\bdazn\b|\bgoat\b|\bnsports\b|\bcaze|\bprime video\b|\bapple tv\b|\bdisney plus\b|\bparamount\b|\bx sports\b|\bsporty/.test(c)) return 'ppv-esportes';
  if (/\binfant|\bkids?\b|\bdesenho|\bdisney\b/.test(c)) return 'infantil';
  if (/\bfilme|\bseries?\b|\bcine\b|\bcinema\b|\bruntime\b|\btnt\b|\buniversal\b|\bwarner\b/.test(c)) return 'filmes-series';
  if (/\bdocumentar/.test(c)) return 'documentarios';
  if (/\brelig|\bgospel\b|\bcatolic|\bevangel/.test(c)) return 'religiosos';
  if (/\binternaciona|\blegendad|\busa\b|\bportugal\b|\blatin/.test(c)) return 'internacionais';
  if (/\bvariedade|\bfazenda\b|\breality\b|\bpluto\b|\bnovela|\bradio|\bmusica|\bmusic\b|\bshows?\b|\bagro\b|\bprogramas?\b/.test(c)) return 'variedades';
  if (/\bcentro oeste\b|\bnordeste\b|\bsudeste\b|\bnorte\b|\bsul\b|\bregiona|\bcapita/.test(c)) return 'regionais';
  if (/\babertos?\b|\bpublicos?\b|\bgerais?\b|\bnacionais?\b/.test(c)) return 'canais';
  return null;
}

/** Balde final de um canal (ou null = remover). */
function bucketFor(channelName, rawCategory) {
  if (isRemoved(channelName, rawCategory)) return null;
  const n = norm(channelName);
  const c = norm(rawCategory);
  const byName = byChannelName(n);
  if (byName) return byName;
  // Globo/SBT/Record/Band/RedeTV (e afiliadas) em qualquer categoria da fonte
  if (networkOf(n)) return networkBucket(n);
  if (/\b(globo|globos|band|sbt|record|recordtv|redetv)\b/.test(c)) return 'regionais';
  return byCategoryName(c) || 'outros';
}

// Ordem dentro da categoria: canais principais primeiro, depois A-Z com numeros
// em ordem natural (SporTV, SporTV 2, SporTV 3...).
const TOP_CANAIS = ['globo', 'sbt', 'record', 'band', 'redetv', 'cultura', 'tv brasil', 'gazeta', 'cnt', 'rede vida'];
function rankInBucket(bucket, name) {
  const n = norm(name);
  if (bucket === 'canais' || bucket === 'capitais') {
    const net = networkOf(n) || '';
    const i = TOP_CANAIS.findIndex((t) => t === net || n.startsWith(t));
    return i >= 0 ? i : TOP_CANAIS.length;
  }
  return 0;
}

const collator = new Intl.Collator('pt-BR', { numeric: true, sensitivity: 'base' });
function compareChannels(a, b) {
  const ra = rankInBucket(a.categoryId, a.name);
  const rb = rankInBucket(b.categoryId, b.name);
  if (ra !== rb) return ra - rb;
  return collator.compare(a.name, b.name);
}

/** Nome limpo para mostrar: sem "-", "BR •", "PPV.", numeros sobrescritos. */
function cleanChannelName(name) {
  let s = String(name || '');
  s = s.replace(/[\u00b9\u00b2\u00b3\u2070-\u209f]/g, ' ');
  s = s.replace(/^[\s\-–—•|:.]+/, '');
  s = s.replace(/^(BR|PT|BRA)\s*[•|:\-»]+\s*/i, '');
  s = s.replace(/^PPV\.\s*/i, '');
  s = s.replace(/\s*\|+\s*$/, '').replace(/\s*\|\s*/g, ' ');
  s = s.replace(/\s+H\.?26[45]\b/gi, '').replace(/\b(HD|FHD)26[45]\b/gi, '');
  s = s.replace(/^TC\s+(Action|Cult|Fun|Pipoca|Premium|Touch)\b/i, (m, w) => `Telecine ${w}`);
  // "Premiere2" -> "Premiere 2", "Max 01" -> "Max 1" (juntam com os iguais)
  s = s.replace(/\b([A-Za-z]{3,})(\d{1,2})$/, '$1 $2').replace(/\b0(\d)\b/g, '$1');
  s = s.replace(/\s+/g, ' ').trim();
  if (s && s === s.toUpperCase() && /[A-Z]/.test(s) && s.length > 4) {
    // "REDE GLOBO SÃO PAULO" -> "Rede Globo São Paulo" (siglas curtas ficam)
    s = s.toLowerCase().replace(/(^|[\s/(&+-])(\p{L})/gu, (m, sp, ch) => sp + ch.toUpperCase());
    s = s.replace(/\b(Sbt|Tv|Hd|Fhd|Uhd|Sd|Rbs|Nsc|Eptv|Cnn|Espn|Hbo|Tnt|Axn|Amc|Tcm|Cnt|Ric|Mtv|Bbc|Nhk|Cbn|Cnbc|Ufc|Nba|Nfl|Ppv|Ge|Rj|Sp|Mg|Df|Bsb|Pr|Rs|Sc|Es|Ba|Pe|Ce|Pa|Am|Go|Mt|Ms|To|Ro|Pb|Rn|Al|Se|Pi|Ma|Ap|Rr|Ac|Usa|Id|Tc|Tlc|Gnt|A&e|Bh)\b/g, (m) => m.toUpperCase());
    s = s.replace(/\bSBT\b/g, 'SBT').replace(/\bRedetv\b/g, 'RedeTV').replace(/\bSportv\b/g, 'SporTV');
  }
  return s || String(name || '').trim();
}

/**
 * Aplica as categorias fixas na lista ja juntada.
 * channels: [{ id, name, categoryId(rawKey), rawCategory, ... }]
 */
const MAX_STREAMS = 10;

function organize(channels) {
  const out = [];
  for (const ch of channels) {
    const name = cleanChannelName(ch.name);
    const bucket = bucketFor(name, ch.rawCategory || '');
    if (!bucket) continue;
    out.push({ ...ch, name, categoryId: bucket });
  }
  // 2a passada: nomes que so diferem por caixa/espaco/zero viram um canal so
  // ("Premiere2" + "Premiere 2", "Max 01" + "Max 1", "TC Pipoca" + "Telecine Pipoca").
  const merged = new Map();
  const deduped = [];
  for (const ch of out) {
    const key = `${ch.categoryId}|${norm(ch.name).replace(/\b(channel|canal)\b/g, '').replace(/\s+/g, ' ').trim()}`;
    const prev = merged.get(key);
    if (!prev) {
      const copy = { ...ch, streams: [...(ch.streams || [])] };
      merged.set(key, copy);
      deduped.push(copy);
      continue;
    }
    const seen = new Set(prev.streams.map((x) => x.url));
    for (const st of ch.streams || []) if (!seen.has(st.url)) { prev.streams.push(st); seen.add(st.url); }
    prev.streams.sort((a, b) => (a.priority ?? 0) - (b.priority ?? 0));
    if (prev.streams.length > MAX_STREAMS) prev.streams.length = MAX_STREAMS;
    if (!prev.logo && ch.logo) prev.logo = ch.logo;
  }
  out.length = 0;
  out.push(...deduped);
  out.sort((a, b) => {
    const oa = BUCKET_ORDER.get(a.categoryId) ?? 99;
    const ob = BUCKET_ORDER.get(b.categoryId) ?? 99;
    if (oa !== ob) return oa - ob;
    return compareChannels(a, b);
  });
  const used = new Set(out.map((c) => c.categoryId));
  const categories = BUCKETS.filter(([id]) => used.has(id)).map(([id, name]) => ({ id, name }));
  return { channels: out, categories };
}

module.exports = { organize, bucketFor, isRemoved, cleanChannelName, networkBucket, norm, BUCKETS, BUCKET_NAME };
