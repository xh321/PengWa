pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content { includeModule("com.github.aptabase", "aptabase-kotlin") }
        }
    }
}

rootProject.name = "PengWa"
include(":app")
include(":core:model", ":core:protocol", ":core:data", ":core:bluetooth")
include(":core:designsystem", ":core:nfc", ":core:media")
