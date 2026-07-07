# StreamFlixVIP — espelho rodando no Zeabur

Este projeto é uma cópia do StreamFlixVIP adaptada para rodar como servidor
Express no Zeabur, em paralelo com a instância que já roda na Vercel — mesmo
Supabase, mesmo catálogo, mesma lógica de cada endpoint.

## Por que existe

A Vercel (plano Hobby) tem duas limitações que já causaram problema:
1. Máximo de 12 Serverless Functions por deploy.
2. Bloqueio de "fair use" quando o uso (banda/execução) passa do limite do
   plano gratuito — que já pausou o site inteiro uma vez.

Rodar uma cópia no Zeabur dá redundância: se a Vercel cair ou pausar de novo,
o Zeabur continua no ar (e vice-versa).

## O que mudou em relação à versão Vercel

Cada arquivo em `api/*.js` já usava o formato
`module.exports = async function handler(req, res) {...}`, que é compatível
quase sem alteração com Express — `req.query`, `req.body`, `req.method`,
`req.headers`, `res.status().json()` funcionam igual nos dois. Por isso os
12 arquivos de API foram copiados **sem mudança na lógica interna**, só
"plugados" como rotas dentro de `server.js`.

## Passo a passo do deploy

1. **Criar o projeto no Zeabur**: painel do Zeabur → "New Project" → conectar
   este repositório (ou fazer upload direto da pasta).
2. **Configurar as variáveis de ambiente**: aba "Variables" do serviço →
   copiar os mesmos valores que já estão na Vercel (ver `.env.example` pra
   saber quais). São as MESMAS credenciais — não precisa gerar nada novo.
3. **Deploy**: o Zeabur detecta o `package.json` e roda `npm install && npm start`
   automaticamente.
4. **Testar**: acessar a URL que o Zeabur gerar (tipo
   `streamflixvip.zeabur.app`) e conferir se o site carrega e o admin
   funciona igual ao da Vercel.

## ⚠️ Atenção: cron job de sincronização IPTV

Na Vercel, o `vercel.json` tinha um cron nativo:
```json
"crons": [{ "path": "/api/iptv-sync", "schedule": "0 4 * * *" }]
```
O Zeabur **não tem cron nativo equivalente** for esse tipo de projeto. Para
manter a sincronização diária funcionando também nesta cópia, use um
serviço externo gratuito de agendamento, por exemplo:
- [cron-job.org](https://cron-job.org) (gratuito) — configurar para fazer
  uma requisição `GET` diária para
  `https://SEU-DOMINIO-ZEABUR/api/iptv-sync?secret=SEU_CRON_SECRET`
  (o `CRON_SECRET` é o mesmo valor configurado na env var).

Sem isso, a sincronização IPTV **só vai rodar na instância da Vercel** (que
já tem o cron nativo) — o que é aceitável se você mantiver a Vercel como
fonte "principal" de sincronização e o Zeabur como espelho/backup de leitura.

## Estrutura

```
server.js          → servidor Express, registra as rotas e serve os estáticos
api/*.js            → os 12 endpoints, copiados sem mudança de lógica
lib/iptv-parser.js  → módulo auxiliar usado por api/iptv-sync.js
public/             → index.html, admin.html, embed/, manifest.json, etc
                       (equivalente ao que ficava solto na raiz na Vercel)
```

## Diferenças a manter em mente

- **Domínio próprio**: o Zeabur gera uma URL tipo `*.zeabur.app` por padrão;
  se quiser um domínio customizado (ex: um subdomínio dedicado), configure
  isso nas configurações do serviço no painel do Zeabur.
- **Custo**: diferente do "12 functions" da Vercel, o Zeabur cobra por
  recurso do container (CPU/memória/tempo rodando) — confira o plano atual
  no seu painel Zeabur antes de assumir que é gratuito para este uso.
