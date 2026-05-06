package com.stepviewer.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private var currentFileUri: String? = null
    private var currentFileInfo: StepFileInfo = StepFileInfo()
    private var currentVolume: Double = 0.0

    // Commands to send to WebView (consumed once by StepWebView)
    private val _pendingJsCommand = MutableStateFlow<String?>(null)
    val pendingJsCommand: StateFlow<String?> = _pendingJsCommand.asStateFlow()

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

                val result = cadFileRepo.copyToCache(uri)
                currentFileUri = uri.toString()

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

                // Send file path to WebView — it will read the file directly
                val format = result.format.name.lowercase()
                val escapedPath = result.localPath?.replace("\\", "\\\\") ?: ""
                val escapedName = result.fileName.replace("'", "\\'")

                val command = """
                    loadFileFromPath('${escapedPath}', '${escapedName}', '${format}');
                """.trimIndent()

                _pendingJsCommand.value = command

            } catch (e: Exception) {
                val message = when {
                    e.message?.contains("too large") == true -> "File is too large (max 200MB)"
                    e.message?.contains("Unsupported") == true -> "Unsupported file format"
                    else -> "Error loading file: ${e.message}"
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
        viewModelScope.launch {
            preferences.setMeasurementEnabled(newState)
        }
        sendJsCommand("setMeasurementMode($newState)")
    }

    /**
     * Set the view mode (solid, wireframe, transparent).
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
        _pendingJsCommand.value = js
    }

    /**
     * Consume a pending JS command (called by the WebView composable).
     */
    fun consumeJsCommand(): String? {
        val cmd = _pendingJsCommand.value
        _pendingJsCommand.value = null
        return cmd
    }
}
