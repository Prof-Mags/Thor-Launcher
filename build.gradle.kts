/*
 * THOR Launcher — root build script.
 *
 * All real configuration lives in the `build-logic` convention plugins; this
 * file only declares the plugins so their versions resolve once for the whole
 * build, and registers a couple of project-wide helper tasks.
 */

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}

/** Removes every module's build directory. */
tasks.register<Delete>("cleanAll") {
    group = "build"
    description = "Deletes the build directory of the root project and all subprojects."
    delete(rootProject.layout.buildDirectory)
    subprojects.forEach { delete(it.layout.buildDirectory) }
}
