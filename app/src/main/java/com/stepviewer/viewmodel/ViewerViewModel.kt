package com.stepviewer.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.stepviewer.R
import com.stepviewer.bridge.WebViewBridge
import com.stepviewer.data.model.CadFormat
import com.stepviewer.data.model.Material
import com.stepviewer.data.model.Measurement
import com.stepviewer.data.model.StepFileInfo
import com.stepviewer.data.model.ThemeMode
import com.stepviewer.data.model.ViewMode
import com.stepviewer.data.model.ViewerUiState
import com.stepviewer.data.local.FileHistoryEntity
import com.stepviewer.data.preferences.AppPreferences
import com.stepviewer.data.repository.CadFileRepository
import com.stepviewer.data.repository.FileHistoryRepository
import com.stepviewer.data.repository.MaterialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val cadFileRepo: CadFileRepository,
    private val fileHistoryRepo: FileHistoryRepository,
    private val materialRepo: MaterialRepository,
    private val preferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private var currentFileUri: String? = null
    private var currentFileInfo: StepFileInfo = StepFileInfo()
    private var currentVolume: Double = 0.0
    private var lastLoadedFileName: String? = null
    private var lastLoadedFormat: String? = null

    // Commands to send to WebView (queued via Channel for ordered delivery)
    private val _pendingJsCommand = Channel<String>(Channel.BUFFERED)
    val pendingJsCommand: Flow<String> = _pendingJsCommand.receiveAsFlow()

    // File history state
    private val _recentFiles = MutableStateFlow<List<FileHistoryEntity>>(emptyList())
    val recentFiles: StateFlow<List<FileHistoryEntity>> = _recentFiles.asStateFlow()

    private val _favorites = MutableStateFlow<List<FileHistoryEntity>>(emptyList())
    val favorites: StateFlow<List<FileHistoryEntity>> = _favorites.asStateFlow()

    init {
        // Load preferences
        viewModelScope.launch {
            preferences.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        // Load materials
        viewModelScope.launch {
            materialRepo.allMaterials().collect { materials ->
                _uiState.update { it.copy(materials = materials) }
            }
        }
        // Restore last material
        viewModelScope.launch {
            preferences.lastMaterialName.first().let { name ->
                materialRepo.findMaterial(name)?.let { material ->
                    _uiState.update { it.copy(selectedMaterial = material) }
                }
            }
        }
        // Collect file history
        viewModelScope.launch {
            fileHistoryRepo.getRecentFiles().collect { files ->
                _recentFiles.value = files
            }
        }
        viewModelScope.launch {
            fileHistoryRepo.getFavorites().collect { files ->
                _favorites.value = files
            }
        }
    }

    /**
     * Process a file selected via SAF.
     * Copies file to internal cache and sends the path to WebView.
     * This avoids loading the entire file into base64 memory.
     */
    fun loadFile(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                // Safety timeout: clear loading state after 60s in case bridge callback is lost
                viewModelScope.launch {
                    kotlinx.coroutines.delay(60_000)
                    if (_uiState.value.isLoading) {
                        _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.error_timed_out)) }
                    }
                }

                val result = cadFileRepo.copyToCache(uri)
                currentFileUri = uri.toString()
                lastLoadedFileName = result.fileName
                lastLoadedFormat = result.format.name.lowercase()

                // Save to file history
                fileHistoryRepo.addOrUpdate(
                    uri = uri.toString(),
                    fileName = result.fileName,
                    fileSize = result.fileSize,
                    format = result.format,
                )

                // Check if this file is a favorite
                val history = fileHistoryRepo.getByUri(uri.toString())
                _uiState.update { it.copy(isFavorite = history?.isFavorite ?: false) }

                // Send URL to WebView — served by WebViewAssetLoader from internal storage
                val format = result.format.name.lowercase()
                val encodedName = java.net.URLEncoder.encode(result.fileName, "UTF-8")
                    .replace("+", "%20")
                val modelUrl = "https://appassets.androidplatform.net/models/$encodedName"
                val escapedName = result.fileName.replace("'", "\\'")

                val command = """
                    loadFileFromUrl('${modelUrl}', '${escapedName}', '${format}');
                """.trimIndent()

                Log.d("ViewerVM", "Sending JS command for: $modelUrl")
                sendJsCommand(command)

            } catch (e: Exception) {
                Log.e("ViewerVM", "Error in loadFile: ${e.message}", e)
                val message = when {
                    e.message?.contains("too large") == true -> context.getString(R.string.error_file_too_large)
                    e.message?.contains("Unsupported") == true -> context.getString(R.string.error_format)
                    e.message?.contains("Permission") == true -> context.getString(R.string.error_permission)
                    else -> context.getString(R.string.error_generic) + ": ${e.message}"
                }
                _uiState.update { it.copy(isLoading = false, error = message) }
            }
        }
    }

    /**
     * Called when JS reports model info is ready.
     */
    fun onModelInfoReceived(info: StepFileInfo) {
        currentFileInfo = info.copy(fileName = uiState.value.fileInfo.fileName)
        currentVolume = info.volume
        val mass = calculateMass(info.volume)

        _uiState.update {
            it.copy(
                isLoading = false,
                isModelLoaded = true,
                error = null,
                fileInfo = info.copy(mass = mass),
            )
        }

        // Persist last material choice
        viewModelScope.launch {
            preferences.setLastMaterial(uiState.value.selectedMaterial.name)
        }
    }

    /**
     * Called when a measurement result comes from JS.
     */
    fun onMeasurementReceived(measurement: Measurement) {
        _uiState.update { state ->
            val measurements = state.activeMeasurements + measurement
            state.copy(activeMeasurements = measurements, measurementVertexCount = 0)
        }
    }

    /**
     * Change the selected material and recalculate mass.
     */
    fun selectMaterial(material: Material) {
        _uiState.update { it.copy(selectedMaterial = material) }
        recalculateMass()
        viewModelScope.launch {
            preferences.setLastMaterial(material.name)
        }
    }

    /**
     * Add a custom material with the given name and density.
     */
    fun addCustomMaterial(name: String, density: Double) {
        viewModelScope.launch {
            try {
                val material = materialRepo.addCustomMaterial(name, density)
                _uiState.update { it.copy(selectedMaterial = material) }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to add material: ${e.message}") }
            }
        }
    }

    /**
     * Update an existing custom material.
     */
    fun updateCustomMaterial(id: Long, name: String, density: Double) {
        viewModelScope.launch {
            materialRepo.updateCustomMaterial(id, name, density)
            // Refresh selected material
            materialRepo.findMaterial(name)?.let {
                _uiState.update { state -> state.copy(selectedMaterial = it) }
            }
            recalculateMass()
        }
    }

    /**
     * Delete a custom material.
     */
    fun deleteCustomMaterial(id: Long) {
        viewModelScope.launch {
            materialRepo.deleteCustomMaterial(id)
            // If the deleted material was selected, reset to first preset
            if (_uiState.value.selectedMaterial.id == id) {
                _uiState.value.materials.firstOrNull()?.let { selectMaterial(it) }
            }
        }
    }

    /**
     * Toggle measurement mode.
     */
    fun toggleMeasurement() {
        val newState = !_uiState.value.isMeasuring
        _uiState.update {
            it.copy(
                isMeasuring = newState,
                activeMeasurements = if (!newState) emptyList() else it.activeMeasurements,
                measurementVertexCount = 0,
            )
        }
        sendJsCommand("setMeasurementMode($newState)")
    }

    /**
     * Toggle snap-to-vertex mode.
     */
    fun toggleSnapToVertex() {
        val newState = !_uiState.value.snapToVertex
        _uiState.update { it.copy(snapToVertex = newState) }
        sendJsCommand("setSnapToVertex($newState)")
    }

    /**
     * Toggle 3D dimension indicators.
     */
    fun toggleShowDimensions() {
        val newState = !_uiState.value.showDimensions
        _uiState.update { it.copy(showDimensions = newState) }
        sendJsCommand("setShowDimensions($newState)")
    }

    /**
     * Toggle info panel visibility.
     */
    fun toggleInfoPanel() {
        _uiState.update { it.copy(showInfoPanel = !it.showInfoPanel) }
    }

    /**
     * Set the view mode.
     */
    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
        sendJsCommand("setViewMode('${mode.name.lowercase()}')")
    }

    /**
     * Reset camera to fit all geometry.
     */
    fun fitView() {
        sendJsCommand("fitView()")
    }

    /**
     * Set the theme mode.
     */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
        }
    }

    /**
     * Toggle favorite status for current file.
     */
    fun toggleFavorite() {
        val currentUri = currentFileUri ?: return
        val newFavorite = !_uiState.value.isFavorite
        _uiState.update { it.copy(isFavorite = newFavorite) }
        viewModelScope.launch {
            fileHistoryRepo.setFavorite(currentUri, newFavorite)
        }
    }

    /**
     * Remove a measurement by its ID.
     */
    fun removeMeasurement(id: String) {
        _uiState.update { state ->
            state.copy(activeMeasurements = state.activeMeasurements.filter { it.id != id })
        }
        sendJsCommand("removeMeasurement('$id')")
    }

    /**
     * Toggle the file history sheet.
     */
    fun toggleFileHistory() {
        _uiState.update { it.copy(showFileHistory = !it.showFileHistory) }
    }

    /**
     * Toggle the material editor dialog.
     */
    fun toggleMaterialEditor(material: Material? = null) {
        _uiState.update {
            it.copy(
                showMaterialEditor = !it.showMaterialEditor,
                editingMaterial = material,
            )
        }
    }

    /**
     * Clear the snackbar message.
     */
    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Dismiss any JS error.
     */
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Re-send the last loaded model to WebView. Used after activity recreation
     * (e.g. language switch) where the WebView is rebuilt but the model is still cached.
     */
    fun reloadLastModel() {
        // Skip if a manual loadFile() is already in progress — avoids
        // racing two load commands where one fails but the other succeeds.
        if (_uiState.value.isLoading) return

        val fileName = lastLoadedFileName ?: return
        val format = lastLoadedFormat ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }

        val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8")
            .replace("+", "%20")
        val modelUrl = "https://appassets.androidplatform.net/models/$encodedName"
        val escapedName = fileName.replace("'", "\\'")
        val command = """
            loadFileFromUrl('${modelUrl}', '${escapedName}', '${format}');
        """.trimIndent()
        sendJsCommand(command)
    }

    /**
     * Re-load from file URI (for "reopen" flow).
     */
    fun reloadFile(uriString: String) {
        loadFile(Uri.parse(uriString))
    }

    /**
     * Delete a file from history.
     */
    fun deleteFromHistory(uri: String) {
        viewModelScope.launch {
            fileHistoryRepo.delete(uri)
        }
    }

    /**
     * Clear all non-favorite file history entries.
     */
    fun clearHistory() {
        viewModelScope.launch {
            fileHistoryRepo.clearNonFavorites()
        }
    }

    /**
     * Toggle favorite on a file history entry.
     */
    fun toggleHistoryFavorite(uri: String, isFavorite: Boolean) {
        viewModelScope.launch {
            fileHistoryRepo.setFavorite(uri, isFavorite)
        }
    }

    // --- Private helpers ---

    private fun calculateMass(volume: Double): Double {
        val density = _uiState.value.selectedMaterial.density
        return volume * density
    }

    private fun recalculateMass() {
        val mass = calculateMass(currentVolume)
        _uiState.update { state ->
            state.copy(fileInfo = state.fileInfo.copy(mass = mass))
        }
    }

    private fun sendJsCommand(js: String) {
        viewModelScope.launch {
            _pendingJsCommand.send(js)
        }
    }
}
