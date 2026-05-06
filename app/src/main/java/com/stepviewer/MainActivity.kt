package com.stepviewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.stepviewer.data.model.ThemeMode
import com.stepviewer.ui.components.ViewerScreen
import com.stepviewer.ui.theme.StepViewerTheme
import com.stepviewer.viewmodel.ViewerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: ViewerViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            val isDarkTheme = when (uiState.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            StepViewerTheme(isDarkTheme = isDarkTheme) {
                ViewerScreen()
            }

            // Handle file opened via intent (ACTION_VIEW or ACTION_SEND)
            handleFileIntent(intent, viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle intent in the composable by restarting content
        recreate()
    }

    /**
     * Extract and load a file from an incoming intent.
     * Supports ACTION_VIEW (open with) and ACTION_SEND (share to).
     */
    private fun handleFileIntent(intent: Intent, viewModel: ViewerViewModel) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) {
            viewModel.loadFile(uri)
            // Clear the intent data to avoid reloading on config changes
            intent.data = null
            intent.removeExtra(Intent.EXTRA_STREAM)
        }
    }
}
