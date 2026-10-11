#!/usr/bin/env python3
"""Corrige @Composable que ficou acima de formatCommentDate em vez de CommentsModal."""
from pathlib import Path

p = Path(__file__).resolve().parents[1] / "android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailScreen.kt"
t = p.read_text()

old = '''@Composable

private fun formatCommentDate(iso: String?): String {
'''
new = '''private fun formatCommentDate(iso: String?): String {
'''

if "@Composable\n\nprivate fun formatCommentDate" in t or old in t:
    t = t.replace(old, new, 1)
    print("ok removeu @Composable do helper")
elif "@Composable\r\n\r\nprivate fun formatCommentDate" in t:
    t = t.replace("@Composable\r\n\r\nprivate fun formatCommentDate", "private fun formatCommentDate", 1)
    print("ok removeu @Composable do helper (crlf)")
else:
    print("aviso: padrao @Composable+helper nao encontrado")

# Garante @Composable logo antes de CommentsModal
if "@Composable\nprivate fun CommentsModal(" in t or "@Composable\r\nprivate fun CommentsModal(" in t:
    print("ok CommentsModal ja tem @Composable")
elif "private fun CommentsModal(" in t:
    t = t.replace("private fun CommentsModal(", "@Composable\nprivate fun CommentsModal(", 1)
    print("ok adicionou @Composable em CommentsModal")
else:
    print("erro: CommentsModal nao encontrado")
    raise SystemExit(1)

p.write_text(t)
print("fim apply_fix_composable_comments")
