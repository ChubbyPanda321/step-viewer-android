package com.stepviewer.ui.components

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import androidx.webkit.WebViewAssetLoader.InternalStoragePathHandler
import com.stepviewer.bridge.WebViewBridge
import com.stepviewer.viewmodel.ViewerViewModel
import kotlinx.coroutines.flow.first
import java.io.File

@Composable
fun StepWebView(
    bridge: WebViewBridge,
    viewModel: ViewerViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val cadModelsDir = remember {
        File(context.filesDir, "cad_models").also { it.mkdirs() }
    }

    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", AssetsPathHandler(context))
            .addPathHandler("/models/", InternalStoragePathHandler(context, cadModelsDir))
            .build()
    }

    val webView = remember(context) {
        android.webkit.WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            }

            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(0x00000000)
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
            clearCache(true)

            addJavascriptInterface(bridge, "AndroidBridge")

            webViewClient = object : android.webkit.WebViewClient() {
                @Suppress("DEPRECATION")
                override fun shouldInterceptRequest(
                    view: android.webkit.WebView?,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request.url)
                }

                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript("""
                        if (typeof onAndroidReady === 'function') {
                            onAndroidReady();
                        }
                    """.trimIndent(), null)
                }
            }

            loadUrl("https://appassets.androidplatform.net/assets/viewer/index.html")
        }
    }

    // Collect JS commands and send when bridge is ready
    LaunchedEffect(Unit) {
        viewModel.pendingJsCommand.collect { cmd ->
            bridge.isJsReady.first { it }
            webView.evaluateJavascript(cmd, null)
        }
    }

    // After bridge becomes ready (e.g. after activity recreation), reload
    // the last model if one was loaded — WebView loses its state on recreate.
    LaunchedEffect(Unit) {
        bridge.isJsReady.first { it }
        if (viewModel.uiState.value.isModelLoaded) {
            viewModel.reloadLastModel()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize(),
    )
}
