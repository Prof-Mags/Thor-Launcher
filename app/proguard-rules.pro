# THOR Launcher — R8 rules for the release build.
#
# Verified by building `assembleRelease` and checking `mapping.txt` for the
# generated serializers: if a `$$serializer` goes missing, settings and metadata
# fail to decode at runtime and the launcher cannot start. Keep that check in mind
# when editing anything below.

# ---------------------------------------------------------------- attributes
# Serialization and reflection both need the annotations to survive.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature

# ---------------------------------------------------- kotlinx.serialization
# Conditional rules, so only classes that are actually @Serializable are kept.
# A blanket `-keep class com.thor.core.model.** { *; }` also works but exempts the
# whole model from shrinking, and misses the @Serializable DTOs that live in
# `:data` — these cover every module at once and cost nothing in size.
-dontnote kotlinx.serialization.**

# The synthetic companion holding serializer() on a @Serializable class.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# The generated $$serializer for that class.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable objects expose INSTANCE rather than a companion.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Serializer descriptors are looked up on the companion by name.
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ------------------------------------------------------------------- Room
# Room resolves its generated *_Impl by name from the abstract class.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------ Hilt / Dagger
-dontwarn dagger.hilt.**

# ----------------------------------------------------------- OkHttp / Okio
# Optional TLS providers that are never present on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Optional codecs referenced by Commons Compress but not bundled.
-dontwarn org.apache.commons.compress.**

# ------------------------------------------------------------------ enums
# `PerformanceProfile.valueOf` runs against persisted column values, so the
# synthetic enum accessors have to stay. `Enum.valueOf` matches the name captured
# at construction, so obfuscating the static fields themselves is harmless.
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ----------------------------------------------------------------- Compose
# Compose, Coil and Media3 all ship consumer rules; nothing extra is required.

# -------------------------------------------------------------------- DAOs
# The library DAOs are reached through a `java.lang.reflect.Proxy` so that every
# query follows the signed-in profile — see `ProfileScopedDao`. A proxy resolves
# each call against the interface's own `Method`, so the interfaces and their
# members have to survive intact; R8 has no way to see the call and would
# otherwise be free to merge or drop methods that appear unused.
-keep interface com.thor.core.database.dao.** { *; }
