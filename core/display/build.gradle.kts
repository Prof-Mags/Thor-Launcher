plugins {
    alias(libs.plugins.thor.android.library)
    alias(libs.plugins.thor.android.library.compose)
    alias(libs.plugins.thor.android.hilt)
}

android {
    namespace = "com.thor.core.display"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // The presentation window has to hand the activity's result registry and
    // back dispatcher down itself; neither is reachable from a display context.
    implementation(libs.androidx.activity.compose)
}
