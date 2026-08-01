# The Moonlight streaming core, built for THOR.
#
# A transcription of the upstream `moonlight-core/Android.mk`, not a redesign:
# the same translation units, include paths, feature defines and static
# libraries. It exists because ndk-build is GNU Make, and Make cannot address a
# path containing a space or a bracket — which this project's directory has. The
# symptom was a makefile reporting its own Android.mk as "an unknown file".
#
# Keep it in step with the vendored `Android.mk`, which is left in place as the
# reference this was copied from.

cmake_minimum_required(VERSION 3.22.1)
project(moonlight-core C)

set(CORE ${CMAKE_CURRENT_SOURCE_DIR}/moonlight-core)
set(COMMON ${CORE}/moonlight-common-c)

# Prebuilt, and deliberately so. OpenSSL and Opus ship with Moonlight as static
# archives built by its own CI; building either from source here would add an
# autotools and Perl toolchain to the requirements for compiling a launcher.
add_library(opus STATIC IMPORTED)
set_target_properties(opus PROPERTIES
    IMPORTED_LOCATION ${CORE}/libopus/${ANDROID_ABI}/libopus.a)

add_library(ssl STATIC IMPORTED)
set_target_properties(ssl PROPERTIES
    IMPORTED_LOCATION ${CORE}/openssl/${ANDROID_ABI}/libssl.a)

add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES
    IMPORTED_LOCATION ${CORE}/openssl/${ANDROID_ABI}/libcrypto.a)

# Ships with the NDK rather than with Moonlight; the stream core asks it what
# the CPU can do before choosing a Reed-Solomon implementation.
add_library(cpufeatures STATIC
    ${ANDROID_NDK}/sources/android/cpufeatures/cpu-features.c)
target_include_directories(cpufeatures PUBLIC
    ${ANDROID_NDK}/sources/android/cpufeatures)

add_library(moonlight-core SHARED
    ${COMMON}/src/AudioStream.c
    ${COMMON}/src/ByteBuffer.c
    ${COMMON}/src/Connection.c
    ${COMMON}/src/ConnectionTester.c
    ${COMMON}/src/ControlStream.c
    ${COMMON}/src/FakeCallbacks.c
    ${COMMON}/src/InputStream.c
    ${COMMON}/src/LinkedBlockingQueue.c
    ${COMMON}/src/Misc.c
    ${COMMON}/src/Platform.c
    ${COMMON}/src/PlatformCrypto.c
    ${COMMON}/src/PlatformSockets.c
    ${COMMON}/src/RtpAudioQueue.c
    ${COMMON}/src/RtpVideoQueue.c
    ${COMMON}/src/RtspConnection.c
    ${COMMON}/src/RtspParser.c
    ${COMMON}/src/SdpGenerator.c
    ${COMMON}/src/SimpleStun.c
    ${COMMON}/src/VideoDepacketizer.c
    ${COMMON}/src/VideoStream.c
    ${COMMON}/reedsolomon/rs.c
    ${COMMON}/enet/callbacks.c
    ${COMMON}/enet/compress.c
    ${COMMON}/enet/host.c
    ${COMMON}/enet/list.c
    ${COMMON}/enet/packet.c
    ${COMMON}/enet/peer.c
    ${COMMON}/enet/protocol.c
    ${COMMON}/enet/unix.c
    ${COMMON}/enet/win32.c
    ${CORE}/simplejni.c
    ${CORE}/callbacks.c
    ${CORE}/minisdl.c
)

target_include_directories(moonlight-core PRIVATE
    ${COMMON}/enet/include
    ${COMMON}/reedsolomon
    ${COMMON}/src
    ${CORE}/libopus/include
    ${CORE}/openssl/include
)

# HAS_SOCKLEN_T and HAVE_CLOCK_GETTIME describe the platform; LC_ANDROID selects
# the Android logging and socket behaviour inside the core.
target_compile_definitions(moonlight-core PRIVATE
    HAS_SOCKLEN_T=1
    LC_ANDROID
    HAVE_CLOCK_GETTIME=1
)

target_link_libraries(moonlight-core
    log
    opus
    ssl
    crypto
    cpufeatures
)

# Keeps OpenSSL's symbols inside the library.
#
# Static archives export everything they define by default, which would put a
# second, older copy of the entire OpenSSL symbol table into the process
# alongside the platform's — and which one a caller binds to is then a matter of
# load order.
target_link_options(moonlight-core PRIVATE -Wl,--exclude-libs,ALL)
