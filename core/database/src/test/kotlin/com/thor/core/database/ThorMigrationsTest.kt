package com.thor.core.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Guards the 1 → 2 migration against the schema Room actually generates.
 *
 * A migration that produces even a slightly different column set — a missing
 * default, a nullability difference, a reordered declaration — passes
 * compilation and then throws `IllegalStateException: Migration didn't properly
 * handle` the first time an *upgraded* install opens the database. Fresh
 * installs are unaffected, so the failure only appears for existing users, and
 * only on launch. That is exactly the bug a runnable check is worth having for.
 *
 * This compares the migration's own DDL against Room's exported schema rather
 * than executing SQLite, so it runs as a plain JVM test.
 */
class ThorMigrationsTest {

    @Test
    fun `platforms DDL matches the exported version 2 schema`() {
        val exported = exportedPlatformsDdl(version = 2)
        assertThat(normalise(exported)).isEqualTo(normalise(ThorMigrations.PLATFORMS_V2_COLUMNS))
    }

    /**
     * 2 → 3 adds columns in place, so there is no DDL constant to compare — the
     * check is that the columns it adds are exactly the ones the entity gained,
     * spelled the same way. A typo here is a migration that runs cleanly and
     * leaves Room looking for a column that does not exist.
     */
    @Test
    fun `version 3 adds exactly the artwork columns`() {
        val v2 = normalise(exportedPlatformsDdl(version = 2))
        val v3 = normalise(exportedPlatformsDdl(version = 3))

        val added = columnNames(v3) - columnNames(v2)
        assertThat(added).containsExactly(
            "artwork_icon_uri",
            "artwork_hero_uri",
            "artwork_logo_uri",
            "artwork_pack_id",
        )

        // Nullable with no default, which is what "no pack installed" means.
        added.forEach { column ->
            assertThat(v3).contains("`$column` TEXT,")
        }
    }

    @Test
    fun `migrations declare the expected version ranges`() {
        assertThat(ThorMigrations.MIGRATION_1_2.startVersion).isEqualTo(1)
        assertThat(ThorMigrations.MIGRATION_1_2.endVersion).isEqualTo(2)
        assertThat(ThorMigrations.MIGRATION_2_3.startVersion).isEqualTo(2)
        assertThat(ThorMigrations.MIGRATION_2_3.endVersion).isEqualTo(3)
        assertThat(ThorMigrations.ALL).hasLength(ThorDatabase.VERSION - 1)
    }

    @Test
    fun `every version step from one has a migration`() {
        // A gap here means an upgrade path silently falls through to a crash.
        val covered = ThorMigrations.ALL.map { it.startVersion to it.endVersion }.toSet()
        (1 until ThorDatabase.VERSION).forEach { version ->
            assertThat(covered).contains(version to version + 1)
        }
    }

    /**
     * Pulls the column list out of the exported schema's `CREATE TABLE` for
     * `platforms`, which Room writes with a `${'$'}{TABLE_NAME}` placeholder.
     */
    private fun exportedPlatformsDdl(version: Int): String {
        val schema = File(SCHEMA_DIR, "$version.json")
        assertThat(schema.exists()).isTrue()

        val json = schema.readText()
        val marker = "\"tableName\": \"platforms\""
        val tableStart = json.indexOf(marker)
        assertThat(tableStart).isGreaterThan(-1)

        val createKey = "\"createSql\": \""
        val createStart = json.indexOf(createKey, tableStart) + createKey.length
        val createEnd = json.indexOf('"', createStart)
        val createSql = json.substring(createStart, createEnd)

        // Everything between the outermost parentheses is the column list.
        val open = createSql.indexOf('(')
        val close = createSql.lastIndexOf(')')
        return createSql.substring(open + 1, close)
    }

    /** Column names from a normalised column list, in declaration order. */
    private fun columnNames(ddl: String): List<String> =
        Regex("`([a-z_]+)` (?:TEXT|INTEGER|REAL|BLOB)")
            .findAll(ddl)
            .map { it.groupValues[1] }
            .toList()

    /** Collapses whitespace and escaping so only structure is compared. */
    private fun normalise(ddl: String): String = ddl
        .replace("\\'", "'")
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        val SCHEMA_DIR = File("schemas/com.thor.core.database.ThorDatabase")
    }
}
