#!/usr/bin/env python3
"""Onda 1: sessao com refresh nos paineis + grid/salvar card no FlixHub."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]


def patch(rel, pairs):
    p = root / rel
    if not p.exists():
        print("aviso falta", rel)
        return
    t = p.read_text()
    n = 0
    for old, new in pairs:
        if old in t:
            t = t.replace(old, new, 1)
            n += 1
        else:
            print("aviso nao achou bloco em", rel)
    p.write_text(t)
    print("ok", rel, n, "blocos", p.stat().st_size)


p = root / "Public/admin-flixhub.html"
if p.exists():
    t = p.read_text()
    old_css = "    label.chk { display:inline-flex; align-items:center; gap:6px; margin-right:12px; font-size:.85rem; color:var(--muted); }"
    new_css = old_css + "\n    .cat-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(200px,1fr)); gap:4px 10px; max-height:280px; overflow:auto; padding-right:4px; }\n    .cat-grid .chk { margin:0; align-items:flex-start; }"
    if "cat-grid" not in t and old_css in t:
        t = t.replace(old_css, new_css, 1)
        print("ok flixhub css")
    t = t.replace('style="max-height:220px;overflow:auto;padding-right:4px"', 'class="cat-grid"')
    t = t.replace('style="max-height:180px;overflow:auto;padding-right:4px"', 'class="cat-grid"')
    if "function persistPack" not in t:
        old_apply = """function applyCats(i) {
  const s = _servers[i];
  if (!s) return;
  saveDraft();
  const box = document.getElementById('catBox' + i);
  if (box) box.outerHTML = catSummary(s, i);
  toast('Pastas do servidor ' + (i + 1) + ' no formulario. Clique Salvar do pack.');
}"""
        new_apply = """async function persistPack(keep) {
  saveDraft();
  const missing = _servers.filter((s) => !s.host || !s.user || !s.pass);
  if (missing.length) { toast('Algum servidor sem host, usuario ou senha'); return false; }
  if (!_servers.length) { toast('Adicione pelo menos 1 servidor'); return false; }
  toast('Salvando…');
  const d = await api('save', {
    id: _editId || undefined,
    name: document.getElementById('pName').value.trim() || 'FlixHub',
    servers: _servers,
    public_enabled: !!document.getElementById('pPublic').checked,
  });
  if (d.error) { toast(typeof d.error === 'string' ? d.error : (d.hint || JSON.stringify(d.error))); return false; }
  if (d.pack && d.pack.id) _editId = d.pack.id;
  if (!keep) {
    clearDraft();
    toast('Salvo');
    if (d.manifest_url) { try { await navigator.clipboard.writeText(d.manifest_url); } catch (_) {}
    }
    _editId = null;
    document.getElementById('formTitle').textContent = 'Novo pack';
    document.getElementById('btnSave').textContent = 'Salvar e gerar';
    document.getElementById('btnCancel').style.display = 'none';
    document.getElementById('pPublic').checked = false;
    _servers = [];
    renderSrvs();
  } else {
    clearDraft();
    toast('Servidor gravado no pack');
    document.getElementById('formTitle').textContent = 'Editar pack';
    document.getElementById('btnSave').textContent = 'Salvar alteracoes';
    document.getElementById('btnCancel').style.display = 'inline-block';
  }
  loadList();
  return true;
}
async function applyCats(i) {
  const s = _servers[i];
  if (!s) return;
  saveDraft();
  const box = document.getElementById('catBox' + i);
  if (box) box.outerHTML = catSummary(s, i);
  if (_editId) await persistPack(true);
  else toast('Pastas no formulario. Clique Salvar e gerar.');
}
async function saveCard(i) {
  saveDraft();
  if (!_editId) { toast('Crie o pack uma vez com Salvar e gerar. Depois o card grava sozinho.'); return; }
  await persistPack(true);
}"""
        if old_apply in t:
            t = t.replace(old_apply, new_apply, 1)
            print("ok flixhub persistPack")
        else:
            print("aviso applyCats")
    if "async function save() {\n  await persistPack(false);\n}" not in t:
        import re
        t2, n = re.subn(
            r"async function save\(\) \{[\s\S]*?loadList\(\);\n\}",
            "async function save() {\n  await persistPack(false);\n}",
            t,
            count=1,
        )
        if n:
            t = t2
            print("ok flixhub save")
    if "Salvar card" not in t:
        needle = "'<button class=\"btn-sec\" style=\"padding:6px 10px;font-size:.75rem\" onclick=\"loadCats(' + i + ')\">Pastas</button>' +"
        ins = needle + "\n      '<button class=\"btn-gold\" style=\"padding:6px 10px;font-size:.75rem\" onclick=\"saveCard(' + i + ')\">Salvar card</button>' +"
        if needle in t:
            t = t.replace(needle, ins, 1)
            print("ok flixhub botao card")
    t = t.replace(
        "Marque o que quiser. Nenhuma = todas. Depois Salvar do pack.",
        "Marque o que quiser. Nenhuma = todas. Salvar pastas ja grava o pack se estiver editando.",
    )
    p.write_text(t)
    print("fim flixhub html", p.stat().st_size)
else:
    print("aviso sem admin-flixhub.html")

patch(
    "Public/admin-addons.html",
    [
        (
            "function api(action,extra={}){const res=await fetch('/api/admin-addons',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+_session.access_token},body:JSON.stringify({action,...extra})});const text=await res.text();try{return JSON.parse(text)}catch(e){return{error:text||('HTTP '+res.status)}}}",
            "async function ensureSession(){try{const {data}=await db.auth.getSession();if(data&&data.session){_session=data.session;return _session}}catch(_){}try{const {data,error}=await db.auth.refreshSession();if(!error&&data&&data.session){_session=data.session;return _session}}catch(_){}return null}\n"
            "async function api(action,extra={}){let sess=await ensureSession();if(!sess||!sess.access_token)return{error:'Sessao invalida — saia e entre de novo'};let res=await fetch('/api/admin-addons',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+sess.access_token},body:JSON.stringify({action,...extra})});if(res.status===401){sess=await ensureSession();if(!sess)return{error:'Sessao invalida — saia e entre de novo'};res=await fetch('/api/admin-addons',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+sess.access_token},body:JSON.stringify({action,...extra})})}const text=await res.text();try{return JSON.parse(text)}catch(e){return{error:text||('HTTP '+res.status)}}}",
        )
    ],
)

patch(
    "Public/admin-bridge.html",
    [
        (
            """function api(action, extra) {
  const res = await fetch('/api/admin-bridge', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + _session.access_token },
    body: JSON.stringify(Object.assign({ action: action }, extra || {})),
  });
  const t = await res.text();
  try { return JSON.parse(t); } catch (e) { return { error: t || ('HTTP ' + res.status) }; }
}""",
            """async function ensureSession() {
  try {
    const { data } = await db.auth.getSession();
    if (data && data.session) { _session = data.session; return _session; }
  } catch (_) {}
  try {
    const { data, error } = await db.auth.refreshSession();
    if (!error && data && data.session) { _session = data.session; return _session; }
  } catch (_) {}
  return null;
}
async function api(action, extra) {
  let sess = await ensureSession();
  if (!sess || !sess.access_token) return { error: 'Sessao invalida — saia e entre de novo' };
  let res = await fetch('/api/admin-bridge', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + sess.access_token },
    body: JSON.stringify(Object.assign({ action: action }, extra || {})),
  });
  if (res.status === 401) {
    sess = await ensureSession();
    if (!sess) return { error: 'Sessao invalida — saia e entre de novo' };
    res = await fetch('/api/admin-bridge', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + sess.access_token },
      body: JSON.stringify(Object.assign({ action: action }, extra || {})),
    });
  }
  const txt = await res.text();
  try { return JSON.parse(txt); } catch (e) { return { error: txt || ('HTTP ' + res.status) }; }
}""",
        )
    ],
)

patch(
    "Public/admin-sources.html",
    [
        (
            "function api(url,body){const r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+session.access_token},body:JSON.stringify(body)});const t=await r.text();try{return JSON.parse(t)}catch{return{error:t||('HTTP '+r.status)}}}",
            "async function ensureSession(){try{const {data}=await db.auth.getSession();if(data&&data.session){session=data.session;return session}}catch(_){}try{const {data,error}=await db.auth.refreshSession();if(!error&&data&&data.session){session=data.session;return session}}catch(_){}return null}"
            "async function api(url,body){let sess=await ensureSession();if(!sess||!sess.access_token)return{error:'Sessao invalida — saia e entre de novo'};let r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+sess.access_token},body:JSON.stringify(body)});if(r.status===401){sess=await ensureSession();if(!sess)return{error:'Sessao invalida — saia e entre de novo'};r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+sess.access_token},body:JSON.stringify(body)})}const t=await r.text();try{return JSON.parse(t)}catch{return{error:t||('HTTP '+r.status)}}}",
        )
    ],
)

patch(
    "Public/admin.html",
    [
        (
            """  async function api(action, extra = {}) {
    const token = _session?.access_token;
    const res = await fetch('/api/admin-vip', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ action, ...extra })
    });
    return res.json();
  }""",
            """  async function ensureSession() {
    try {
      const { data } = await db.auth.getSession();
      if (data && data.session) { _session = data.session; return _session; }
    } catch (_) {}
    try {
      const { data, error } = await db.auth.refreshSession();
      if (!error && data && data.session) { _session = data.session; return _session; }
    } catch (_) {}
    return null;
  }
  async function api(action, extra = {}) {
    let sess = await ensureSession();
    if (!sess || !sess.access_token) return { error: 'Sessao invalida — saia e entre de novo' };
    let res = await fetch('/api/admin-vip', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + sess.access_token
      },
      body: JSON.stringify({ action, ...extra })
    });
    if (res.status === 401) {
      sess = await ensureSession();
      if (!sess) return { error: 'Sessao invalida — saia e entre de novo' };
      res = await fetch('/api/admin-vip', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + sess.access_token
        },
        body: JSON.stringify({ action, ...extra })
      });
    }
    return res.json().catch(() => ({ error: 'HTTP ' + res.status }));
  }""",
        )
    ],
)

print("fim apply_wave1_panels")
