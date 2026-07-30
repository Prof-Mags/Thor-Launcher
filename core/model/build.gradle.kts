plugins {
    alias(libs.plugins.thor.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
}
