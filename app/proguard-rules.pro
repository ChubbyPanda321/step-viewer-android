# ProGuard rules for Step Viewer

# Keep generic signatures — required by Gson TypeToken
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room entities
-keep class com.stepviewer.data.local.** { *; }

# Keep Gson models and TypeToken
-keep class com.stepviewer.data.model.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep JavaScript interface
-keepclassmembers class com.stepviewer.bridge.WebViewBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.stepviewer.bridge.WebViewBridge { *; }

# Keep WebView
-keep class * extends android.webkit.WebView { *; }

# Keep all app code (catch-all — prevents R8 from being too aggressive)
-keep class com.stepviewer.** { *; }

# Keep WebViewAssetLoader
-keep class androidx.webkit.WebViewAssetLoader { *; }
-keep class androidx.webkit.WebViewAssetLoader$* { *; }
-dontwarn androidx.webkit.**
