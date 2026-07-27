# Project ProGuard/R8 rules.
#
# Philosophy: keep rules exist ONLY where something is genuinely resolved by
# reflection, JNI, or class name at runtime. Library-wide `-keep class lib.**`
# blocks defeat R8 optimization for every class they touch (Play's R8 insights
# reported a 50% optimization rate under the old whole-library keeps) — AndroidX,
# OkHttp/Okio, ML Kit, and Room all ship their own consumer rules, so they must
# not be re-kept here.

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
# WorkManager — workers are instantiated reflectively through the
# (Context, WorkerParameters) constructor. Constructor-only keep:
# worker bodies stay optimizable.
# ============================================================
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
# ML Kit — ships its own consumer rules; only silence warnings.
# ============================================================
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
