// api/embed-config.js
// Config remota do player distribuído (/embed). Todo carregamento do
// embed chama isso ANTES de tocar qualquer vídeo — é o mecanismo de
// "bloqueio remoto": desativar uma key aqui derruba o player em todo
// site que a estiver usando, sem precisar tocar no código instalado
// em lugar nenhum, porque o comportamento depende desta resposta.
//
// Além de validar a key, este endpoint TAMBÉM resolve a URL de vídeo
// a partir do tmdb_id — o parceiro nunca vê nem manipula a URL real da
// fonte (streamtape/xtream/etc), só sabe "qual filme" via TMDB ID, que
// é o mesmo ID público que ele já usaria pra puxar capa/sinopse do TMDB.
//
// GET  = validação + resolução da fonte de vídeo (fluxo original)
// POST = recebe telemetria do player (adblock/sandbox/status de ads).
//        Os dois ficam no mesmo arquivo de propósito: o plano Hobby da
//        Vercel permite no máximo 12 Serverless Functions por deploy, e
//        esse projeto já está no limite — criar um arquivo separado só
//        pra telemetria estouraria a conta (ver histórico de correção
//        do limite feito em api/subtitles.js).
//
// Uso no front-end (embed):
//   GET  /api/embed-config?key=<key>&tmdb=<id>&type=movie|tv&season=<n>&episode=<n>
//   POST /api/embed-config  body: { key, tmdbId, adblock, sandboxed, adStatus }
//
// Configuração necessária na Vercel:
//   Settings > Environment Variables > SUPABASE_SERVICE_ROLE_KEY = <service role key>

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';

// Ad zone padrão usada quando o parceiro não tem uma própria configurada
// na tabela (ads_enabled=true mas ad_zone_id vazio). Fica null de
// propósito: como a Zone ID é preenchida manualmente por parceiro, direto
// no painel (aba 🔗 Embeds → campo "Ad Zone ID do Monetag"), não faz
// sentido ter um valor padrão fixo aqui — um placeholder de texto nesse
// lugar geraria uma URL de anúncio inválida (era o bug anterior:
// "TROCAR_PELA_SUA_ZONE_ID_PADRAO" virava
// https://omg10.com/4/TROCAR_PELA_SUA_ZONE_ID_PADRAO, que não abre nada).
// Com null, o player simplesmente não tenta carregar ad pra um parceiro
// sem zone própria configurada — sem erro, sem link quebrado.
const DEFAULT_AD_ZONE_ID = null;

// ════════════════════════════════════════════════════════════
// POST: recebe telemetria (adblock/sandbox/status de ads)
// ════════════════════════════════════════════════════════════
async function handleTelemetry(req, res) {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) { res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY não configurada' }); return; }

  const { key, tmdbId, adblock, sandboxed, adStatus } = req.body || {};
  if (!key) { res.status(400).json({ error: 'Informe key' }); return; }

  const validAdStatuses = ['not_attempted', 'loaded', 'blocked', 'error'];
  const safeAdStatus = validAdStatuses.includes(adStatus) ? adStatus : 'not_attempted';

  try {
    await fetch(`${SUPABASE_URL}/rest/v1/embed_telemetry`, {
      method: 'POST',
      headers: {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
        'Content-Type': 'application/json',
        Prefer: 'return=minimal',
      },
      body: JSON.stringify([{
        partner_key: key,
        tmdb_id: tmdbId ? String(tmdbId) : null,
        adblock: typeof adblock === 'boolean' ? adblock : null,
        sandboxed: typeof sandboxed === 'boolean' ? sandboxed : null,
        ad_status: safeAdStatus,
      }]),
    });
    // Telemetria nunca deve virar motivo de erro visível pro usuário
    // final assistindo vídeo — sempre responde 200, mesmo se a
    // gravação falhar internamente (só loga no servidor).
    res.status(200).json({ ok: true });
  } catch (e) {
    console.error('embed-telemetry error:', e);
    res.status(200).json({ ok: false });
  }
}

