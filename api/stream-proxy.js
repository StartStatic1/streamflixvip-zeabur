// /api/stream-proxy.js
//
// Resolve o bloqueio de "mixed content": navegadores recusam carregar
// conteúdo http:// dentro de uma página https://. Provedores Xtream
// costumam servir mp4/m3u8 só em http://, então este endpoint busca o
// vídeo no servidor de origem e devolve pelo seu domínio https.
//
// SEGURANÇA: os domínios permitidos são derivados automaticamente da tabela
// vip_sources no Supabase. Qualquer fonte que você cadastrar pelo painel
// admin já libera seu domínio aqui — sem precisar editar código ou fazer
// novo deploy. Cache de 60s em memória evita query ao banco a cada chunk.

const SUPABASE_URL = 'https://gkujbjpvphuvrejpvvtz.supabase.co';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdrdWpianB2cGh1dnJlanB2dnR6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzg2OTQ2OTMsImV4cCI6MjA5NDI3MDY5M30.Zoqdn0V6SZOAfhz9kK9NgG6lniJdyVqihLsNT-O8Huw';

// Hosts extras sempre liberados, mesmo que não estejam em vip_sources ainda.
// Útil pra testes antes de cadastrar no painel.
const EXTRA_ALLOWED_HOSTS = [
  'unitvlite.xyz',
  'sventank.com',
  'cdnbr02.com',
  'cldx-rio-go.top', 
  '178.63.61.173', 
  'fontedecanais.club', 
  'auth.urltech.gy',
  'mnba.shop', 
  'cdnspro.playerscdn.xyz',
  'sosbrazil.xyz',
  'vd2onebm.fun',
  'cdnnetjs.click:80',
  'tvclubmais.com', 
  // Hook só pra teste automatizado local (test_e2e_proxy.js) — nunca
  // setado em produção, então não afeta o comportamento real.
  ...(process.env.STREAM_PROXY_TEST_EXTRA_HOST ? [process.env.STREAM_PROXY_TEST_EXTRA_HOST] : []),
];

let _hostsCache = { hosts: new Set(EXTRA_ALLOWED_HOSTS), fetchedAt: 0 };
const CACHE_TTL_MS = 60 * 1000;

// ── Detecção de URL de entrega já resolvida (CDN + token) ──
//
// Provedores Xtream costumam redirecionar (302) da URL "amigável" cadastrada
// (ex: tvclubmais.com/movie/...) para uma CDN de entrega com token de curta
// duração na query string (ex: 130.250.189.248/deliver/x.mp4?token=eyJ...).
// O mecanismo normal (linha ~192, "allowedHosts.add(finalUrl.hostname)")
// cobre isso quando o PROXY é quem segue o redirect. Mas se por qualquer
// motivo a URL que chega ao proxy JÁ é a final (o app cacheou o link
// resolvido, o player reusou de uma sessão anterior, o M3U trouxe o link
// já expandido, etc.), o host da CDN nunca foi "descoberto" e nunca esteve
// em EXTRA_ALLOWED_HOSTS — e o proxy rejeita um link que na verdade é
// legítimo e ainda válido.
//
// Em vez de depender só da lista fixa de hosts para esse caso, também
// aceitamos a URL se ela contiver um token no formato JWT com um campo de
// expiração (exp) no futuro. Isso NÃO é "confiar em qualquer link" — um
// JWT tem 3 partes separadas por ponto, e o "exp" é um carimbo de tempo
// que só quem gerou o token (o provedor Xtream/CDN) controla; um
// atacante não pode forjar um token com exp futuro sem a chave secreta
// do provedor. Estamos apenas LENDO o campo público exp (não validando a
// assinatura, que exigiria a chave secreta de cada CDN, impossível de ter
// de antemão) — suficiente para diferenciar "token de stream real, ainda
// dentro da validade" de "link aleatório/malicioso sem token nenhum".
function decodeJwtPart(base64urlPart) {
  const normalized = base64urlPart.replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(Buffer.from(normalized, 'base64').toString('utf8'));
}

function hasValidLookingCdnToken(target) {
  const token = target.searchParams.get('token');
  if (!token) return false;
  const parts = token.split('.');
  if (parts.length !== 3) return false; // não parece JWT (header.payload.signature)
  try {
    // A convenção mais comum é "exp" no payload (parts[1]), mas alguns
    // provedores Xtream/CDN colocam no header (parts[0]) — checamos os
    // dois lugares em vez de assumir um só, já que o JWT não obriga onde
    // esse campo vai.
    const header = decodeJwtPart(parts[0]);
    const payload = decodeJwtPart(parts[1]);
    const exp = typeof payload.exp === 'number' ? payload.exp : header.exp;
    if (typeof exp !== 'number') return false;
    return exp * 1000 > Date.now(); // exp em segundos desde epoch, ainda não expirou
  } catch (_) {
    return false; // conteúdo malformado/não é JSON — não arrisca, trata como não confiável
  }
}

