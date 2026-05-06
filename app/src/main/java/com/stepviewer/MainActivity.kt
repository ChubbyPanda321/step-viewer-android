package com.stepviewer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import com.stepviewer.util.LocaleHelper
import com.stepviewer.viewmodel.ViewerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrapWithLocale(newBase))
    }

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
                ViewerScreen(
                    onLanguageChanged = { code ->
                        if (code != LocaleHelper.getLanguageCode(this)) {
                            LocaleHelper.setLanguageCode(this, code)
                            Toast.makeText(this, R.string.lang_switched, Toast.LENGTH_SHORT).show()
                            recreate()
                        }
                    },
                )
            }

            // Handle file opened via intent (ACTION_VIEW or ACTION_SEND)
            handleFileIntent(intent, viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun handleFileIntent(intent: Intent, viewModel: ViewerViewModel) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null && uri.scheme != "file") {
            viewModel.loadFile(uri)
        }
        intent.data = null
        intent.removeExtra(Intent.EXTRA_STREAM)
    }
}
