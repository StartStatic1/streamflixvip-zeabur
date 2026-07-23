// app/build.gradle.kts (módulo TV)
//
// Diferenças de propósito em relação ao android/app/build.gradle.kts
// (celular):
// - androidx.tv:tv-material no lugar de material3 puro: os componentes
//   dessa lib (Card, Button, LazyRow etc. do pacote androidx.tv.material3)
//   já vêm com destaque de foco (scale/glow) e movimentação de D-pad
//   corretos por padrão — é o que faltava no app de celular rodando no
//   Fire Stick, que dependia só do foco genérico do Android sem nenhum
//   tratamento visual ou de navegação intencional.
// - applicationId diferente (.tv no final): permite instalar os dois
//   apps (celular e TV) ao mesmo tempo no mesmo dispositivo/conta sem
//   um substituir o outro.
// - android:banner exigido pela Play Store/launcher de TV (ver Manifest)
//   — androidTv precisa desse recurso extra que o app de celular não usa.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.streamflixvip.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.streamflixvip.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Mesmo backend do app de celular — API_BASE_URL, SUPABASE_URL e
        // SUPABASE_ANON_KEY precisam ficar IDÊNTICOS aos valores em
        // android/app/build.gradle.kts. Se um dia esses valores mudarem
        // lá (troca de domínio, rotação de chave), replicar aqui também.
        buildConfigField("String", "API_BASE_URL", "\"https://www.streamflixvip.online/\"")
        buildConfigField("String", "SUPABASE_URL", "\"https://gkujbjpvphuvrejpvvtz.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdrdWpianB2cGh1dnJlanB2dnR6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzg2OTQ2OTMsImV4cCI6MjA5NDI3MDY5M30.Zoqdn0V6SZOAfhz9kK9NgG6lniJdyVqihLsNT-O8Huw\"")
    }

    signingConfigs {
        // Keystore separada da do app de celular de propósito — os dois
        // apps têm applicationId diferentes, então não existe motivo pra
        // compartilhar a mesma chave. Mesmas variáveis de ambiente do
        // GitHub Actions do celular funcionam aqui se você reaproveitar
        // o mesmo Secret; ou criar Secrets TV_* separados se preferir
        // rotacionar as chaves de forma independente no futuro.
        getByName("debug") {
            storeFile = file(System.getenv("DEBUG_KEYSTORE_PATH") ?: "debug.keystore")
            storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: "streamflixvip123"
            keyAlias = System.getenv("DEBUG_KEY_ALIAS") ?: "streamflixvip-debug"
            keyPassword = System.getenv("DEBUG_KEY_PASSWORD") ?: "streamflixvip123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
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
    // Compose base (mesmo BOM do app de celular, pra manter Kotlin/Compose
    // compiler compatíveis entre os dois módulos)
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    // Compose for TV — Card, Button, LazyRow, ImmersiveList etc. com
    // suporte nativo a foco/D-pad. É a peça central que faltava no app
    // de celular rodando no Fire Stick.
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0")

    // Player nativo — mesmas libs do app de celular
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Rede — mesmo padrão do app de celular (Retrofit + Moshi)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Carregamento de imagem — pôsteres/backdrops em telas grandes de TV
    // se beneficiam ainda mais de cache do que no celular
    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    testImplementation("junit:junit:4.13.2")
}
