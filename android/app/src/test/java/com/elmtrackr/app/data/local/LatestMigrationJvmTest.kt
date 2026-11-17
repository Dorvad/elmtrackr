package com.elmtrackr.app.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The newest migration, checked on every unit-test run rather than only on an emulator.
 *
 * `ElmTrackrDatabaseMigrationTest` is the thorough suite, and it is instrumented:
 * it runs in the emulator job, not in `testDebugUnitTest`. That is how it came to
 * fall a version behind without anything going red — the database moved to 20,
 * the test's `CURRENT_VERSION` stayed at 19 and `MIGRATION_19_20` was never added
 * to its list, so the one step every existing install takes on upgrade was not
 * exercised anywhere.
 *
 * Two things here, both cheap enough to run on the JVM:
 *
 *  1. The newest migration is run for real. A database is built from the previous
 *     version's exported schema, given rows, and opened through Room with the
 *     migration attached. Room validates the migrated schema against the entities
 *     on open and throws if a column, type or index is off — the same check
 *     `MigrationTestHelper.runMigrationsAndValidate` performs.
 *  2. The instrumented suite is held to the database's version, so it cannot
 *     drift again unnoticed.
 *
 * This is not a replacement for the instrumented suite, which covers every start
 * version and runs on a device's SQLite. It is the tripwire in front of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LatestMigrationJvmTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `19 to 20 keeps existing rows and produces the schema Room expects`() {
        createFromExportedSchema(DB_NAME, version = 19) { db ->
            db.execSQL(
                """
                INSERT INTO tasks (
                    localId, userId, name, icon, hourlyRate, isArchived,
                    createdAt, updatedAt, syncStatus
                ) VALUES ('t1', 'user', 'Driving', '🚗', 62.5, 0, 0, 0, 'SYNCED')
                """.trimIndent(),
            )
        }

        val room = Room.databaseBuilder(context, ElmTrackrDatabase::class.java, DB_NAME)
            .addMigrations(ElmTrackrDatabase.MIGRATION_19_20)
            .allowMainThreadQueries()
            .build()
        try {
            // Opening is what runs the migration and Room's schema validation.
            val db = room.openHelper.writableDatabase
            assertEquals(20, db.version)

            db.query("SELECT name, hourlyRate, compensationProfileId FROM tasks WHERE localId = 't1'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Driving", cursor.getString(0))
                    assertEquals(62.5, cursor.getDouble(1), 0.0)
                    // Not assigned to a profile by the upgrade: see MIGRATION_19_20.
                    assertTrue(cursor.isNull(2))
                }
        } finally {
            room.close()
        }
    }

    @Test
    fun `the exported schema history ends at the database's version`() {
        assertEquals(
            "app/schemas is missing the export for the current database version",
            currentDatabaseVersion(),
            newestExportedSchemaVersion(),
        )
    }

    /**
     * Reads the instrumented suite's source, the same way `DesignSystemBudgetTest`
     * reads the UI sources. Crude, and exactly the check that was missing: nothing
     * else connects a number in an androidTest file to the `@Database` annotation.
     */
    @Test
    fun `the instrumented migration suite targets the database's version`() {
        val current = currentDatabaseVersion()
        val suite = File(
            "src/androidTest/java/com/elmtrackr/app/data/local/ElmTrackrDatabaseMigrationTest.kt",
        ).readText()

        assertTrue(
            "ElmTrackrDatabaseMigrationTest.CURRENT_VERSION is not $current",
            Regex("""const val CURRENT_VERSION = $current\b""").containsMatchIn(suite),
        )
        assertTrue(
            "MIGRATION_${current - 1}_$current is missing from ElmTrackrDatabaseMigrationTest.ALL_MIGRATIONS",
            suite.contains("ElmTrackrDatabase.MIGRATION_${current - 1}_$current,"),
        )
    }

    private fun currentDatabaseVersion(): Int {
        val room = Room.inMemoryDatabaseBuilder(context, ElmTrackrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return try {
            room.openHelper.readableDatabase.version
        } finally {
            room.close()
        }
    }

    private fun newestExportedSchemaVersion(): Int =
        SCHEMA_DIR.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .max()

    /** Builds a database exactly as Room exported it at [version], then lets [seed] add rows. */
    private fun createFromExportedSchema(name: String, version: Int, seed: (SQLiteDatabase) -> Unit) {
        val schema = JSONObject(File(SCHEMA_DIR, "$version.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(name).apply {
            parentFile?.mkdirs()
            delete()
        }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace(TABLE_PLACEHOLDER, table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(
                        indices.getJSONObject(j).getString("createSql").replace(TABLE_PLACEHOLDER, table),
                    )
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
            seed(db)
        }
    }

    private companion object {
        const val DB_NAME = "latest-migration-jvm-test"
        const val TABLE_PLACEHOLDER = "\${TABLE_NAME}"
        val SCHEMA_DIR = File("schemas/com.elmtrackr.app.data.local.ElmTrackrDatabase")
    }
}
