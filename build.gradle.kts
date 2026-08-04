import java.io.File

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

/**
 * Fails the build on text that will render as garbage in the app.
 *
 * Three separate faults have reached the UI this way, all from source written
 * through a tool that did not agree with the file about its encoding:
 *
 *  - a `\u2019` escape losing its prefix, so a settings row read
 *    "another launcher2019s downloaded media folder";
 *  - the same for `\u2026`, giving a status line of "Importing2026";
 *  - a raw Windows-1252 em-dash byte (0x97) in a UTF-8 file, which is not
 *    valid UTF-8 at all and decodes to a replacement glyph.
 *
 * None is caught by the compiler: every one is a perfectly good string. They
 * are only wrong to a reader, which is why this looks at the bytes instead.
 */
tasks.register("checkSourceText") {
    group = "verification"
    description = "Rejects mangled unicode and non-UTF-8 bytes in Kotlin sources."

    val sources = fileTree(rootDir) {
        include("**/src/**/*.kt")
        exclude("**/build/**")
    }
    inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE)
    // Captured as plain values: the task action must not reach back into the
    // project, or the configuration cache rejects it.
    val root = rootDir
    // Nothing is produced, so declare a stamp to keep the task cacheable rather
    // than re-reading every source file on every build.
    val stamp = layout.buildDirectory.file("checkSourceText.stamp")
    outputs.file(stamp)

    doLast {
        val decoder = Charsets.UTF_8.newDecoder()
        /*
         * An escape that lost its backslash-u leaves the four hex digits welded
         * to the surrounding word: "launcher2019s", "Importing2026".
         *
         * The codepoints are listed rather than given as a range, because a
         * range catches real text — a package name ending "mame4d2024" and a
         * film title carrying its year both sit inside 2000-20FF. These are only
         * the punctuation marks that turn up in prose, which is the whole set
         * that has ever been mangled here.
         */
        val punctuation = listOf(
            "2018", "2019", "201C", "201D", "2013", "2014", "2026",
            "00A0", "00B7", "00D7", "2192", "2022",
        ).joinToString("|")
        val strippedEscape = Regex("""[A-Za-z]($punctuation)([A-Za-z ,.!?]|")""")
        // Correctly written escapes contain the same digits, so they are taken
        // out before the line is examined rather than special-cased in the
        // pattern.
        val writtenEscape = Regex("""\\u[0-9A-Fa-f]{4}""")
        val problems = mutableListOf<String>()

        val kotlinFiles = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains("${File.separator}build${File.separator}") }

        kotlinFiles.forEach { file ->
            val bytes = file.readBytes()
            runCatching { decoder.reset().decode(java.nio.ByteBuffer.wrap(bytes)) }
                .onFailure { problems += "${file.relativeTo(root)}: not valid UTF-8" }
                .onSuccess { text ->
                    if (text.startsWith('\uFEFF')) {
                        problems += "${file.relativeTo(root)}: starts with a byte-order mark"
                    }
                    text.lineSequence().forEachIndexed { index, line ->
                        if (strippedEscape.containsMatchIn(writtenEscape.replace(line, ""))) {
                            problems += "${file.relativeTo(root)}:${index + 1}: " +
                                "looks like a unicode escape that lost its prefix -${line.trim()}"
                        }
                    }
                }
        }

        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("${problems.size}")
        if (problems.isNotEmpty()) {
            error("Mangled source text:\n" + problems.joinToString("\n") { "  $it" })
        }
    }
}

subprojects {
    plugins.withId("base") {
        tasks.named("check") { dependsOn(rootProject.tasks.named("checkSourceText")) }
    }
}
