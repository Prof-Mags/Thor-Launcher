import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import thor.configureKotlinJvm
import thor.libs

/** Pure-Kotlin module baseline, used by `:core:model`. */
class ThorJvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureKotlinJvm()

        dependencies {
            add("testImplementation", libs.findLibrary("junit4").get())
            add("testImplementation", libs.findLibrary("truth").get())
        }
    }
}
