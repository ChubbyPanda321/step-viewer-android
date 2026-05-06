package com.stepviewer.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.stepviewer.data.model.CadFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CadFileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val MAX_FILE_SIZE = 200L * 1024 * 1024 // 200 MB
        const val WARN_FILE_SIZE = 50L * 1024 * 1024  // 50 MB
    }

    data class FileLoadResult(
        val bytes: ByteArray,
        val fileName: String,
        val fileSize: Long,
        val format: CadFormat,
        val localPath: String? = null,
    )

    /**
     * Copy file from URI to internal cache for WebView access.
     * Returns a FileLoadResult with metadata and the local file path.
     * Uses streaming copy to avoid loading the entire file into memory.
     */
    suspend fun copyToCache(uri: Uri): FileLoadResult = withContext(Dispatchers.IO) {
        val fileName = queryFileName(uri) ?: uri.lastPathSegment ?: "unknown"
        val fileSize = queryFileSize(uri)

        require(fileSize <= MAX_FILE_SIZE) { "File is too large (${fileSize / 1024 / 1024}MB). Max ${MAX_FILE_SIZE / 1024 / 1024}MB." }

        val ext = fileName.substringAfterLast('.', "").lowercase()
        val format = CadFormat.fromExtension(ext)
            ?: throw IllegalArgumentException("Unsupported file format: .$ext")

        // Copy to internal cache so WebView can access via file:// protocol
        val cacheDir = java.io.File(context.filesDir, "cad_models")
        cacheDir.mkdirs()
        val localFile = java.io.File(cacheDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(localFile).use { output ->
                input.copyTo(output, 8192)
            }
        } ?: throw IllegalStateException("Could not read file")

        FileLoadResult(
            bytes = ByteArray(0),
            fileName = fileName,
            fileSize = fileSize,
            format = format,
            localPath = localFile.absolutePath,
        )
    }

    /**
     * Read file content from a URI into a ByteArray.
     * Detects CAD format from the file extension.
     */
    suspend fun readFile(uri: Uri): FileLoadResult = withContext(Dispatchers.IO) {
        val fileName = queryFileName(uri) ?: uri.lastPathSegment ?: "unknown"
        val fileSize = queryFileSize(uri)

        require(fileSize <= MAX_FILE_SIZE) { "File is too large (${fileSize / 1024 / 1024}MB). Max ${MAX_FILE_SIZE / 1024 / 1024}MB." }

        val ext = fileName.substringAfterLast('.', "").lowercase()
        val format = CadFormat.fromExtension(ext)
            ?: throw IllegalArgumentException("Unsupported file format: .$ext")

        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArrayOutputStream()
            stream.copyTo(buffer)
            buffer.toByteArray()
        } ?: throw IllegalStateException("Could not read file")

        FileLoadResult(bytes, fileName, fileSize, format)
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(index)
            }
        }
        return name
    }

    private fun queryFileSize(uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) {
                size = cursor.getLong(index)
            }
        }
        return size
    }
}
