package thor

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Access to the `libs` version catalog from inside precompiled convention plugins.
 *
 * The generated type-safe accessors are not visible here, so the catalog has to
 * be resolved through the [VersionCatalogsExtension] by name.
 */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Reads an integer version (SDK levels) out of the catalog. */
internal fun VersionCatalog.intVersion(alias: String): Int =
    findVersion(alias).get().requiredVersion.toInt()

/** Reads a plain string version out of the catalog. */
internal fun VersionCatalog.stringVersion(alias: String): String =
    findVersion(alias).get().requiredVersion
