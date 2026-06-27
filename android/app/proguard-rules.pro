# Add project-specific ProGuard rules here.

# ============================================================
# Debugging — preserve source file names and line numbers
# so crash stack traces remain readable in production.
# ============================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# Kotlin metadata — required for reflection, serialization,
# and many Jetpack libraries that inspect Kotlin annotations.
# ============================================================
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Suppress known-safe warnings from Kotlin stdlib
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ============================================================
# kotlinx.serialization
# Keep every class annotated with @Serializable, its
# generated Companion + serializer(), and the serialization
# runtime infrastructure used by Supabase's postgrest module.
# ============================================================
-dontnote kotlinx.serialization.AnnotationsKt

-keep @kotlinx.serialization.Serializable class * { *; }

-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** serializer(...);
    static ** INSTANCE;
    static ** $serializer;
}

-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# ============================================================
# Supabase — the client library uses reflection and service
# loaders internally; keep the entire public surface to be safe.
# ============================================================
-keep class io.github.jan.supabase.** { *; }
-keepclassmembers class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# ============================================================
# Ktor (OkHttp engine used by Supabase)
# ============================================================
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# ============================================================
# Room — entities, DAOs, and the database class are accessed
# reflectively by the Room runtime; KSP-generated *_Impl
# classes must also survive shrinking.
# ============================================================
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers @androidx.room.Database class * { *; }

# TypeConverters are found by class name at runtime
-keep @androidx.room.TypeConverters class * { *; }

# KSP-generated Room implementations
-keep class **_Impl { *; }
-keep class **_Impl$* { *; }

# ============================================================
# Domain model layer — data classes passed through Room
# queries and mapped from Supabase JSON responses.
# ============================================================
-keep class com.elmtrackr.app.domain.model.** { *; }
-keepclassmembers class com.elmtrackr.app.domain.model.** { *; }
-keepclassmembers enum com.elmtrackr.app.domain.model.ClockStyle {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    ** fromPersisted(...);
}

# Room entity and converter classes
-keep class com.elmtrackr.app.data.local.entity.** { *; }
-keepclassmembers class com.elmtrackr.app.data.local.entity.** { *; }
-keep class com.elmtrackr.app.data.local.converter.** { *; }
-keepclassmembers class com.elmtrackr.app.data.local.converter.** { *; }

# ============================================================
# Jetpack Glance — widget composables, GlanceAppWidget,
# GlanceAppWidgetReceiver, and action classes are loaded by
# the launcher via ComponentName (reflection + class name).
# ============================================================
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# Keep all widget classes: receiver, actions, state, updater
-keep class com.elmtrackr.app.widget.** { *; }
-keepclassmembers class com.elmtrackr.app.widget.** { *; }

# ============================================================
# WorkManager — Worker subclasses are instantiated by name
# from the WorkerFactory; they must not be renamed or removed.
# ============================================================
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-dontwarn androidx.work.**

# ============================================================
# BroadcastReceivers & Services declared in AndroidManifest
# (e.g. ClockOutReceiver) are loaded by class name by Android.
# ============================================================
-keep class com.elmtrackr.app.notification.** { *; }
-keep class com.elmtrackr.app.sync.** { *; }

# ============================================================
# kotlinx.coroutines — volatile fields are accessed via
# Unsafe; stripping them causes subtle runtime crashes.
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
# Suppress miscellaneous warnings from transitive dependencies
# ============================================================
-dontwarn com.sun.nio.file.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
