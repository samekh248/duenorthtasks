pluginManagement {
    repositories {
        google()
        // Google's copy of Maven Central, ahead of Central itself: Claude cloud sessions and
        // CI runners share IPs that Central often rate-limits (HTTP 429).
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
    }
}

rootProject.name = "DueNorthTasks"

include(":app")
include(":benchmark")
include(":core:design")
include(":core:data")
include(":core:sync")
include(":core:share")
include(":provider:api")
include(":provider:fake")
include(":provider:google")
include(":provider:microsoft")
