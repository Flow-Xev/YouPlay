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
            content {
                includeGroup("com.github.MetrolistGroup.innertubex")
                includeGroup("com.github.MetrolistGroup.quackjs")
                includeGroupByRegex("com\\.github\\.MetrolistGroup(\\..*)?")
                // NewPipeExtractor (+ its nanojson dependency) - the stream engine Flow uses
                includeGroupByRegex("com\\.github\\.(TeamNewPipe|teamnewpipe)(\\..*)?")
            }
        }
    }
}

rootProject.name = "youplay"
include(":app")
