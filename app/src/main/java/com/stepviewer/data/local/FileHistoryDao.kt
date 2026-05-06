package com.stepviewer.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FileHistoryDao {
    @Query("SELECT * FROM file_history ORDER BY isFavorite DESC, lastOpenedAt DESC LIMIT :limit")
    fun getRecentFiles(limit: Int = 50): Flow<List<FileHistoryEntity>>

    @Query("SELECT * FROM file_history WHERE isFavorite = 1 ORDER BY lastOpenedAt DESC")
    fun getFavorites(): Flow<List<FileHistoryEntity>>

    @Query("SELECT * FROM file_history WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): FileHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FileHistoryEntity)

    @Update
    suspend fun update(entity: FileHistoryEntity)

    @Delete
    suspend fun delete(entity: FileHistoryEntity)

    @Query("DELETE FROM file_history WHERE isFavorite = 0")
    suspend fun clearNonFavorites()

    @Query("UPDATE file_history SET isFavorite = :isFavorite WHERE uri = :uri")
    suspend fun setFavorite(uri: String, isFavorite: Boolean)

    @Query("UPDATE file_history SET thumbnailPath = :path WHERE uri = :uri")
    suspend fun updateThumbnail(uri: String, path: String)

    @Query("DELETE FROM file_history")
    suspend fun clearAll()
}
