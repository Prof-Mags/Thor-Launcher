plugins {
    alias(libs.plugins.thor.android.library)
    alias(libs.plugins.thor.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.thor.core.datastore"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
}
