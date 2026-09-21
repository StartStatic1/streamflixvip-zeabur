#!/usr/bin/env python3
"""Liga TorBox no server, addons torrent e painel Central de fontes."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def once(path: Path, old: str, new: str, label: str):
    t = path.read_text()
    if new.strip() in t and old not in t:
        print('ok ja tem', label)
        return
    if old not in t:
        print('trecho nao achado', label)
        return
    path.write_text(t.replace(old, new, 1))
    print('ok', label)


def patch_server():
    p = ROOT / 'server.js'
    t = p.read_text()
    if 'admin-torbox' in t:
        print('ok server.js ja tem torbox')
        return
    t = t.replace(
        "const adminReels = require('./api/admin-reels.js');",
        "const adminTorbox = require('./api/admin-torbox.js');\nconst adminReels = require('./api/admin-reels.js');",
        1,
    )
    t = t.replace(
        "app.all('/api/admin-reels', wrap(adminReels));",
        "app.all('/api/admin-torbox', wrap(adminTorbox));\napp.all('/api/admin-reels', wrap(adminReels));",
        1,
    )
    p.write_text(t)
    print('ok server.js rotas')


def patch_addons():
    p = ROOT / 'lib' / 'stremio-addons.js'
    old = """function streamToSource(stream, addonName, addonPriority) {
  const url = (stream.url || '').trim();
  if (!isHttpStreamUrl(url)) return null;
  if (stream.infoHash || (stream.sources && !url)) return null;"""
    new = """function extractInfoHash(stream) {
  if (stream && stream.infoHash) return String(stream.infoHash).toLowerCase();
  const raw = String((stream && (stream.url || stream.magnet)) || '');
  const m = raw.match(/btih:([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})/i);
  return m ? m[1].toLowerCase() : null;
}

function streamToSource(stream, addonName, addonPriority) {
  const url = (stream.url || '').trim();
  const hash = extractInfoHash(stream);
  if (!isHttpStreamUrl(url) && !hash) return null;
  if (!url && stream.sources && !hash) return null;"""
    once(p, old, new, 'streamToSource hash')

    old2 = """  return {
    source_url: url,
    source_label: parts.join(' · '),
    priority: Number.isFinite(Number(addonPriority)) ? Number(addonPriority) : ADDON_PRIORITY,
    _score: streamScore(q, a),
  };
}"""
    new2 = """  if (isHttpStreamUrl(url)) {
    return {
      source_url: url,
      source_label: parts.join(' · '),
      priority: Number.isFinite(Number(addonPriority)) ? Number(addonPriority) : ADDON_PRIORITY,
      _score: streamScore(q, a),
    };
  }
  return {
    source_url: 'magnet:?xt=urn:btih:' + hash,
    source_label: parts.join(' · '),
    priority: Number.isFinite(Number(addonPriority)) ? Number(addonPriority) : ADDON_PRIORITY,
    _score: streamScore(q, a),
    kind: 'torrent',
    info_hash: hash,
  };
}"""
    once(p, old2, new2, 'streamToSource torrent return')


def patch_media():
    p = ROOT / 'api' / 'media-sources.js'
    t = p.read_text()
    if 'resolveTorrentSources' in t:
        print('ok media-sources ja tem torbox')
        return
    t = t.replace(
        "const { collectAddonSources, loadActiveAddons } = require('../lib/stremio-addons');",
        "const { collectAddonSources, loadActiveAddons } = require('../lib/stremio-addons');\nconst { resolveTorrentSources } = require('../lib/torbox');",
        1,
    )
    old = """      try {
        pushUnique(
          await collectAddonSources(serviceKey, tmdbId, mediaType, season, episode),
          seen,
          sources,
        );
      } catch (addonErr) {
        console.warn('[media-sources] addons skip:', addonErr.message);
      }"""
    new = """      try {
        const addonRaw = await collectAddonSources(serviceKey, tmdbId, mediaType, season, episode);
        const httpOnes = (addonRaw || []).filter((s) => s && s.kind !== 'torrent' && !/^magnet:/i.test(String(s.source_url || '')));
        const torrents = (addonRaw || []).filter((s) => s && (s.kind === 'torrent' || /^magnet:/i.test(String(s.source_url || ''))));
        pushUnique(httpOnes, seen, sources);
        try {
          pushUnique(await resolveTorrentSources(serviceKey, torrents), seen, sources);
        } catch (tbErr) {
          console.warn('[media-sources] torbox skip:', tbErr.message);
        }
      } catch (addonErr) {
        console.warn('[media-sources] addons skip:', addonErr.message);
      }"""
    if old not in t:
        print('trecho nao achado media collect')
        return
    p.write_text(t.replace(old, new, 1))
    print('ok media-sources torbox')


def patch_panel():
    p = ROOT / 'Public' / 'admin-sources.html'
    t = p.read_text()
    old = '<section id="debrid" class="tabbody" style="display:none"><div class="panel"><h2>TorBox</h2><p class="sub">Preparação para resolver infoHash/magnet em links HTTP reproduzíveis.</p><div class="card"><div class="title">Integração protegida</div><p class="muted">Ainda não vou gravar sua API key nesta primeira versão. O próximo passo será criar uma rota de backend protegida e uma tabela própria, sem misturar credenciais com bridges ou manifests.</p><span class="pill warn">Em preparação</span><span class="pill">Não altera IPTV</span></div></div></section>'
    new = '<section id="debrid" class="tabbody" style="display:none"><div class="panel"><h2>TorBox</h2><p class="sub">A chave fica só no servidor. Magnet dos addons vira HTTP no player com o nome TorBox.</p><div class="card"><div class="title">API key</div><p class="muted" id="tbStatus">Carregando…</p><input id="tbKey" type="password" placeholder="Cole a API key da TorBox" style="display:block;width:100%;margin:10px 0;padding:12px;border:1px solid var(--line);border-radius:10px;background:#0d101a;color:var(--text)"><label class="muted"><input id="tbOn" type="checkbox"> Ligado no player</label><div class="actions"><button class="primary" onclick="saveTorbox()">Salvar</button><button onclick="testTorbox()">Testar key</button></div><p id="tbMsg" class="muted"></p></div></div></section>'
    if 'function saveTorbox' in t:
        print('ok painel ja tem form torbox')
    elif old in t:
        t = t.replace(old, new, 1)
        print('ok painel html')
    else:
        print('trecho nao achado painel html')

    hook = "let bridges=[],addons=[];async function refreshAll(){const [b,a]=await Promise.all([api('/api/admin-bridge',{action:'list'}),api('/api/admin-addons',{action:'list-addons'})]);bridges=b.bridges||[];addons=a.addons||[];render()}"
    hook_new = """let bridges=[],addons=[];
