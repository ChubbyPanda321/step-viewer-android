# CLAUDE.md — STEP Viewer Android

Android app for viewing STEP/IGES 3D CAD models. WebView + Three.js + occt-import-js (WASM). Kotlin/Compose UI, JS 3D engine. Default locale `zh`, override `en`.

## Build & install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.stepviewer/.MainActivity
```

Emulator: `emulator-5554`. Test file: `/sdcard/Download/ring2.stp`.

## Key files

- `viewer.js` — Three.js scene, model loading, measurement, 5 view modes, 2D canvas compositing
- `bridge.js` — JS bridge: `window.*` functions exposed to Kotlin
- `ViewerViewModel.kt` — central state, file loading, JS command Channel
- `ViewerScreen.kt` — main layout: toolbar, FAB, InfoPanel, MeasurementBar
- `StepWebView.kt` — WebView + WebViewAssetLoader setup
- `InfoPanel.kt` — persistent bottom card (dimensions, material, view modes)
- `MeasurementBar.kt` — floating measurement results (top-right)
- `Models.kt` — all data classes, enums (ViewMode uses `R.string.*`)
- `CadFileRepository.kt` — SAF URI → internal cache copy + persistable URI permission
- `LocaleHelper.kt` — locale override via SharedPreferences + context wrapping
- `strings.xml` — Chinese (default); `values-en/strings.xml` — English

## Critical gotchas

1. **2D canvas compositing** — Emulator GPU doesn't composite WebGL to WebView. Three.js renders off-screen (left: -9999px), copied to visible 2D canvas via `drawImage()` each frame. `preserveDrawingBuffer: true` required. Do NOT remove.

2. **Channel, not StateFlow** for JS commands — ensures ordered delivery without conflation.

3. **WebViewAssetLoader** serves models from `filesDir/cad_models/` at `https://appassets.androidplatform.net/models/{name}`. No file:// URIs.

4. **Measurement clearing** — `setMeasurementMode(false)` clears both pending points AND completed measurement objects.

5. **60s loading timeout** in ViewModel in case bridge callback is lost.

6. **Always build+install after changes.**

7. **Use `git checkout` to revert uncommitted file changes** — faster and guarantees exact reversal.

## Git

Branch: `master` · User: `ChubbyPanda123` · Commit co-author: `Co-Authored-By: DeepSeek V4 pro`
