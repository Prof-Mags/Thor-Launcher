plugins {
    alias(libs.plugins.thor.android.library)
}

android {
    namespace = "com.thor.core.moonlight"

    defaultConfig {
        /*
         * Only the ABIs the handheld and its emulator actually run.
         *
         * Each one costs a full copy of OpenSSL and Opus in the APK — around
         * 8 MB apiece — and the AYN Thor is arm64. `armeabi-v7a` is kept because
         * the vendored prebuilts ship it and a 32-bit device would otherwise
         * install and then fail at `loadLibrary`, which is a crash rather than a
         * refusal.
         */
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        /*
         * Shipped with the module, because only this module knows they are
         * needed.
         *
         * The native library resolves its Java callbacks by name at run time,
         * which R8 cannot see — it removes them as dead code and the release
         * build then fails inside C on the first frame. Consumer rules mean the
         * app gets this protection by depending on the module rather than by
         * remembering to.
         */
        consumerProguardFiles("consumer-rules.pro")
    }

    /*
     * CMake rather than Moonlight's own ndk-build, and not by preference.
     *
     * `ndk-build` is GNU Make, and Make cannot address a path containing a space
     * or a bracket — it splits on the one and treats the other as a function
     * call. This project lives in a directory with both, and the failure is
     * thoroughly misleading: the makefile reports its own `Android.mk` as "an
     * unknown file" while the file is plainly there.
     *
     * `src/main/jni/CMakeLists.txt` is a transcription of the vendored
     * `Android.mk`, which is kept beside it as the reference.
     */
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Pinned rather than floating: the vendored static libraries were built
    // against a known NDK, and a mismatched one fails at link time with
    // unresolved symbols that read as missing source files.
    ndkVersion = "27.0.12077973"

    sourceSets.named("main") {
        // The JNI entry points must keep their upstream package — the native
        // code looks the class up by name — so they live in `java/` beside the
        // module's own Kotlin.
        java.srcDirs("src/main/java")
    }

    packaging {
        jniLibs {
            // Loaded by name at runtime rather than opened from the APK, so it
            // has to exist as a real file on disk.
            useLegacyPackaging = false
        }
    }
}
