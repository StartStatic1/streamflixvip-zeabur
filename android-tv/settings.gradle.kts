// settings.gradle.kts (nível raiz do módulo android-tv)
//
// Este é um projeto Gradle SEPARADO do android/ (celular) — cada um com
// seu próprio settings.gradle.kts, mas vivendo lado a lado no mesmo
// repositório GitHub. Não são "submódulos" um do outro; é mais simples
// assim: dois projetos Android independentes, compilados cada um com seu
// próprio `./gradlew`, cada um gerando seu APK, sem risco de uma mudança
// de configuração de um afetar o outro.
//
// Os arquivos de rede/dados (SupabaseApi.kt, VipApi.kt etc.) são
// duplicados aqui em vez de compartilhados via módulo Gradle `:core` —
// escolha deliberada pra manter o setup simples de operar só pelo
// Termux/GitHub mobile, sem multi-módulo Gradle cross-project. O custo é
// ter que copiar manualmente qualquer mudança de API pros dois lados;
// ver o comentário no topo de network/ para o checklist dessa sincronia.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "StreamFlixVIP-TV"
include(":app")
