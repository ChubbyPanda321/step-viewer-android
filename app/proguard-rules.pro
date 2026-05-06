# ProGuard rules for Step Viewer

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room entities
-keep class com.stepviewer.data.local.** { *; }

# Keep Gson models
-keep class com.stepviewer.data.model.** { *; }

# Keep JavaScript interface
-keepclassmembers class com.stepviewer.bridge.WebViewBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView
-keep class * extends android.webkit.WebView { *; }

# Keep occt-import-js (if bundled in native)
-keep class com.stepviewer.** { *; }

# Keep WebViewAssetLoader
-keep class androidx.webkit.WebViewAssetLoader { *; }
-keep class androidx.webkit.WebViewAssetLoader$* { *; }
-dontwarn androidx.webkit.**
