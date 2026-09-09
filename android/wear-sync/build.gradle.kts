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

    // Sentry types for CrashReportScrubber, and nothing else.
    //
    // compileOnly, so this module does not put the SDK on a consumer's classpath:
    // :app gets it from the Sentry Gradle plugin's auto-installation and :wear
    // declares sentry-android-core by hand, both at the version this catalog names.
    // The scrubber is the one piece of crash-report handling the phone and the watch
    // must not implement twice, so it lives here with the text rules it applies.
    compileOnly(libs.sentry.java)

    testImplementation(libs.junit)
    // On the test classpath for real: the scrubber tests build actual SentryEvent,
    // Breadcrumb and Request objects rather than asserting against a stand-in.
    testImplementation(libs.sentry.java)
}
