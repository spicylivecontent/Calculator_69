package com.calculator.app.data.dao

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.calculator.app.data.entity.VideoEntity
import com.calculator.app.data.LocalVideoSearchHit
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    /** Engångsläsning, används av migreringen från den okrypterade databasen. */
    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    suspend fun getAllVideosOnce(): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getVideoById(id: String): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    @Delete
    suspend fun deleteVideo(video: VideoEntity)

    @Query("DELETE FROM videos")
    suspend fun clearAll()

    /** FTS5/BM25 query is intentionally raw so Room does not parse the virtual table schema. */
    @RawQuery(observedEntities = [VideoEntity::class])
    suspend fun searchLocalVideoHits(query: SupportSQLiteQuery): List<LocalVideoSearchHit>
}
