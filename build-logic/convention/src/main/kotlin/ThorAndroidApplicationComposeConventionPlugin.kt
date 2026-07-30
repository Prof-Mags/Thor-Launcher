import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import thor.configureAndroidCompose

/** Adds Compose to the application module. */
class ThorAndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<ApplicationExtension> {
            configureAndroidCompose(this)
        }
    }
}
