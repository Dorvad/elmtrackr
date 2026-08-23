# R8 rules for the watch module.
#
# Same philosophy as the phone module (app/proguard-rules.pro): keep only what
# is genuinely resolved by reflection or by class name at runtime, so R8 full
# mode can still optimize everything else. The watch module minifies AND shrinks
# resources in release, and until now it carried two keepclassmembers rules and
# nothing else — no stack-trace attributes, no serialization keeps, no worker
# name keeps. A release-only R8 fault here is invisible in a debug build, and
# the watch module is not built by CI, so nothing caught it before upload.

# ============================================================
# Debugging — without these a crash on a reviewer's or a user's
# watch arrives as fully obfuscated frames, which is why the last
# store rejection ("does not launch without crashing") could not
# be traced to a line. Costs a few KB; keep it.
# ============================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

-dontwarn kotlin.**
-dontwarn kotlinx.**

# ============================================================
# Wear data layer message and state paths.
#
# These are String constants shared with the phone app. R8 inlines them, but
# keeping the members documents the contract and stops a future refactor that
# moves them behind a property accessor from silently dropping them.
# ============================================================
-keepclassmembers class com.elmtrackr.wear.sync.WearMessages { *; }
-keepclassmembers class com.elmtrackr.wear.sync.WearPaths { *; }

# ============================================================
# kotlinx.serialization — the snapshot the phone pushes over the
# data layer, and the punch result it sends back, are JSON. The
# generated serializers are reached through Companion.serializer()
# and the $serializer nested class. Mirrors the phone module so
# both ends of the wire agree after minification.
# ============================================================
-dontnote kotlinx.serialization.AnnotationsKt

-keep @kotlinx.serialization.Serializable class * { *; }

-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** serializer(...);
    static ** INSTANCE;
    static ** $serializer;
}

# The wire model itself: field names are baked into the generated serializer at
# compile time, but the phone and the watch are separate artifacts that can be
# built and shipped at different versions, so the payload shape has to stay
# stable independently of what R8 does to either side.
-keep class com.elmtrackr.wear.sync.WearShiftSnapshot { *; }
-keep class com.elmtrackr.wear.sync.PunchResult { *; }

# ============================================================
# WorkManager — the tile refresh worker's class NAME is written into
# WorkManager's database when the work is enqueued and read back by
# the default WorkerFactory on the next run, which resolves it with
# Class.forName. A rename orphans work queued by the previous build:
# logcat shows "Could not instantiate ..." and the tile count-up
# quietly stops for the rest of the shift.
# ============================================================
-keepnames class * extends androidx.work.ListenableWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-dontwarn androidx.work.**

# ============================================================
# kotlinx.coroutines — volatile fields are accessed through atomic
# field updaters and Unsafe; stripping them causes subtle runtime
# failures rather than a clean error.
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
# The brand typefaces are referenced from Kotlin by R.font id, so R8 sees the
# reference and keeps them; the resource shrinker sees it too. Nothing to keep
# here — this note exists so the next person does not add a defensive rule for
# them. If the wordmark ever renders in the system font on a release build, the
# cause is a missing glyph, not shrinking: neither face covers Hebrew or Arabic
# (verified against their cmap tables), so those locales fall back to the
# platform font on the watch exactly as they already do on the phone.
# ============================================================

# ============================================================
# Wear surfaces the system starts by ComponentName. The activity,
# the tile service, the complication provider and the data-layer
# listener are all declared in the manifest, so AGP's generated
# rules already keep them by name; the trampoline is kept
# explicitly because a tile's LaunchAction resolves it from a
# class name string embedded in the tile layout, which R8 cannot
# see as a reference.
# ============================================================
-keep class com.elmtrackr.wear.tile.WearPunchTrampolineActivity { *; }
