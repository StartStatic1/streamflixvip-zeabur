#!/usr/bin/env python3
"""Painel: checkbox Area Free + admin-vip is_free."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

# admin-vip.js
av = ROOT / 'api/admin-vip.js'
if av.exists():
    t = av.read_text(encoding='utf-8')
    t = t.replace(
        'select=tmdb_id,media_type,vip_lock,vip_free_episode_limit&limit=1',
        'select=tmdb_id,media_type,vip_lock,vip_free_episode_limit,is_free&limit=1',
    )
    t = t.replace(
        'select=tmdb_id,media_type,vip_lock,vip_free_episode_limit&limit=5000',
        'select=tmdb_id,media_type,vip_lock,vip_free_episode_limit,is_free&limit=5000',
    )
    t = t.replace(
        '? { vip_lock: !!row.vip_lock, vip_free_episode_limit: row.vip_free_episode_limit ?? null }\n        : { vip_lock: false, vip_free_episode_limit: null },',
        '? { vip_lock: !!row.vip_lock, vip_free_episode_limit: row.vip_free_episode_limit ?? null, is_free: !!row.is_free }\n        : { vip_lock: false, vip_free_episode_limit: null, is_free: false },',
    )
    old_set = '''    const vipLock = !!body.vip_lock;
    let freeLimit = body.vip_free_episode_limit;
    if (freeLimit === '' || freeLimit === undefined) freeLimit = null;
    if (freeLimit != null) freeLimit = parseInt(freeLimit, 10);
    if (freeLimit != null && (isNaN(freeLimit) || freeLimit < 1)) freeLimit = null;
    if (vipLock) freeLimit = null;
    if (tmdbId == null) { res.status(400).json({ error: 'Informe tmdb_id' }); return; }

    const payload = {
      tmdb_id: Number(tmdbId),
      media_type: mediaType,
      vip_lock: vipLock,
      vip_free_episode_limit: freeLimit,
    };'''
    new_set = '''    let vipLock = !!body.vip_lock;
    let isFree = !!body.is_free;
    let freeLimit = body.vip_free_episode_limit;
    if (freeLimit === '' || freeLimit === undefined) freeLimit = null;
    if (freeLimit != null) freeLimit = parseInt(freeLimit, 10);
    if (freeLimit != null && (isNaN(freeLimit) || freeLimit < 1)) freeLimit = null;
    if (isFree) { vipLock = false; freeLimit = null; }
    if (vipLock) { freeLimit = null; isFree = false; }
    if (tmdbId == null) { res.status(400).json({ error: 'Informe tmdb_id' }); return; }

    const payload = {
      tmdb_id: Number(tmdbId),
      media_type: mediaType,
      vip_lock: vipLock,
      vip_free_episode_limit: freeLimit,
      is_free: isFree,
    };'''
    if old_set in t:
        t = t.replace(old_set, new_set)
        print('admin-vip set is_free')
    elif 'is_free: isFree' in t:
        print('admin-vip already')
    else:
        print('aviso admin-vip set block')
    av.write_text(t, encoding='utf-8')

# admin.html
ad = ROOT / 'Public/admin.html'
if ad.exists():
    t = ad.read_text(encoding='utf-8')
    if 'vipTitleIsFreeInput' not in t:
        old_html = '''          <input type="checkbox" id="vipTitleLockInput" style="width:auto;margin:0" onchange="onVipTitleLockChange()"/>
          <span>🔒 Exclusivo VIP (bloqueia este título inteiro pra quem não é VIP)</span>
        </label>
        <div id="vipTitleFreeLimitField"'''
        new_html = '''          <input type="checkbox" id="vipTitleLockInput" style="width:auto;margin:0" onchange="onVipTitleLockChange()"/>
          <span>🔒 Exclusivo VIP (bloqueia este título inteiro pra quem não é VIP)</span>
        </label>
        <label style="display:flex;align-items:center;gap:8px;cursor:pointer;margin:10px 0 0 0">
          <input type="checkbox" id="vipTitleIsFreeInput" style="width:auto;margin:0" onchange="onVipTitleIsFreeChange()"/>
          <span>🎁 Area Free (só filme) — free assiste; não precisa VIP</span>
        </label>
        <div id="vipTitleFreeLimitField"'''
        if old_html in t:
            t = t.replace(old_html, new_html, 1)
            print('admin.html checkbox')
        else:
            print('aviso admin.html checkbox block')
    if 'vipTitleIsFreeInput' in t and 'config.is_free' not in t:
        t = t.replace(
            "const config = result.config || { vip_lock: false, vip_free_episode_limit: null };\n    document.getElementById('vipTitleLockInput').checked = !!config.vip_lock;",
            "const config = result.config || { vip_lock: false, vip_free_episode_limit: null, is_free: false };\n    document.getElementById('vipTitleLockInput').checked = !!config.vip_lock;\n    const freeEl = document.getElementById('vipTitleIsFreeInput');\n    if (freeEl) freeEl.checked = !!config.is_free;",
        )
        t = t.replace(
            'vip_lock: vipLock,\n        vip_free_episode_limit: freeLimit,',
            "vip_lock: vipLock,\n        vip_free_episode_limit: freeLimit,\n        is_free: !!(document.getElementById('vipTitleIsFreeInput') && document.getElementById('vipTitleIsFreeInput').checked),",
        )
        old_js = '''function onVipTitleLockChange() {
    // Exclusivo VIP total e limite parcial de episódios são mutuamente
    // exclusivos — marcar um esconde o outro (não apaga, só oculta).
    const locked = document.getElementById('vipTitleLockInput').checked;
    document.getElementById('vipTitleFreeLimitField').style.display = locked ? 'none' : 'block';
  }'''
        new_js = '''function onVipTitleLockChange() {
    const locked = document.getElementById('vipTitleLockInput').checked;
    if (locked) {
      const freeEl = document.getElementById('vipTitleIsFreeInput');
      if (freeEl) freeEl.checked = false;
    }
    document.getElementById('vipTitleFreeLimitField').style.display = locked ? 'none' : 'block';
  }
  function onVipTitleIsFreeChange() {
    const freeEl = document.getElementById('vipTitleIsFreeInput');
    if (freeEl && freeEl.checked) {
      document.getElementById('vipTitleLockInput').checked = false;
      document.getElementById('vipTitleFreeLimitField').style.display = 'block';
    }
  }'''
        if old_js in t:
            t = t.replace(old_js, new_js, 1)
            print('admin.html js')
        ad.write_text(t, encoding='utf-8')
    else:
        ad.write_text(t, encoding='utf-8')
        print('admin.html skip or partial')

print('done patch_admin_is_free')
