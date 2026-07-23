# StreamFlixVIP — App Android TV

App de streaming para Android TV com design moderno inspirado em referências de entusiastas (Streambox, serivia, PlayBox). Completamente diferente de Netflix/Disney+ — focado em eficiência visual com controles de D-pad otimizados.

## Funcionalidades

### Home Moderna
- Hero cinematográfico com backdrop grande + gradiente duplo para legibilidade
- Pills de metadado (ano, gênero, nota com estrela)
- Sinopse truncada + botões dourados (Assistir, Trailer, +)
- Sidebar fixa à esquerda com ícones de navegação (Home, Buscar, Perfil, Configurações)
- 10 carrosséis horizontais por categoria: Em Alta, Filmes Populares, Séries Populares, Ação, Comédia, Drama, Terror, Ficção Científica, Animes, Família
- Cards de pôster com badge de nota amarelo no canto superior

### Tela de Detalhe
- Hero com backdrop + poster sobreposto + info completa
- Tagline, título, metadados (ano, duração, gêneros, nota)
- Sinopse + botões Assistir e Trailer
- Seção de Elenco (cards circulares com foto + nome + personagem)
- Temporadas e Episódios (navegação por temporada com still + título + sinopse + botão Assistir)
- Títulos Similares (carrossel horizontal)
- Seletor de servidor (modal central para múltiplas fontes)

### Player Nativo (ExoPlayer/Media3)
- Player nativo com ExoPlayer para streams HLS/MP4
- WebView fallback para embeds de terceiros (MegaEmbed, etc.)
- Painel de configurações no canto inferior direito com:
  - **Aspect Ratio**: 16:9, Preencher, Zoom, 21:9
  - **Legendas**: seleção de faixas, on/off
  - **Áudio**: seleção de faixas de áudio
  - **Qualidade**: seleção manual (4K, 1080p, 720p, 480p)
  - **Velocidade**: 0.5x a 2x
  - **Abrir no VLC**: player externo
- Controles auto-hide após 4 segundos
- Título e info do episódio no topo durante controles
- Headers VLC para compatibilidade com provedores IPTV

### Integração TMDB
- API via proxy Express do backend (api/tmdb.js)
- Trending (dia/semana), populares, filmes/séries por gênero
- Detalhes completos: elenco, temporadas, episódios, similares, trailers
- Filtros por idioma original para Animes

### Integração Supabase
- Fontes de vídeo via PostgREST (vip_sources)
- Bloqueio VIP por título (vip_titles)
- Progresso de reprodução (watch_progress)
- Favoritos (favorites)
- Comentários (title_comments)

## Arquitetura

```
android-tv/
├── app/
│   ├── build.gradle.kts          # Configuração Gradle com Compose for TV
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/streamflixvip/tv/
│       │   ├── MainTvActivity.kt           # Activity única + Navigation Compose
│       │   ├── StreamFlixTvApp.kt          # Application class
│       │   ├── data/
│       │   │   ├── SessionStore.kt         # Persistência de sessão/auth
│       │   │   └── VipStatusHolder.kt      # Status VIP compartilhado
│       │   ├── network/
│       │   │   ├── NetworkModule.kt        # OkHttpClient + Retrofit singletons
│       │   │   ├── TmdbApi.kt              # Interface Retrofit TMDB
│       │   │   ├── SupabaseApi.kt          # Interface Retrofit Supabase
│       │   │   ├── SupabaseAuthApi.kt      # Auth Supabase
│       │   │   ├── VipApi.kt               # Endpoints VIP Express
│       │   │   ├── AppVersionApi.kt        # Verificação de versão
│       │   │   └── StreamUrlResolver.kt    # Resolver de URLs de stream
│       │   └── ui/
│       │       ├── home/
│       │       │   ├── HomeTvScreen.kt     # Tela Home com hero + carrosséis
│       │       │   └── HomeTvViewModel.kt  # ViewModel com cache
│       │       ├── detail/
│       │       │   ├── DetailTvScreen.kt   # Tela de Detalhe completa
│       │       │   └── DetailTvViewModel.kt # ViewModel com temporadas
│       │       ├── player/
│       │       │   └── PlayerTvScreen.kt   # Player ExoPlayer com controles
│       │       └── theme/
│       │           └── TvTheme.kt          # Tema escuro + dourado
│       └── res/
│           ├── drawable/
│           ├── mipmap-anydpi-v26/
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── xml/
│               └── network_security_config.xml
├── build.gradle.kts              # Gradle root
├── settings.gradle.kts
└── gradle.properties
```

