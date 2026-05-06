package com.stepviewer.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream

/**
 * Copy text to system clipboard.
 */
fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * Format a double value to 2 decimal places.
 */
fun Double.format2(): String = String.format("%.2f", this)

/**
 * Global Gson instance for reuse.
 */
val gson: Gson by lazy { Gson() }

/**
 * Write a ByteArray to an internal file and return the path.
 */
fun Context.saveToFile(data: ByteArray, fileName: String): File {
    val dir = File(cacheDir, "exports")
    dir.mkdirs()
    val file = File(dir, fileName)
    FileOutputStream(file).use { it.write(data) }
    return file
}