// ════════════════════════════════════════════════════════════
// GET: validação da key + resolução da fonte de vídeo (fluxo original)
// ════════════════════════════════════════════════════════════
async function handleConfig(req, res) {
  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) {
    res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY não configurada nas env vars da Vercel' });
    return;
  }

  const key = (req.query.key || '').trim();
  const tmdbId = (req.query.tmdb || '').trim();
  const mediaType = (req.query.type || 'movie').trim(); // 'movie' ou 'tv'
  const season  = parseInt(req.query.season, 10)  || null;
  const episode = parseInt(req.query.episode, 10) || null;

  if (!key) {
    res.status(400).json({ allowed: false, reason: 'missing_key' });
    return;
  }
  if (!tmdbId) {
    res.status(400).json({ allowed: false, reason: 'missing_tmdb_id' });
    return;
  }

  // Referer real da requisição (mais confiável que um parâmetro "origin"
  // que o próprio client poderia forjar) — usado como sinal principal.
  // Fallback pro header Origin quando o Referer não vier: navegadores
  // modernos frequentemente omitem Referer por política de privacidade
  // (Referrer-Policy: no-referrer/strict-origin, extensões de bloqueio,
  // HTTPS→HTTP, etc), mas ainda mandam Origin na maioria dos casos —
  // SEM esse fallback, refererHost ficava vazio e a checagem de domínio
  // era pulada inteira, liberando o embed em qualquer site mesmo com um
  // domínio específico configurado no parceiro.
  const referer = req.headers.referer || req.headers.referrer || '';
  const origin  = req.headers.origin || '';
  let refererHost = '';
  try { refererHost = new URL(referer).hostname.replace(/^www\./, ''); } catch (e) { /* segue pro fallback */ }
  if (!refererHost) {
    try { refererHost = new URL(origin).hostname.replace(/^www\./, ''); } catch (e) { /* sem nenhum dos dois — segue sem essa checagem */ }
  }

  const headers = {
    'apikey': serviceKey,
    'Authorization': `Bearer ${serviceKey}`,
  };

  try {
    // ── 1) Valida a key do parceiro (mesma lógica de antes) ──
    const partnerUrl = `${SUPABASE_URL}/rest/v1/embed_partners?key=eq.${encodeURIComponent(key)}&select=*`;
    const pr = await fetch(partnerUrl, { headers });
    const partnerRows = await pr.json();
    const partner = Array.isArray(partnerRows) && partnerRows.length ? partnerRows[0] : null;

    if (!partner) {
      res.status(200).json({ allowed: false, reason: 'unknown_key' });
      return;
    }
    if (!partner.active) {
      res.status(200).json({ allowed: false, reason: 'blocked_by_admin' });
      return;
    }
    // Se um domínio específico foi configurado (partner.domain preenchido),
    // o site que está carregando o iframe PRECISA bater com ele. ANTES,
    // esta checagem só rodava dentro de "if (partner.domain && refererHost
    // && ...)" — ou seja, se refererHost viesse vazio (nenhum Referer/
    // Origin identificável, comum em iframes), a condição inteira dava
    // false e o domínio configurado era simplesmente ignorado, liberando
    // o embed em QUALQUER host. Agora: domínio configurado + não foi
    // possível identificar a origem = bloqueia (mais seguro travar quando
    // não dá pra checar do que liberar por padrão).
    if (partner.domain) {
      if (!refererHost) {
        res.status(200).json({ allowed: false, reason: 'domain_check_failed_no_referer' });
        return;
      }
      const domainMatches = refererHost === partner.domain || refererHost.endsWith('.' + partner.domain);
      if (!domainMatches) {
        res.status(200).json({ allowed: false, reason: 'domain_mismatch' });
        return;
      }
    }

    // ── 2) Resolve a fonte de vídeo pelo tmdb_id no SEU catálogo ──
    // O parceiro nunca vê source_url — ela só circula aqui, servidor a
    // servidor, e volta pro player já dentro da resposta consumida por
    // ele mesmo (nunca aparece no HTML/JS da página do parceiro).
    let sourceQuery = `${SUPABASE_URL}/rest/v1/vip_sources?tmdb_id=eq.${encodeURIComponent(tmdbId)}&media_type=eq.${encodeURIComponent(mediaType)}&is_active=eq.true`;
    if (mediaType === 'tv') {
      if (season)  sourceQuery += `&season=eq.${season}`;
      if (episode) sourceQuery += `&episode=eq.${episode}`;
    }
    sourceQuery += `&select=source_url,source_label,priority&order=priority.asc&limit=1`;

    const sr = await fetch(sourceQuery, { headers });
    const sourceRows = await sr.json();
    const source = Array.isArray(sourceRows) && sourceRows.length ? sourceRows[0] : null;

    if (!source) {
      res.status(200).json({ allowed: false, reason: 'title_not_found' });
      return;
    }

    // Atualiza last_used_at de forma best-effort (não bloqueia a resposta
    // se falhar — é só telemetria pro painel admin).
    fetch(`${SUPABASE_URL}/rest/v1/embed_partners?key=eq.${encodeURIComponent(key)}`, {
      method: 'PATCH',
      headers: { ...headers, 'Content-Type': 'application/json', Prefer: 'return=minimal' },
      body: JSON.stringify({ last_used_at: new Date().toISOString() }),
    }).catch(() => {});

    // ── 3) Mixed content: source_url pode vir em http:// (comum em
    // provedores Xtream/IPTV) mas a página do embed roda em https://. O
    // navegador do parceiro bloqueia isso silenciosamente — sem erro
    // visível, sem onerror confiável, sem nada: só fica com tela preta.
    // Isso é o que fazia o embed "não funcionar no site mas funcionar no
    // APK" (WebView pode ter mixed content liberado nas configs; navegador
    // comum não). A correção é a mesma já usada no player principal
    // (Public/index.html, resolvePlayableUrl): reescreve pra passar pelo
    // /api/stream-proxy, que busca o vídeo em http:// server-to-server e
    // devolve pelo seu próprio domínio https — o navegador do parceiro só
    // vê https o tempo todo.
    const playableSrc = source.source_url.startsWith('http://')
      ? `/api/stream-proxy?url=${encodeURIComponent(source.source_url)}`
      : source.source_url;

    res.status(200).json({
      allowed: true,
      src: playableSrc,
      ads: {
        enabled: !!partner.ads_enabled,
        zoneId: partner.ad_zone_id || DEFAULT_AD_ZONE_ID,
      },
      label: partner.label,
    });
  } catch (err) {
    console.error('embed-config error:', err);
    // Em caso de falha do nosso lado, o mais seguro é NÃO liberar —
    // evita que uma queda do Supabase vire "modo aberto sem controle".
    res.status(200).json({ allowed: false, reason: 'backend_error' });
  }
}

// ── Roteador por método HTTP ──
module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method === 'POST') { await handleTelemetry(req, res); return; }
  if (req.method === 'GET')  { await handleConfig(req, res); return; }
  res.status(405).json({ error: 'Method not allowed' });
};
