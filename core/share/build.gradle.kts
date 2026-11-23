plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Share by tap (spec 011): the protocol, crypto and merge rules, with no Android types so they
// run in plain JVM tests. NFC and Keystore live in :app, Room in :core:data.

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.bouncycastle)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.coroutines.test)
    testRuntimeOnly(libs.junit5.launcher)
}

tasks.test {
    useJUnitPlatform()
}
