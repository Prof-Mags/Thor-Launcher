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
        val exported = exportedPlatformsDdl()
        assertThat(normalise(exported)).isEqualTo(normalise(ThorMigrations.PLATFORMS_V2_COLUMNS))
    }

    @Test
    fun `migration declares the expected version range`() {
        assertThat(ThorMigrations.MIGRATION_1_2.startVersion).isEqualTo(1)
        assertThat(ThorMigrations.MIGRATION_1_2.endVersion).isEqualTo(2)
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
    private fun exportedPlatformsDdl(): String {
        val schema = File(SCHEMA_DIR, "${ThorDatabase.VERSION}.json")
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

    /** Collapses whitespace and escaping so only structure is compared. */
    private fun normalise(ddl: String): String = ddl
        .replace("\\'", "'")
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        val SCHEMA_DIR = File("schemas/com.thor.core.database.ThorDatabase")
    }
}
