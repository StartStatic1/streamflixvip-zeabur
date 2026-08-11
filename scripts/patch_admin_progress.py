#!/usr/bin/env python3
"""Restore admin.html from known-good commit and patch progress label."""
from pathlib import Path
import subprocess
import sys

ROOT = Path('.')
TARGET = ROOT / 'Public' / 'admin.html'

raw = TARGET.read_text() if TARGET.exists() else ''
if raw.strip() == 'PLACEHOLDER' or len(raw) < 1000 or 'function iptvProgressLabel' not in raw:
    print('Restoring from commit 94269df2...')
    r = subprocess.run(
        ['git', 'show', '94269df2c1d1fec2c3b41a566af9c9191aaad595:Public/admin.html'],
        capture_output=True, text=True, check=True
    )
    TARGET.write_text(r.stdout)
    print('Restored', TARGET.stat().st_size, 'bytes')

t = TARGET.read_text()
if 'function iptvStatNum(' in t:
    print('Already patched')
    sys.exit(0)

old = """  function iptvProgressLabel(source) {
    if (iptvSourceType(source) === 'xtream_api') {
      // Progresso da API Xtream: filmes e séries têm cursor/estatística
      // próprios (xtream_sync_cursor / xtream_series_sync_cursor), pois
      // rodam como duas fases dentro do mesmo GitHub Action.
      const movieStats = source.xtream_last_sync_stats;
      const seriesStats = source.xtream_series_last_sync_stats;
      const parts = [];
      if (movieStats && !movieStats.error) {
        parts.push(`🎬 ${movieStats.last_run_matched} filmes ok, ${movieStats.last_run_unmatched} sem match`);
      } else if (source.xtream_sync_cursor > 0) {
        parts.push(`🎬 em andamento: posição ${source.xtream_sync_cursor}`);
      } else if (movieStats && movieStats.error) {
        parts.push(`❌ erro: ${movieStats.error}`);
      }
      if (seriesStats) {
        parts.push(`📺 ${seriesStats.last_run_matched} séries ok, ${seriesStats.last_run_unmatched} sem match`);
      } else if (source.xtream_series_sync_cursor > 0) {
        parts.push(`📺 em andamento: posição ${source.xtream_series_sync_cursor}`);
      }
      if (!parts.length) return '⏳ Aguardando primeira sincronização (roda automaticamente a cada 30min)';
      return parts.join(' · ');
    }
    const stats = source.last_sync_stats;
    if (source.sync_phase === 'done' && stats) {
      return `✅ Volta completa: ${stats.last_run_matched} encontrados, ${stats.last_run_unmatched} não encontrados (de ${stats.total_movies_in_playlist})`;
    }
    if (source.sync_cursor > 0) {
      return `⏳ Em andamento: posição ${source.sync_cursor}${stats ? ' (última volta: ' + stats.total_movies_in_playlist + ' filmes)' : ''}`;
    }
    return '⏳ Aguardando primeira sincronização (roda automaticamente todo dia)';
  }"""

new = """  function iptvStatNum(stats, ...keys) {
    if (!stats || typeof stats !== 'object') return null;
    for (const k of keys) {
      const v = stats[k];
      if (v !== undefined && v !== null && v !== '') {
        const n = Number(v);
        return Number.isFinite(n) ? n : v;
      }
    }
    return null;
  }

  function iptvProgressLabel(source) {
    if (iptvSourceType(source) === 'xtream_api') {
      const movieStats = source.xtream_last_sync_stats;
      const seriesStats = source.xtream_series_last_sync_stats;
      const parts = [];
      if (movieStats && movieStats.error) {
        parts.push(`❌ filmes: ${movieStats.error}`);
      } else if (movieStats && !movieStats.error) {
        const ok = iptvStatNum(movieStats, 'last_run_matched', 'matched', 'ok');
        const miss = iptvStatNum(movieStats, 'last_run_unmatched', 'unmatched', 'nao');
        const total = iptvStatNum(movieStats, 'total_movies_in_api', 'total', 'total_movies_in_playlist');
        if (ok !== null || miss !== null) {
          let line = `🎬 ${ok ?? 0} filmes ok, ${miss ?? 0} sem match`;
          if (total !== null) line += ` (de ${total})`;
          parts.push(line);
        }
      } else if (source.xtream_sync_cursor > 0) {
        parts.push(`🎬 em andamento: posição ${source.xtream_sync_cursor}`);
      }
      if (seriesStats && seriesStats.error) {
        parts.push(`❌ séries: ${seriesStats.error}`);
      } else if (seriesStats) {
        const ok = iptvStatNum(seriesStats, 'last_run_matched', 'matched', 'ok');
        const miss = iptvStatNum(seriesStats, 'last_run_unmatched', 'unmatched', 'nao');
        const total = iptvStatNum(seriesStats, 'total_series_in_api', 'total', 'total_episodes_in_playlist');
        if (ok !== null || miss !== null) {
          let line = `📺 ${ok ?? 0} séries ok, ${miss ?? 0} sem match`;
          if (total !== null) line += ` (de ${total})`;
          parts.push(line);
        }
      } else if (source.xtream_series_sync_cursor > 0) {
        parts.push(`📺 em andamento: posição ${source.xtream_series_sync_cursor}`);
      }
      if (!parts.length) return '⏳ Aguardando primeira sincronização (roda automaticamente a cada 30min)';
      return parts.join(' · ');
    }
    const stats = source.last_sync_stats;
    if (stats && stats.error) {
      return `❌ Erro: ${stats.error}`;
    }
    if (source.sync_phase === 'done' && stats) {
      const ok = iptvStatNum(stats, 'last_run_matched', 'matched', 'ok') ?? 0;
      const miss = iptvStatNum(stats, 'last_run_unmatched', 'unmatched', 'nao') ?? 0;
      const total = iptvStatNum(stats, 'total_movies_in_playlist', 'total', 'total_movies');
      const totalTxt = total !== null ? total : (ok + miss);
      return `✅ Volta completa: ${ok} encontrados, ${miss} não encontrados (de ${totalTxt})`;
    }
    if (source.sync_cursor > 0) {
      const total = stats ? iptvStatNum(stats, 'total_movies_in_playlist', 'total', 'total_movies') : null;
      return `⏳ Em andamento: posição ${source.sync_cursor}${total !== null ? ' (última volta: ' + total + ' filmes)' : ''}`;
    }
    return '⏳ Aguardando primeira sincronização (roda automaticamente todo dia)';
  }"""

if old not in t:
    raise SystemExit('OLD BLOCK NOT FOUND')
TARGET.write_text(t.replace(old, new, 1))
assert 'function iptvStatNum(' in TARGET.read_text()
print('PATCHED OK', TARGET.stat().st_size)
