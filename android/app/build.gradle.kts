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
        // 52, and the number matters more than it looks.
        //
        // Production is live on phone 51 while this file said 43, because the code
        // was bumped by hand at build time and never committed. That drift is how a
        // watch artifact Play rejected (10041) stayed on the listing for two months
        // while its replacement sat in git: releases went out phone-only, from a
        // tree whose numbers described a different build.
        //
        // 52 is the next code above production, and the module invariant is
        // wear == 10000 + this number, so the watch ships as 10052 — comfortably
        // above the burned 10041. Bump both together, here, in a commit.
        versionCode = 52
        versionName = "1.3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("supabase.anon.key", "")}\"")
        // The Google *Web* OAuth client id — the one Supabase verifies the ID
        // token against, not the Android client id. It is not a secret (it ships
        // inside the APK either way), but it is per-project configuration, so it
        // is read from local.properties like the Supabase keys rather than
        // committed. Left empty the Google button hides itself instead of
        // failing at tap time.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${localProps.getProperty("google.web.client.id", "")}\"",
        )
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")

        // Whether clock face packs cost money. On: the five products in
        // ClockFacePackProducts are live in Play Console.
        //
        // The default was false while they did not exist, because a build that
        // charges for products Play has never heard of gets an empty
        // queryProductDetails response and shows every pack as unavailable. It
        // stays a flag rather than becoming unconditional so that turning selling
        // off is a local.properties line rather than a revert: a product pulled
        // from the console, or a build for a device with no Play at all, both want
        // the free behaviour back without touching code.
        //
        // Set `paid.clock.face.packs=false` in local.properties to get it.
        buildConfigField(
            "boolean",
            "PAID_CLOCK_FACE_PACKS",
            localProps.getProperty("paid.clock.face.packs", "true").toBoolean().toString(),
        )

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val releaseKeystoreFile =
        rootProject.file(localProps.getProperty("KEYSTORE_PATH") ?: "keystore/elmtrackr-release.jks")

    /**
     * Whether this invocation asks for a release artifact someone could install, as
     * opposed to a release-configured *check* like `lintVitalRelease`.
     *
     * The distinction is the whole point. A missing keystore used to fall back to the
     * debug key for every release build, with a warning nobody reads in CI output,
     * and `build_release.bat` then printed "signed APK produced". Play rejecting a
     * debug-signed upload is a control on the Play path only — it does nothing about a
     * file handed to a tester or sideloaded, which is exactly what a local release
     * build is for.
     *
     * Verification builds still need the fallback: `bundleRelease` is how R8 and
     * `lintVitalRelease` get exercised, and CI generates a throwaway keystore of its
     * own. So the fallback stays available, but it has to be asked for.
     */
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
            signingConfig = when {
                releaseKeystoreFile.exists() -> signingConfigs.getByName("release")

                // Configuring the release build type happens for every invocation,
                // including `assembleDebug`, so this branch must not fail those. It
                // also covers release-configured checks, which produce nothing
                // installable.
                !releaseArtifactRequested -> signingConfigs.getByName("debug")

                allowDebugSignedRelease -> {
                    logger.warn(
                        "Release keystore not found at $releaseKeystoreFile — signing with the " +
                            "DEBUG key because -PallowDebugSignedRelease=true was passed. " +
                            "This artifact is for verification only. Do not distribute it.",
                    )
                    signingConfigs.getByName("debug")
                }

                else -> throw GradleException(
                    "Release keystore not found at $releaseKeystoreFile.\n" +
                        "Set KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD in " +
                        "local.properties to sign for real.\n" +
                        "To build an unsigned-for-distribution artifact anyway — R8 or lint " +
                        "verification only, never for a tester — pass " +
                        "-PallowDebugSignedRelease=true and read the warning it prints.",
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }

    bundle {
        language {
            // Ship every offered language to every device.
            //
            // Play's default is to split by language and install only the one the
            // device is set to. That is the wrong default for this app: the
            // language is chosen *inside* ElmTrackr, independently of the phone's,
            // so a device set to English installs no Hebrew resources and picking
            // Hebrew in Settings then has nothing to resolve against. Google's own
            // App Bundle guidance says to disable the split when an app switches
            // language in-app.
            //
            // With a handful of languages the cost is a few tens of kilobytes; see
            // localeFilters below, which is what keeps it that small.
            enableSplit = false
        }
    }

    androidResources {
        // Only the languages ElmTrackr actually offers, matching
        // res/xml/locales_config.xml.
        //
        // This matters more with the language split off: without it the base APK
        // would carry every translation AndroidX and Play Services ship — around
        // eighty locales the app has no UI for — on every device.
        localeFilters += listOf("en", "iw", "ar", "ru")
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
    implementation(libs.play.billing)
    implementation(libs.play.billing.ktx)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.google.mlkit.document.scanner)
    implementation(libs.google.mlkit.text.recognition)
    implementation(libs.tesseract4android)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)

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
