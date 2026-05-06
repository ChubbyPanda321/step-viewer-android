package com.stepviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.stepviewer.R
import com.stepviewer.data.model.Measurement
import com.stepviewer.util.format2
import kotlin.math.roundToInt

@Composable
fun MeasurementBar(
    measurements: List<Measurement>,
    isMeasuring: Boolean,
    snapToVertex: Boolean,
    onRemove: (String) -> Unit,
    onToggleSnapToVertex: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (measurements.isEmpty() && !isMeasuring) return

    var collapsed by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(IntOffset.Zero) }
    val density = LocalDensity.current

    Surface(
        modifier = modifier
            .statusBarsPadding()
            .offset { dragOffset }
            .padding(start = 8.dp, top = 48.dp)
            .width(200.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    dragOffset = IntOffset(
                        (dragOffset.x + dragAmount.x).roundToInt(),
                        (dragOffset.y + dragAmount.y).roundToInt(),
                    )
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            // Header row with collapse toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = if (isMeasuring) stringResource(R.string.measuring) else stringResource(R.string.measurements),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMeasuring) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface,
                    )
                    if (isMeasuring) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Straighten,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (measurements.isNotEmpty()) {
                    IconButton(
                        onClick = { collapsed = !collapsed },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                            contentDescription = if (collapsed) stringResource(R.string.expand) else stringResource(R.string.collapse),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Snap-to-vertex toggle — only visible while measuring
            if (isMeasuring) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    FilterChip(
                        selected = snapToVertex,
                        onClick = onToggleSnapToVertex,
                        label = {
                            Text(
                                stringResource(R.string.snap_to_vertex),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            if (measurements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))

                if (collapsed) {
                    // Show only latest measurement
                    val latest = measurements.last()
                    MeasurementRow(latest, onRemove)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            items = measurements,
                            key = { it.id },
                        ) { m ->
                            MeasurementRow(m, onRemove)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasurementRow(
    measurement: Measurement,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${measurement.distance.format2()} ${stringResource(R.string.unit_mm)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        IconButton(
            onClick = { onRemove(measurement.id) },
            modifier = Modifier.size(20.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.remove_measurement),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