async function tbApi(action,extra){return api('/api/admin-torbox',Object.assign({action:action},extra||{}))}
async function loadTorbox(){const d=await tbApi('status');const st=document.getElementById('tbStatus');const msg=document.getElementById('tbMsg');if(!st)return;if(d.error){st.textContent=d.error;st.className='bad';return}st.innerHTML=(d.is_enabled?'<span class="ok">Ligado</span>':'<span class="warn">Pausado</span>')+' · key '+(d.has_key?(d.key_mask||'ok'):'ausente')+(d.env_fallback?' (env)':'')+(d.last_error?(' · '+d.last_error):'');document.getElementById('tbOn').checked=!!d.is_enabled;if(msg&&!msg.dataset.keep)msg.textContent='';}
async function saveTorbox(){const key=document.getElementById('tbKey').value.trim();const on=document.getElementById('tbOn').checked;const d=await tbApi('save',{api_key:key,is_enabled:on});const msg=document.getElementById('tbMsg');msg.textContent=d.error?JSON.stringify(d.error):'Salvo';msg.className=d.error?'bad':'ok';await loadTorbox()}
async function testTorbox(){const key=document.getElementById('tbKey').value.trim();const d=await tbApi('test',{api_key:key});const msg=document.getElementById('tbMsg');msg.dataset.keep='1';msg.textContent=d.ok?('Key ok'+(d.email?' · '+d.email:'')):('Falhou: '+(d.error||''));msg.className=d.ok?'ok':'bad';}
async function refreshAll(){const [b,a]=await Promise.all([api('/api/admin-bridge',{action:'list'}),api('/api/admin-addons',{action:'list-addons'})]);bridges=b.bridges||[];addons=a.addons||[];render();loadTorbox().catch(()=>{})}"""
    if 'function saveTorbox' in t:
        print('ok painel js ja tem')
    elif hook in t:
        t = t.replace(hook, hook_new, 1)
        print('ok painel js')
    else:
        print('trecho nao achado painel js')
    p.write_text(t)


if __name__ == '__main__':
    patch_server()
    patch_addons()
    patch_media()
    patch_panel()
    print('fim apply_torbox')
