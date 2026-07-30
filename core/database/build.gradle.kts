plugins {
    alias(libs.plugins.thor.android.library)
    alias(libs.plugins.thor.android.hilt)
    alias(libs.plugins.thor.android.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.thor.core.database"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.room.paging)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
