package thor

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Enables Jetpack Compose for a module and wires up the shared Compose BOM.
 *
 * Strong skipping is on by default in the Kotlin 2.x Compose compiler, which is
 * what keeps the icon grid from recomposing wholesale when a single cell's
 * selection state changes — but only for parameters the compiler believes are
 * stable, which is why the stability configuration below is not optional here.
 */
internal fun Project.configureAndroidCompose(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        buildFeatures {
            compose = true
        }
    }

    /*
     * Tell the compiler which of THOR's own types are stable.
     *
     * `:core:model` is a plain JVM module so the domain stays free of Android
     * imports. The Compose compiler is therefore never applied to it, those
     * classes carry no stability metadata, and every composable that takes one —
     * a grid cell, a shelf, a settings row, a panel — is inferred unstable and
     * can never skip. The effect is not subtle: moving the cursor one cell
     * recomposed the whole launcher instead of the two cells that changed.
     *
     * Resolved from the root so every module reads the same file.
     */
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        stabilityConfigurationFiles.add(
            isolated.rootProject.projectDirectory.file("compose-stability.conf"),
        )
    }

    dependencies {
        val bom = libs.findLibrary("androidx-compose-bom").get()
        add("implementation", platform(bom))
        add("androidTestImplementation", platform(bom))

        add("implementation", libs.findLibrary("androidx-compose-ui").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
        add("implementation", libs.findLibrary("androidx-compose-foundation").get())
        add("implementation", libs.findLibrary("androidx-compose-animation").get())
        add("implementation", libs.findLibrary("androidx-compose-material3").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())

        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
    }
}
