package com.ferhat.commutetracker.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Builds a real v2 database, applies [AppDatabase.MIGRATION_2_3], and checks that old
 * rows survive and the new schema works.
 */
@RunWith(RobolectricTestRunner::class)
class Migration2to3Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    private fun createV2(): SupportSQLiteDatabase {
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE `places` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL, " +
                                "`latitude` REAL, `longitude` REAL, `radiusMeters` REAL NOT NULL, `category` TEXT, " +
                                "`isConfirmed` INTEGER NOT NULL, `visitCount` INTEGER NOT NULL, `firstSeenAt` INTEGER NOT NULL, " +
                                "`lastSeenAt` INTEGER NOT NULL, `source` TEXT NOT NULL)",
                        )
                        db.execSQL("CREATE INDEX `index_places_latitude` ON `places` (`latitude`)")
                        db.execSQL("CREATE INDEX `index_places_longitude` ON `places` (`longitude`)")
                        db.execSQL(
                            "CREATE TABLE `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `originPlaceId` INTEGER, " +
                                "`destinationPlaceId` INTEGER, `startEpochMillis` INTEGER NOT NULL, `endEpochMillis` INTEGER, " +
                                "`distanceMeters` REAL NOT NULL, `sampleCount` INTEGER NOT NULL, `isAuto` INTEGER NOT NULL, " +
                                "`isConfirmed` INTEGER NOT NULL, `note` TEXT, " +
                                "FOREIGN KEY(`originPlaceId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, " +
                                "FOREIGN KEY(`destinationPlaceId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)",
                        )
                        db.execSQL("CREATE INDEX `index_trips_originPlaceId` ON `trips` (`originPlaceId`)")
                        db.execSQL("CREATE INDEX `index_trips_destinationPlaceId` ON `trips` (`destinationPlaceId`)")
                        db.execSQL("CREATE INDEX `index_trips_startEpochMillis` ON `trips` (`startEpochMillis`)")
                        db.execSQL("CREATE INDEX `index_trips_endEpochMillis` ON `trips` (`endEpochMillis`)")
                        db.execSQL(
                            "CREATE TABLE `location_samples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tripId` INTEGER, " +
                                "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracyMeters` REAL NOT NULL, " +
                                "`speedMetersPerSecond` REAL NOT NULL, `epochMillis` INTEGER NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE TABLE `place_wifi_signatures` (`placeId` INTEGER NOT NULL, `bssid` TEXT NOT NULL, " +
                                "`weight` REAL NOT NULL, `lastSeenAt` INTEGER NOT NULL, PRIMARY KEY(`placeId`, `bssid`), " +
                                "FOREIGN KEY(`placeId`) REFERENCES `places`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                        )
                        db.execSQL("CREATE INDEX `index_place_wifi_signatures_placeId` ON `place_wifi_signatures` (`placeId`)")
                        db.execSQL(
                            "CREATE TABLE `route_labels` (`originPlaceId` INTEGER NOT NULL, `destinationPlaceId` INTEGER NOT NULL, " +
                                "`label` TEXT NOT NULL, `category` TEXT, `isUserSet` INTEGER NOT NULL, " +
                                "PRIMARY KEY(`originPlaceId`, `destinationPlaceId`))",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        return helper.writableDatabase
    }

    @Test
    fun `v2 to v3 keeps places and trips and adds new tables`() {
        createV2().use { db ->
            db.execSQL(
                "INSERT INTO places (label, radiusMeters, isConfirmed, visitCount, firstSeenAt, lastSeenAt, source) " +
                    "VALUES ('Home', 35.0, 1, 4, 1000, 2000, 'USER')",
            )
            db.execSQL(
                "INSERT INTO trips (originPlaceId, startEpochMillis, endEpochMillis, distanceMeters, sampleCount, isAuto, isConfirmed) " +
                    "VALUES (1, 100, 200, 1234.0, 5, 1, 0)",
            )
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val db = room.openHelper.writableDatabase
        db.query("SELECT label, kind FROM places").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("Home")
            assertThat(c.getString(1)).isEqualTo("normal")
        }
        db.query("SELECT straightness, maxAccuracyMeters FROM trips").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.isNull(0)).isTrue()
        }
        // New tables are usable.
        db.execSQL("INSERT INTO position_log (latitude, longitude, accuracyMeters, speedMps, bearingDeg, epochMillis, source) VALUES (1.0, 2.0, 5.0, 0.0, 0.0, 42, 'trip')")
        db.execSQL("INSERT INTO trip_stops (tripId, latitude, longitude, arrivalMillis, departureMillis, kind) VALUES (1, 1.0, 2.0, 10, 20, 'wait')")
        db.query("SELECT COUNT(*) FROM position_log").use { c -> c.moveToFirst(); assertThat(c.getInt(0)).isEqualTo(1) }

        room.close()
        context.deleteDatabase(dbName)
    }
}
