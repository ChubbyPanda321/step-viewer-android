# Third-Party Licenses

This project includes or depends on the following third-party software.

## occt-import-js (LGPL-2.1)

- **Project**: https://github.com/kovacsv/occt-import-js
- **License**: GNU Lesser General Public License v2.1
- **Usage**: WASM-based STEP/IGES CAD file parser, loaded as a separate JavaScript asset in the WebView.
- **Note**: occt-import-js is distributed as an independent, replaceable JavaScript file. You may obtain its source from the upstream repository above.

```
                  GNU LESSER GENERAL PUBLIC LICENSE
                       Version 2.1, February 1999

 Copyright (C) 1991, 1999 Free Software Foundation, Inc.
 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 Everyone is permitted to copy and distribute verbatim copies
 of this license document, but changing it is not allowed.

(This is only a reference — the full LGPL-2.1 text is at:
 https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html)
```

## Three.js (MIT)

- **Project**: https://threejs.org/
- **License**: MIT
- **Copyright**: 2010-2023 Three.js Authors
- **Usage**: 3D rendering engine for the WebView-based model viewer.

```
MIT License

Copyright (c) 2010-2023 Three.js Authors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## AndroidX / Jetpack Libraries (Apache 2.0)

The following libraries are used under the Apache License 2.0:

| Library | Artifact |
|---------|----------|
| Compose UI, Material3, Foundation | `androidx.compose.*` |
| Activity Compose | `androidx.activity:activity-compose` |
| Lifecycle (ViewModel, Runtime) | `androidx.lifecycle:lifecycle-*` |
| WebKit | `androidx.webkit:webkit` |
| Room | `androidx.room:room-*` |
| DataStore Preferences | `androidx.datastore:datastore-preferences` |
| Core KTX | `androidx.core:core-ktx` |
| Hilt Navigation Compose | `androidx.hilt:hilt-navigation-compose` |

## Hilt / Dagger (Apache 2.0)

- `com.google.dagger:hilt-android`
- `com.google.dagger:hilt-android-compiler`

## Gson (Apache 2.0)

- `com.google.code.gson:gson`

## Kotlin Coroutines (Apache 2.0)

- `org.jetbrains.kotlinx:kotlinx-coroutines-android`

## JUnit 4 (Eclipse Public License 1.0)

- `junit:junit` (test dependency only, not included in the APK)
