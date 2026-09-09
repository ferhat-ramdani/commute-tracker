package com.ferhat.commutetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Place::class,
        Trip::class,
        LocationSample::class,
        PlaceWifiSignature::class,
        RouteLabel::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun tripDao(): TripDao
    abstract fun locationSampleDao(): LocationSampleDao
    abstract fun routeLabelDao(): RouteLabelDao
    abstract fun placeWifiSignatureDao(): PlaceWifiSignatureDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "commute-tracker.db",
            )
                // v1 (manual-only) had a tiny, disposable data model. Rather than risk a
                // launch crash from a subtly wrong hand-written migration, v1 -> v2 drops
                // the old tables. Future migrations (v2+) are done properly with exported
                // schemas and MigrationTestHelper.
                .fallbackToDestructiveMigrationFrom(1)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "INSERT INTO places (label, radiusMeters, isConfirmed, visitCount, firstSeenAt, lastSeenAt, source) " +
                                "VALUES ('Home', 35.0, 1, 0, strftime('%s','now')*1000, strftime('%s','now')*1000, 'USER')",
                        )
                        db.execSQL(
                            "INSERT INTO places (label, radiusMeters, isConfirmed, visitCount, firstSeenAt, lastSeenAt, source) " +
                                "VALUES ('Work', 35.0, 1, 0, strftime('%s','now')*1000, strftime('%s','now')*1000, 'USER')",
                        )
                    }
                })
                .build()
    }
}
