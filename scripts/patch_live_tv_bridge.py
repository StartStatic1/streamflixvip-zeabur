from pathlib import Path

p = Path('api/live-tv.js')
t = p.read_text()
if 'loadBridgeRows' in t:
    print('live-tv ja tem pontes')
    raise SystemExit(0)

needle = 'async function loadManualChannels(serviceKey) {'
insert = '''async function loadBridgeRows(serviceKey) {
  try {
    const rows = await sbSelect(
      serviceKey,
      'iptv_bridges',
      'is_active=eq.true&use_live=eq.true&select=id,name,xtream_host,xtream_user,xtream_pass,live_cats&order=created_at.desc&limit=8',
    );
    return Array.isArray(rows) ? rows : [];
  } catch (e) {
    console.warn('[live-tv] iptv_bridges:', e.message);
    return [];
  }
}

function filterBridgeLive(loaded, liveCats) {
  const allow = new Set((Array.isArray(liveCats) ? liveCats : []).map((c) => String(c && c.id != null ? c.id : c)));
  if (!allow.size) return loaded;
  loaded.categories = (loaded.categories || []).filter((c) => allow.has(String(c.id)));
  loaded.streams = (loaded.streams || []).filter((s) => allow.has(String(s.categoryId)));
  return loaded;
}

async function loadFromBridge(bridge) {
  const loaded = await loadFromSource({
    name: bridge.name || 'Bridge',
    xtream_host: bridge.xtream_host,
    xtream_user: bridge.xtream_user,
    xtream_pass: bridge.xtream_pass,
    priority: 80,
  });
  return filterBridgeLive(loaded, bridge.live_cats);
}

''' + needle

if needle not in t:
    raise SystemExit('bloco loadManualChannels nao achado')
t = t.replace(needle, insert, 1)

old = '''    const results = withCreds.length
      ? await Promise.all(
          withCreds.map((s) =>
            loadFromSource(s).catch((err) => {
              console.error('[live-tv] fonte falhou', s.name, err.message);
              return {
                sourceName: s.name,
                priority: s.priority,
                categories: [],
                streams: [],
                skipReason: err.message,
              };
            }),
          ),
        )
      : [];

    const manuals = await loadManualChannels(serviceKey);
    if (!withCreds.length && !manuals.length) {'''

new = '''    let results = withCreds.length
      ? await Promise.all(
          withCreds.map((s) =>
            loadFromSource(s).catch((err) => {
              console.error('[live-tv] fonte falhou', s.name, err.message);
              return {
                sourceName: s.name,
                priority: s.priority,
                categories: [],
                streams: [],
                skipReason: err.message,
              };
            }),
          ),
        )
      : [];

    const bridges = await loadBridgeRows(serviceKey);
    if (bridges.length) {
      const br = await Promise.all(
        bridges.map((b) =>
          loadFromBridge(b).catch((err) => {
            console.error('[live-tv] bridge falhou', b.name, err.message);
            return {
              sourceName: b.name || 'Bridge',
              priority: 80,
              categories: [],
              streams: [],
              skipReason: err.message,
            };
          }),
        ),
      );
      results = results.concat(br);
      console.log('[live-tv] pontes live:', bridges.map((b) => b.name).join(', '));
    }

    const manuals = await loadManualChannels(serviceKey);
    if (!withCreds.length && !bridges.length && !manuals.length) {'''

if old not in t:
    raise SystemExit('bloco results nao achado — confira live-tv.js')
t = t.replace(old, new, 1)
p.write_text(t)
print('live-tv pontes ok', p.stat().st_size)
