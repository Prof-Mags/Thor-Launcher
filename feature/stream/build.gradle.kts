plugins {
    alias(libs.plugins.thor.android.feature)
}

android {
    namespace = "com.thor.feature.stream"
}

dependencies {
    implementation(projects.data)
    implementation(libs.androidx.compose.material.icons.extended)
}
