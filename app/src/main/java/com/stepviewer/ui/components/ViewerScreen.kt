package com.stepviewer.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stepviewer.R
import com.stepviewer.bridge.WebViewBridge
import com.stepviewer.util.LocaleHelper
import com.stepviewer.util.copyToClipboard
import com.stepviewer.viewmodel.ViewerViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel = hiltViewModel(),
    onLanguageChanged: ((String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentFiles by viewModel.recentFiles.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val bridge = remember { WebViewBridge() }

    var showFileHistory by remember { mutableStateOf(false) }
    var infoPanelExpanded by remember { mutableStateOf(false) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.loadFile(it) } }

    // Collect bridge events
    LaunchedEffect(Unit) {
        bridge.modelLoaded.collectLatest { info ->
            info?.let { viewModel.onModelInfoReceived(it) }
        }
    }

    LaunchedEffect(Unit) {
        bridge.measurementResult.collectLatest { measurement ->
            measurement?.let { viewModel.onMeasurementReceived(it) }
        }
    }

    LaunchedEffect(Unit) {
        bridge.jsError.collectLatest { error ->
            error?.let {
                snackbarHostState.showSnackbar(it)
                bridge.clearError()
            }
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    // Refresh material presets when locale changes (activity recreates)
    val currentLang = LocaleHelper.getLanguageCode(context)
    LaunchedEffect(currentLang) {
        viewModel.refreshMaterials()
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                androidx.compose.material3.Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        },
        containerColor = Color.Transparent,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Full-screen 3D WebView
            StepWebView(
                bridge = bridge,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
            )

            // Top toolbar (semi-transparent)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = uiState.fileInfo.fileName.ifEmpty { stringResource(R.string.app_title_fallback) },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )

                    // Language toggle
                    val nextLang = if (currentLang == "zh") "en" else "zh"
                    val nextLangLabel = if (nextLang == "zh") "中" else "EN"
                    androidx.compose.material3.TextButton(
                        onClick = { onLanguageChanged?.invoke(nextLang) },
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            nextLangLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    IconButton(onClick = { showFileHistory = true }) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = stringResource(R.string.file_history),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Measurement results floating card
            MeasurementBar(
                measurements = uiState.activeMeasurements,
                isMeasuring = uiState.isMeasuring,
                snapToVertex = uiState.snapToVertex,
                onRemove = { viewModel.removeMeasurement(it) },
                onToggleSnapToVertex = { viewModel.toggleSnapToVertex() },
                modifier = Modifier.align(Alignment.TopStart),
            )

            // Persistent info panel at bottom
            Box(
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                InfoPanel(
                    fileInfo = uiState.fileInfo,
                    selectedMaterial = uiState.selectedMaterial,
                    materials = uiState.materials,
                    isMeasuring = uiState.isMeasuring,
                    showDimensions = uiState.showDimensions,
                    viewMode = uiState.viewMode,
                    isFavorite = uiState.isFavorite,
                    isModelLoaded = uiState.isModelLoaded,
                    expanded = infoPanelExpanded,
                    onToggleExpanded = { infoPanelExpanded = !infoPanelExpanded },
                    onCopy = { key, value ->
                        context.copyToClipboard(key, value)
                        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                    },
                    onMaterialSelect = { viewModel.selectMaterial(it) },
                    onAddCustomMaterial = { viewModel.toggleMaterialEditor() },
                    onToggleMeasurement = { viewModel.toggleMeasurement() },
                    onToggleShowDimensions = { viewModel.toggleShowDimensions() },
                    onViewModeChange = { viewModel.setViewMode(it) },
                    onFavoriteToggle = { viewModel.toggleFavorite() },
                    onFitView = { viewModel.fitView() },
                )
            }

            // FAB for opening files
            FloatingActionButton(
                onClick = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = if (uiState.isModelLoaded) 64.dp else 16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = stringResource(R.string.open_file),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }

    // File history bottom sheet
    if (showFileHistory) {
        FileHistorySheet(
            recentFiles = recentFiles,
            favorites = favorites,
            onSelect = { entity ->
                try {
                    val uri = Uri.parse(entity.uri)
                    viewModel.loadFile(uri)
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.error_invalid_uri), Toast.LENGTH_SHORT).show()
                }
                showFileHistory = false
            },
            onDelete = { entity ->
                viewModel.deleteFromHistory(entity.uri)
            },
            onToggleFavorite = { entity ->
                viewModel.toggleHistoryFavorite(entity.uri, !entity.isFavorite)
            },
            onClearAll = { viewModel.clearHistory() },
            onDismiss = { showFileHistory = false },
        )
    }

    // Material editor dialog
    if (uiState.showMaterialEditor) {
        MaterialEditorDialog(
            existingMaterial = uiState.editingMaterial,
            onSave = { name, density ->
                val existing = uiState.editingMaterial
                if (existing != null) {
                    viewModel.updateCustomMaterial(existing.id, name, density)
                } else {
                    viewModel.addCustomMaterial(name, density)
                }
                viewModel.toggleMaterialEditor()
            },
            onDismiss = { viewModel.toggleMaterialEditor() },
        )
    }
}
