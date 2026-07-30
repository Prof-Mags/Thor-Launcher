import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import thor.configureKotlinAndroid
import thor.libs

/**
 * Baseline for every Android library module in the project.
 */
class ThorAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)

            // Library modules carry no resources that need per-variant handling;
            // disabling BuildConfig keeps the generated surface minimal.
            buildFeatures.buildConfig = false
        }

        dependencies {
            add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())
            add("testImplementation", libs.findLibrary("junit4").get())
            add("testImplementation", libs.findLibrary("truth").get())
            add("testImplementation", libs.findLibrary("turbine").get())
            add("testImplementation", libs.findLibrary("mockk").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}
