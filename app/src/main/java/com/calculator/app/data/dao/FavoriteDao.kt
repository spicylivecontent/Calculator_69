package com.calculator.app.data.dao

import androidx.room.*
import com.calculator.app.data.entity.FavoriteEntity
import com.calculator.app.data.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("""
        SELECT videos.* FROM videos
        INNER JOIN favorites ON videos.id = favorites.videoId
        ORDER BY favorites.addedAt DESC
    """)
    fun getFavoriteVideos(): Flow<List<VideoEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoId = :videoId)")
    suspend fun isFavorite(videoId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE videoId = :videoId")
    suspend fun removeFavorite(videoId: String)

    @Query("SELECT COUNT(*) FROM favorites")
    fun getFavoriteCount(): Flow<Int>
}
