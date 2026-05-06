package com.stepviewer.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomMaterialDao {
    @Query("SELECT * FROM custom_materials ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CustomMaterialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CustomMaterialEntity): Long

    @Update
    suspend fun update(entity: CustomMaterialEntity)

    @Delete
    suspend fun delete(entity: CustomMaterialEntity)

    @Query("DELETE FROM custom_materials WHERE id = :id")
    suspend fun deleteById(id: Long)
}
