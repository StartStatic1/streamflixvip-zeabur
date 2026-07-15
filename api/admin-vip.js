// api/admin-vip.js
// API de admin VIP — roda no servidor (Vercel), protegida por checagem na
// tabela vip_panel_admins (mesma usada pelo painel de filmes/séries —
// painel único, lista única de quem tem acesso administrativo).
// Operações: listar códigos, criar códigos, desativar código.

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  if (req.method === 'OPTIONS') { res.status(200).end(); return; }
  if (req.method !== 'POST') { res.status(405).json({ error: 'Method not allowed' }); return; }

  const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!serviceKey) { res.status(500).json({ error: 'SUPABASE_SERVICE_ROLE_KEY não configurada' }); return; }

  // Verificar token do usuário logado
  const authHeader = req.headers['authorization'] || '';
  const userToken  = authHeader.replace('Bearer ', '').trim();
  if (!userToken) { res.status(401).json({ error: 'Token não fornecido' }); return; }

  // Validar token no Supabase
  const userRes = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { 'apikey': serviceKey, 'Authorization': `Bearer ${userToken}` }
  });
  if (!userRes.ok) { res.status(401).json({ error: 'Token inválido' }); return; }
  const userJson = await userRes.json();
  const userId = userJson?.id;
  if (!userId) { res.status(401).json({ error: 'Token inválido' }); return; }

  // Checa se esse usuário está na lista de admins do painel (mesma tabela
  // usada pelo painel de filmes — um único lugar para autorizar acesso).
  const adminRes = await fetch(
    `${SUPABASE_URL}/rest/v1/vip_panel_admins?id=eq.${encodeURIComponent(userId)}&select=id`,
    { headers: { 'apikey': serviceKey, 'Authorization': `Bearer ${serviceKey}` } }
  );
  const adminRows = await adminRes.json();
  if (!adminRes.ok || !Array.isArray(adminRows) || adminRows.length === 0) {
    res.status(403).json({ error: 'Acesso negado' });
    return;
  }

  // Parse body
  let body = req.body;
  if (typeof body === 'string') { try { body = JSON.parse(body); } catch(e) { body = {}; } }

  const action = body?.action;
  const svcHeaders = {
    'apikey': serviceKey,
    'Authorization': `Bearer ${serviceKey}`,
    'Content-Type': 'application/json',
  };

  // ── LIST ──
  if (action === 'list') {
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes?select=*&order=created_at.desc&limit=200`, { headers: svcHeaders });
    const rows = await r.json();
    res.status(200).json({ codes: rows });
    return;
  }

  // ── CREATE ──
  if (action === 'create') {
    const { codes } = body; // array de { code, duration_hours, plan_label }
    if (!Array.isArray(codes) || codes.length === 0) { res.status(400).json({ error: 'Informe os códigos' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes`, {
      method: 'POST',
      headers: { ...svcHeaders, 'Prefer': 'return=representation' },
      body: JSON.stringify(codes),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao criar', detail: result }); return; }
    res.status(200).json({ created: result });
    return;
  }

  // ── DEACTIVATE ──
  if (action === 'deactivate') {
    const { code } = body;
    if (!code) { res.status(400).json({ error: 'Informe o code' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes?code=eq.${encodeURIComponent(code)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, 'Prefer': 'return=representation' },
      body: JSON.stringify({ is_active: false }),
    });
    const result = await r.json();
    res.status(200).json({ updated: result });
    return;
  }

  // ── REACTIVATE ──
  if (action === 'reactivate') {
    const { code } = body;
    if (!code) { res.status(400).json({ error: 'Informe o code' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_codes?code=eq.${encodeURIComponent(code)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, 'Prefer': 'return=representation' },
      body: JSON.stringify({ is_active: true }),
    });
    const result = await r.json();
    res.status(200).json({ updated: result });
    return;
  }

  // ── LIST USERS (vip_status: todo mundo que já logou, com ou sem VIP) ──
  if (action === 'list-users') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_status?select=user_id,email,name,first_login_at,last_login_at,expires_at,plan_label,last_code_used&order=last_login_at.desc&limit=500`,
      { headers: svcHeaders }
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar usuários', detail: rows }); return; }
    res.status(200).json({ users: rows });
    return;
  }

  // ── LIST REDEMPTIONS (histórico de códigos usados por um usuário) ──
  if (action === 'list-redemptions') {
    const { userId } = body;
    if (!userId) { res.status(400).json({ error: 'Informe userId' }); return; }
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_redemptions?user_id=eq.${encodeURIComponent(userId)}&select=*&order=redeemed_at.desc`,
      { headers: svcHeaders }
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar histórico', detail: rows }); return; }
    res.status(200).json({ redemptions: rows });
    return;
  }

  // ── FILMES/SÉRIES: checa quais tmdb_ids de uma lista já têm fonte cadastrada ──
  if (action === 'list-sources-for') {
    const { mediaType, ids } = body;
    if (!mediaType || !Array.isArray(ids) || ids.length === 0) {
      res.status(200).json({ ids: [] });
      return;
    }
    const idsParam = ids.join(',');
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_sources?media_type=eq.${encodeURIComponent(mediaType)}&tmdb_id=in.(${idsParam})&select=tmdb_id`,
      { headers: svcHeaders }
    );
    const rows = await r.json();
    if (!r.ok) { res.status(200).json({ ids: [] }); return; }
    res.status(200).json({ ids: (rows || []).map(x => x.tmdb_id) });
    return;
  }

  // ── FILMES/SÉRIES: lista todas as fontes cadastradas (pro painel) ──
  // MANTIDO por compatibilidade, mas não é mais usado pelo painel — baixar
  // as 5000+ linhas inteiras a cada abertura da aba (e reprocessar tudo no
  // cliente a cada tecla do filtro) era a causa da lentidão. Use
  // 'list-sources-filtered' abaixo, que filtra e pagina no banco.
  if (action === 'list-sources') {
    const PAGE_SIZE = 1000;
    let allRows = [];
    let offset = 0;
    while (true) {
      const r = await fetch(
        `${SUPABASE_URL}/rest/v1/vip_sources?select=id,tmdb_id,media_type,season,episode,title,poster_path,source_url,source_label,priority,is_active,created_at&order=created_at.desc`,
        {
          headers: {
            ...svcHeaders,
            'Range-Unit': 'items',
            'Range': `${offset}-${offset + PAGE_SIZE - 1}`,
          },
        }
      );
      const rows = await r.json();
      if (!r.ok) { res.status(502).json({ error: 'Erro ao listar fontes', detail: rows }); return; }
      if (!Array.isArray(rows) || rows.length === 0) break;
      allRows = allRows.concat(rows);
      if (rows.length < PAGE_SIZE) break;
      offset += PAGE_SIZE;
    }
    res.status(200).json({ sources: allRows });
    return;
  }

  // ── FILMES/SÉRIES: lista fontes com filtro/paginação feitos no banco ──
  // Substitui 'list-sources': em vez de baixar as 5000+ linhas inteiras pro
  // cliente e reagrupar/filtrar tudo em JS a cada tecla, filtramos e
  // paginamos direto no PostgREST. Como o agrupamento por título ainda
  // precisa acontecer por tmdb_id (um título pode ter várias fontes), a
  // paginação aqui é por TÍTULO DISTINTO, não por linha:
  //   1) pega os tmdb_ids distintos que batem com o filtro, já paginados
  //   2) busca todas as fontes só desses tmdb_ids (poucas linhas)
  // Isso mantém a resposta pequena e rápida independente do total no banco.
  if (action === 'list-sources-filtered') {
    const {
      search = '',
      mediaType = 'all',   // 'all' | 'movie' | 'tv'
      status = 'all',      // 'all' | 'active' | 'inactive'
      sort = 'recent',     // 'recent' | 'az' | 'za' | 'sources'
      page = 1,
      pageSize = 30,
    } = body;

    const qs = [];
    qs.push('select=id,tmdb_id,media_type,season,episode,title,poster_path,is_active,created_at');
    if (mediaType === 'movie' || mediaType === 'tv') qs.push(`media_type=eq.${mediaType}`);
    if (search && search.trim()) {
      // ilike com % dos dois lados = "contém", sem diferenciar maiúsculas
      qs.push(`title=ilike.*${encodeURIComponent(search.trim())}*`);
    }
    if (status === 'active') qs.push('is_active=eq.true');
    else if (status === 'inactive') qs.push('is_active=eq.false');
    qs.push('order=created_at.desc');

    // Busca até 4000 linhas que já batem no filtro de texto/tipo/status —
    // isso é bem mais leve que baixar as 5000+ linhas inteiras da tabela,
    // já que a maioria das buscas por título reduz bastante o total.
    const listRes = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_sources?${qs.join('&')}&limit=4000`,
      { headers: svcHeaders }
    );
    const rows = await listRes.json();
    if (!listRes.ok) { res.status(502).json({ error: 'Erro ao listar fontes', detail: rows }); return; }

    // Agrupa por título (tmdb_id + media_type) — igual à lógica antiga do
    // front, só que agora sobre um conjunto já filtrado e bem menor.
    const groups = new Map();
    (rows || []).forEach(s => {
      const key = s.media_type + ':' + s.tmdb_id;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(s);
    });

    let groupList = [...groups.entries()].map(([key, items]) => {
      const resolvedTitle = (items.find(s => s.title && s.title.trim())?.title) || '(sem título)';
      const activeCount   = items.filter(s => s.is_active).length;
      const latest = Math.max(...items.map(s => new Date(s.created_at).getTime()));
      return {
        key,
        tmdb_id: items[0].tmdb_id,
        media_type: items[0].media_type,
        poster_path: items.find(s => s.poster_path)?.poster_path || null,
        resolvedTitle,
        sourceCount: items.length,
        activeCount,
        inactiveCount: items.length - activeCount,
        latest,
      };
    });

    // Filtro por status precisa ser reaplicado no nível do GRUPO (não da
    // linha): "com ativo" = grupo tem >=1 fonte ativa; "só inativos" =
    // grupo não tem nenhuma ativa. Isso não dá pra fazer 100% via query de
    // linha acima porque um grupo pode ter fontes ativas e inativas juntas.
    if (status === 'active') groupList = groupList.filter(g => g.activeCount > 0);
    else if (status === 'inactive') groupList = groupList.filter(g => g.activeCount === 0);

    const totalGroups = groupList.length;

    if (sort === 'az') groupList.sort((a,b) => a.resolvedTitle.localeCompare(b.resolvedTitle, 'pt-BR'));
    else if (sort === 'za') groupList.sort((a,b) => b.resolvedTitle.localeCompare(a.resolvedTitle, 'pt-BR'));
    else if (sort === 'sources') groupList.sort((a,b) => b.sourceCount - a.sourceCount);
    else groupList.sort((a,b) => b.latest - a.latest);

    const start = (Math.max(1, page) - 1) * pageSize;
    const pageItems = groupList.slice(start, start + pageSize);

    res.status(200).json({
      groups: pageItems.map(g => ({
        tmdb_id: g.tmdb_id, media_type: g.media_type, title: g.resolvedTitle,
        poster_path: g.poster_path, sourceCount: g.sourceCount,
        activeCount: g.activeCount, inactiveCount: g.inactiveCount,
      })),
      totalGroups,
      page: Math.max(1, page),
      hasMore: start + pageSize < totalGroups,
    });
    return;
  }

  // ── FILMES/SÉRIES: busca as fontes completas de UM título específico ──
  // Usado ao expandir um grupo na lista (a listagem paginada acima não traz
  // url/label/priority de cada fonte pra não pesar a resposta da página).
  if (action === 'list-sources-for-title') {
    const { tmdbId, mediaType } = body;
    if (!tmdbId || !mediaType) { res.status(400).json({ error: 'Informe tmdbId e mediaType' }); return; }
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_sources?tmdb_id=eq.${encodeURIComponent(tmdbId)}&media_type=eq.${encodeURIComponent(mediaType)}&select=id,tmdb_id,media_type,season,episode,title,poster_path,source_url,source_label,priority,is_active,created_at&order=season.asc,episode.asc,priority.desc`,
      { headers: svcHeaders }
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao buscar fontes do título', detail: rows }); return; }
    res.status(200).json({ sources: rows || [] });
    return;
  }

  // ── FILMES/SÉRIES: cria nova fonte ──
  if (action === 'create-source') {
    const { tmdb_id, media_type, title, poster_path, season, episode, source_url, source_label, priority } = body;
    if (!tmdb_id || !media_type || !source_url) {
      res.status(400).json({ error: 'Dados incompletos para criar a fonte' });
      return;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources`, {
      method: 'POST',
      headers: { ...svcHeaders, 'Prefer': 'return=representation' },
      body: JSON.stringify({
        tmdb_id, media_type, title, poster_path, season, episode,
        source_url, source_label, priority, created_by: userId,
      }),
    });
    const result = await r.json();
    if (!r.ok) {
      const msg = JSON.stringify(result).includes('duplicate') ? 'duplicate key' : (result?.message || 'Erro ao criar fonte');
      res.status(409).json({ error: msg, detail: result });
      return;
    }
    res.status(200).json({ created: result });
    return;
  }

  // ── FILMES/SÉRIES: atualiza fonte existente ──
  if (action === 'update-source') {
    const { sourceId, season, episode, source_url, source_label, priority, title, poster_path } = body;
    if (!sourceId) { res.status(400).json({ error: 'Informe sourceId' }); return; }
    const patchBody = { season, episode, source_url, source_label, priority };
    // Só sobrescreve título/poster se vier preenchido — assim uma edição
    // nunca APAGA um título que já estava certo, só reforça/conserta um
    // que estivesse em branco.
    if (title) patchBody.title = title;
    if (poster_path) patchBody.poster_path = poster_path;
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, 'Prefer': 'return=representation' },
      body: JSON.stringify(patchBody),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao atualizar fonte', detail: result }); return; }
    res.status(200).json({ updated: result });
    return;
  }

  // ── VIP: busca a config de bloqueio do TÍTULO inteiro (tabela dedicada
  // vip_titles — 1 linha por tmdb_id+media_type, independente de quantas
  // fontes/servidores o título tenha em vip_sources) ──
  if (action === 'get-vip-title') {
    const { tmdb_id, media_type } = body;
    if (!tmdb_id || !media_type) { res.status(400).json({ error: 'Informe tmdb_id e media_type' }); return; }
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_titles?tmdb_id=eq.${encodeURIComponent(tmdb_id)}&media_type=eq.${encodeURIComponent(media_type)}&select=vip_lock,vip_free_episode_limit`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao buscar config VIP', detail: rows }); return; }
    // Sem linha ainda = título nunca configurado = sem bloqueio algum.
    res.status(200).json({ config: rows[0] || { vip_lock: false, vip_free_episode_limit: null } });
    return;
  }

  // ── VIP: cria/atualiza a config de bloqueio do TÍTULO inteiro — 1 ÚNICA
  // linha por título, nunca precisa tocar nas fontes/servidores pra
  // marcar ou desmarcar VIP. Usa upsert (on_conflict) porque o título
  // pode ou não já ter uma linha em vip_titles. ──
  if (action === 'set-vip-title') {
    const { tmdb_id, media_type, vip_lock, vip_free_episode_limit } = body;
    if (!tmdb_id || !media_type) { res.status(400).json({ error: 'Informe tmdb_id e media_type' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_titles?on_conflict=tmdb_id,media_type`, {
      method: 'POST',
      headers: { ...svcHeaders, 'Prefer': 'resolution=merge-duplicates,return=representation' },
      body: JSON.stringify({
        tmdb_id, media_type,
        vip_lock: !!vip_lock,
        vip_free_episode_limit: (!vip_lock && vip_free_episode_limit) ? vip_free_episode_limit : null,
        updated_by: userId,
      }),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao salvar config VIP', detail: result }); return; }
    res.status(200).json({ saved: result });
    return;
  }

  // ── FILMES/SÉRIES: ativa/desativa fonte ──
  if (action === 'toggle-source') {
    const { sourceId, isActive } = body;
    if (!sourceId) { res.status(400).json({ error: 'Informe sourceId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, 'Prefer': 'return=representation' },
      body: JSON.stringify({ is_active: !!isActive }),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao atualizar fonte', detail: result }); return; }
    res.status(200).json({ updated: result });
    return;
  }

  // ── FILMES/SÉRIES: exclui fonte ──
  if (action === 'delete-source') {
    const { sourceId } = body;
    if (!sourceId) { res.status(400).json({ error: 'Informe sourceId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_sources?id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'DELETE',
      headers: svcHeaders,
    });
    if (!r.ok) { const detail = await r.text(); res.status(502).json({ error: 'Erro ao excluir fonte', detail }); return; }
    res.status(200).json({ success: true });
    return;
  }

  // ── FONTES IPTV (playlists M3U/Xtream inteiras, não filmes individuais) ──

  // ── DASHBOARD: números agregados de todo o sistema numa chamada só ──
  if (action === 'dashboard-stats') {
    // Usa Prefer: count=exact + head:true — o PostgREST retorna só o total
    // no header Content-Range, sem baixar as linhas em si. Muito mais
    // rápido que buscar tudo só pra contar, principalmente com milhares
    // de itens em vip_sources.
    async function countRows(table, filter = '') {
      const r = await fetch(`${SUPABASE_URL}/rest/v1/${table}?select=id${filter}`, {
        method: 'GET',
        headers: { ...svcHeaders, Prefer: 'count=exact', Range: '0-0' },
      });
      const range = r.headers.get('content-range'); // formato: "0-0/12345"
      const total = range ? parseInt(range.split('/')[1], 10) : 0;
      return isNaN(total) ? 0 : total;
    }

    try {
      const [
        totalVipSources,
        totalIptvSources,
        activeVipCodes,
        totalUsers,
        totalUnmatched,
        activeAds,
      ] = await Promise.all([
        countRows('vip_sources'),
        countRows('iptv_sources', '&is_active=eq.true'),
        countRows('vip_codes', '&is_active=eq.true'),
        countRows('vip_status'), // usuários VIP registrados
        countRows('iptv_unmatched_items'),
        countRows('vip_ads', '&is_active=eq.true'),
      ]);

      // Breakdown de filmes/séries por servidor/fonte — dá visão rápida
      // de quais fontes têm mais conteúdo, já separando filme de série,
      // já que o auto-sync (iptv-sync) só traz filmes por enquanto e as
      // séries entram majoritariamente por cadastro manual.
      //
      // IMPORTANTE: séries têm uma linha por EPISÓDIO em vip_sources
      // (colunas season/episode), diferente de filme que é 1 linha = 1
      // filme. Por isso "séries com fonte" não pode ser contagem de
      // linhas — precisa contar tmdb_id únicos entre as linhas de série,
      // senão 1 série com 5 episódios cadastrados aparece como "5 séries".
      // A tabela por servidor mostra contagem de episódios mesmo (útil
      // pra ver volume de conteúdo por fonte); só o card do topo usa a
      // contagem de séries distintas.
      //
      // O PostgREST limita respostas a 1000 linhas por padrão. Com ~27 mil
      // registros em vip_sources, uma busca sem paginação trunca
      // silenciosamente nas primeiras ~1000 — por isso paginamos
      // explicitamente até esgotar as linhas (Range em blocos de 1000).
      const bySourceCounts = {}; // label -> { movie, tv } (tv = episódios)
      const seriesIds = new Set(); // tmdb_id únicos de série, pra contagem distinta
      let totalMovies = 0;
      const PAGE_SIZE = 1000;
      let from = 0;
      while (true) {
        const pageRes = await fetch(
          `${SUPABASE_URL}/rest/v1/vip_sources?select=source_label,media_type,tmdb_id`,
          { headers: { ...svcHeaders, Range: `${from}-${from + PAGE_SIZE - 1}` } }
        );
        const pageRows = await pageRes.json();
        if (!Array.isArray(pageRows) || pageRows.length === 0) break;

        for (const row of pageRows) {
          const label = row.source_label || '(sem nome)';
          const isTv = row.media_type === 'tv';
          if (!bySourceCounts[label]) bySourceCounts[label] = { movie: 0, tv: 0 };
          if (isTv) { bySourceCounts[label].tv++; seriesIds.add(row.tmdb_id); }
          else { bySourceCounts[label].movie++; totalMovies++; }
        }

        if (pageRows.length < PAGE_SIZE) break; // última página
        from += PAGE_SIZE;
      }
      const totalSeries = seriesIds.size;
      const topSources = Object.entries(bySourceCounts)
        .map(([label, c]) => ({ label, movie: c.movie, tv: c.tv, count: c.movie + c.tv }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 8);

      res.status(200).json({
        totalVipSources,
        totalIptvSources,
        activeVipCodes,
        totalUsers,
        totalUnmatched,
        activeAds,
        totalMovies,
        totalSeries,
        topSources,
      });
    } catch (err) {
      res.status(502).json({ error: 'Erro ao carregar estatísticas', detail: err.message });
    }
    return;
  }

  if (action === 'list-iptv-sources') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/iptv_sources?select=id,name,xtream_host,xtream_user,priority,is_active,sync_phase,sync_cursor,last_batch_at,last_synced_at,last_sync_stats&order=created_at.desc`,
      { headers: svcHeaders }
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar fontes IPTV', detail: rows }); return; }
    res.status(200).json({ sources: rows });
    return;
  }

  if (action === 'create-iptv-source') {
    const { name, xtreamHost, xtreamUser, xtreamPass, priority } = body;
    if (!name || !xtreamHost || !xtreamUser || !xtreamPass) {
      res.status(400).json({ error: 'Informe name, xtreamHost, xtreamUser e xtreamPass' });
      return;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/iptv_sources`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({
        name, xtream_host: xtreamHost, xtream_user: xtreamUser, xtream_pass: xtreamPass,
        priority: priority ?? 5,
      }),
    });
    const created = await r.json();
    if (!r.ok) {
      // Com o índice único idx_iptv_sources_name_unique (ver
      // sql/corrigir-fonte-duplicada.sql), o Postgres bloqueia nome
      // repetido — aqui traduzimos o erro técnico pra uma mensagem clara.
      const detailStr = JSON.stringify(created);
      if (detailStr.includes('duplicate key') || detailStr.includes('idx_iptv_sources_name_unique')) {
        res.status(409).json({ error: `Já existe uma fonte chamada "${name}". Escolha outro nome ou exclua a antiga primeiro.` });
        return;
      }
      res.status(502).json({ error: 'Erro ao criar fonte IPTV', detail: created });
      return;
    }
    res.status(200).json({ success: true, source: created[0] });
    return;
  }

  if (action === 'toggle-iptv-source') {
    const { sourceId, isActive } = body;
    if (!sourceId) { res.status(400).json({ error: 'Informe sourceId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/iptv_sources?id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'PATCH',
      headers: svcHeaders,
      body: JSON.stringify({ is_active: !!isActive }),
    });
    if (!r.ok) { const detail = await r.text(); res.status(502).json({ error: 'Erro ao atualizar fonte IPTV', detail }); return; }
    res.status(200).json({ success: true });
    return;
  }

  // Exclui a fonte IPTV inteira + TODOS os filmes que vieram dela em
  // vip_sources (identificados pelo source_label = nome da fonte) + os
  // itens não encontrados associados. É a versão "1 clique" do que os
  // comandos SQL manuais fazem em sql/substituir-fonte.sql — remove o
  // "servidor morto" da tela de todo mundo de uma vez.
  if (action === 'delete-iptv-source') {
    const { sourceId } = body;
    if (!sourceId) { res.status(400).json({ error: 'Informe sourceId' }); return; }

    // Busca o nome da fonte primeiro, pra saber qual source_label limpar em vip_sources
    const sourceRes = await fetch(
      `${SUPABASE_URL}/rest/v1/iptv_sources?id=eq.${encodeURIComponent(sourceId)}&select=name`,
      { headers: svcHeaders }
    );
    const sourceRows = await sourceRes.json();
    if (!sourceRes.ok || !sourceRows.length) {
      res.status(404).json({ error: 'Fonte IPTV não encontrada' });
      return;
    }
    const sourceName = sourceRows[0].name;

    // 1) apaga os filmes vindos dessa fonte (isso é o que some com o botão)
    const delMoviesRes = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_sources?source_label=eq.${encodeURIComponent(sourceName)}`,
      { method: 'DELETE', headers: svcHeaders }
    );
    if (!delMoviesRes.ok) {
      const detail = await delMoviesRes.text();
      res.status(502).json({ error: 'Erro ao excluir filmes da fonte', detail });
      return;
    }

    // 2) apaga os itens não encontrados associados (limpeza, não afeta o site)
    await fetch(`${SUPABASE_URL}/rest/v1/iptv_unmatched_items?source_id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'DELETE', headers: svcHeaders,
    });

    // 3) apaga a fonte em si
    const delSourceRes = await fetch(`${SUPABASE_URL}/rest/v1/iptv_sources?id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'DELETE', headers: svcHeaders,
    });
    if (!delSourceRes.ok) {
      const detail = await delSourceRes.text();
      res.status(502).json({ error: 'Fonte parcialmente excluída (filmes já removidos), mas erro ao remover o registro da fonte', detail });
      return;
    }

    res.status(200).json({ success: true, deletedSourceName: sourceName });
    return;
  }

  // ── ANÚNCIOS: lista todos (ativos e inativos) para o painel ──
  if (action === 'list-ads') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_ads?select=*&order=created_at.desc`,
      { headers: svcHeaders }
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar anúncios', detail: rows }); return; }
    res.status(200).json({ ads: rows });
    return;
  }

  // ── ANÚNCIOS: cria novo ──
  if (action === 'create-ad') {
    const { name, ad_type, content_type, content, placement, priority } = body;
    if (!name || !ad_type || !content_type || !content) {
      res.status(400).json({ error: 'Informe name, ad_type, content_type e content.' });
      return;
    }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_ads`, {
      method: 'POST',
      headers: { ...svcHeaders, 'Prefer': 'return=representation' },
      body: JSON.stringify({
        name, ad_type, content_type, content,
        placement: placement || 'watch',
        priority: Number.isFinite(priority) ? priority : 0,
      }),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao criar anúncio', detail: result }); return; }
    res.status(200).json({ created: result });
    return;
  }

  // ── ANÚNCIOS: edita existente ──
  if (action === 'update-ad') {
    const { adId, name, ad_type, content_type, content, placement, priority } = body;
    if (!adId) { res.status(400).json({ error: 'Informe adId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_ads?id=eq.${encodeURIComponent(adId)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, 'Prefer': 'return=representation' },
      body: JSON.stringify({
        name, ad_type, content_type, content, placement, priority,
        updated_at: new Date().toISOString(),
      }),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao atualizar anúncio', detail: result }); return; }
    res.status(200).json({ updated: result });
    return;
  }

  // ── ANÚNCIOS: ativa/desativa sem apagar ──
  if (action === 'toggle-ad') {
    const { adId, isActive } = body;
    if (!adId) { res.status(400).json({ error: 'Informe adId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_ads?id=eq.${encodeURIComponent(adId)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, 'Prefer': 'return=representation' },
      body: JSON.stringify({ is_active: !!isActive, updated_at: new Date().toISOString() }),
    });
    const result = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao atualizar anúncio', detail: result }); return; }
    res.status(200).json({ updated: result });
    return;
  }

  // ── ANÚNCIOS: exclui ──
  if (action === 'delete-ad') {
    const { adId } = body;
    if (!adId) { res.status(400).json({ error: 'Informe adId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/vip_ads?id=eq.${encodeURIComponent(adId)}`, {
      method: 'DELETE',
      headers: svcHeaders,
    });
    if (!r.ok) { const detail = await r.text(); res.status(502).json({ error: 'Erro ao excluir anúncio', detail }); return; }
    res.status(200).json({ success: true });
    return;
  }

  // ════════════════════════════════════════════════════════════
  // EMBEDS/PARCEIROS: controle remoto do player distribuído
  // (/embed) usado por sites de terceiros via iframe. Cada key
  // é validada pelo endpoint público /api/embed-config a cada
  // carregamento do player — desativar aqui derruba o embed em
  // qualquer site que a esteja usando, sem tocar em código instalado.
  // ════════════════════════════════════════════════════════════

  if (action === 'list-embed-partners') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/embed_partners?select=*&order=created_at.desc`,
      { headers: svcHeaders }
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar parceiros de embed', detail: rows }); return; }
    res.status(200).json({ partners: rows });
    return;
  }

  // Agrega os últimos N registros de telemetria por key: % de sessões
  // com adblock, % com sandbox detectado, e breakdown de status de ads.
  // A agregação é feita aqui em JS (não em SQL) porque o volume esperado
  // por parceiro é pequeno o bastante pra não precisar de uma view/RPC
  // no Postgres — mais simples de manter, ajusta fácil se crescer muito.
  if (action === 'embed-telemetry-stats') {
    const { partnerKey, days } = body;
    if (!partnerKey) { res.status(400).json({ error: 'Informe partnerKey' }); return; }

    const sinceDate = new Date(Date.now() - (Number(days) || 7) * 24 * 60 * 60 * 1000).toISOString();
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/embed_telemetry?partner_key=eq.${encodeURIComponent(partnerKey)}&created_at=gte.${encodeURIComponent(sinceDate)}&select=adblock,sandboxed,ad_status&limit=5000`,
      { headers: svcHeaders }
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao buscar telemetria', detail: rows }); return; }

    const total = rows.length;
    const adblockCount  = rows.filter(x => x.adblock === true).length;
    const sandboxCount  = rows.filter(x => x.sandboxed === true).length;
    const adStatusCount = rows.reduce((acc, x) => {
      const k = x.ad_status || 'not_attempted';
      acc[k] = (acc[k] || 0) + 1;
      return acc;
    }, {});

    res.status(200).json({
      total,
      adblockPct: total ? Math.round((adblockCount / total) * 100) : null,
      sandboxPct: total ? Math.round((sandboxCount / total) * 100) : null,
      adStatusCount,
    });
    return;
  }

  if (action === 'create-embed-partner') {
    const { label, domain, adsEnabled, adZoneId, notes } = body;
    if (!label) { res.status(400).json({ error: 'Informe label' }); return; }

    // Gera uma key aleatória e legível (prefixo sfv_ + 16 hex chars).
    const key = 'sfv_' + Array.from({ length: 16 }, () => '0123456789abcdef'[Math.floor(Math.random() * 16)]).join('');

    const r = await fetch(`${SUPABASE_URL}/rest/v1/embed_partners`, {
      method: 'POST',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({
        key,
        label,
        domain: domain || null,
        ads_enabled: adsEnabled !== false,
        ad_zone_id: adZoneId || null,
        notes: notes || null,
      }),
    });
    const created = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao criar parceiro de embed', detail: created }); return; }
    res.status(200).json({ success: true, partner: created[0] });
    return;
  }

  if (action === 'toggle-embed-partner') {
    const { partnerId, active } = body;
    if (!partnerId) { res.status(400).json({ error: 'Informe partnerId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/embed_partners?id=eq.${encodeURIComponent(partnerId)}`, {
      method: 'PATCH',
      headers: svcHeaders,
      body: JSON.stringify({ active: !!active }),
    });
    if (!r.ok) { const detail = await r.text(); res.status(502).json({ error: 'Erro ao atualizar parceiro', detail }); return; }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'toggle-embed-ads') {
    const { partnerId, adsEnabled } = body;
    if (!partnerId) { res.status(400).json({ error: 'Informe partnerId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/embed_partners?id=eq.${encodeURIComponent(partnerId)}`, {
      method: 'PATCH',
      headers: svcHeaders,
      body: JSON.stringify({ ads_enabled: !!adsEnabled }),
    });
    if (!r.ok) { const detail = await r.text(); res.status(502).json({ error: 'Erro ao atualizar anúncios do parceiro', detail }); return; }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'update-embed-partner') {
    const { partnerId, label, domain, adZoneId, notes } = body;
    if (!partnerId) { res.status(400).json({ error: 'Informe partnerId' }); return; }
    const patch = {};
    if (label !== undefined) patch.label = label;
    if (domain !== undefined) patch.domain = domain || null;
    if (adZoneId !== undefined) patch.ad_zone_id = adZoneId || null;
    if (notes !== undefined) patch.notes = notes || null;
    const r = await fetch(`${SUPABASE_URL}/rest/v1/embed_partners?id=eq.${encodeURIComponent(partnerId)}`, {
      method: 'PATCH',
      headers: svcHeaders,
      body: JSON.stringify(patch),
    });
    if (!r.ok) { const detail = await r.text(); res.status(502).json({ error: 'Erro ao editar parceiro', detail }); return; }
    res.status(200).json({ success: true });
    return;
  }

  if (action === 'delete-embed-partner') {
    const { partnerId } = body;
    if (!partnerId) { res.status(400).json({ error: 'Informe partnerId' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/embed_partners?id=eq.${encodeURIComponent(partnerId)}`, {
      method: 'DELETE',
      headers: svcHeaders,
    });
    if (!r.ok) { const detail = await r.text(); res.status(502).json({ error: 'Erro ao excluir parceiro', detail }); return; }
    res.status(200).json({ success: true });
    return;
  }

  res.status(400).json({ error: 'Ação inválida' });
};
