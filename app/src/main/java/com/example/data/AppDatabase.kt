package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [GroceryItem::class, Purchase::class, ConsumptionLog::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groceryDao(): GroceryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE grocery_items ADD COLUMN trackingMode TEXT NOT NULL DEFAULT 'AUTOMATIC'")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE grocery_items ADD COLUMN isPaused INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE consumption_logs ADD COLUMN isAutomated INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `consumption_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `quantityConsumed` REAL NOT NULL, `unit` TEXT NOT NULL, `isAutomated` INTEGER NOT NULL DEFAULT 0)")
            }
        }

        private val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE grocery_items ADD COLUMN trackingMode TEXT NOT NULL DEFAULT 'AUTOMATIC'")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE grocery_items ADD COLUMN isPaused INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                db.execSQL("CREATE TABLE IF NOT EXISTS `consumption_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `quantityConsumed` REAL NOT NULL, `unit` TEXT NOT NULL, `isAutomated` INTEGER NOT NULL DEFAULT 0)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE purchases ADD COLUMN brand TEXT DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE purchases ADD COLUMN rating INTEGER DEFAULT NULL")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "grocery_tracker_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_1_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

