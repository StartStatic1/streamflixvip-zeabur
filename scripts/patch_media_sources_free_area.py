#!/usr/bin/env python3
"""Aplica regra Area Free em api/media-sources.js (se o arquivo estiver inteiro)."""
from pathlib import Path
import subprocess
ROOT = Path(__file__).resolve().parents[1]
p = ROOT / 'api/media-sources.js'
text = p.read_text(encoding='utf-8')
if text.strip() == 'PLACEHOLDER' or len(text) < 500:
    # recupera versao anterior ao placeholder
    try:
        old = subprocess.check_output(
            ['git', 'log', '--pretty=format:%H', '--', 'api/media-sources.js'],
            cwd=str(ROOT), text=True,
        ).strip().splitlines()
        restored = False
        for sha in old[1:8]:
            try:
                content = subprocess.check_output(
                    ['git', 'show', f'{sha}:api/media-sources.js'],
                    cwd=str(ROOT), text=True, stderr=subprocess.DEVNULL,
                )
                if content.strip() and content.strip() != 'PLACEHOLDER' and 'titleRequiresVip' in content:
                    p.write_text(content, encoding='utf-8')
                    text = content
                    print('recuperado media-sources de', sha[:7])
                    restored = True
                    break
            except Exception:
                continue
        if not restored:
            raise SystemExit('media-sources.js esta PLACEHOLDER e nao achei versao boa no git')
    except SystemExit:
        raise
    except Exception as e:
        raise SystemExit('falha ao recuperar media-sources: ' + str(e))

# select is_free
if 'is_free' not in text or 'select=vip_lock,vip_free_episode_limit,is_free' not in text:
    text = text.replace(
        'select=vip_lock,vip_free_episode_limit',
        'select=vip_lock,vip_free_episode_limit,is_free',
    )
    print('select is_free')

old_fn = '''function titleRequiresVip(config, episodeNumber) {
  if (!config) return false;
  if (config.vip_lock === true) return true;
  const limit = config.vip_free_episode_limit;
  if (limit != null && episodeNumber != null && Number.isFinite(episodeNumber)) {
    return episodeNumber > Number(limit);
  }
  return false;
}'''

new_fn = '''function titleRequiresVip(config, episodeNumber, mediaType) {
  // Area Free: serie sempre VIP; filme so libera se is_free=true
  if (mediaType === 'tv') return true;
  if (config && config.is_free === true) return false;
  return true;
}'''

if 'mediaType === \'tv\') return true' in text or 'mediaType === "tv") return true' in text:
    print('titleRequiresVip ja Area Free')
elif old_fn in text:
    text = text.replace(old_fn, new_fn)
    print('titleRequiresVip atualizado')
else:
    print('AVISO: bloco titleRequiresVip nao encontrado (pode ja estar custom)')

text2 = text.replace(
    "const needsVip = titleRequiresVip(vipConfig, mediaType === 'tv' ? episode : null);",
    "const needsVip = titleRequiresVip(vipConfig, mediaType === 'tv' ? episode : null, mediaType);",
)
if text2 != text:
    text = text2
    print('call titleRequiresVip ok')

if 'is_free: !!vipConfig.is_free' not in text:
    text = text.replace(
        'vip_lock: !!vipConfig.vip_lock, vip_free_episode_limit: vipConfig.vip_free_episode_limit ?? null',
        'vip_lock: !!vipConfig.vip_lock, vip_free_episode_limit: vipConfig.vip_free_episode_limit ?? null, is_free: !!vipConfig.is_free',
    )
    print('response is_free')

p.write_text(text, encoding='utf-8')
print('done media-sources', p.stat().st_size)
