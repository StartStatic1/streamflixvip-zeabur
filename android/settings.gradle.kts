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
        // ADICIONE ESTA LINHA AQUI EMBAIXO TAMBÉM:
        maven { url = uri("https://maven.start.io/artifactory/libs-release-local") }
    }
}

rootProject.name = "StreamFlixVIP"
include(":app")
