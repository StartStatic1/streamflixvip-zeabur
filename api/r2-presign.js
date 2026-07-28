// api/r2-presign.js
// Gera uma URL de upload temporária (assinada) pro Cloudflare R2 — o painel
// pede essa URL aqui, e o navegador manda o arquivo DIRETO pro R2 usando
// ela. A chave secreta do R2 nunca é vista pelo navegador: ela fica só
// nas variáveis de ambiente do servidor, aqui.
//
// Isso substitui o antigo painel_r2.html, que tinha accessKeyId e
// secretAccessKey escritos direto no JavaScript do navegador — qualquer
// pessoa que abrisse a página ou visse o código-fonte conseguia roubar as
// duas chaves e ler/escrever/apagar o bucket inteiro. Depois de trocar a
// chave vazada no painel da Cloudflare, configure as NOVAS credenciais
// aqui embaixo, só no servidor.
//
// Variáveis de ambiente necessárias (Hetzner/PM2, nunca no código):
//   R2_ACCOUNT_ID          — ID da conta Cloudflare (parte do endpoint)
//   R2_ACCESS_KEY_ID       — access key NOVA (pós-rotação)
//   R2_SECRET_ACCESS_KEY   — secret key NOVA (pós-rotação)
//   R2_BUCKET_NAME         — nome do bucket (ex: streamflix-uploads)
//   R2_PUBLIC_BASE_URL     — base pública, ex: https://pub-xxxx.r2.dev

const { S3Client, PutObjectCommand } = require('@aws-sdk/client-s3');
const { getSignedUrl } = require('@aws-sdk/s3-request-presigner');

function sanitizeFilename(name) {
  return String(name || 'arquivo')
    .replace(/[^a-zA-Z0-9._-]+/g, '_')
    .slice(-150); // evita nome absurdamente longo, mantém o fim (extensão)
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  if (req.method !== 'POST') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }

  const { R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_BUCKET_NAME, R2_PUBLIC_BASE_URL } = process.env;
  if (!R2_ACCOUNT_ID || !R2_ACCESS_KEY_ID || !R2_SECRET_ACCESS_KEY || !R2_BUCKET_NAME || !R2_PUBLIC_BASE_URL) {
    res.status(500).json({ error: 'Variáveis de ambiente do R2 não configuradas no servidor.' });
    return;
  }

  let body = req.body;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch (e) { body = {}; }
  }
  const originalName = body?.filename;
  const contentType = body?.contentType || 'application/octet-stream';
  if (!originalName) {
    res.status(400).json({ error: 'Informe "filename".' });
    return;
  }

  // Mesmo padrão que já era usado antes: timestamp na frente evita
  // colisão de nomes de dois uploads com o mesmo arquivo.
  const key = `${Date.now()}-${sanitizeFilename(originalName)}`;

  try {
    const s3 = new S3Client({
      region: 'auto',
      endpoint: `https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com`,
      credentials: {
        accessKeyId: R2_ACCESS_KEY_ID,
        secretAccessKey: R2_SECRET_ACCESS_KEY,
      },
    });

    const command = new PutObjectCommand({
      Bucket: R2_BUCKET_NAME,
      Key: key,
      ContentType: contentType,
    });

    // Válida por 15 minutos — tempo de sobra pra um upload de vídeo
    // grande em conexão lenta, sem deixar a URL "aberta" por muito tempo.
    const uploadUrl = await getSignedUrl(s3, command, { expiresIn: 900 });
    const publicUrl = `${R2_PUBLIC_BASE_URL.replace(/\/$/, '')}/${key}`;

    res.status(200).json({ uploadUrl, publicUrl, key });
  } catch (err) {
    console.error('r2-presign error:', err);
    res.status(500).json({ error: 'Erro ao gerar URL de upload.' });
  }
};
