// app/build.gradle.kts
//
// Dependências escolhidas com propósito:
// - Compose: UI declarativa nativa (equivalente moderno de views XML)
// - Media3/ExoPlayer: player NATIVO real, usado pros embeds Bunny/MP4
//   diretos e HLS (.m3u8) das fontes IPTV — é o que dá a diferença real
//   de "app de verdade" vs WebView na hora de assistir.
// - Retrofit + Moshi: cliente HTTP tipado pra falar com /api/tmdb (o
//   mesmo proxy Express que o site já usa) e com a REST API do Supabase
//   direto (mesmo padrão do db.from('vip_sources') que o index.html usa).
// - Coil: carregamento de imagem (pôsteres/backdrops do TMDB) com cache,
//   equivalente nativo do que o navegador faz sozinho com <img>.
// - WebView (androidx.webkit): usado APENAS na tela de player quando a
//   fonte selecionada é um iframe de terceiro sem URL direta — não é a
//   base do app, é só uma tela isolada acionada sob demanda.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.streamflixvip.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.streamflixvip.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // URL base do backend Express — o MESMO domínio que o site usa
        // hoje (Koyeb). Trocar aqui se o domínio mudar de novo no futuro,
        // sem precisar caçar a string espalhada pelo código.
        buildConfigField("String", "API_BASE_URL", "\"https://logical-canidae-meu-bot-cinema-39259a5d.koyeb.app/\"")
        buildConfigField("String", "SUPABASE_URL", "\"https://gkujbjpvphuvrejpvvtz.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdrdWpianB2cGh1dnJlanB2dnR6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzg2OTQ2OTMsImV4cCI6MjA5NDI3MDY5M30.Zoqdn0V6SZOAfhz9kK9NgG6lniJdyVqihLsNT-O8Huw\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Necessário pra java.time (OffsetDateTime/DateTimeFormatter, usados
        // em VipSection pra formatar data de expiração) funcionar em
        // minSdk 24-25 — essas APIs só existem nativamente a partir da API 26.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    // Explícitas (não só transitivas via material3) porque usamos
    // HorizontalPager (foundation) e animateFloatAsState (animation) no
    // banner rotativo da Home — melhor garantir a versão certa do BOM
    // do que depender de resolução transitiva.
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // "extended" traz ícones que não vêm no core (Groups, FavoriteBorder,
    // Share, OpenInNew, etc.) — usados na bottom bar e no modal "Como
    // deseja assistir". É um artefato bem maior que o core, mas evita
    // ter que desenhar ícone customizado pra cada um desses.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // Player nativo (Media3/ExoPlayer) — toca HLS (.m3u8) e MP4 direto
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Rede: Retrofit fala com /api/tmdb (proxy Express) e com a REST API
    // do Supabase (mesma anon key pública que o site usa no navegador)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Carregamento de imagem (pôsteres/backdrops do TMDB)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // WebView isolado, só pra fontes que são iframe de terceiro
    implementation("androidx.webkit:webkit:1.11.0")

    // Material Components for Android — necessário pois themes.xml usa
    // parent="Theme.Material3.DayNight.NoActionBar", que vem desta lib
    // (diferente do androidx.compose.material3, que não define estilos XML).
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    testImplementation("junit:junit:4.13.2")
}
