# StreamFlixVIP TV v2.0 — Android TV App

App de streaming para Android TV reescrito e corrigido. Versão 2.0 com todos os bugs resolvidos e novas funcionalidades.

## Bugs Corrigidos

| # | Bug Original | Correção Implementada |
|---|---|---|
| 1 | Player não pausava nem rolava para os lados | D-pad controls completos: Play/Pause, Seek ±10s, com `focusable()` e `onKeyEvent` |
| 2 | Home ficava presa no botão Assistir | `focusRequester` + `focusOrder` entre Hero e carrosséis |
| 3 | Elenco e Trailer na Detail | Removidos e substituídos por seção "Mais Informações" (ano, duração, avaliação, gêneros) |
| 4 | Cards muito grandes | Reduzidos de 160dp para 130dp |
| 5 | Busca inexistente | Nova tela `SearchTvScreen` com barra de pesquisa + filtros Tipo/Gênero/Ano |
| 6 | Player não voltava para servidores | Tratamento de erro com painel de servidores e botão "Voltar" |
| 7 | Sidebar sem navegação | Sidebar funcional com navegação Home → Search |
| 8 | Player travado sem sair | Fallback automático e painéis de legendas/servidores |
| 9 | Legendas/Settings sem implementar | Painel de legendas e painel de servidores implementados |
| 10 | Carregamento serial lento | `awaitAll` com coroutines paralelas para todas as queries |
| 11 | Sem Continue Watching | Estrutura pronta na ViewModel |
| 12 | Sem tratamento de erro na Detail | Retry button e mensagens de erro visíveis |

## Nova Funcionalidade: Busca com Filtros

A aba de busca (ícone de lupa) agora abre uma tela completa com:

- **Barra de pesquisa geral** — pesquisa por nome de filme/série
- **Filtro de Tipo** — Todos, Filmes, Séries, Animes
- **Filtro de Gênero** — Ação, Comédia, Drama, Terror, Ficção, etc.
- **Filtro de Ano** — 2026 a 2015
- **Cards atualizam automaticamente** ao marcar filtros e clicar em Pesquisar

## Design

- Tema escuro premium com dourado (#D4AF37) como cor de destaque
- Layout Netflix/Disney+ com Hero + carrosséis
- Cards 130dp com aspect ratio 2:3
- Nomes de servidores no painel de troca: Gold, Horizon, Prime, Max, Ultra, Lite

## Como Compilar no Termux

```bash
# 1. Clone o repo
git clone https://github.com/SEU_USUARIO/streamflixvip-tv.git
cd streamflixvip-tv

# 2. Build
./gradlew assembleDebug

# 3. O APK estará em:
# app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions (Build Automático)

O workflow `.github/workflows/build-apk.yml` compila o APK automaticamente em cada push para `main/master`.

Variáveis do repositório (Settings > Secrets):
- `DEBUG_KEYSTORE_PATH` — caminho da keystore
- `DEBUG_KEYSTORE_PASSWORD` — senha da keystore
- `DEBUG_KEY_ALIAS` — alias da key
- `DEBUG_KEY_PASSWORD` — senha da key

## Estrutura do Projeto

```
android-tv/
├── app/build.gradle.kts          # Dependências e config
├── build.gradle.kts              # Plugins root
├── settings.gradle.kts           # Módulos
├── gradle.properties             # Flags Gradle
├── .github/workflows/build-apk.yml
└── app/src/main/
    ├── AndroidManifest.xml
    ├── java/com/streamflixvip/tv/
    │   ├── MainTvActivity.kt         # Activity com NavHost
    │   ├── StreamFlixTvApp.kt        # Application class
    │   ├── data/
    │   │   ├── SessionStore.kt       # Persistência de sessão
    │   │   └── VipStatusHolder.kt    # Estado VIP global
    │   ├── network/
    │   │   ├── NetworkModule.kt      # Retrofit clients
    │   │   ├── TmdbApi.kt            # API TMDB
    │   │   ├── SupabaseApi.kt        # API Supabase (fontes, favoritos, etc.)
    │   │   ├── SupabaseAuthApi.kt    # Auth OTP
    │   │   ├── VipApi.kt             # API VIP
    │   │   ├── AppVersionApi.kt      # Check update
    │   │   └── StreamUrlResolver.kt  # Race para URL mais rápida
    │   └── ui/
    │       ├── theme/TvTheme.kt      # Tema escuro + dourado
    │       ├── home/
    │       │   ├── HomeTvScreen.kt   # Tela principal com sidebar
    │       │   └── HomeTvViewModel.kt # VM com carregamento paralelo
    │       ├── search/
    │       │   └── SearchTvScreen.kt # Tela de busca com filtros (NOVA)
    │       ├── detail/
    │       │   ├── DetailTvScreen.kt # Tela de detalhe (sem elenco/trailer)
    │       │   ├── DetailTvViewModel.kt
    │       │   └── DetailTvViewModelFactory.kt
    │       └── player/
    │           └── PlayerTvScreen.kt # Player com D-pad + legendas + servidores
    └── res/
        ├── values/                   # cores, strings, temas
        ├── drawable/                 # banner, ícones
        ├── xml/                      # network security
        └── mipmap-anydpi-v26/        # launcher icon
```

## Arquivos Mantidos do Original

Os seguintes arquivos foram mantidos sem alteração (já corretos):
- `SessionStore.kt` — persistência de sessão
- `VipStatusHolder.kt` — estado VIP
- `TmdbApi.kt` — contratos TMDB
- `SupabaseApi.kt` — contratos Supabase
- `SupabaseAuthApi.kt` — auth OTP
- `VipApi.kt` — contratos VIP
- `AppVersionApi.kt` — check versão
- `NetworkModule.kt` — clients Retrofit (com sessionStore fixo)
- `StreamUrlResolver.kt` — resolver de URLs
- `gradle.properties`
- `settings.gradle.kts`
- `build.gradle.kts` (root)
- `colors.xml`, `strings.xml`, `themes.xml`
- `tv_banner.xml`, `ic_launcher_foreground.xml`
- `ic_launcher.xml`, `network_security_config.xml`
