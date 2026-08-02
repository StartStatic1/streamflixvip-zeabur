// Public/admin-announcements.js — Avisos do app mobile (Perfil)
(function () {
  function escapeHtml(str) {
    return String(str || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  async function loadAnnouncements() {
    const wrap = document.getElementById('announcementsListWrap');
    if (!wrap) return;
    wrap.innerHTML = '<p style="color:var(--muted);font-size:0.85rem">Carregando…</p>';
    try {
      const data = await api('list-announcements');
      const list = data.announcements || [];
      if (!list.length) {
        wrap.innerHTML = '<p style="color:var(--muted);font-size:0.85rem;padding:16px 0;text-align:center">Nenhum aviso ainda. Publique o primeiro acima.</p>';
        return;
      }
      const typeLabel = { info: 'Aviso', movie: 'Filme novo', maintenance: 'Manutenção', promo: 'Promoção' };
      wrap.innerHTML = list.map((a) => {
        const when = a.created_at ? new Date(a.created_at).toLocaleString('pt-BR') : '—';
        const badge = a.active
          ? '<span class="pill active">Ativo</span>'
          : '<span class="pill inactive">Inativo</span>';
        const link = a.link_tmdb_id
          ? `<div style="font-size:0.75rem;color:var(--muted);margin-top:4px">Link: ${a.link_media_type || '?'} #${a.link_tmdb_id}</div>`
          : '';
        return `<div class="source-row" style="flex-direction:column;align-items:stretch;gap:8px;margin-bottom:10px">
          <div style="display:flex;justify-content:space-between;gap:10px;align-items:flex-start">
            <div>
              <div style="font-weight:600">${escapeHtml(a.title || '')} <span style="font-size:0.75rem;color:var(--muted);font-weight:400">${typeLabel[a.type] || a.type || ''}</span></div>
              <div style="font-size:0.85rem;color:var(--muted);margin-top:4px;white-space:pre-wrap">${escapeHtml(a.body || '')}</div>
              ${link}
              <div style="font-size:0.72rem;color:var(--muted);margin-top:6px">${when}</div>
            </div>
            ${badge}
          </div>
          <div style="display:flex;gap:8px;flex-wrap:wrap">
            <button class="btn-secondary" style="width:auto;padding:6px 12px;font-size:0.8rem" onclick="toggleAnnouncement('${a.id}', ${!a.active})">${a.active ? 'Desativar' : 'Ativar'}</button>
            <button class="btn-secondary" style="width:auto;padding:6px 12px;font-size:0.8rem;color:#f87171" onclick="deleteAnnouncement('${a.id}')">Excluir</button>
          </div>
        </div>`;
      }).join('');
    } catch (e) {
      wrap.innerHTML = `<p style="color:#f87171">Erro: ${escapeHtml(e.message || String(e))}</p>`;
    }
  }

  async function createAnnouncement() {
    const title = (document.getElementById('annTitle').value || '').trim();
    const body = (document.getElementById('annBody').value || '').trim();
    const type = document.getElementById('annType').value || 'info';
    const tmdb = document.getElementById('annTmdb').value;
    const media = document.getElementById('annMedia').value || '';
    if (!title || !body) { showToast('Preencha título e texto'); return; }
    try {
      await api('create-announcement', {
        title, body, type,
        link_tmdb_id: tmdb ? Number(tmdb) : null,
        link_media_type: media || null,
        active: true,
      });
      document.getElementById('annTitle').value = '';
      document.getElementById('annBody').value = '';
      document.getElementById('annTmdb').value = '';
      document.getElementById('annMedia').value = '';
      showToast('✅ Aviso publicado');
      loadAnnouncements();
    } catch (e) {
      showToast('Erro: ' + (e.message || e));
    }
  }

  async function toggleAnnouncement(id, active) {
    try {
      await api('toggle-announcement', { id, active: !!active });
      showToast(active ? 'Aviso ativado' : 'Aviso desativado');
      loadAnnouncements();
    } catch (e) {
      showToast('Erro: ' + (e.message || e));
    }
  }

  async function deleteAnnouncement(id) {
    if (!confirm('Excluir este aviso permanentemente?')) return;
    try {
      await api('delete-announcement', { id });
      showToast('Aviso excluído');
      loadAnnouncements();
    } catch (e) {
      showToast('Erro: ' + (e.message || e));
    }
  }

  window.loadAnnouncements = loadAnnouncements;
  window.createAnnouncement = createAnnouncement;
  window.toggleAnnouncement = toggleAnnouncement;
  window.deleteAnnouncement = deleteAnnouncement;

  // Integra com switchTab se existir
  const _origSwitch = window.switchTab;
  if (typeof _origSwitch === 'function') {
    window.switchTab = function (tab) {
      _origSwitch(tab);
      if (tab === 'announcements') loadAnnouncements();
    };
  }
})();
