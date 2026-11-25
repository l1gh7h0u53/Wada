package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ClothingItem::class,
        CachedWadaColor::class,
        CachedWeather::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clothingDao(): ClothingDao
    abstract fun localCacheDao(): LocalCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clothing_items ADD COLUMN colorGroup TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clothing_items ADD COLUMN thermalType TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clothing_items ADD COLUMN wadaColorName TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clothing_items_colorGroup ON clothing_items(colorGroup)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_clothing_items_category ON clothing_items(category)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_wada_colors (
                        colorIndex INTEGER NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        hex TEXT NOT NULL,
                        combinationsCsv TEXT NOT NULL,
                        colorGroup TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cached_weather (
                        id INTEGER NOT NULL PRIMARY KEY,
                        temperatureCelsius REAL NOT NULL,
                        conditionName TEXT NOT NULL,
                        windSpeedKmh REAL NOT NULL,
                        fetchedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wardrobe_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