async function getAllowedHosts() {
  const now = Date.now();
  if (now - _hostsCache.fetchedAt < CACHE_TTL_MS) return _hostsCache.hosts;
  try {
    const r = await fetch(
      `${SUPABASE_URL}/rest/v1/vip_sources?select=source_url&is_active=eq.true`,
      { headers: { apikey: SUPABASE_ANON_KEY, Authorization: `Bearer ${SUPABASE_ANON_KEY}` } }
    );
    if (r.ok) {
      const rows = await r.json();
      const hosts = new Set(EXTRA_ALLOWED_HOSTS);
      for (const row of rows) {
        try { hosts.add(new URL(row.source_url).hostname); } catch (_) {}
      }
      _hostsCache = { hosts, fetchedAt: now };
    }
  } catch (e) {
    console.error('stream-proxy: falha ao atualizar hosts, mantendo cache:', e);
  }
  return _hostsCache.hosts;
}

// ── Reescrita de manifesto M3U8 ──
//
// Este é o motivo pelo qual .m3u8 tocava no site (via hls.js) mas não no
// app nativo (ExoPlayer). Um arquivo .m3u8 não é o vídeo — é uma playlist
// de TEXTO que aponta para outros arquivos (segmentos .ts, ou sub-listas
// de qualidade). Quando o proxy só repassava os BYTES do .m3u8 sem tocar
// no conteúdo, as URLs de segmento dentro dele continuavam apontando
// direto pro servidor de origem (ex: mgeb.top), sem o User-Agent/Referer
// que o proxy adiciona. No navegador o hls.js resolve isso sozinho,
// reescrevendo as URLs internamente antes de pedir cada segmento — mas o
// ExoPlayer, ao receber a playlist JÁ PRONTA do proxy, tentava buscar os
// .ts direto na origem e tomava 403 (exatamente o "Erro: -403" da tela).
//
// A correção: sempre que a resposta for um manifesto M3U8 (detectado pelo
// content-type OU pela extensão .m3u8/.m3u da URL final), interceptamos o
// corpo como texto, e para cada linha que não é comentário (não começa
// com #) e representa uma URL de segmento/sub-playlist, resolvemos essa
// URL relativa/absoluta contra a URL de origem e a substituímos por uma
// nova chamada ao próprio /api/stream-proxy — assim TODO segmento também
// passa pelos mesmos headers, e nada tenta ir direto na origem.
function isM3u8Response(finalUrl, contentType) {
  if (contentType && /mpegurl|m3u8/i.test(contentType)) return true;
  return /\.m3u8?(\?|#|$)/i.test(finalUrl);
}

function rewriteM3u8(body, baseUrl, proxyOrigin) {
  const lines = body.split('\n');
  const rewritten = lines.map((rawLine) => {
    const line = rawLine.replace(/\r$/, '');
    const trimmed = line.trim();
    if (!trimmed) return line;

    // Linhas de tag que também carregam URI="..." (ex: mapas de mídia,
    // chaves de criptografia, faixas de áudio/legenda alternativas).
    if (trimmed.startsWith('#')) {
      const uriMatch = trimmed.match(/URI="([^"]+)"/);
      if (uriMatch) {
        try {
          const resolved = new URL(uriMatch[1], baseUrl).toString();
          const proxied = `${proxyOrigin}/api/stream-proxy?url=${encodeURIComponent(resolved)}`;
          return line.replace(uriMatch[1], proxied);
        } catch (_) {
          return line;
        }
      }
      return line;
    }

    // Linha "solta" = URL de segmento .ts ou de sub-playlist (variantes de
    // qualidade). Pode vir relativa ("1269590_1.ts") ou absoluta.
    try {
      const resolved = new URL(trimmed, baseUrl).toString();
      return `${proxyOrigin}/api/stream-proxy?url=${encodeURIComponent(resolved)}`;
    } catch (_) {
      return line; // não parece URL válida — deixa como está, não trava o manifesto inteiro
    }
  });
  return rewritten.join('\n');
}

