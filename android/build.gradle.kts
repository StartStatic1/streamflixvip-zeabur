// build.gradle.kts (nível raiz do projeto)
// Só declara versões de plugins aqui — a configuração real de cada módulo
// fica no build.gradle.kts de app/. Isso é o padrão moderno do Android
// Studio (Gradle plugin DSL), evita duplicar versões em vários lugares.

plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
