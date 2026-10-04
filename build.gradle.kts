plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.7.1")
    }
}

/*
 * Constitution Principle III: only :app (for DI wiring) may depend on the concrete providers.
 * Everything else talks to :provider:api.
 */
val concreteProviders = setOf(":provider:google", ":provider:microsoft")
val allowedToDependOnProviders = setOf(":app")

val checkModuleBoundaries by tasks.registering {
    group = "verification"
    description = "Fails if a module other than :app depends on a concrete provider module."
    val violations =
        provider {
            subprojects.flatMap { project ->
                if (project.path in allowedToDependOnProviders || project.path in concreteProviders) {
                    emptyList()
                } else {
                    project.configurations
                        .flatMap { it.dependencies.withType(ProjectDependency::class.java) }
                        .map { it.path }
                        .filter { it in concreteProviders }
                        .distinct()
                        .map { "${project.path} -> $it" }
                }
            }
        }
    doLast {
        val found = violations.get()
        check(found.isEmpty()) {
            "Module boundary violation (constitution Principle III):\n" + found.joinToString("\n")
        }
    }
}
