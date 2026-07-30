plugins {
    alias(libs.plugins.thor.android.feature)
}

android {
    namespace = "com.thor.feature.topscreen"
}

dependencies {
    implementation(projects.data)
    implementation(libs.androidx.compose.material.icons.extended)
    // Game preview clips on the backdrop.
    implementation(libs.androidx.media3.exoplayer)
}
