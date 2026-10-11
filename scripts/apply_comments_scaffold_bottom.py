#!/usr/bin/env python3
"""Troca o layout do CommentsModal para Scaffold com bottomBar.
Campo de digitar fica fixo embaixo; lista no body.
"""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text()

if "bottomBar = {" in t and "CommentsModal" in t:
    # check if already applied inside CommentsModal
    s = t.find("private fun CommentsModal(")
    e = t.find("private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl", s)
    if "bottomBar" in t[s:e]:
        print("ok ja scaffold")
        raise SystemExit(0)

# Replace the Column opening + header start area with Scaffold structure.
# Strategy: 
# 1) Change Column(fillMaxSize().imePadding()) to Box + Scaffold
# 2) Move Surface composer into bottomBar
# 3) Keep list in content with padding

old_open = (
    "        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {\n"
    "            Column(Modifier.fillMaxSize().imePadding()) {\n"
    "                Row(\n"
    "                    modifier = Modifier.fillMaxWidth().padding(16.dp),\n"
    "                    horizontalArrangement = Arrangement.SpaceBetween,\n"
    "                    verticalAlignment = Alignment.CenterVertically,\n"
    "                ) {\n"
)

if old_open not in t:
    print("aviso: open nao encontrado")
    # show nearby
    i = t.find("private fun CommentsModal(")
    print(repr(t[i:i+500][:200]))
    raise SystemExit(1)

# We'll do a more surgical approach:
# Find Surface composer block and the Box weight list, reorder via markers.

# Marker A: start of list Box
list_start = t.find("                Box(modifier = Modifier.weight(1f)) {")
# Marker B: end of list Box - the closing before composer comment
composer_comment = t.find("                // Campo de digitar: s")
if list_start < 0 or composer_comment < 0:
    print("aviso markers", list_start, composer_comment)
    raise SystemExit(1)

# Find Surface of composer
surf = t.find("                Surface(", composer_comment)
# Find end of composer Surface - after "Entre na sua conta" block
entre = t.find("Entre na sua conta para comentar.", surf)
# From entre, find the closing braces of Surface
# Pattern after Text block:
#                     }
#                 }
#             }
#         }
#     }
# }

# Extract header Row (from Column open to before list Box)
col_open = t.find("            Column(Modifier.fillMaxSize().imePadding()) {")
header = t[col_open + len("            Column(Modifier.fillMaxSize().imePadding()) {"):list_start]

# Extract list block
list_block = t[list_start:composer_comment]

# Extract composer Surface through its close
# Find matching close for Surface at surf
# Simple: from surf to the line after "Entre na sua conta" section closes
rest = t[entre:]
# closes: Text ), else branch }, Surface }, Column }, dialog Surface }, Dialog }, fun }
# We want composer = from "                Surface(" to including the Surface close only

# Walk from surf to find Surface close at indentation of 16 spaces "                }"
lines = t[surf:].splitlines(True)
depth = 0
end_rel = 0
started = False
for i, ln in enumerate(lines):
    end_rel += len(ln)
    if "Surface(" in ln and not started:
        started = True
        depth = 1
        continue
    if not started:
        continue
    depth += ln.count("{") - ln.count("}")
    if depth <= 0:
        break
composer_block = t[surf:surf+end_rel]
print("composer block lines", composer_block.count(chr(10)))

# After composer, remaining closes for Column/Surface/Dialog/fun
after = t[surf+end_rel:]

# Build new structure
new_body = f'''            Column(Modifier.fillMaxSize().imePadding()) {{
                // Header
{header.rstrip()}

                // Lista (ocupa o meio)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {{
{list_block[list_block.find("{")+1:list_block.rfind("}")].rstrip()}
                }}

                // Campo FIXO no rodape (ultimo filho da Column = sempre embaixo)
{composer_block.rstrip()}
            }}
'''

# Actually simpler fix: the issue might be list Box not closed and Surface inside Box.
# Let's verify brace depth at composer_comment

def depth_at(text, pos):
    return text[:pos].count("{") - text[:pos].count("}")

print("depth at list_start", depth_at(t, list_start))
print("depth at composer_comment", depth_at(t, composer_comment))
print("depth at surf", depth_at(t, surf))

# Alternative simple approach: insert Spacer with weight if Surface somehow first
# Better: use explicit verticalArrangement and ensure Surface is last.

# RADICAL simple fix that always works:
# Put composer Surface as first child of Column with order? No.
# Use Box + align with label:

new_open = (
    "        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {\n"
    "            Box(Modifier.fillMaxSize().imePadding()) {\n"
    "            Column(Modifier.fillMaxSize()) {\n"
)

if "Box(Modifier.fillMaxSize().imePadding())" in t[t.find("CommentsModal"):t.find("CommentsModal")+2000]:
    print("ja tem box outer")
else:
    t = t.replace(old_open, new_open, 1)
    print("ok added outer Box")

# Change Surface composer to use align BottomCenter with explicit receiver via BoxScope
old_surf_mod = "                    modifier = Modifier.fillMaxWidth(),"
# Only first Surface after Campo de digitar
idx_campo = t.find("// Campo de digitar:")
if idx_campo > 0:
    idx_mod = t.find("modifier = Modifier.fillMaxWidth(),", idx_campo)
    if idx_mod > 0 and idx_mod < idx_campo + 400:
        # Close Column before this Surface, then align Surface to bottom of Box
        # Insert close Column before Surface if not present
        surf_pos = t.rfind("                Surface(", idx_campo-50, idx_campo+200)
        if surf_pos < 0:
            surf_pos = t.find("                Surface(", idx_campo)
        before = t[surf_pos-80:surf_pos]
        if "} // end list column" not in t and "fim Column" not in t[idx_campo-100:idx_campo+50]:
            # insert column close
            t = t[:surf_pos] + "                } // end list+header column\n" + t[surf_pos:]
            print("ok close column before surface")
            idx_mod = t.find("modifier = Modifier.fillMaxWidth(),", idx_campo)
        t = t[:idx_mod] + "modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()," + t[idx_mod+len("modifier = Modifier.fillMaxWidth(),"):]
        print("ok align bottom")

# Add one extra closing brace for the outer Box before dialog surface close
# Find CommentsModal end
marker = "Entre na sua conta para comentar."
i = t.find(marker)
j = t.find("private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl", i)
block = t[i:j]
# count trailing }
stripped = block.rstrip()
closes = 0
tmp = stripped
while tmp.endswith("}"):
    tmp = tmp[:-1].rstrip()
    closes += 1
print("trailing closes", closes)
# Expected with outer Box: 6 (if, surface, box, dialogSurface, dialog, fun)
# Without: 5
# Current after our edits: if we closed column early and added Box, need closes for: if, surface, box, dialogS, dialog, fun = 6
if closes == 5:
    # add one }
    pos = t.rfind("}\n", i, j)
    t = t[:pos+2] + "}\n" + t[pos+2:]
    print("ok added close for Box")
elif closes >= 6:
    print("ok closes enough", closes)
else:
    print("aviso closes", closes)

p.write_text(t)
# verify depths
s = t.find("private fun CommentsModal(")
e = t.find("private fun com.streamflixvip.app.network.TmdbResponse.bestLogoUrl", s)
c = t[s:e]
print("balance", c.count("{'), c.count("}"))
print("has align Bottom", "align(Alignment.BottomCenter)" in c)
print("fim apply_comments_scaffold_bottom")
