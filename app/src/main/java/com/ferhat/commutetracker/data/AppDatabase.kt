package com.ferhat.commutetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Place::class,
        Trip::class,
        TripStop::class,
        PositionLog::class,
        PlaceWifiSignature::class,
        RouteLabel::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun tripDao(): TripDao
    abstract fun tripStopDao(): TripStopDao
    abstract fun positionLogDao(): PositionLogDao
    abstract fun routeLabelDao(): RouteLabelDao
    abstract fun placeWifiSignatureDao(): PlaceWifiSignatureDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `places` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'normal'")
                db.execSQL("ALTER TABLE `places` ADD COLUMN `wifiSsids` TEXT")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `straightness` REAL")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `maxAccuracyMeters` REAL")
                db.execSQL("DROP TABLE IF EXISTS `location_samples`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `position_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, " +
                        "`accuracyMeters` REAL NOT NULL, `speedMps` REAL NOT NULL, " +
                        "`bearingDeg` REAL NOT NULL, `epochMillis` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_position_log_epochMillis` ON `position_log` (`epochMillis`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trip_stops` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tripId` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, " +
                        "`arrivalMillis` INTEGER NOT NULL, `departureMillis` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL DEFAULT 'unknown', " +
                        "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_stops_tripId` ON `trip_stops` (`tripId`)")
            }
        }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "commute-tracker.db",
            )
                .addMigrations(MIGRATION_2_3)
                // v1 (manual-only) had a disposable data model; v1 -> v2 stays destructive.
                .fallbackToDestructiveMigrationFrom(1)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "INSERT INTO places (label, radiusMeters, isConfirmed, visitCount, firstSeenAt, lastSeenAt, source, kind) " +
                                "VALUES ('Home', 35.0, 1, 0, strftime('%s','now')*1000, strftime('%s','now')*1000, 'USER', 'normal')",
                        )
                        db.execSQL(
                            "INSERT INTO places (label, radiusMeters, isConfirmed, visitCount, firstSeenAt, lastSeenAt, source, kind) " +
                                "VALUES ('Work', 35.0, 1, 0, strftime('%s','now')*1000, strftime('%s','now')*1000, 'USER', 'normal')",
                        )
                    }
                })
                .build()
    }
}
