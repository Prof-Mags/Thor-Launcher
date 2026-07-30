/*
 * Convention plugins for THOR.
 *
 * Each module applies one or more `thor.*` plugins instead of repeating Android
 * DSL boilerplate. This is the single place where SDK levels, Java/Kotlin
 * toolchains, Compose configuration and annotation processing are defined.
 */

plugins {
    `kotlin-dsl`
}

group = "com.thor.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "thor.android.application"
            implementationClass = "ThorAndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "thor.android.application.compose"
            implementationClass = "ThorAndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "thor.android.library"
            implementationClass = "ThorAndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "thor.android.library.compose"
            implementationClass = "ThorAndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "thor.android.feature"
            implementationClass = "ThorAndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "thor.android.hilt"
            implementationClass = "ThorHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "thor.android.room"
            implementationClass = "ThorRoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "thor.jvm.library"
            implementationClass = "ThorJvmLibraryConventionPlugin"
        }
    }
}
