rootProject.name = "DevVaultAndroid"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":app")

include(":core:model")
include(":core:security")
include(":core:data")
include(":core:ui")
include(":feature:entries")
include(":feature:todos")
include(":feature:settings")
