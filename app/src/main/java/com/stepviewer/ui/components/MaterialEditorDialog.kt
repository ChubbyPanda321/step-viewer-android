package com.stepviewer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun MaterialEditorDialog(
    existingMaterial: Material? = null,
    onSave: (name: String, density: Double) -> Unit,
    onDelete: ((Long) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existingMaterial?.name ?: "") }
    var densityStr by remember { mutableStateOf(existingMaterial?.density?.toString() ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var densityError by remember { mutableStateOf<String?>(null) }

    val isEditing = existingMaterial != null

    // Capture strings needed in non-composable onClick lambdas
    val nameRequiredMsg = stringResource(R.string.name_required)
    val densityInvalidMsg = stringResource(R.string.density_invalid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEditing) stringResource(R.string.edit_material_title)
                else stringResource(R.string.add_material_title),
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text(stringResource(R.string.material_name)) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = densityStr,
                    onValueChange = {
                        densityStr = it
                        densityError = null
                    },
                    label = { Text(stringResource(R.string.material_density)) },
                    placeholder = { Text(stringResource(R.string.density_placeholder)) },
                    isError = densityError != null,
                    supportingText = densityError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        nameError = nameRequiredMsg
                        return@TextButton
                    }
                    val density = densityStr.toDoubleOrNull()
                    if (density == null || density <= 0) {
                        densityError = densityInvalidMsg
                        return@TextButton
                    }
                    onSave(name.trim(), density)
                },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
