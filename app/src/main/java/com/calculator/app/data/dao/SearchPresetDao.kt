package com.calculator.app.data.dao

import androidx.room.*
import com.calculator.app.data.entity.SearchPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchPresetDao {

    @Query("SELECT * FROM search_presets ORDER BY createdAt DESC")
    fun getAllPresets(): Flow<List<SearchPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: SearchPresetEntity)

    @Delete
    suspend fun deletePreset(preset: SearchPresetEntity)

    @Query("SELECT COUNT(*) FROM search_presets")
    fun getCount(): Flow<Int>
}
