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
    }
}

rootProject.name = "SmartReceiverPro"

include(":app")

// Core Modules
include(":core:common")
include(":core:database")
include(":core:network")
include(":core:security")
include(":core:design")
include(":core:media")
include(":core:discovery")

// Receiver Engine Modules
include(":receiver-engine:api")
include(":receiver-engine:protocol")
include(":receiver-engine:connection")
include(":receiver-engine:capabilities")

// Receiver Brand / Protocol Plugins
include(":receiver-plugins:senator")
include(":receiver-plugins:gmscreen")
include(":receiver-plugins:generic-upnp")

// Feature Modules
include(":feature:dashboard")
include(":feature:devices")
include(":feature:remote")
include(":feature:channels")
include(":feature:live-tv")
include(":feature:epg")
include(":feature:timers")
include(":feature:settings")
include(":feature:satfinder")
include(":feature:media-sharing")
include(":feature:voice-control")
include(":feature:backups")
