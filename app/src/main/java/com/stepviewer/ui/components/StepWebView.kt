package com.stepviewer.ui.components

import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.stepviewer.bridge.WebViewBridge
import com.stepviewer.viewmodel.ViewerViewModel

@Composable
fun StepWebView(
    bridge: WebViewBridge,
    viewModel: ViewerViewModel,
    modifier: Modifier = Modifier,
) {
    val pendingCommand by viewModel.pendingJsCommand.collectAsState()
    val isJsReady by bridge.isJsReady.collectAsState()

    val context = LocalContext.current

    // Read WASM binary from assets once
    val wasmBytes = remember {
        try {
            context.assets.open("viewer/occt-import-js.wasm").use { it.readBytes() }
        } catch (e: Exception) {
            ByteArray(0)
        }
    }

    val webView = remember(context, wasmBytes) {
        android.webkit.WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
            }

            addJavascriptInterface(bridge, "AndroidBridge")

            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldInterceptRequest(
                    view: android.webkit.WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    // Serve WASM from memory — avoids file:// XHR cross-origin block
                    if (url.endsWith("occt-import-js.wasm") && wasmBytes.isNotEmpty()) {
                        return WebResourceResponse(
                            "application/wasm",
                            null,
                            wasmBytes.inputStream()
                        )
                    }
                    return null
                }

                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Inject WASM binary so occt-import-js can use it without XHR.
                    // Then dynamically load occt-import-js.js.
                    val wasmB64 = if (wasmBytes.isNotEmpty()) {
                        Base64.encodeToString(wasmBytes, Base64.NO_WRAP)
                    } else ""
                    view?.evaluateJavascript("""
                        // Set up WASM binary so occt-import-js doesn't need file:// XHR
                        var _wasmB64 = '$wasmB64';
                        var _wasmStr = atob(_wasmB64);
                        var _wasmBytes = new Uint8Array(_wasmStr.length);
                        for (var i = 0; i < _wasmStr.length; i++) {
                            _wasmBytes[i] = _wasmStr.charCodeAt(i);
                        }
                        if (!window.Module) window.Module = {};
                        window.Module.wasmBinary = _wasmBytes;
                        _wasmB64 = _wasmStr = null;

                        // Load occt-import-js.js dynamically (wasmBinary is already set)
                        var s = document.createElement('script');
                        s.src = 'occt-import-js.js';
                        s.onload = function() {
                            if (typeof onAndroidReady === 'function') {
                                onAndroidReady();
                            }
                        };
                        document.head.appendChild(s);
                    """.trimIndent(), null)
                }
            }

            loadUrl("file:///android_asset/viewer/index.html")
        }
    }

    // Send pending JS commands only after the bridge reports ready
    LaunchedEffect(pendingCommand, isJsReady) {
        if (isJsReady) {
            pendingCommand?.let { cmd ->
                webView.evaluateJavascript(cmd, null)
                viewModel.consumeJsCommand()
            }
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
