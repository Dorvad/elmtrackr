# Project ProGuard/R8 rules.
#
# Philosophy: keep rules exist ONLY where something is genuinely resolved by
# reflection, JNI, or class name at runtime. Library-wide `-keep class lib.**`
# blocks defeat R8 optimization for every class they touch (Play's R8 insights
# reported a 50% optimization rate under the old whole-library keeps), so most
# dependencies are deliberately left un-kept: AndroidX, OkHttp/Okio, Ktor,
# supabase-kt, Glance and Room ship consumer rules and are verified working
# without extra keeps here.
#
# Two exceptions are NOT optional, because they ship no usable consumer rules of
# their own and rely on static singletons/registries that R8 *full mode*
# rewrites: ML Kit and Tink (androidx.security.crypto). Removing ML Kit's keeps
# once shipped a release that crashed with a NullPointerException as soon as the
# travel-refund section composed. Verify with `unzip -l <aar> | grep proguard`
# before assuming a library protects itself — most of these do not.
#
# When changing anything here, smoke-test the reflection-heavy paths on a real
# release build: receipt scan (ML Kit + Tesseract), database open (SQLCipher +
# Tink), widgets (Glance), sync (Ktor/serialization), and background workers.

# ============================================================
# Debugging — preserve source file names and line numbers
# so crash stack traces remain readable in production.
# ============================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations/signatures consumed at runtime by serialization and Jetpack.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Suppress known-safe warnings from Kotlin stdlib
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ============================================================
# kotlinx.serialization
# Generated serializers are looked up via Companion.serializer() /
# the $serializer nested class; this also covers the @Serializable
# DTOs inside supabase-kt. Field names are baked into the generated
# serializers at compile time, so nothing else needs keeping.
# ============================================================
-dontnote kotlinx.serialization.AnnotationsKt

-keep @kotlinx.serialization.Serializable class * { *; }

-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** serializer(...);
    static ** INSTANCE;
    static ** $serializer;
}

# ============================================================
# Ktor (OkHttp engine used by Supabase) — the client engine is
# discovered through ServiceLoader, so the container class must
# survive by name. Everything else in Ktor/OkHttp/Okio is either
# covered by the libraries' own consumer rules or plain code.
# ============================================================
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ============================================================
# Room — the database implementation is resolved by name
# (Class.forName(<Database>.name + "_Impl")). Scoped to this
# app's package so library classes stay optimizable; Room's own
# consumer rules cover the rest of its runtime.
# ============================================================
-keep class com.elmtrackr.app.**_Impl { *; }
-keep class com.elmtrackr.app.**_Impl$* { *; }

# TypeConverters are found by class name at runtime
-keep @androidx.room.TypeConverters class * { *; }
-keep class com.elmtrackr.app.data.local.converter.** { *; }

# ============================================================
# Domain model + Room entities — persisted names (enum wire
# values, snapshot JSON) must stay stable across app updates.
# ============================================================
-keep class com.elmtrackr.app.domain.model.** { *; }
-keep class com.elmtrackr.app.data.local.entity.** { *; }

# ============================================================
# App components loaded by class name by the system: Glance
# widget receivers/actions (launcher ComponentName) and the
# manifest-declared notification receivers. The Glance library
# itself ships consumer rules and is not re-kept here.
# ============================================================
-keep class com.elmtrackr.app.widget.** { *; }
-keep class com.elmtrackr.app.notification.** { *; }
-dontwarn androidx.glance.**

# ============================================================
# WorkManager — worker class NAMES are persisted in WorkManager's own
# database when work is enqueued, and Hilt's worker factory keys its map on
# the same names. A rename therefore breaks two things at once: already-queued
# jobs from a previous install can no longer be instantiated (logcat shows
# "Could not instantiate <Worker>" and the job is dropped, silently stopping
# sync), and the Hilt lookup misses so WorkManager falls back to the default
# factory and tries a (Context, WorkerParameters) constructor that @HiltWorker
# classes do not have. Keep names unconditionally; bodies stay optimizable.
# ============================================================
-keepnames class * extends androidx.work.ListenableWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-dontwarn androidx.work.**

# ============================================================
# kotlinx.coroutines — volatile fields are accessed via
# atomic FU/Unsafe; stripping them causes subtle runtime crashes.
# ============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}

# ============================================================
# SQLCipher (net.zetetic) — the native library calls back into
# these classes through JNI by name. (The old rule targeted the
# legacy net.sqlcipher package, which this app does not use.)
# ============================================================
-keep class net.zetetic.database.** { *; }
-dontwarn net.sqlcipher.**

# ============================================================
# androidx.security.crypto + Tink — same static-registry pattern that R8 full
# mode broke in ML Kit, and it guards the database passphrase: a failure here
# means the encrypted database cannot be opened at all. The optimization saved
# by shrinking these is not worth that risk.
# ============================================================
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn androidx.security.crypto.**
-dontwarn com.google.crypto.tink.**

# ============================================================
# ML Kit / Play Services vision — MUST stay un-optimized.
#
# Do not reduce these to -dontwarn. ML Kit wires its recognizers through
# static singletons and a dynamite module loader, and R8 full mode's
# static-field and class-initializer optimizations break that: with only
# -dontwarn, `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`
# in MLKitReceiptTextRecognizer's constructor read a null internal instance and
# threw NullPointerException the moment the travel-refund section composed
# (tapping "Claim" on a shift). The library's own consumer rules do not cover
# full-mode optimization, only shrinking.
# ============================================================
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**

# ============================================================
# Tesseract4Android — Hebrew OCR. JNI calls back into these
# classes by name, so they must survive shrinking untouched.
# ============================================================
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-dontwarn com.sun.nio.file.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
