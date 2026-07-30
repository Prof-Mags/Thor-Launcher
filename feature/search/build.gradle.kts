plugins {
    alias(libs.plugins.thor.android.feature)
}

android {
    namespace = "com.thor.feature.search"
}

dependencies {
    implementation(projects.data)
    implementation(libs.androidx.compose.material.icons.extended)
}
