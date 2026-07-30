import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project
import thor.libs

/**
 * Baseline for `:feature:*` modules.
 *
 * A feature module owns screens and view models. It always depends on the
 * design system, the shared UI primitives, the domain model and the controller
 * input layer, so those edges are declared once here rather than in four
 * separate build files.
 */
class ThorAndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("thor.android.library")
        pluginManager.apply("thor.android.library.compose")
        pluginManager.apply("thor.android.hilt")

        extensions.configure<LibraryExtension> {
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:input"))

            add("implementation", libs.findLibrary("androidx-core-ktx").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-ktx").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
            add("implementation", libs.findLibrary("androidx-navigation-compose").get())
            add("implementation", libs.findLibrary("coil-compose").get())
            add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())

            add("androidTestImplementation", libs.findLibrary("androidx-test-ext-junit").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-runner").get())
            add("androidTestImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
        }
    }
}
