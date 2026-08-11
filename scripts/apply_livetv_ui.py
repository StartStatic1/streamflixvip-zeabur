#!/usr/bin/env python3
"""Patch admin.html + admin-vip.js for TV ao vivo tab."""
from pathlib import Path

# admin-vip
av = Path("api/admin-vip.js")
s = av.read_text()
if "update-live-tv-source" not in s:
    end = s.find("\n  if (action ===", s.find("delete-live-tv-source") + 10)
    if end < 0:
        raise SystemExit("delete-live-tv-source block not found")
    block = """
  if (action === 'update-live-tv-source') {
    const { sourceId } = body;
    if (!sourceId) { res.status(400).json({ error: 'Informe sourceId' }); return; }
    const patch = {};
    if (body.name != null && String(body.name).trim()) patch.name = String(body.name).trim();
    if (body.xtreamHost != null || body.xtream_host != null) {
      const h = String(body.xtreamHost || body.xtream_host || '').trim().replace(/\\/+$/, '');
      if (h) patch.xtream_host = h;
    }
    if (body.xtreamUser != null || body.xtream_user != null) {
      const u = String(body.xtreamUser || body.xtream_user || '').trim();
      if (u) patch.xtream_user = u;
    }
    if (body.xtreamPass != null || body.xtream_pass != null) {
      const p = String(body.xtreamPass || body.xtream_pass || '').trim();
      if (p) patch.xtream_pass = p;
    }
    if (body.priority != null) patch.priority = Number(body.priority) || 10;
    if (!Object.keys(patch).length) { res.status(400).json({ error: 'Nada para atualizar' }); return; }
    const r = await fetch(`${SUPABASE_URL}/rest/v1/live_tv_sources?id=eq.${encodeURIComponent(sourceId)}`, {
      method: 'PATCH', headers: svcHeaders, body: JSON.stringify(patch),
    });
    if (!r.ok) { res.status(502).json({ error: 'Erro atualizar live TV', detail: await r.text() }); return; }
    res.status(200).json({ success: true });
    return;
  }
"""
    av.write_text(s[:end] + block + s[end:])
    print("admin-vip: update-live-tv-source added")
else:
    print("admin-vip: update already present")

t = Path("Public/admin.html").read_text()
if 'data-tab="livetv"' in t and "loadLiveTvSources" in t:
    print("admin already patched")
    raise SystemExit(0)

t = t.replace(
    """    <button class=\"panel-tab\" data-tab=\"embeds\" onclick=\"switchTab('embeds')\">🔗 Embeds</button>""",
    """    <button class=\"panel-tab\" data-tab=\"livetv\" onclick=\"switchTab('livetv')\">📺 TV ao vivo</button>""",
    1,
)

SECTION = """
    <section id=\"tab-livetv\" class=\"tab-panel\" style=\"display:none\">
      <div class=\"card\">
        <div class=\"card-title\">📺 Nova fonte de TV ao vivo</div>
        <p style=\"color:var(--muted);font-size:0.82rem;margin:-4px 0 16px\">
          Só canais ao vivo (Xtream). Até <b>5 fontes ativas</b> por prioridade (menor = primeiro), com fallback no player.
        </p>
        <label>Nome</label>
        <input type=\"text\" id=\"liveTvNameInput\" placeholder=\"ex: StreamFlix.Svent\"/>
        <label style=\"margin-top:14px\">Host</label>
        <input type=\"text\" id=\"liveTvHostInput\" placeholder=\"ex: http://sventank.com\"/>
        <div style=\"display:flex;gap:10px;margin-top:14px\">
          <div style=\"flex:1\"><label>Usuário</label><input type=\"text\" id=\"liveTvUserInput\"/></div>
          <div style=\"flex:1\"><label>Senha</label><input type=\"text\" id=\"liveTvPassInput\"/></div>
        </div>
        <label style=\"margin-top:14px\">Prioridade (menor = primeiro)</label>
        <input type=\"number\" id=\"liveTvPriorityInput\" value=\"10\"/>
        <button class=\"btn-primary\" style=\"margin-top:16px\" id=\"saveLiveTvBtn\" onclick=\"saveLiveTvSource()\">Salvar fonte de TV</button>
      </div>
      <div class=\"card\" style=\"margin-top:24px\">
        <div class=\"card-title\">📋 Fontes de TV</div>
        <div class=\"table-wrap\" id=\"liveTvListWrap\"></div>
      </div>
    </section>
"""

sec_start = t.find('    <section id="tab-embeds"')
if sec_start >= 0:
    sec_end = t.find('    </section>', sec_start)
    sec_end = t.find('\n', sec_end) + 1
    t = t[:sec_start] + SECTION + t[sec_end:]
    print("HTML: embeds section replaced")
else:
    print("WARN: tab-embeds not found")

t = t.replace("if (tab === 'embeds') loadEmbedPartners();", "if (tab === 'livetv') loadLiveTvSources();")

