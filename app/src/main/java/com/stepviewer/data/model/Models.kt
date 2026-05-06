package com.stepviewer.data.model

import com.stepviewer.R
import java.io.Serializable

/**
 * Supported CAD file formats.
 */
enum class CadFormat(val displayNameRes: Int, val extensions: List<String>) {
    STEP(R.string.format_step, listOf("stp", "step", "p21")),
    IGES(R.string.format_iges, listOf("igs", "iges"));

    companion object {
        fun fromExtension(ext: String): CadFormat? {
            val lower = ext.lowercase().removePrefix(".")
            return entries.find { lower in it.extensions }
        }
    }
}

/**
 * Information about a loaded CAD file/model.
 */
data class StepFileInfo(
    val fileName: String = "",
    val fileSize: Long = 0L,
    val format: CadFormat = CadFormat.STEP,
    val height: Double = 0.0,
    val width: Double = 0.0,
    val length: Double = 0.0,
    val volume: Double = 0.0,
    val surfaceArea: Double = 0.0,
    val faceCount: Int = 0,
    val mass: Double = 0.0,
)

/**
 * A material with name and density (g/mm³).
 */
data class Material(
    val id: Long = 0L,
    val name: String = "",
    val density: Double = 0.0,
    val isCustom: Boolean = false,
)

/**
 * A single vertex-to-vertex measurement.
 */
data class Measurement(
    val id: String = java.util.UUID.randomUUID().toString(),
    val p1: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0),
    val p2: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0),
    val distance: Double = 0.0,
    val label: String = "",
)

/**
 * View mode for 3D rendering.
 */
enum class ViewMode(val labelRes: Int) {
    SOLID(R.string.view_solid),
    SOLID_EDGES(R.string.view_solid_edges),
    WIREFRAME(R.string.view_wireframe),
    TRANSPARENT(R.string.view_transparent),
    HIDDEN_LINE(R.string.view_hidden_line),
}

/**
 * Theme preference state.
 */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

/**
 * Central UI state for the viewer screen.
 */
data class ViewerUiState(
    val isLoading: Boolean = false,
    val isModelLoaded: Boolean = false,
    val error: String? = null,
    val fileInfo: StepFileInfo = StepFileInfo(),
    val selectedMaterial: Material = Material(),
    val materials: List<Material> = emptyList(),
    val customMaterials: List<Material> = emptyList(),
    val isMeasuring: Boolean = false,
    val activeMeasurements: List<Measurement> = emptyList(),
    val measurementVertexCount: Int = 0,
    val pendingMeasurementPoint: Triple<Double, Double, Double>? = null,
    val viewMode: ViewMode = ViewMode.SOLID,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val isFavorite: Boolean = false,
    val showMaterialEditor: Boolean = false,
    val editingMaterial: Material? = null,
    val showFileHistory: Boolean = false,
    val snackbarMessage: String? = null,
    val snapToVertex: Boolean = true,
    val showInfoPanel: Boolean = false,
    val showDimensions: Boolean = false,
)
