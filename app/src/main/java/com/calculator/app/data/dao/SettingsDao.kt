package com.calculator.app.data.dao

import androidx.room.*
import com.calculator.app.data.entity.AppSettingsEntity

@Dao
interface SettingsDao {

    @Query("SELECT * FROM app_settings")
    suspend fun getAllOnce(): List<AppSettingsEntity>

    @Query("SELECT value FROM app_settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: AppSettingsEntity)

    @Query("DELETE FROM app_settings WHERE key = :key")
    suspend fun delete(key: String)
}
