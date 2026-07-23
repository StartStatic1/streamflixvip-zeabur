// build.gradle.kts (nível raiz do projeto android-tv)
// Mesmas versões de plugin do app de celular (android/build.gradle.kts),
// pra evitar duas versões de Kotlin/AGP diferentes convivendo no mesmo
// repositório sem necessidade.

plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
