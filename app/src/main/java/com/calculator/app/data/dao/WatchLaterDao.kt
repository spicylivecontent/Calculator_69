package com.calculator.app.data.dao

import androidx.room.*
import com.calculator.app.data.entity.VideoEntity
import com.calculator.app.data.entity.WatchLaterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchLaterDao {

    @Query("""
        SELECT videos.* FROM videos
        INNER JOIN watch_later ON videos.id = watch_later.videoId
        ORDER BY watch_later.addedAt DESC
    """)
    fun getWatchLaterVideos(): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE videoId = :videoId")
    suspend fun remove(videoId: String)

    @Query("SELECT COUNT(*) FROM watch_later")
    fun getCount(): Flow<Int>
}
