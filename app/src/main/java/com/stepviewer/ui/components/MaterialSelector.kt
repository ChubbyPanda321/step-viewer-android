package com.stepviewer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stepviewer.R
import com.stepviewer.data.model.Material

@Composable
fun MaterialSelector(
    materials: List<Material>,
    selectedMaterial: Material?,
    onSelect: (Material) -> Unit,
    onAddCustom: () -> Unit,
    onEditCustom: (Material) -> Unit,
    onDeleteCustom: (Material) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val presets = materials.filter { !it.isCustom }
    val customs = materials.filter { it.isCustom }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = true },
    ) {
        OutlinedTextField(
            value = selectedMaterial?.name ?: stringResource(R.string.select_material),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.material)) },
            trailingIcon = {
                androidx.compose.material3.IconButton(onClick = { expanded = true }) {
                    Text("▲", style = MaterialTheme.typography.bodySmall)
                }
            },
            singleLine = true,
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.fillMaxWidth(0.9f),
    ) {
        presets.forEach { material ->
            DropdownMenuItem(
                text = {
                    Text(
                        "${material.name} (${material.density} ${stringResource(R.string.unit_density)})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = {
                    onSelect(material)
                    expanded = false
                },
            )
        }

        if (customs.isNotEmpty()) {
            HorizontalDivider()
            customs.forEach { material ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "✎ ${material.name} (${material.density} ${stringResource(R.string.unit_density)})",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = {
                        onSelect(material)
                        expanded = false
                    },
                )
            }
        }

        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(R.string.add_custom_material),
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            onClick = {
                onAddCustom()
                expanded = false
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
    }
}


