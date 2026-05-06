package com.stepviewer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "Edit Material" else "Add Custom Material")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Material Name") },
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
                    label = { Text("Density (g/mm³)") },
                    placeholder = { Text("e.g. 0.00785") },
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
                    // Validate
                    if (name.isBlank()) {
                        nameError = "Name is required"
                        return@TextButton
                    }
                    val density = densityStr.toDoubleOrNull()
                    if (density == null || density <= 0) {
                        densityError = "Enter a valid positive density"
                        return@TextButton
                    }
                    onSave(name.trim(), density)
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
