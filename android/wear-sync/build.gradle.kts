plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Pinned to 17, matching :app and :wear. Without it the Kotlin JVM plugin
// targets whatever JVM Gradle happens to run on — 21 on CI, often 17 or 21
// locally — so the same source produced different class-file versions on
// different machines, and the classes that ended up inside the phone and watch
// artifacts depended on who built them. Everything else about the output is
// unchanged; both consumers already compile to 17.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
