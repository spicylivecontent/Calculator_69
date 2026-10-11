package com.calculator.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.calculator.app.data.dao.*
import com.calculator.app.data.entity.*

@Database(
    entities = [
        VideoEntity::class,
        FavoriteEntity::class,
        WatchLaterEntity::class,
        DislikeRuleEntity::class,
        SearchPresetEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun dislikeRuleDao(): DislikeRuleDao
    abstract fun searchPresetDao(): SearchPresetDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Preserve stored data on upgrades; add explicit Room migrations for schema changes.
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calculator_vault.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
