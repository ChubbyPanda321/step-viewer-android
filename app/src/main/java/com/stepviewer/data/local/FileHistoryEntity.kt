package com.stepviewer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_history")
data class FileHistoryEntity(
    @PrimaryKey
    val uri: String,
    val fileName: String,
    val fileSize: Long,
    val format: String,
    val thumbnailPath: String?,
    val isFavorite: Boolean = false,
    val lastOpenedAt: Long = System.currentTimeMillis(),
)
