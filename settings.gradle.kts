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

rootProject.name = "DueNorthTasks"

include(":app")
include(":benchmark")
include(":core:design")
include(":core:data")
include(":core:sync")
include(":provider:api")
include(":provider:fake")
include(":provider:google")
include(":provider:microsoft")
