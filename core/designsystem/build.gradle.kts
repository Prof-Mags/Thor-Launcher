plugins {
    alias(libs.plugins.thor.android.library)
    alias(libs.plugins.thor.android.library.compose)
}

android {
    namespace = "com.thor.core.designsystem"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.palette)
}
