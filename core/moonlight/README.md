# `core:moonlight` — vendored streaming core

Third-party code, kept in a module of its own so it is obvious which parts of
THOR were written here and which were not. Nothing in `src/main/jni` or
`src/main/java/com/limelight` is THOR's work.

## What is here, and where it came from

| Path | Upstream | Licence |
| --- | --- | --- |
| `src/main/jni/moonlight-core/{callbacks,simplejni,minisdl}.c`, headers | [moonlight-android](https://github.com/moonlight-stream/moonlight-android) | GPL-3.0 |
| `src/main/jni/moonlight-core/moonlight-common-c/src` | [moonlight-common-c](https://github.com/moonlight-stream/moonlight-common-c) @ `8af4562` | GPL-3.0 |
| `src/main/jni/moonlight-core/moonlight-common-c/enet` | [cgutman/enet](https://github.com/cgutman/enet) | MIT |
| `src/main/jni/moonlight-core/moonlight-common-c/reedsolomon` | librscode | public domain |
| `src/main/jni/moonlight-core/{openssl,libopus}` | [moonlight-mobile-deps](https://github.com/cgutman/moonlight-mobile-deps) — prebuilt static archives | Apache-2.0 / BSD-3-Clause |
| `src/main/java/com/limelight/**` | moonlight-android | GPL-3.0 |

THOR is GPL-3.0 because of this module. See the repository `LICENSE`.

## Why the Java keeps its upstream package

`com/limelight/nvstream/jni/MoonBridge.java` **cannot be renamed or moved.** The
native library binds to it by name — `callbacks.c` calls
`FindClass("com/limelight/nvstream/jni/MoonBridge")` and every exported symbol is
of the form `Java_com_limelight_nvstream_jni_MoonBridge_*`. A package rename
compiles cleanly and then fails at run time, on the first frame, with a
`NoSuchMethodError` from inside C.

The three interfaces beside it — `NvConnectionListener`, `AudioRenderer`,
`VideoDecoderRenderer` — are the callbacks the core invokes, and are vendored for
the same reason. THOR implements them; it does not modify them.

Everything else from upstream is deliberately **not** here. THOR has its own
discovery, pairing, HTTP client and UI, in Kotlin, under `com.thor`.

## Building

Needs the NDK and CMake:

```
sdkmanager "ndk;27.0.12077973" "cmake;3.22.1"
```

The build is driven by `src/main/jni/CMakeLists.txt`, which is a transcription of
the vendored `Android.mk`. The makefile is kept beside it as the reference, but
is not used: `ndk-build` is GNU Make, which cannot address a path containing a
space or a bracket, and reports a file it cannot parse as "an unknown file"
rather than as a path problem.

Only `arm64-v8a` and `armeabi-v7a` are built. Each ABI carries its own copy of
OpenSSL and Opus, so the others are real weight for hardware this launcher does
not target.

## Updating it

1. Replace the sources from upstream at a known commit, and record it above.
2. Diff `Android.mk` against `CMakeLists.txt` — new translation units have to be
   added to both, and a missing one is a link error naming a symbol rather than
   a file.
