# STEP Viewer

A 3D CAD model viewer for Android — view STEP and IGES files on your phone.

Supports five visual modes (solid, solid+edges, wireframe, transparent, hidden-line), vertex-to-vertex measurement with snap-to-vertex, bounding box dimension labels, material-based mass calculation, and i18n (Chinese/English).

## Disclaimer

**This application was produced entirely through vibe coding** — conversational AI-assisted development without hand-written implementation. Every line of Kotlin, JavaScript, and Compose UI was generated through iterative prompting and review. No traditional software engineering was involved.

Use at your own discretion. The authors make no warranty as to the correctness, reliability, or fitness of this software for any purpose.

## Download

See [Releases](https://github.com/ChubbyPanda321/step-viewer-android/releases) for the latest signed APK.

## Tech Stack

- **Android**: Kotlin, Jetpack Compose, Material 3, Hilt
- **3D Engine**: Three.js (WebView + WebGL)
- **CAD Parsing**: occt-import-js (WASM)

## License

This project is licensed under the Apache License 2.0 — see [LICENSE](LICENSE).

Third-party components are covered by their own licenses — see [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

## Credits

- [Three.js](https://threejs.org/) — MIT
- [occt-import-js](https://github.com/kovacsv/occt-import-js) — LGPL-2.1
- AndroidX, Compose, Hilt, and other Jetpack libraries — Apache 2.0
