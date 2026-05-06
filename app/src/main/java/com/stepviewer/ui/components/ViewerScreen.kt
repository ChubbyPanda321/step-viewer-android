package com.stepviewer.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stepviewer.bridge.WebViewBridge
import com.stepviewer.util.copyToClipboard
import com.stepviewer.viewmodel.ViewerViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val recentFiles by viewModel.recentFiles.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val bridge = remember { WebViewBridge() }

    var showInfoPanel by remember { mutableStateOf(true) }
    var showFileHistory by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

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

            // Loading overlay
            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "Loading...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

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
                        text = uiState.fileInfo.fileName.ifEmpty { "STEP Viewer" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )

                    IconButton(onClick = { showFileHistory = true }) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = "File History",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    ThemeToggle(
                        currentMode = uiState.themeMode,
                        onToggle = { viewModel.setThemeMode(it) },
                    )

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Info Panel") },
                                onClick = {
                                    showInfoPanel = !showInfoPanel
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("File History") },
                                onClick = {
                                    showFileHistory = true
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Fit View") },
                                onClick = {
                                    viewModel.fitView()
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export Screenshot") },
                                onClick = {
                                    showMenu = false
                                    Toast.makeText(context, "Screenshot captured", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }
            }

            // FAB for opening files
            FloatingActionButton(
                onClick = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = "Open File",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }

    // Info panel bottom sheet
    if (showInfoPanel) {
        InfoPanel(
            fileInfo = uiState.fileInfo,
            selectedMaterial = uiState.selectedMaterial,
            materials = uiState.materials,
            isMeasuring = uiState.isMeasuring,
            activeMeasurements = uiState.activeMeasurements,
            viewMode = uiState.viewMode,
            isFavorite = uiState.isFavorite,
            isModelLoaded = uiState.isModelLoaded,
            onCopy = { key, value ->
                context.copyToClipboard(key, value)
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            },
            onMaterialSelect = { viewModel.selectMaterial(it) },
            onAddCustomMaterial = { viewModel.toggleMaterialEditor() },
            onToggleMeasurement = { viewModel.toggleMeasurement() },
            onViewModeChange = { viewModel.setViewMode(it) },
            onFavoriteToggle = { viewModel.toggleFavorite() },
            onFitView = { viewModel.fitView() },
            onDismiss = { showInfoPanel = false },
        )
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
                    Toast.makeText(context, "Invalid file URI", Toast.LENGTH_SHORT).show()
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
