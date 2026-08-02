import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.sentry.android.gradle)
    id("app.cash.paparazzi") version "2.0.0-alpha05"
}

val localPropsFile = rootProject.file("local.properties")
val localProps = Properties().apply {
    if (localPropsFile.exists()) load(localPropsFile.inputStream())
}
val sentryDsn = localProps.getProperty("sentry.dsn", "")

sentry {
    autoUploadProguardMapping.set(false)
    autoUploadNativeSymbols.set(false)
    includeSourceContext.set(false)
    telemetry.set(false)
}

android {
    namespace = "com.elmtrackr.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.elmlaunch.myapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("supabase.anon.key", "")}\"")
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val releaseKeystoreFile =
        rootProject.file(localProps.getProperty("KEYSTORE_PATH") ?: "keystore/elmtrackr-release.jks")

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
            // Without the upload keystore, fall back to debug signing so R8/release
            // builds stay verifiable locally and on CI. Debug-signed release builds
            // are rejected by Play, so this cannot ship by accident.
            signingConfig = if (releaseKeystoreFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "WARNING: release keystore not found at $releaseKeystoreFile - " +
                        "signing the release build with the DEBUG key. Do not distribute this build.",
                )
                signingConfigs.getByName("debug")
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }

    lint {
        // AndroidX's detector is binary-incompatible with this Kotlin analysis API version.
        disable += "NullSafeMutableLiveData"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true

        // Give each Robolectric + Compose test class a fresh JVM.
        //
        // Compose keeps process-global state — the recomposer attached to the
        // test thread, and the Espresso idling policies Compose's test rule
        // drives — and Robolectric tears an Android environment down and builds
        // another one inside the same process for every class. In a full-suite
        // run the project detail-screen renders stopped ever reporting idle and
        // failed with AppNotIdleException, while passing 22 of 22 when the class
        // ran on its own. That difference is the leak, not the screen.
        //
        // Recycling the JVM per class is the ordinary remedy and costs start-up
        // time on a few hundred classes rather than correctness on all of them.
        // Raising the idling budget was tried first and changed nothing, which is
        // consistent with a wedged clock rather than a slow one.
        unitTests.all {
            it.forkEvery = 1
            it.maxParallelForks = (Runtime.getRuntime().availableProcessors() - 2).coerceAtLeast(1)
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "false")
}

dependencies {
    implementation(project(":wear-sync"))
    // The watch app is NOT embedded in the phone APK. `wearApp(...)` is the
    // legacy Wear 1.x micro-app mechanism; Wear OS 2+ (this app targets Wear OS
    // 3, minSdk 30) ignores embedded APKs and will never install one from the
    // phone. The watch app ships as its own artifact under the same application
    // id (com.elmlaunch.myapp) and is delivered to the paired watch by Play.
    // Build it with `./gradlew :wear:bundleRelease`.

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.play.services.wearable)
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)
    // In-app review flow; Task results are awaited with kotlinx-coroutines-play-services.
    implementation(libs.play.review)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.google.mlkit.document.scanner)
    implementation(libs.google.mlkit.text.recognition)
    implementation(libs.tesseract4android)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":wear-sync"))
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    // For IdlingPolicies: the Robolectric Compose tests need a longer idling
    // budget than Espresso's 60s default (see ProjectsRenderTest).
    testImplementation(libs.androidx.test.espresso.core)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
