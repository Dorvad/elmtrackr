import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localPropsFile = rootProject.file("local.properties")
val localProps = Properties().apply {
    if (localPropsFile.exists()) load(localPropsFile.inputStream())
}

android {
    namespace = "com.elmtrackr.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.elmlaunch.myapp"
        minSdk = 30
        targetSdk = 36
        // Play requires a watch artifact's versionCode to be unique across all
        // form factors of the listing, and the phone module shares this
        // applicationId — so the wear module keeps its own range:
        // 10000 + the phone :app versionCode. Bump both together.
        versionCode = 10012
        versionName = "1.2.0"
    }

    // The Play Store only delivers the watch app to a paired watch when it is
    // signed with the SAME certificate as the phone app. This mirrors the phone
    // module (:app) so a release bundle built with the release keystore is a
    // valid companion; without it the wear release would be debug-signed and
    // Play would refuse to pair it with the release-signed phone build.
    val releaseKeystoreFile =
        rootProject.file(localProps.getProperty("KEYSTORE_PATH") ?: "keystore/elmtrackr-release.jks")

    // Same rule as the phone module: a distributable release needs a real key, a
    // verification build has to ask for the debug fallback. The watch APK ships in
    // the same release as the phone one, so a silent debug-signed wear artifact is
    // the same defect.
    val releaseArtifactRequested = gradle.startParameter.taskNames.any {
        Regex("(assemble|bundle|package|install)Release", RegexOption.IGNORE_CASE).containsMatchIn(it)
    }
    val allowDebugSignedRelease =
        (project.findProperty("allowDebugSignedRelease") as String?)?.toBoolean() == true

    signingConfigs {
        create("release") {
            storeFile = releaseKeystoreFile
            storePassword = localProps.getProperty("KEYSTORE_PASSWORD") ?: ""
            keyAlias = localProps.getProperty("KEY_ALIAS") ?: ""
            keyPassword = localProps.getProperty("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Match the phone module: fall back to debug signing when the upload
            // keystore is absent so release builds stay verifiable locally and on
            // CI. Debug-signed builds are rejected by Play, so this cannot ship by
            // accident.
            signingConfig = when {
                releaseKeystoreFile.exists() -> signingConfigs.getByName("release")
                !releaseArtifactRequested -> signingConfigs.getByName("debug")
                allowDebugSignedRelease -> {
                    logger.warn(
                        "Release keystore not found at $releaseKeystoreFile — signing the wear " +
                            "release build with the DEBUG key because " +
                            "-PallowDebugSignedRelease=true was passed. Verification only.",
                    )
                    signingConfigs.getByName("debug")
                }
                else -> throw GradleException(
                    "Release keystore not found at $releaseKeystoreFile — cannot sign the wear " +
                        "release build. See the phone module's message for the properties to set, " +
                        "or pass -PallowDebugSignedRelease=true for a verification build.",
                )
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    bundle {
        language {
            // Same reason as :app — the watch UI follows the language chosen in
            // the phone app, not the device's, so Play must not install only one.
            enableSplit = false
        }
    }

    androidResources {
        localeFilters += listOf("en", "iw")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":wear-sync"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.work.runtime.ktx)
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.animation:animation")
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)

    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.tiles.material)
    implementation(libs.androidx.wear.watchface.complications)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
