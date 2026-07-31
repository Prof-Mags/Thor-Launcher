plugins {
    alias(libs.plugins.thor.android.feature)
}

android {
    namespace = "com.thor.feature.movies"
}

dependencies {
    implementation(projects.data)
    implementation(libs.androidx.compose.material.icons.extended)
    // The player. Video on the top panel, controls on the bottom.
    implementation(libs.androidx.media3.exoplayer)
}
