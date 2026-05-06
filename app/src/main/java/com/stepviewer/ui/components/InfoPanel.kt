package com.stepviewer.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stepviewer.data.model.Material
import com.stepviewer.data.model.Measurement
import com.stepviewer.data.model.StepFileInfo
import com.stepviewer.data.model.ViewMode
import com.stepviewer.ui.components.FavoriteToggle
import com.stepviewer.util.copyToClipboard
import com.stepviewer.util.format2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoPanel(
    fileInfo: StepFileInfo,
    selectedMaterial: Material?,
    materials: List<Material>,
    isMeasuring: Boolean,
    activeMeasurements: List<Measurement>,
    viewMode: ViewMode,
    isFavorite: Boolean,
    isModelLoaded: Boolean,
    onCopy: (String, String) -> Unit,
    onMaterialSelect: (Material) -> Unit,
    onAddCustomMaterial: () -> Unit,
    onToggleMeasurement: () -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onFavoriteToggle: () -> Unit,
    onFitView: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            // Header with filename and favorite
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fileInfo.fileName.ifEmpty { "No model loaded" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                FavoriteToggle(
                    isFavorite = isFavorite,
                    onToggle = onFavoriteToggle,
                    enabled = isModelLoaded,
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            if (isModelLoaded) {
                // Model info rows
                InfoRow("Name", fileInfo.fileName.ifEmpty { "-" }, "name", onCopy)
                InfoRow("Format", fileInfo.format.displayName, "format", onCopy)
                InfoRow("Height", "${fileInfo.height.format2()} mm", "height", onCopy)
                InfoRow("Width", "${fileInfo.width.format2()} mm", "width", onCopy)
                InfoRow("Length", "${fileInfo.length.format2()} mm", "length", onCopy)
                InfoRow("Volume", "${fileInfo.volume.format2()} mm³", "volume", onCopy)
                InfoRow("Mass", "${fileInfo.mass.format2()} g", "mass", onCopy)
                InfoRow("Faces", "${fileInfo.faceCount}", "faces", onCopy)

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Material Selector
                MaterialSelector(
                    materials = materials,
                    selectedMaterial = selectedMaterial,
                    onSelect = onMaterialSelect,
                    onAddCustom = onAddCustomMaterial,
                    onEditCustom = {},
                    onDeleteCustom = {},
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // View modes
                Text(
                    "View Mode",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ViewMode.entries.forEach { mode ->
                        FilterChip(
                            selected = viewMode == mode,
                            onClick = { onViewModeChange(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Measurement
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = isMeasuring,
                        onClick = onToggleMeasurement,
                        label = { Text("Measure") },
                        leadingIcon = {
                            Icon(Icons.Filled.Straighten, contentDescription = null, modifier = Modifier.height(18.dp))
                        },
                    )
                    Button(onClick = onFitView) {
                        Text("Fit View")
                    }
                }

                // Active measurements
                if (activeMeasurements.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Measurements",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    activeMeasurements.forEach { measurement ->
                        MeasurementRow(measurement = measurement, onCopy = onCopy)
                    }
                }
            } else {
                // No model loaded placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Open a STEP or IGES file to view model info",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    copyKey: String,
    onCopy: (String, String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onCopy(copyKey, value) },
            modifier = Modifier.height(32.dp),
        ) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy $label",
                modifier = Modifier.height(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MeasurementRow(
    measurement: Measurement,
    onCopy: (String, String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .padding(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp),
            ) {
                // Blue indicator bar
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(androidx.compose.ui.graphics.Color(0xFF1565C0))
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "${measurement.distance.format2()} mm",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onCopy("measurement_${measurement.id}", measurement.distance.format2()) },
            modifier = Modifier.height(32.dp),
        ) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy distance",
                modifier = Modifier.height(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
