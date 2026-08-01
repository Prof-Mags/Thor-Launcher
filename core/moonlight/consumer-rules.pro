# Keeps the bridge the native library talks to.
#
# `callbacks.c` resolves the class by name — FindClass("com/limelight/nvstream/
# jni/MoonBridge") — and then looks up twenty-one static methods on it with
# GetStaticMethodID, by name and signature. R8 can see none of that: from Java
# those methods are called by nobody, so they are dead code and it removes them.
#
# The failure that produces is the worst shape a failure can have. Everything
# builds, the debug build works perfectly, and the release build crashes the
# first time a stream starts — inside C, on a method R8 deleted months of
# testing ago. Nothing short of streaming on a release build reveals it.
#
# Consumer rules rather than the app's, so any module that depends on this one
# gets them without having to know why.

-keep class com.limelight.nvstream.jni.MoonBridge { *; }
-keep class com.limelight.nvstream.jni.MoonBridge$* { *; }

# The renderers and the listener, whose methods the bridge calls straight back
# out to. Keeping the bridge alone is not enough: R8 would still be free to
# rename the interface methods it dispatches through, and the vendored bridge is
# compiled against their original names.
-keep interface com.limelight.nvstream.NvConnectionListener { *; }
-keep interface com.limelight.nvstream.av.audio.AudioRenderer { *; }
-keep class com.limelight.nvstream.av.video.VideoDecoderRenderer { *; }

# Native method names are load-bearing: the library exports
# Java_com_limelight_nvstream_jni_MoonBridge_<name>, which is resolved from the
# Java name at link time.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
