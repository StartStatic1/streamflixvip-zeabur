#!/usr/bin/env python3
from pathlib import Path

p = Path("api/admin-vip.js")
t = p.read_text()

old1 = """  if (action === 'toggle-source') {
    const { id, is_active } = body;
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }"""
new1 = """  if (action === 'toggle-source') {
    const id = body.id || body.sourceId;
    const is_active = body.is_active !== undefined ? body.is_active : body.isActive;
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }"""

old2 = """  if (action === 'delete-source') {
    const { id } = body;
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }"""
new2 = """  if (action === 'delete-source') {
    const id = body.id || body.sourceId;
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }"""

old3 = """  if (action === 'update-source') {
    const { id, source_url, source_label, priority, season, episode, title } = body;
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }"""
new3 = """  if (action === 'update-source') {
    const id = body.id || body.sourceId;
    const { source_url, source_label, priority, season, episode, title } = body;
    if (!id) { res.status(400).json({ error: 'Informe id' }); return; }"""

if old1 not in t:
    raise SystemExit("toggle block not found")
if old2 not in t:
    raise SystemExit("delete block not found")
t = t.replace(old1, new1, 1).replace(old2, new2, 1)
if old3 in t:
    t = t.replace(old3, new3, 1)
    print("update ok")
p.write_text(t)
print("DONE")
