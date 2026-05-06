package com.stepviewer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stepviewer.R
import com.stepviewer.data.model.Material
import com.stepviewer.data.model.StepFileInfo
import com.stepviewer.data.model.ViewMode
import com.stepviewer.util.format2

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoPanel(
    fileInfo: StepFileInfo,
    selectedMaterial: Material?,
    materials: List<Material>,
    isMeasuring: Boolean,
    showDimensions: Boolean,
    viewMode: ViewMode,
    isFavorite: Boolean,
    isModelLoaded: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCopy: (String, String) -> Unit,
    onMaterialSelect: (Material) -> Unit,
    onAddCustomMaterial: () -> Unit,
    onToggleMeasurement: () -> Unit,
    onToggleShowDimensions: () -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onFavoriteToggle: () -> Unit,
    onFitView: () -> Unit,
) {
    if (!isModelLoaded) return

    val unitGram = stringResource(R.string.unit_gram)
    val unitMm = stringResource(R.string.unit_mm)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Collapsed row (always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FavoriteToggle(
                    isFavorite = isFavorite,
                    onToggle = onFavoriteToggle,
                    enabled = true,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileInfo.fileName.ifEmpty { stringResource(R.string.file_name_fallback) },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Text(
                        text = "${fileInfo.mass.format2()} $unitGram · ${selectedMaterial?.name ?: "—"} · ${fileInfo.length.format2()}×${fileInfo.width.format2()}×${fileInfo.height.format2()} $unitMm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onFitView, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.FitScreen,
                        contentDescription = stringResource(R.string.fit_view),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    HorizontalDivider()

                    // Dimensions section
                    SectionHeader(stringResource(R.string.dimensions))
                    InfoRow(stringResource(R.string.length_x), "${fileInfo.length.format2()} $unitMm", onCopy)
                    InfoRow(stringResource(R.string.width_y), "${fileInfo.width.format2()} $unitMm", onCopy)
                    InfoRow(stringResource(R.string.height_z), "${fileInfo.height.format2()} $unitMm", onCopy)
                    InfoRow(stringResource(R.string.volume), "${fileInfo.volume.format2()} ${stringResource(R.string.unit_mm3)}", onCopy)
                    InfoRow(stringResource(R.string.surface_area), "${fileInfo.surfaceArea.format2()} ${stringResource(R.string.unit_mm2)}", onCopy)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Material
                    SectionHeader(stringResource(R.string.material))
                    Spacer(modifier = Modifier.height(4.dp))
                    MaterialSelector(
                        materials = materials,
                        selectedMaterial = selectedMaterial,
                        onSelect = onMaterialSelect,
                        onAddCustom = onAddCustomMaterial,
                        onEditCustom = {},
                        onDeleteCustom = {},
                    )

                    // Mass — prominent with copy
                    val massLabel = stringResource(R.string.mass)
                    val massValue = "${fileInfo.mass.format2()} $unitGram"
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = massLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = massValue,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            IconButton(
                                onClick = { onCopy(massLabel, massValue) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.copy_mass),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // View modes
                    SectionHeader(stringResource(R.string.view_mode))
                    Spacer(modifier = Modifier.height(2.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ViewMode.entries.forEach { mode ->
                            FilterChip(
                                selected = viewMode == mode,
                                onClick = { onViewModeChange(mode) },
                                label = { Text(stringResource(mode.labelRes), style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Actions row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = isMeasuring,
                            onClick = onToggleMeasurement,
                            label = {
                                Text(
                                    if (isMeasuring) stringResource(R.string.measuring)
                                    else stringResource(R.string.measure),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Straighten,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                        FilterChip(
                            selected = showDimensions,
                            onClick = onToggleShowDimensions,
                            label = { Text(stringResource(R.string.show_dimensions)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Straighten,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String, onCopy: (String, String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onCopy(label, value) },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.copy),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
