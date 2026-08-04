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
    // Couch mode uses the stock player view and its controller rather than a
    // hand-built transport; see CouchMoviePlayer.
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.datasource.okhttp)

    // Declared rather than leant on transitively: the player builds its data
    // source from the injected client, so OkHttp is part of this module's own
    // compile surface.
    implementation(libs.okhttp)
}
