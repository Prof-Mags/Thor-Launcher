package com.thor.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations.
 *
 * Migrations are written out rather than falling back to a destructive
 * rebuild: the library carries scan results, hand-placed grid positions, play
 * history and edited metadata, none of which can be regenerated.
 */
object ThorMigrations {

    /**
     * 1 → 2: platforms gain multiple emulators and an explicit "added" flag.
     *
     * `default_emulator` (one package) becomes `emulator_packages` (an ordered
     * JSON list), and `is_hidden` is replaced by `is_added` — the inverse, and
     * a more honest name now that platforms exist in the table from first run
     * purely so the scanner can recognise their file types.
     *
     * SQLite cannot drop or rename columns portably at this API level, so the
     * table is rebuilt. Only `platforms` is touched; games, placements and play
     * history are untouched.
     */
    /**
     * The `platforms` table as of version 2.
     *
     * Exposed so a test can assert it matches the schema Room generates. A
     * column-level mismatch between a migration and the entity is not caught at
     * compile time and surfaces as a fatal `IllegalStateException` the first
     * time an upgraded install opens the database — which is to say, on launch,
     * for everyone who already had the app.
     */
    internal const val PLATFORMS_V2_COLUMNS =
        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `short_name` TEXT NOT NULL, " +
            "`manufacturer` TEXT NOT NULL, `release_year` INTEGER, " +
            "`accent_argb` INTEGER NOT NULL, `rom_extensions` TEXT NOT NULL, " +
            "`provider_ids` TEXT NOT NULL, `emulator_packages` TEXT NOT NULL DEFAULT '[]', " +
            "`is_custom` INTEGER NOT NULL, `is_added` INTEGER NOT NULL DEFAULT 0, " +
            "`sort_index` INTEGER NOT NULL, PRIMARY KEY(`id`)"

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `platforms_new` ($PLATFORMS_V2_COLUMNS)",
            )

            // A platform that already had an emulator assigned was one the user
            // had configured, so it carries over as "added" with that emulator
            // first in the list. Everything else starts unadded.
            db.execSQL(
                """
                INSERT INTO `platforms_new` (
                    `id`, `name`, `short_name`, `manufacturer`, `release_year`,
                    `accent_argb`, `rom_extensions`, `provider_ids`,
                    `emulator_packages`, `is_custom`, `is_added`, `sort_index`
                )
                SELECT
                    `id`, `name`, `short_name`, `manufacturer`, `release_year`,
                    `accent_argb`, `rom_extensions`, `provider_ids`,
                    CASE
                        WHEN `default_emulator` IS NOT NULL AND `default_emulator` != ''
                        THEN '["' || `default_emulator` || '"]'
                        ELSE '[]'
                    END,
                    `is_custom`,
                    CASE
                        WHEN `default_emulator` IS NOT NULL AND `default_emulator` != ''
                        THEN 1 ELSE 0
                    END,
                    `sort_index`
                FROM `platforms`
                """.trimIndent(),
            )

            db.execSQL("DROP TABLE `platforms`")
            db.execSQL("ALTER TABLE `platforms_new` RENAME TO `platforms`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_platforms_sort_index` " +
                    "ON `platforms` (`sort_index`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
