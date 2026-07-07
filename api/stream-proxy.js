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
];

let _hostsCache = { hosts: new Set(EXTRA_ALLOWED_HOSTS), fetchedAt: 0 };
// Reduzido de 60s para 10s: com 60s, cadastrar uma fonte nova no admin e
// testar o vídeo logo em seguida batia num cache antigo (sem o host novo)
// e o proxy bloqueava com 403 — o player então mostrava "Servidor
// indisponível" mesmo a fonte estando correta, só por timing. 10s ainda
// evita consultar o Supabase a cada chunk de vídeo (que seria centenas de
// queries por minuto durante uma reprodução), mas deixa o admin utilizável
// quase na hora depois de cadastrar/editar uma fonte.
const CACHE_TTL_MS = 10 * 1000;

// ── Limite de streams simultâneos ──
// Cada requisição de vídeo aqui mantém um buffer de chunks na memória do
// processo Node enquanto faz o pipe origem→navegador (ver comentário mais
// abaixo sobre por que usamos stream em vez de arrayBuffer). Numa VM de
// 2GB, isso significa que memória sobe com o NÚMERO de pessoas assistindo
// ao mesmo tempo, não com CPU — é exatamente o padrão visto no painel
// Zeabur (72% de RAM com só 6% de CPU). Sem um teto, muitos streams
// simultâneos enchem a RAM e o processo trava/reinicia (os "502" e as
// várias instâncias em "Starting" que apareceram no painel). Este limite
// rejeita novas conexões de vídeo além do teto com uma mensagem clara, em
// vez de deixar a VM inteira travar para todo mundo.
const MAX_CONCURRENT_STREAMS = 15; // ajuste conforme observar o uso real de RAM por stream
let _activeStreams = 0;

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
      // Log de diagnóstico: se aparecer nos Runtime logs da Zeabur com
      // contagens de hosts diferentes entre si (ex: um log com 4 hosts,
      // outro com 6, alternando sem padrão), é sinal de que existe mais
      // de um processo Node rodando simultaneamente na mesma VM, cada um
      // com seu próprio cache em memória — nesse caso o problema não é
      // mais o tempo de cache, e sim a Zeabur duplicando o processo (ver
      // aquelas instâncias extras em "Starting" no painel).
      console.log(`stream-proxy: cache de hosts atualizado (${hosts.size} hosts) — pid ${process.pid}`);
    }
  } catch (e) {
    console.error('stream-proxy: falha ao atualizar hosts, mantendo cache:', e);
  }
  return _hostsCache.hosts;
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
  if (!allowedHosts.has(target.hostname)) {
    res.status(403).json({ error: 'Domínio não autorizado. Cadastre a fonte no painel admin primeiro.' });
    return;
  }

  if (_activeStreams >= MAX_CONCURRENT_STREAMS) {
    res.status(503).json({ error: 'Servidor com muitos streams simultâneos no momento. Tente novamente em instantes ou escolha outro servidor.' });
    return;
  }
  _activeStreams++;

  try {
    // ── Estratégia de User-Agent por tentativa, não fixo ──
    // Testamos e confirmamos que hosts diferentes (unitvlite.xyz, sventank.com,
    // cdnbr03.com) reagem de forma DIFERENTE ao mesmo User-Agent: um fixo
    // "VLC/3.0.4 LibVLC/3.0.4" ajudou o cdnbr03.com mas quebrou o unitvlite.xyz.
    // Cada operador de painel Xtream/IPTV configura seu próprio firewall e
    // regras do Cloudflare, então não existe um UA universal que sirva pra
    // todos. Em vez de fixar um valor e trocar qual fonte quebra a cada
    // ajuste, tentamos primeiro SEM forçar UA (deixando o padrão do Node) e,
    // só se a origem recusar (403/406/erro), tentamos de novo com o UA de
    // player de vídeo como fallback — assim cada fonte usa o que funciona
    // para ela, automaticamente, sem precisar de configuração manual por host.
    async function tryFetch(withPlayerUA) {
      const headers = {};
      if (req.headers.range) headers.range = req.headers.range;
      if (withPlayerUA) headers['User-Agent'] = 'VLC/3.0.4 LibVLC/3.0.4';
      return fetch(target.toString(), { headers, redirect: 'follow' });
    }

    let upstream = await tryFetch(false);

    // Se a tentativa "neutra" falhou de forma que sugere bloqueio por UA
    // (403 Forbidden, 406 Not Acceptable), tenta de novo com o UA de player.
    if (!upstream.ok && upstream.status !== 206 && [403, 406].includes(upstream.status)) {
      upstream = await tryFetch(true);
    }

    if (!upstream.ok && upstream.status !== 206) {
      res.status(upstream.status).json({ error: 'Servidor de origem retornou erro: ' + upstream.status });
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

    // IMPORTANTE: repassa o corpo em stream (pipe), sem baixar o arquivo
    // inteiro pra memória antes de responder. Com arrayBuffer() o vídeo
    // inteiro (pode passar de 1-2GB) precisa terminar de baixar do servidor
    // de origem ANTES do navegador receber o primeiro byte — isso estoura
    // o tempo máximo de execução da function (10s no plano Hobby da Vercel)
    // e o limite de memória, e o player fica girando pra sempre. Streaming
    // manda os bytes pro navegador conforme chegam da origem.
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
      // fallback (ambiente sem suporte a stream do fetch): buffer completo
      const buffer = Buffer.from(await upstream.arrayBuffer());
      res.send(buffer);
    }
  } catch (e) {
    if (!res.headersSent) {
      res.status(502).json({ error: 'Falha ao buscar o vídeo de origem: ' + e.message });
    } else {
      res.end();
    }
  } finally {
    // Sempre libera a "vaga" de stream, mesmo em caso de erro, timeout,
    // ou usuário fechando a página no meio — senão o contador só cresce
    // e MAX_CONCURRENT_STREAMS trava novos streams pra sempre.
    _activeStreams--;
  }
}

module.exports = handler;

module.exports.config = {
  api: {
    responseLimit: false, // vídeos costumam passar do limite padrão de resposta da Vercel
  },
};
