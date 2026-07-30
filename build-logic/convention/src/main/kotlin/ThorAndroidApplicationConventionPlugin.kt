import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import thor.configureKotlinAndroid
import thor.intVersion
import thor.libs

/**
 * Applied by `:app`. Configures the Android application plugin with THOR's
 * SDK levels, build types and signing-agnostic release settings.
 */
class ThorAndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig.targetSdk = libs.intVersion("targetSdk")

            buildTypes {
                getByName("debug") {
                    applicationIdSuffix = ".debug"
                    isMinifyEnabled = false
                }
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                    // The release build is signed with the debug key so that
                    // `assembleRelease` produces an installable artifact out of
                    // the box; swap in a real keystore before distributing.
                    signingConfig = signingConfigs.getByName("debug")
                }
            }
        }
    }
}
