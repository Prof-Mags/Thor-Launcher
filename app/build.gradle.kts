plugins {
    alias(libs.plugins.thor.android.application)
    alias(libs.plugins.thor.android.application.compose)
    alias(libs.plugins.thor.android.hilt)
}

android {
    namespace = "com.thor.launcher"

    defaultConfig {
        applicationId = "com.thor.launcher"
        versionCode = 3
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    // BouncyCastle ships four jars, each carrying the same OSGi metadata, and
    // the merger refuses a duplicate rather than picking one. None of it means
    // anything to Android: these files describe the jars to an OSGi container,
    // which an APK is not, so excluding them removes metadata and not code.
    //
    // Scoped to the paths that actually collide rather than a blanket
    // META-INF exclusion, which would also drop the service-loader entries the
    // security provider registers itself through.
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.display)
    implementation(projects.core.input)
    implementation(projects.core.datastore)
    implementation(projects.data)

    implementation(projects.feature.home)
    implementation(projects.feature.topscreen)
    implementation(projects.feature.settings)
    implementation(projects.feature.search)
    implementation(projects.feature.movies)
    implementation(projects.feature.stream)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.testing)
    kspAndroidTest(libs.hilt.compiler)
}
