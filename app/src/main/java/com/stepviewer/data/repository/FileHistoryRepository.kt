package com.stepviewer.data.repository

import com.stepviewer.data.local.FileHistoryDao
import com.stepviewer.data.local.FileHistoryEntity
import com.stepviewer.data.model.CadFormat
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileHistoryRepository @Inject constructor(
    private val dao: FileHistoryDao,
) {
    fun getRecentFiles(): Flow<List<FileHistoryEntity>> = dao.getRecentFiles()

    fun getFavorites(): Flow<List<FileHistoryEntity>> = dao.getFavorites()

    suspend fun addOrUpdate(
        uri: String,
        fileName: String,
        fileSize: Long,
        format: CadFormat,
    ) {
        val existing = dao.getByUri(uri)
        if (existing != null) {
            dao.update(
                existing.copy(
                    fileName = fileName,
                    fileSize = fileSize,
                    format = format.name,
                    lastOpenedAt = System.currentTimeMillis(),
                )
            )
        } else {
            dao.insert(
                FileHistoryEntity(
                    uri = uri,
                    fileName = fileName,
                    fileSize = fileSize,
                    format = format.name,
                    thumbnailPath = null,
                    lastOpenedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun getByUri(uri: String): FileHistoryEntity? = dao.getByUri(uri)

    suspend fun setFavorite(uri: String, isFavorite: Boolean) {
        dao.setFavorite(uri, isFavorite)
    }

    suspend fun delete(uri: String) {
        dao.getByUri(uri)?.let { dao.delete(it) }
    }

    suspend fun clearNonFavorites() = dao.clearNonFavorites()

    suspend fun clearAll() = dao.clearAll()

    suspend fun updateThumbnail(uri: String, path: String) {
        dao.updateThumbnail(uri, path)
    }
}
