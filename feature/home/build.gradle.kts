plugins {
    alias(libs.plugins.thor.android.feature)
}

android {
    namespace = "com.thor.feature.home"
}

dependencies {
    implementation(projects.data)
    implementation(libs.androidx.compose.material.icons.extended)
    // The icon picker in the edit dialog needs an activity-result launcher.
    implementation(libs.androidx.activity.compose)
}
