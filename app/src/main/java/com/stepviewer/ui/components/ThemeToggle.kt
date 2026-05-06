package com.stepviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stepviewer.data.model.ThemeMode

@Composable
fun ThemeToggle(
    currentMode: ThemeMode,
    onToggle: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nextMode = when (currentMode) {
        ThemeMode.SYSTEM -> ThemeMode.LIGHT
        ThemeMode.LIGHT -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.SYSTEM
    }

    val icon = when (currentMode) {
        ThemeMode.LIGHT -> Icons.Filled.LightMode
        ThemeMode.DARK -> Icons.Filled.DarkMode
        ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
    }

    IconButton(
        onClick = { onToggle(nextMode) },
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Toggle theme",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
