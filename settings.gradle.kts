pluginManagement {
    // Convention plugins live in an isolated included build so that AGP/KGP stay off this build's
    // classpath — see the comment in build-logic/convention/build.gradle.kts.
    includeBuild("build-logic")

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
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "SalahWatch"

// Modules are added here as they are implemented, so the build stays green at every step.
// Target layout is documented in RESEARCH.md §4:
//   :app :wear
//   :core:model :core:calculation :core:database :core:preferences
//   :data:location :data:prayer-times :data:iqama :data:sync

include(":core:model")
include(":core:calculation")
include(":core:preferences")
include(":core:database")
include(":data:prayer-times")
include(":data:location")
include(":data:prayer-sync")
include(":data:sync")
include(":app")
include(":wear")
