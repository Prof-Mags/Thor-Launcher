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

    /**
     * 2 → 3: platforms gain icon-pack artwork.
     *
     * Four nullable columns added in place. Unlike 1 → 2 this needs no table
     * rebuild — nothing is dropped or renamed, and `ALTER TABLE ADD COLUMN` is
     * the one schema change SQLite has always supported cleanly.
     *
     * Every column is nullable with no default, which is exactly right: a
     * platform with no pack installed has no artwork, and null is what "no
     * artwork" means everywhere else in this schema.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "artwork_icon_uri",
                "artwork_hero_uri",
                "artwork_logo_uri",
                "artwork_pack_id",
            ).forEach { column ->
                db.execSQL("ALTER TABLE `platforms` ADD COLUMN `$column` TEXT")
            }
        }
    }

    /**
     * Resume points for films and episodes.
     *
     * A new table rather than a column anywhere: what it records belongs to a
     * title the library has never heard of — the Movies section browses a
     * catalogue rather than a scanned folder, so there is no existing row to
     * hang a position on.
     *
     * Written out rather than left to a destructive fallback, like every
     * migration here, because the alternative is a grid that empties itself the
     * first time someone updates the launcher.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `watch_progress` (
                    `id` TEXT NOT NULL,
                    `media_key` TEXT NOT NULL,
                    `season_number` INTEGER,
                    `episode_number` INTEGER,
                    `position_millis` INTEGER NOT NULL,
                    `duration_millis` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `poster_url` TEXT,
                    `backdrop_url` TEXT,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_watch_progress_updated_at` " +
                    "ON `watch_progress` (`updated_at`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_watch_progress_media_key` " +
                    "ON `watch_progress` (`media_key`)",
            )
        }
    }

    /**
     * Adds the widget table.
     *
     * Purely additive: a widget's *placement* already has somewhere to live, in
     * the `placements` table every other entry uses, so nothing existing is
     * touched and an install with no widgets is indistinguishable from one that
     * never had the table.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `widgets` (
                    `app_widget_id` INTEGER NOT NULL,
                    `provider` TEXT NOT NULL,
                    `label` TEXT NOT NULL,
                    `span_columns` INTEGER NOT NULL,
                    `span_rows` INTEGER NOT NULL,
                    `added_at` INTEGER NOT NULL,
                    PRIMARY KEY(`app_widget_id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_widgets_provider` ON `widgets` (`provider`)",
            )
        }
    }

    /**
     * Widgets learn whether the launcher draws them.
     *
     * One column, added in place with a default, because every row that exists
     * when this runs is an Android app widget — there was no other kind until
     * now, so the default is not a guess but a statement of fact about the data.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `widgets` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'app'")
        }
    }

    /**
     * Games gain the fingerprints of their own files.
     *
     * Three nullable columns added in place. Null is the honest starting value
     * for every existing row: nothing has been hashed yet, and the first scrape
     * that touches a game fills them in.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf("rom_crc32", "rom_md5", "rom_sha1").forEach { column ->
                db.execSQL("ALTER TABLE `games` ADD COLUMN `$column` TEXT")
            }
        }
    }

    /**
     * Adds the notes table.
     *
     * Purely additive, and with no foreign key to `games` on purpose: a note has
     * to outlive a rescan that could not find its ROM, which is the moment the
     * game row goes and the note is the only record left of where you got to.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `game_notes` (
                    `entry_id` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`entry_id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_game_notes_updated_at` " +
                    "ON `game_notes` (`updated_at`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
    )
}
