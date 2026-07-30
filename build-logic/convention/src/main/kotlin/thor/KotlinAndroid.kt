package thor

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Opt-ins used across the project.
 *
 * `ExperimentalCoroutinesApi` is required by the `flatMapLatest` / `mapLatest`
 * operators that drive most of the launcher's reactive plumbing.
 */
private val THOR_COMPILER_ARGS = listOf(
    "-opt-in=kotlin.RequiresOptIn",
    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
    "-opt-in=kotlinx.coroutines.FlowPreview",
)

/**
 * Shared Android + Kotlin configuration applied to every Android module.
 *
 * Note: these are plain `.kt` sources, not `.gradle.kts` scripts, so Gradle's
 * lazy-property assignment operator is unavailable — `Property.set()` is used
 * throughout.
 */
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        compileSdk = libs.intVersion("compileSdk")

        defaultConfig {
            minSdk = libs.intVersion("minSdk")
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        packaging {
            resources {
                excludes.add("/META-INF/{AL2.0,LGPL2.1}")
                excludes.add("/META-INF/LICENSE*")
                excludes.add("/META-INF/DEPENDENCIES")
                excludes.add("META-INF/*.kotlin_module")
            }
        }

        testOptions {
            unitTests.isIncludeAndroidResources = true
            unitTests.isReturnDefaultValues = true
        }
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(THOR_COMPILER_ARGS)
        }
    }
}

/** Shared configuration for pure-JVM (non Android) modules. */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(THOR_COMPILER_ARGS)
        }
    }
}
