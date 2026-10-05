import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
}

// Client IDs come from local.properties or CI secrets, never from git (constitution, Technical Constraints).
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use(::load)
    }

fun secret(name: String): String = (
    localProperties.getProperty(name)
        ?: System.getenv(name.uppercase().replace('.', '_'))
        ?: ""
    )

android {
    namespace = "app.duenorth.tasks"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.duenorth.tasks"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${secret("google.webClientId")}\"")
        buildConfigField("String", "MSAL_CLIENT_ID", "\"${secret("msal.clientId")}\"")
        buildConfigField("String", "MSAL_SIGNATURE_HASH", "\"${secret("msal.signatureHash")}\"")
        // Path of the MSAL sign-in redirect activity declared by :provider:microsoft.
        manifestPlaceholders["msalSignatureHash"] = secret("msal.signatureHash")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key until release signing lands (T060); lets benchmarks run on release builds.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric downloads android-all from the canonical Maven Central host.
        unitTests.all { it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        baseline = file("lint-baseline.xml")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":core:sync"))
    implementation(project(":provider:api"))
    // Only :app may see the concrete providers, and only to bind them in DI (constitution Principle III).
    implementation(project(":provider:google"))
    implementation(project(":provider:microsoft"))
    debugImplementation(project(":provider:fake"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.work.runtime)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.profileinstaller)
    implementation(libs.metrics.performance)

    debugImplementation(libs.compose.ui.tooling)

    baselineProfile(project(":benchmark"))

    testImplementation(libs.junit4)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.compose.ui.test.manifest)
}
