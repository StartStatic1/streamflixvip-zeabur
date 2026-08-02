it fetch(`${SUPABASE_URL}/rest/v1/vip_codes?code=eq.${encodeURIComponent(code)}`, {
      method: 'PATCH',
      headers: { ...svcHeaders, Prefer: 'return=representation' },
      body: JSON.stringify({ is_active: action === 'reactivate' }),
    });
    res.status(200).json({ updated: await r.json() });
    return;
  }

  if (action === 'list-users') {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_status?select=user_id,email,name,first_login_at,last_login_at,last_seen_at,expires_at,plan_label,last_code_used&order=last_login_at.desc&limit=500`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro ao listar usuarios', detail: rows }); return; }
    res.status(200).json({ users: rows });
    return;
  }

  if (action === 'activations') {
    const [statusRes, codesRes, tvRes] = await Promise.all([
      fetch(`${SUPABASE_URL}/rest/v1/vip_status?select=user_id,email,expires_at,plan_label,last_code_used&order=expires_at.desc&limit=500`, { headers: svcHeaders }),
      fetch(`${SUPABASE_URL}/rest/v1/vip_codes?select=code,used_by,plan_label,is_active`, { headers: svcHeaders }),
      fetch(`${SUPABASE_URL}/rest/v1/tv_activations?select=code,device_id,device_label,expires_at,is_active`, { headers: svcHeaders }),
    ]);
    const statusRows = await statusRes.json();
    const codesRows = await codesRes.json();
    const tvRows = await tvRes.json();
    if (!statusRes.ok) { res.status(502).json({ error: 'Erro vip_status', detail: statusRows }); return; }
    const codesByUser = new Map();
    if (Array.isArray(codesRows)) {
      for (const c of codesRows) if (c.used_by) codesByUser.set(c.used_by, c.code);
    }
    const tvByCode = new Map();
    if (Array.isArray(tvRows)) {
      for (const t of tvRows) {
        if (!t.is_active) continue;
        if (!tvByCode.has(t.code)) tvByCode.set(t.code, []);
        tvByCode.get(t.code).push({ deviceId: t.device_id, deviceLabel: t.device_label, expiresAt: t.expires_at });
      }
    }
    const now = new Date();
    const activations = (Array.isArray(statusRows) ? statusRows : []).map((u) => {
      let origem = 'Desconhecida';
      let codigoRelacionado = null;
      if (u.last_code_used && String(u.last_code_used).startsWith('PIX-MP-')) origem = 'Pagamento PIX automatico';
      else if (u.last_code_used) { origem = 'Codigo: ' + u.last_code_used; codigoRelacionado = u.last_code_used; }
      else if (codesByUser.has(u.user_id)) {
        codigoRelacionado = codesByUser.get(u.user_id);
        origem = 'Codigo: ' + codigoRelacionado;
      }
      const tvs = codigoRelacionado ? (tvByCode.get(codigoRelacionado) || []) : [];
      return {
        email: u.email || '(sem e-mail)',
        userId: u.user_id,
        vipAtivo: u.expires_at ? new Date(u.expires_at) > now : false,
        expiresAt: u.expires_at,
        planLabel: u.plan_label,
        origem,
        codigoRelacionado,
        tvsAtivas: tvs.length,
        tvs,
      };
    });
    res.status(200).json({ activations });
    return;
  }

  if (action === 'list-redemptions') {
    const { userId: uid } = body;
    if (!uid) { res.status(400).json({ error: 'Informe userId' }); return; }
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_redemptions?user_id=eq.${encodeURIComponent(uid)}&select=*&order=redeemed_at.desc`,
      { headers: svcHeaders },
    );
    const rows = await r.json();
    if (!r.ok) { res.status(502).json({ error: 'Erro historico', detail: rows }); return; }
    res.status(200).json({ users: rows });
    return;
  }

  if (action === 'list-sources-for') {
    const { mediaType, ids } = body;
    if (!mediaType || !Array.isArray(ids) || ids.length === 0) {
      res.status(200).json({ ids: [] });
      return;
    }
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_sources?media_type=eq.${encodeURIComponent(mediaType)}&tmdb_id=in.(${ids.join(',')})&select=tmdb_id`,
      { headers: svcHeaders },
