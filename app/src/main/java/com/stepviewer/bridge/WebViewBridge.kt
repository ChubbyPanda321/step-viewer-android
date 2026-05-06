package com.stepviewer.bridge

import android.util.Base64
import android.webkit.JavascriptInterface
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stepviewer.data.model.Measurement
import com.stepviewer.data.model.StepFileInfo
import com.stepviewer.data.model.CadFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.RandomAccessFile
import java.util.UUID

/**
 * Bridge class exposed to JavaScript in the WebView.
 * Handles all communication between Kotlin and the Three.js/occt-import-js world.
 */
class WebViewBridge {

    // Model loaded events
    private val _modelLoaded = MutableStateFlow<StepFileInfo?>(null)
    val modelLoaded: StateFlow<StepFileInfo?> = _modelLoaded.asStateFlow()

    // Measurement result events
    private val _measurementResult = MutableStateFlow<Measurement?>(null)
    val measurementResult: StateFlow<Measurement?> = _measurementResult.asStateFlow()

    // Loading state
    private val _loadProgress = MutableStateFlow("")
    val loadProgress: StateFlow<String> = _loadProgress.asStateFlow()

    // Error events
    private val _jsError = MutableStateFlow<String?>(null)
    val jsError: StateFlow<String?> = _jsError.asStateFlow()

    // JS ready state
    private val _isJsReady = MutableStateFlow(false)
    val isJsReady: StateFlow<Boolean> = _isJsReady.asStateFlow()

    // Commander for one-time JS results
    private var pendingDeferred: CompletableDeferred<String>? = null

    // Chunked file reading — maps handle → open RandomAccessFile
    private val fileHandles = mutableMapOf<String, RandomAccessFile>()
    private val CHUNK_SIZE = 256 * 1024 // 256KB per chunk

    /**
     * Called by JS when the WebView bridge is initialized and ready.
     */
    @JavascriptInterface
    fun onBridgeReady() {
        _isJsReady.value = true
    }

    /**
     * Called by JS when STEP/IGES loading starts.
     */
    @JavascriptInterface
    fun onLoadStart(message: String) {
        _loadProgress.value = message
    }

    /**
     * Called by JS when the model is fully loaded and analyzed.
     * JSON string with StepFileInfo fields (snake_case from JS).
     */
    @JavascriptInterface
    fun onModelLoaded(json: String) {
        try {
            val info = parseModelInfo(json)
            _modelLoaded.value = info
            _loadProgress.value = ""
        } catch (e: Exception) {
            _jsError.value = "Failed to parse model info: ${e.message}"
        }
    }

    /**
     * Called by JS with a measurement result.
     * JSON: { "p1": [x,y,z], "p2": [x,y,z], "distance": d }
     */
    @JavascriptInterface
    fun onMeasurementResult(json: String) {
        try {
            val measurement = parseMeasurement(json)
            _measurementResult.value = measurement
        } catch (e: Exception) {
            _jsError.value = "Failed to parse measurement: ${e.message}"
        }
    }

    /**
     * Called by JS when an error occurs.
     */
    @JavascriptInterface
    fun onError(message: String) {
        _jsError.value = message
        _loadProgress.value = ""
    }

    /**
     * Called by JS for async command responses.
     */
    @JavascriptInterface
    fun onCommandResult(data: String) {
        pendingDeferred?.complete(data)
        pendingDeferred = null
    }

    /**
     * Set a deferred to await the next command result.
     */
    fun setPendingDeferred(deferred: CompletableDeferred<String>) {
        pendingDeferred = deferred
    }

    /**
     * Clear any displayed error.
     */
    fun clearError() {
        _jsError.value = null
    }

    // ---- Chunked file reading via bridge (avoids file:// XHR, avoids OOM) ----

    /**
     * Open a local file for chunked reading. Returns a handle ID string.
     */
    @JavascriptInterface
    fun fileOpen(filePath: String): String {
        try {
            val handle = UUID.randomUUID().toString()
            val raf = RandomAccessFile(filePath, "r")
            fileHandles[handle] = raf
            return handle
        } catch (e: Exception) {
            return ""
        }
    }

    /**
     * Get the size of an open file. Returns "-1" on error.
     */
    @JavascriptInterface
    fun fileGetSize(handle: String): String {
        try {
            val raf = fileHandles[handle] ?: return "-1"
            return raf.length().toString()
        } catch (e: Exception) {
            return "-1"
        }
    }

    /**
     * Read a chunk of base64-encoded data from an open file.
     * Returns empty string when no more data (EOF) or on error.
     */
    @JavascriptInterface
    fun fileReadChunk(handle: String, offset: String): String {
        try {
            val raf = fileHandles[handle] ?: return ""
            val off = offset.toLong()
            val len = minOf(CHUNK_SIZE.toLong(), raf.length() - off).toInt()
            if (len <= 0) return ""
            raf.seek(off)
            val buf = ByteArray(len)
            val read = raf.read(buf, 0, len)
            return if (read > 0) {
                Base64.encodeToString(if (read == buf.size) buf else buf.copyOf(read), Base64.NO_WRAP)
            } else ""
        } catch (e: Exception) {
            return ""
        }
    }

    /**
     * Close a file handle and free resources.
     */
    @JavascriptInterface
    fun fileClose(handle: String) {
        try {
            fileHandles.remove(handle)?.close()
        } catch (_: Exception) { }
    }

    /**
     * Parse model info JSON from JS bridge.
     */
    private fun parseModelInfo(json: String): StepFileInfo {
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = Gson().fromJson(json, type)

        val fileName = (map["fileName"] as? String) ?: "Unknown"
        val formatStr = (map["format"] as? String) ?: "STEP"
        val format = try {
            CadFormat.valueOf(formatStr.uppercase())
        } catch (_: Exception) {
            CadFormat.STEP
        }

        return StepFileInfo(
            fileName = fileName,
            height = (map["height"] as? Number)?.toDouble() ?: 0.0,
            width = (map["width"] as? Number)?.toDouble() ?: 0.0,
            length = (map["length"] as? Number)?.toDouble() ?: 0.0,
            volume = (map["volume"] as? Number)?.toDouble() ?: 0.0,
            surfaceArea = (map["surfaceArea"] as? Number)?.toDouble() ?: 0.0,
            faceCount = (map["faceCount"] as? Number)?.toInt() ?: 0,
            format = format,
        )
    }

    /**
     * Parse measurement JSON from JS bridge.
     */
    private fun parseMeasurement(json: String): Measurement {
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = Gson().fromJson(json, type)

        val p1Arr = (map["p1"] as? List<*>)?.map { (it as Number).toDouble() } ?: listOf(0.0, 0.0, 0.0)
        val p2Arr = (map["p2"] as? List<*>)?.map { (it as Number).toDouble() } ?: listOf(0.0, 0.0, 0.0)
        val distance = (map["distance"] as? Number)?.toDouble() ?: 0.0
        val id = (map["id"] as? String) ?: java.util.UUID.randomUUID().toString()
        val label = (map["label"] as? String) ?: ""

        val p1 = Triple(p1Arr.getOrElse(0) { 0.0 }, p1Arr.getOrElse(1) { 0.0 }, p1Arr.getOrElse(2) { 0.0 })
        val p2 = Triple(p2Arr.getOrElse(0) { 0.0 }, p2Arr.getOrElse(1) { 0.0 }, p2Arr.getOrElse(2) { 0.0 })

        return Measurement(id = id, p1 = p1, p2 = p2, distance = distance, label = label)
    }
}
