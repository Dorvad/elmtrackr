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
        // Tesseract4Android (on-device Hebrew OCR) is published on JitPack only.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.adaptech-cz.Tesseract4Android")
            }
        }
    }
}

rootProject.name = "ElmTrackr"
include(":app")
include(":wear-sync")
include(":wear")
