# R8 rules for the release build.
#
# Room, Hilt and Compose all ship consumer rules, so the app only has to declare what R8
# cannot see for itself: the reflective edges of kotlinx.serialization, and the entity
# classes Room's generated code names by string.

# ── kotlinx.serialization ───────────────────────────────────────────
# The generated serializers are referenced reflectively through Companion.serializer().
# Without these, an export succeeds and an import fails at runtime — the worst possible
# split for a backup format.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The backup payload and everything reachable from it. This is the restore path; a field
# renamed by R8 is a backup that no longer round-trips.
-keep class dev.redplate.data.BackupData { *; }

# ── Room ────────────────────────────────────────────────────────────
# Entities and their fields are named as strings in generated SQL and cursor mapping.
-keep class dev.redplate.data.**Entity { *; }
-keep enum dev.redplate.data.** { *; }
-keep class dev.redplate.data.Converters { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ── Kotlin ──────────────────────────────────────────────────────────
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault, Signature, EnclosingMethod
