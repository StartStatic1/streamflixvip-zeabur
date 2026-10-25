// lib/bounded-map.js — limita caches em Map (evita memoria crescendo sem fim).
// trimByPrefix(map, 'fh:imdb:', 800): se houver mais de 800 chaves com esse
// prefixo, remove as mais antigas (ordem de insercao) so desse prefixo.
function trimByPrefix(map, prefix, max) {
  let count = 0;
  for (const k of map.keys()) if (String(k).startsWith(prefix)) count += 1;
  if (count <= max) return 0;
  let drop = count - max;
  let removed = 0;
  for (const k of map.keys()) {
    if (drop <= 0) break;
    if (String(k).startsWith(prefix)) {
      map.delete(k);
      drop -= 1;
      removed += 1;
    }
  }
  return removed;
}

/** Atualiza a chave para o fim (mais recente) e limita o prefixo. */
function setBounded(map, key, value, prefix, max) {
  map.delete(key);
  map.set(key, value);
  return trimByPrefix(map, prefix, max);
}

module.exports = { trimByPrefix, setBounded };
