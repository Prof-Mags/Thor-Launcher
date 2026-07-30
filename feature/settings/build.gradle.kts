plugins {
    alias(libs.plugins.thor.android.feature)
}

android {
    namespace = "com.thor.feature.settings"
}

dependencies {
    implementation(projects.data)
    implementation(libs.androidx.compose.material.icons.extended)
    // Wallpaper and ROM-directory pickers go through the storage access
    // framework, which needs an activity-result launcher.
    implementation(libs.androidx.activity.compose)
}
