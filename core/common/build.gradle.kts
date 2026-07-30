plugins {
    alias(libs.plugins.thor.android.library)
    alias(libs.plugins.thor.android.hilt)
}

android {
    namespace = "com.thor.core.common"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
}