JS = r"""
  let _allLiveTvSources = [];
  async function loadLiveTvSources() {
    const data = await api('list-live-tv-sources');
    if (data.error) { showToast('❌ ' + (data.error.message || data.error)); return; }
    _allLiveTvSources = data.sources || [];
    renderLiveTvList();
  }
  function renderLiveTvList() {
    const wrap = document.getElementById('liveTvListWrap');
    if (!_allLiveTvSources.length) {
      wrap.innerHTML = '<p style="color:var(--muted);padding:20px;text-align:center">Nenhuma fonte de TV. Cadastre acima.</p>';
      return;
    }
    wrap.innerHTML = `<table><thead><tr><th>Nome</th><th>Host</th><th>User</th><th>Pri</th><th>Status</th><th></th></tr></thead><tbody>
      ${_allLiveTvSources.map(s => `<tr>
        <td>${escapeHtml(s.name)}</td>
        <td style="font-size:0.75rem;color:var(--muted)">${escapeHtml(s.xtream_host||'')}</td>
        <td style="font-size:0.75rem;color:var(--muted)">${escapeHtml(s.xtream_user||'')}</td>
        <td>${s.priority??'—'}</td>
        <td><span class="pill ${s.is_active?'active':'inactive'}">${s.is_active?'Ativa':'Pausada'}</span></td>
        <td style="white-space:nowrap">
          <button class="btn-secondary" style="width:auto;padding:6px 10px;font-size:0.75rem" onclick="editLiveTvSource('${s.id}')">✏️</button>
          <button class="btn-secondary" style="width:auto;padding:6px 10px;font-size:0.75rem" onclick="toggleLiveTvSource('${s.id}', ${!s.is_active})">${s.is_active?'⏸️':'▶️'}</button>
          <button class="btn-secondary" style="width:auto;padding:6px 10px;font-size:0.75rem" onclick="deleteLiveTvSource('${s.id}', '${escapeHtml(s.name).replace(/'/g,"\\'")}')">🗑️</button>
        </td></tr>`).join('')}
    </tbody></table>`;
  }
  async function saveLiveTvSource() {
    const name = document.getElementById('liveTvNameInput').value.trim();
    const xtreamHost = document.getElementById('liveTvHostInput').value.trim().replace(/\/+$/, '');
    const xtreamUser = document.getElementById('liveTvUserInput').value.trim();
    const xtreamPass = document.getElementById('liveTvPassInput').value.trim();
    const priority = parseInt(document.getElementById('liveTvPriorityInput').value, 10) || 10;
    const btn = document.getElementById('saveLiveTvBtn');
    const editingId = btn.dataset.editingId || '';
    if (!name || !xtreamHost || !xtreamUser) { showToast('Preencha nome, host e usuário.'); return; }
    if (!editingId && !xtreamPass) { showToast('Preencha a senha.'); return; }
    if (!/^https?:\/\//i.test(xtreamHost)) { showToast('Host precisa de http:// ou https://'); return; }
    btn.disabled = true; btn.textContent = 'Salvando...';
    let result;
    if (editingId) {
      const payload = { sourceId: editingId, name, xtreamHost, xtreamUser, priority };
      if (xtreamPass) payload.xtreamPass = xtreamPass;
      result = await api('update-live-tv-source', payload);
    } else {
      result = await api('create-live-tv-source', { name, xtreamHost, xtreamUser, xtreamPass, priority });
    }
    btn.disabled = false; btn.textContent = 'Salvar fonte de TV'; delete btn.dataset.editingId;
    if (result.error) { showToast('❌ ' + (result.error.message || result.error)); return; }
    showToast(editingId ? '✅ Atualizado!' : '✅ Fonte de TV salva!');
    ['liveTvNameInput','liveTvHostInput','liveTvUserInput','liveTvPassInput'].forEach(id => document.getElementById(id).value = '');
    document.getElementById('liveTvPriorityInput').value = '10';
    loadLiveTvSources();
  }
  function editLiveTvSource(id) {
    const s = _allLiveTvSources.find(x => x.id === id); if (!s) return;
    document.getElementById('liveTvNameInput').value = s.name || '';
    document.getElementById('liveTvHostInput').value = s.xtream_host || '';
    document.getElementById('liveTvUserInput').value = s.xtream_user || '';
    document.getElementById('liveTvPassInput').value = '';
    document.getElementById('liveTvPassInput').placeholder = 'em branco = manter senha';
    document.getElementById('liveTvPriorityInput').value = s.priority ?? 10;
    const btn = document.getElementById('saveLiveTvBtn');
    btn.dataset.editingId = id; btn.textContent = 'Salvar alterações';
    showToast('✏️ Editando — senha só se for trocar');
  }
  async function toggleLiveTvSource(id, st) {
    const r = await api('toggle-live-tv-source', { sourceId: id, isActive: st });
    if (r.error) { showToast('❌ ' + r.error); return; }
    showToast(st ? '▶️ Ativa' : '⏸️ Pausada'); loadLiveTvSources();
  }
  async function deleteLiveTvSource(id, name) {
    if (!confirm('Excluir fonte de TV "' + name + '"?')) return;
    const r = await api('delete-live-tv-source', { sourceId: id });
    if (r.error) { showToast('❌ ' + r.error); return; }
    showToast('🗑️ Removida'); loadLiveTvSources();
  }

"""

start = t.find('  // ════════════════════════════════════════════════════════════\n  // ABA: EMBEDS')
dash = t.find('  // ════════════════════════════════════════════════════════════\n  // ABA: DASHBOARD')
if start >= 0 and dash > start:
    t = t[:start] + JS + t[dash:]
    print("JS: embeds block replaced")
elif "function loadLiveTvSources" not in t:
    if dash > 0:
        t = t[:dash] + JS + t[dash:]
        print("JS: inserted before dashboard")
    else:
        raise SystemExit("Could not find insertion point for JS")

Path("Public/admin.html").write_text(t)
final = Path("Public/admin.html").read_text()
assert "tab-livetv" in final and "loadLiveTvSources" in final
assert 'data-tab="embeds"' not in final
print("SANITY OK", Path("Public/admin.html").stat().st_size)