## Requisitos

- Android Studio Hedgehog ou superior
- JDK 17
- Gradle 8.x
- Min SDK: 24 (Android 7.0)
- Target SDK: 34 (Android 14)
- Compilar com API 34

## Dependências Principais

| Biblioteca | Versão | Propósito |
|---|---|---|
| androidx.compose:compose-bom | 2024.06.00 | Compose base |
| androidx.tv:tv-foundation | 1.0.0-alpha10 | Compose for TV foundation |
| androidx.tv:tv-material | 1.0.0 | Compose for TV Material |
| androidx.media3:media3-exoplayer | 1.3.1 | Player nativo |
| androidx.media3:media3-exoplayer-hls | 1.3.1 | Suporte HLS |
| androidx.media3:media3-ui | 1.3.1 | UI do ExoPlayer |
| coil-compose | 2.6.0 | Carregamento de imagens |
| retrofit | 2.11.0 | HTTP client |
| moshi | 1.15.1 | JSON parsing |

## Como Buildar

1. Abra a pasta `android-tv/` no Android Studio
2. Aguarde o Gradle sync completar
3. Conecte um dispositivo Android TV ou emulador TV
4. Clique em Run (Shift+F10) ou use `./gradlew assembleDebug`

## Variáveis de Build

As variáveis são definidas em `app/build.gradle.kts`:

- `API_BASE_URL`: URL do backend Express (ex: `https://www.streamflixvip.online/`)
- `SUPABASE_URL`: URL do projeto Supabase
- `SUPABASE_ANON_KEY`: Chave pública Supabase

## Assinatura

A build release usa a mesma keystore do app mobile. As variáveis de ambiente são:

- `DEBUG_KEYSTORE_PATH`
- `DEBUG_KEYSTORE_PASSWORD`
- `DEBUG_KEY_ALIAS`
- `DEBUG_KEY_PASSWORD`

Se não estiverem definidas, usa valores de debug padrão.

## Paleta de Cores

| Cor | Hex | Uso |
|---|---|---|
| Background | `#0A0A10` | Fundo principal escuro |
| Surface | `#15151C` | Cards e superfícies |
| Surface Variant | `#232330` | Elementos elevados |
| Primary (Gold) | `#D4AF37` | Botões, destaques, badges |
| Rating | `#FFC107` | Badge de nota |

## Fluxo de Navegação

```
Home ──click──▶ Detail ──Assistir──▶ Server Picker ──select──▶ Player
  ▲                │                    ▲                        │
  │                │                    │                        │
  └──volta─────────┘                    └──volta─────────────────┘
```

1. **Home**: Carrega todas as seções em paralelo via ViewModel com cache
2. **Detail**: Carrega detalhes TMDB + elenco + similares + temporadas em paralelo
3. **Server Picker**: Mostra se há múltiplas fontes; fonte única pula direto
4. **Player**: Carrega URL via StreamUrlResolver (race Koyeb vs Zeabur)

## Paleta de Cores e Design

O design segue o padrão dos prints de referência:

- **Fundo escuro profundo** (`#0A0A10`) para imersão cinematográfica
- **Dourado** (`#D4AF37`) como cor de destaque — botões, badges, ícones selecionados
- **Gradientes duplos** no hero (horizontal + vertical) para legibilidade do texto
- **Cards com cantos arredondados** (8dp) e cores de superfície escura
- **Badge de nota amarelo** no canto superior dos pôsteres
- **Elenco em cards circulares** com foto + nome + personagem
- **Controles do player** minimalistas com auto-hide
