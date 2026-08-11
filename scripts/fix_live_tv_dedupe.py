#!/usr/bin/env python3
"""Unifica canais (HD/SD/FHD), prioriza qualidade, remove adulto do payload."""
from pathlib import Path
import re

p = Path("api/live-tv.js")
t = p.read_text()

if "function channelMergeKey" not in t:
    helpers = r'''
function channelMergeKey(name) {
  let n = normalizeName(name);
  if (!n) return '';
  n = n.replace(/^\d+\s+/, '');
  n = n.replace(/\b(sd|hd|fhd|uhd|4k|8k|h264|h265|hevc|hdr|lq|hq|full\s*hd)\b/g, ' ');
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
    t = t.replace("function normalizeName(name)", helpers + "function normalizeName(name)", 1)
    print("helpers inserted")
else:
    print("helpers already")

# merge key
t2, n = re.subn(
    r"const key = normalizeName\(s\.name\);\s*if \(!key\) continue;",
    "const key = channelMergeKey(s.name);\n        if (!key || key.length < 2) continue;",
    t,
    count=1,
)
t = t2
print("merge key replacements", n)

# stream push block — only if quality score not already there
if "qualityScore(s.name)" not in t or "ch.streams.push" in t and "quality: q" not in t:
    old = """        if (!ch.logo && s.logo) ch.logo = s.logo;
        if (!ch.streams.some((x) => x.url === s.url)) {
          ch.streams.push({
            url: s.url,
            label: s.sourceLabel,
            priority: r.priority ?? 100,
          });
        }"""
    new = """        if (!ch.logo && s.logo) ch.logo = s.logo;
        if (qualityScore(s.name) > qualityScore(ch.name)) {
          ch.name = String(s.name || '').replace(/\\s*\\([^)]*\\)\\s*/g, ' ').replace(/\\s+/g, ' ').trim() || s.name;
        }
        if (!ch.streams.some((x) => x.url === s.url)) {
          const basePri = r.priority ?? 100;
          const q = qualityScore(s.name);
          ch.streams.push({
            url: s.url,
            label: s.sourceLabel,
            priority: basePri + (q >= 30 ? 0 : q <= 5 ? 400 : 80),
            quality: q,
          });
        }"""
    if old in t:
        t = t.replace(old, new, 1)
        print("stream push updated")
    else:
        print("WARN stream push pattern not found (maybe already patched)")

# final channels map
if "hasGood" not in t:
    old = """    const channels = Array.from(channelMap.values()).map((ch) => ({
      ...ch,
      streams: ch.streams.sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100)),
    }));
    channels.sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));"""
    new = """    let channels = Array.from(channelMap.values()).map((ch) => {
      let streams = ch.streams.slice().sort((a, b) => (a.priority ?? 100) - (b.priority ?? 100));
      const hasGood = streams.some((s) => (s.quality ?? 20) >= 30);
      if (hasGood) {
        streams = streams.filter((s) => (s.quality ?? 20) >= 20);
      }
      const cleanName = String(ch.name || '')
        .replace(/\\s*\\([^)]*\\)\\s*/g, ' ')
        .replace(/\\s+/g, ' ')
        .trim();
      return {
        ...ch,
        name: cleanName || ch.name,
        streams: streams.map(({ url, label, priority }) => ({ url, label, priority })),
      };
    });
    channels = channels.filter((c) => c.categoryId !== ADULT_CATEGORY_ID);
    channels.sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));"""
    if old in t:
        t = t.replace(old, new, 1)
        print("final channels updated")
    else:
        print("WARN final channels pattern not found")
else:
    print("final already has hasGood")

# adult category strip
if "Adulto sempre no FINAL" in t:
    t = t.replace(
        """    // Adulto sempre no FINAL, com id/nome \"000\" (não no início)
    if (hasAdult && usedCats.has(ADULT_CATEGORY_ID)) {
      filteredCategories = filteredCategories.filter((c) => c.id !== ADULT_CATEGORY_ID);
      filteredCategories.push({ id: ADULT_CATEGORY_ID, name: ADULT_CATEGORY_NAME });
    }""",
        "    // Adulto nao entra na lista publica\n    filteredCategories = filteredCategories.filter((c) => c.id !== ADULT_CATEGORY_ID);",
        1,
    )
    print("adult cat stripped")
elif "Adulto nao entra" in t or "Adulto não entra" in t:
    print("adult already stripped")
else:
    # softer replace
    t2, n = re.subn(
        r"if \(hasAdult && usedCats\.has\(ADULT_CATEGORY_ID\)\) \{[\s\S]*?filteredCategories\.push\(\{ id: ADULT_CATEGORY_ID[\s\S]*?\}\);\s*\}",
        "filteredCategories = filteredCategories.filter((c) => c.id !== ADULT_CATEGORY_ID);",
        t,
        count=1,
    )
    t = t2
    print("adult regex replacements", n)

p.write_text(t)
print("api DONE")

# --- Android brand shortcuts ---
vm = Path("android/app/src/main/java/com/streamflixvip/app/ui/livetv/LiveTvViewModel.kt")
if vm.exists():
    vt = vm.read_text()
    if "brandFilter" not in vt:
        vt = vt.replace(
            "val selectedChannelId: String? = null,\n) {",
            "val selectedChannelId: String? = null,\n    val brandFilter: String? = null,\n) {",
            1,
        )
        if "brandFilter?.let" not in vt:
            vt = vt.replace(
                "if (q.isNotEmpty()) {\n                list = list.filter { normalize(it.name).contains(q) }\n            }",
                "brandFilter?.let { brand ->\n                list = list.filter { normalize(it.name).contains(brand) }\n            }\n\n            if (q.isNotEmpty()) {\n                list = list.filter { normalize(it.name).contains(q) }\n            }",
                1,
            )
        if "fun setBrandFilter" not in vt:
            vt = vt.replace(
                "fun setTab(tab: LiveTvTab)",
                "fun setBrandFilter(brand: String?) {\n        _uiState.update {\n            it.copy(brandFilter = brand, tab = LiveTvTab.CHANNELS, searchQuery = \"\")\n        }\n    }\n\n    fun setTab(tab: LiveTvTab)",
                1,
            )
        vm.write_text(vt)
        print("VM brand ok")
    else:
        print("VM already")

screen = Path("android/app/src/main/java/com/streamflixvip/app/ui/livetv/LiveTvScreen.kt")
if screen.exists():
    st = screen.read_text()
    if "setBrandFilter" not in st:
        # match spacer + categorias comment with any dashes
        m = re.search(
            r"(        Spacer\(Modifier\.height\(8\.dp\)\)\n\n        // )([^\n]*Categorias[^\n]*)",
            st,
        )
        if not m:
            raise SystemExit("screen categorias marker not found")
        insert = '''        Spacer(Modifier.height(8.dp))

        if (state.tab == LiveTvTab.CHANNELS) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    val all = state.brandFilter == null
                    FilterChip(
                        selected = all,
                        onClick = { viewModel.setBrandFilter(null) },
                        label = { Text("Todos", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.22f),
                            selectedLabelColor = Accent,
                            containerColor = Color.White.copy(alpha = 0.05f),
                            labelColor = Color.White.copy(alpha = 0.7f),
                        ),
                    )
                }
                items(
                    listOf("telecine" to "Telecine", "hbo" to "HBO", "premiere" to "Premiere"),
                    key = { it.first },
                ) { (id, label) ->
                    val sel = state.brandFilter == id
                    FilterChip(
                        selected = sel,
                        onClick = { viewModel.setBrandFilter(if (sel) null else id) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.22f),
                            selectedLabelColor = Accent,
                            containerColor = Color.White.copy(alpha = 0.05f),
                            labelColor = Color.White.copy(alpha = 0.7f),
                        ),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // '''
        st = st[: m.start()] + insert + m.group(2) + st[m.end() :]
        # fix: we may have broken the // prefix - ensure comment
        st = st.replace(
            "        // ── Categorias",
            "        // ── Categorias",
            1,
        )
        screen.write_text(st)
        print("Screen brand ok")
    else:
        print("Screen already")

print("ALL DONE")
