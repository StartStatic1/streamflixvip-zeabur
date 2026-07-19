// api/app-version.js
//
// Endpoint consultado pelo app Android nativo (StreamFlixVIP) toda vez
// que ele abre, para saber se existe uma versão mais nova disponível.
//
// Como publicar uma atualização (o que você faz manualmente):
//   1. Builda o novo APK (GitHub Actions já faz isso — gera
//      app-debug.apk assinado com a keystore fixa).
//   2. Sobe esse APK em algum lugar público (seu site ou Bunny.net) e
//      pega a URL direta de download.
//   3. Edita o arquivo app-version.json (na raiz do repo) com o novo
//      número de versão e a nova URL.
//   4. Dá commit/push. Pronto — não precisa mexer em código nem reiniciar
//      nada, o Zeabur já serve o arquivo atualizado no próximo request.
//
// O app compara "versionCode" com o BuildConfig.VERSION_CODE dele. Se o
// valor daqui for MAIOR que o instalado, mostra a tela de atualização
// obrigatória. Por isso é essencial incrementar versionCode a cada nova
// versão do APK — nunca usar dois builds diferentes com o mesmo número.
//
// Uso no app:
//   GET /api/app-version

const fs = require('fs');
const path = require('path');

const VERSION_FILE = path.join(__dirname, '..', 'app-version.json');

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Cache-Control', 'no-store'); // nunca cachear — o app precisa
                                               // sempre ver o valor mais recente
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }

  try {
    const raw = fs.readFileSync(VERSION_FILE, 'utf-8');
    const data = JSON.parse(raw);

    // Validação básica — se alguém editar o JSON errado, prefere avisar
    // no log do servidor a devolver algo quebrado que trava o app.
    if (
      typeof data.versionCode !== 'number' ||
      typeof data.versionName !== 'string' ||
      typeof data.apkUrl !== 'string'
    ) {
      console.error('app-version.json com formato inválido:', data);
      res.status(500).json({ error: 'Configuração de versão inválida no servidor' });
      return;
    }

    res.status(200).json({
      versionCode: data.versionCode,
      versionName: data.versionName,
      apkUrl: data.apkUrl,
      forceUpdate: data.forceUpdate !== false, // default true se omitido
      releaseNotes: data.releaseNotes || '',
    });
  } catch (err) {
    console.error('Erro ao ler app-version.json:', err);
    res.status(500).json({ error: 'Não foi possível verificar a versão' });
  }
};
