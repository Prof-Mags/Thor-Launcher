import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.thor.android.library)
    alias(libs.plugins.thor.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.thor.data"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        /*
         * ScreenScraper identifies the *application* with a developer key, not
         * the end user. Every frontend that talks to it registers its own and
         * compiles it in — which is why launchers never ask for one. Supplied
         * here at build time from a gradle property so it stays out of source
         * control:
         *
         *     # local.properties (or ~/.gradle/gradle.properties)
         *     thor.screenscraper.devId=yourDevId
         *     thor.screenscraper.devPassword=yourDevPassword
         *
         * Left blank, ScreenScraper answers 403 and the settings screen's
         * connection check reports it as unavailable.
         */
        buildConfigField(
            "String",
            "SCREENSCRAPER_DEV_ID",
            "\"${localProperty("thor.screenscraper.devId")}\"",
        )
        buildConfigField(
            "String",
            "SCREENSCRAPER_DEV_PASSWORD",
            "\"${localProperty("thor.screenscraper.devPassword")}\"",
        )
    }

    // Off by default in the library convention plugin; the credentials above
    // need it.
    buildFeatures.buildConfig = true
}

/** Reads a build-time secret from `local.properties`, then gradle properties. */
fun localProperty(name: String): String {
    val local = rootProject.file("local.properties")
    val fromFile: String? = if (local.exists()) {
        val properties = Properties()
        FileInputStream(local).use { stream -> properties.load(stream) }
        properties.getProperty(name)
    } else {
        null
    }
    val value = fromFile ?: project.findProperty(name) as? String
    return value?.takeIf { it.isNotBlank() }.orEmpty()
}

dependencies {
    api(projects.core.moonlight)
    api(projects.core.model)
    api(projects.core.database)
    api(projects.core.datastore)
    implementation(projects.core.common)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.apache.commons.compress)
    implementation(libs.bouncycastle.pkix)
}
