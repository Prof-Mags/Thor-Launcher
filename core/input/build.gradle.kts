plugins {
    alias(libs.plugins.thor.android.library)
    alias(libs.plugins.thor.android.library.compose)
    alias(libs.plugins.thor.android.hilt)
}

android {
    namespace = "com.thor.core.input"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
}