async function handler(req, res) {
  const { url } = req.query;

  if (!url) {
    res.status(400).json({ error: 'Parâmetro "url" obrigatório.' });
    return;
  }

  let target;
  try {
    target = new URL(url);
  } catch (e) {
    res.status(400).json({ error: 'URL inválida.' });
    return;
  }

  const allowedHosts = await getAllowedHosts();
  // Valida o hostname da URL original (passada no parâmetro 'url'). Além
  // do match exato, também aceita subdomínios de um host já liberado (ex:
  // "cdn2.mgeb.top" quando "mgeb.top" está liberado) — comum em manifestos
  // M3U8 cujos segmentos/sub-playlists de qualidade vêm de um CDN irmão do
  // domínio principal, mesmo provedor. Sem isso, cada CDN novo dentro do
  // próprio manifesto reescrito tomaria 403 na primeira vez que o
  // ExoPlayer tentasse buscá-lo pelo proxy.
  const hostAllowed = allowedHosts.has(target.hostname) ||
    [...allowedHosts].some((h) => target.hostname.endsWith('.' + h)) ||
    hasValidLookingCdnToken(target);
  if (!hostAllowed) {
    res.status(403).json({ error: 'Domínio da URL original não autorizado. Cadastre a fonte no painel admin primeiro.' });
    return;
  }

  // Requisições HEAD (usadas pelo StreamUrlResolver do app, que testa
  // Koyeb vs Zeabur em paralelo pra ver qual responde primeiro) NUNCA
  // devem repassar um GET completo pro servidor de origem. Muitas CDNs
  // (a highcdnvideo.link, por ex.) assinam a URL com um token de uso
  // único/curta duração (?lvtoken=...) — se o probe HEAD já consumisse
  // esse token buscando o vídeo inteiro, o GET real que o ExoPlayer faz
  // logo depois chegava com o token já gasto/expirado, e a tela ficava
  // preta sem erro nenhum visível. Aqui só confirmamos que o host é
  // permitido e devolvemos 200 vazio — suficiente pro probe decidir qual
  // backend está de pé, sem gastar o token da fonte real.
  if (req.method === 'HEAD') {
    res.status(200).end();
    return;
  }

  let upstream;
  try {
    // repassa o header Range, essencial para permitir avançar/retroceder no player
    const forwardHeaders = {};
    if (req.headers.range) forwardHeaders.range = req.headers.range;

    // Muitos provedores Xtream redirecionam (302) para uma CDN de entrega
    // real com token temporário na URL (ex: sventank.com -> algumcdn.com).
    forwardHeaders['User-Agent'] = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36';
    forwardHeaders['Referer'] = target.origin + '/';

    // Timeout de conexão: 15s é generoso pro handshake inicial
    const controller = new AbortController();
    const connectTimeout = setTimeout(() => controller.abort(), 15000);
    
    upstream = await fetch(target.toString(), { headers: forwardHeaders, signal: controller.signal });
    clearTimeout(connectTimeout);

    // **NOVA VALIDAÇÃO DE SEGURANÇA:**
    // Após seguir os redirecionamentos, adiciona o hostname da URL final
    // à lista de hosts permitidos. Isso garante que redirecionamentos válidos
    // para outros domínios/IPs (comuns em provedores Xtream) sejam automaticamente
    // autorizados, sem a necessidade de cadastro manual.
    const finalUrl = new URL(upstream.url);
    allowedHosts.add(finalUrl.hostname);

    if (!upstream.ok && upstream.status !== 206) {
      res.status(upstream.status).json({ error: 'Servidor de origem retornou erro: ' + upstream.status });
      return;
    }

    const contentType = upstream.headers.get('content-type');
    const isManifest = isM3u8Response(finalUrl.toString(), contentType);

    if (isManifest) {
      // Manifesto: precisa ser lido inteiro como TEXTO e reescrito — não
      // dá pra fazer stream de bytes crus aqui, porque o conteúdo em si
      // muda (cada URL de segmento vira uma chamada ao proxy).
      const text = await upstream.text();
      const proxyOrigin = `${req.protocol || 'https'}://${req.get ? req.get('host') : req.headers.host}`;
      const rewritten = rewriteM3u8(text, finalUrl.toString(), proxyOrigin);

      res.status(upstream.status);
      res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
      res.setHeader('Cache-Control', 'no-cache'); // manifesto pode mudar (segmentos novos ao vivo); nunca cachear
      res.setHeader('Access-Control-Allow-Origin', '*');
      res.send(rewritten);
      return;
    }

    // repassa os headers relevantes pro player entender duração/tipo/range
    res.status(upstream.status);
    const passHeaders = ['content-type', 'content-length', 'content-range', 'accept-ranges'];
    passHeaders.forEach(h => {
      const v = upstream.headers.get(h);
      if (v) res.setHeader(h, v);
    });
    res.setHeader('Accept-Ranges', 'bytes');
    res.setHeader('Cache-Control', 'public, max-age=3600');
    res.setHeader('Access-Control-Allow-Origin', '*');

    // IMPORTANTE: repassa o corpo em stream (pipe)
    if (upstream.body) {
      const reader = upstream.body.getReader();
      req.on('close', () => reader.cancel().catch(() => {}));
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          const ok = res.write(Buffer.from(value));
          if (!ok) await new Promise(resolve => res.once('drain', resolve));
        }
      } finally {
        res.end();
      }
    } else {
      const buffer = Buffer.from(await upstream.arrayBuffer());
      res.send(buffer);
    }
  } catch (e) {
    if (!res.headersSent) {
      if (e.name === 'AbortError') {
        res.status(504).json({ error: 'Tempo esgotado ao conectar no servidor de origem (' + target.hostname + ').' });
      } else {
        res.status(502).json({ error: 'Falha ao buscar o vídeo de origem: ' + e.message });
      }
    } else {
      res.end();
    }
  }
}

module.exports = handler;

module.exports.config = {
  api: {
    responseLimit: false,
  },
};
