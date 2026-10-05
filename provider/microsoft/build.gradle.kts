plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.duenorth.tasks.provider.microsoft"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // The shared TaskProvider contract suite is JUnit 5; these tests are plain JVM (no Android types).
        unitTests.all { it.useJUnitPlatform() }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":provider:api"))
    implementation(libs.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    // display-mask (Surface Duo support) lives only on Microsoft's Azure DevOps feed, which this
    // build cannot reach; MSAL runs without it on ordinary phones.
    implementation(libs.msal) {
        exclude(group = "com.microsoft.device.display")
    }

    testImplementation(testFixtures(project(":provider:api")))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.coroutines.test)
    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testRuntimeOnly(libs.junit5.launcher)
}
