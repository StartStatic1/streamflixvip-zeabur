pluginManagement {
    repositories {
        maven { url = uri("https://maven.start.io/artifactory/libs-release-local") }
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

rootProject.name = "StreamFlixVIP"
include(":app")
