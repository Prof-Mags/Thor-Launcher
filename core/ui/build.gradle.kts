plugins {
    alias(libs.plugins.thor.android.library)
    alias(libs.plugins.thor.android.library.compose)
    alias(libs.plugins.thor.android.hilt)
}

android {
    namespace = "com.thor.core.ui"
}

dependencies {
    api(projects.core.model)
    api(projects.core.designsystem)
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.androidx.palette)
}
