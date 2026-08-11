#!/usr/bin/env python3
from pathlib import Path

# --- API: better merge key + skip pure SD when HD exists + drop adult from payload ---
p = Path("api/live-tv.js")
t = p.read_text()

if "channelMergeKey" not in t:
    helpers = r'''
function channelMergeKey(name) {
  let n = normalizeName(name);
  if (!n) return '';
  // remove numeros iniciais (001 telecine...)
  n = n.replace(/^\d+\s+/, '');
  // qualidade / codec
  n = n.replace(/\b(sd|hd|fhd|uhd|4k|8k|h264|h265|hevc|hdr|lq|hq|full\s*hd)\b/g, ' ');
  // lixo comum de painel
  n = n.replace(/\b(canais?|legenda|legendado|dublado|multi|audio|server|opcao|opt|option|backup|alt)\b/g, ' ');
  n = n.replace(/\s+/g, ' ').trim();
  return n;
}

function qualityScore(name) {
  const n = normalizeName(name);
  if (/\b(4k|uhd)\b/.test(n)) return 50;
  if (/\bfhd\b/.test(n) || /full\s*hd/.test(n)) return 40;
  if (/\bhd\b/.test(n) && !/\bsd\b/.test(n)) return 30;
  if (/\bsd\b/.test(n)) return 5;
  return 20;
}

function isSdOnlyName(name) {
  const n = normalizeName(name);
  return /\bsd\b/.test(n) && !/\b(hd|fhd|uhd|4k|full\s*hd)\b/.test(n);
}

'''
    if "function normalizeName(name)" not in t:
        raise SystemExit("normalizeName missing")
    t = t.replace("function normalizeName(name)", helpers + "function normalizeName(name)", 1)

    # replace merge key usage
    t = t.replace(
        "const key = normalizeName(s.name);\n        if (!key) continue;",
        "const key = channelMergeKey(s.name);\n        if (!key || key.length < 2) continue;",
        1,
    )

    old_push = """        if (!ch.logo && s.logo) ch.logo = s.logo;
        if (!ch.streams.some((x) => x.url === s.url)) {
          ch.streams.push({
            url: s.url,
            label: s.sourceLabel,
            priority: r.priority ?? 100,
          });
        }"""

    new_push = """        if (!ch.logo && s.logo) ch.logo = s.logo;
        // Prefere nome com melhor qualidade na etiqueta
        if (qualityScore(s.name) > qualityScore(ch.name)) {
          ch.name = s.name.replace(/\s*\([^)]*\)\s*/g, ' ').replace(/\s+/g, ' ').trim() || s.name;
        }
        if (!ch.streams.some((x) => x.url === s.url)) {
          const basePri = r.priority ?? 100;
          const q = qualityScore(s.name);
          // SD fica por ultimo no fallback; HD/FHD primeiro
          ch.streams.push({
            url: s.url,
            label: s.sourceLabel,
            priority: basePri + (q >= 30 ? 0 : q <= 5 ? 400 : 80),
            quality: q,
          });
        }"""

    if old_push not in t:
        raise SystemExit("stream push block not found")
    t = t.replace(old_push, new_push, 1)

    old_final = """    const channels = Array.from(channelMap.values()).map((ch) => ({
      ...ch,
      streams: ch.streams.sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100)),
    }));
    channels.sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));"""

    new_final = """    let channels = Array.from(channelMap.values()).map((ch) => {
      let streams = ch.streams.slice().sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100));
      // Se tem fonte HD+, remove streams SD (evita 3 Telecine SD/HD separados e lixo SD)
      const hasGood = streams.some((s) => (s.quality ?? 20) >= 30);
      if (hasGood) {
        streams = streams.filter((s) => (s.quality ?? 20) >= 20);
      }
      // Limpa nome de (CANAIS...) e multiplos espacos
      const cleanName = String(ch.name || '')
        .replace(/\s*\([^)]*\)\s*/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
      return {
        ...ch,
        name: cleanName || ch.name,
        streams: streams.map(({ url, label, priority }) => ({ url, label, priority })),
      };
    });
    // Nao enviar adulto no payload principal (app VIP sem secao adulta)
    channels = channels.filter((c) => c.categoryId !== ADULT_CATEGORY_ID);
    channels.sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));"""

    if old_final not in t:
        raise SystemExit("final channels block not found")
    t = t.replace(old_final, new_final, 1)

    # Don't add adult category at end
    old_adult_cat = """    // Adulto sempre no FINAL, com id/nome "000" (não no início)
    if (hasAdult && usedCats.has(ADULT_CATEGORY_ID)) {
      filteredCategories = filteredCategories.filter((c) => c.id !== ADULT_CATEGORY_ID);
      filteredCategories.push({ id: ADULT_CATEGORY_ID, name: ADULT_CATEGORY_NAME });
    }"""
    new_adult_cat = """    // Adulto nao entra na lista publica de categorias
    filteredCategories = filteredCategories.filter((c) => c.id !== ADULT_CATEGORY_ID);"""
    if old_adult_cat in t:
        t = t.replace(old_adult_cat, new_adult_cat, 1)
        print("adult cat stripped")

    p.write_text(t)
    print("api live-tv patched")
else:
    print("api already has channelMergeKey")

print("DONE")
