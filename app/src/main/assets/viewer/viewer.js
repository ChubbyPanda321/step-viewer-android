/**
 * viewer.js — Three.js scene management, STEP/IGES loading, measurement
 *
 * Dependencies: Three.js, OrbitControls, occt-import-js, bridge.js
 */

(function () {
    'use strict';

    // Wait for dependencies
    function waitForDeps(callback) {
        if (typeof THREE !== 'undefined' &&
            typeof occtimportjs !== 'undefined') {
            var moduleArg = window.Module || {};
            occtimportjs(moduleArg).then(function (mod) {
                window.occtimportjs = mod;
            }).then(callback).catch(function (err) {
                console.error('WASM init failed:', err);
                var loadingOverlay = document.getElementById('loading-overlay');
                if (loadingOverlay) loadingOverlay.style.display = 'none';
                if (window.Bridge && typeof window.Bridge.onError === 'function') {
                    window.Bridge.onError('Failed to initialize 3D engine: ' + (err.message || 'WASM error'));
                }
            });
        } else {
            setTimeout(function () { waitForDeps(callback); }, 100);
        }
    }

    // Main scene state
    const state = {
        scene: null,
        camera: null,
        renderer: null,
        controls: null,
        modelGroup: null,
        measurementGroup: null,
        raycaster: null,
        mouse: null,
        isMeasuring: false,
        currentFormat: 'step',
    };

    // Compute bounding box from geometry vertices
    function computeBoundingBox(geometries) {
        const box = new THREE.Box3();
        geometries.forEach(function (geo) {
            if (geo.isBufferGeometry) {
                geo.computeBoundingBox();
                box.expandByObject(new THREE.Mesh(geo));
            }
        });
        return box;
    }

    // Compute volume using signed tetrahedron method
    function computeVolume(geometries) {
        let totalVolume = 0;
        geometries.forEach(function (geometry) {
            if (!geometry.isBufferGeometry) return;
            const pos = geometry.getAttribute('position');
            const index = geometry.getIndex();
            if (!pos || !index) return;

            const positions = pos.array;
            const indices = index.array;

            for (let i = 0; i < indices.length; i += 3) {
                const a = indices[i] * 3;
                const b = indices[i + 1] * 3;
                const c = indices[i + 2] * 3;

                const ax = positions[a], ay = positions[a + 1], az = positions[a + 2];
                const bx = positions[b], by = positions[b + 1], bz = positions[b + 2];
                const cx = positions[c], cy = positions[c + 1], cz = positions[c + 2];

                const crossX = ay * bz - az * by;
                const crossY = az * bx - ax * bz;
                const crossZ = ax * by - ay * bx;
                const vol = (crossX * cx + crossY * cy + crossZ * cz) / 6;
                totalVolume += vol;
            }
        });
        return Math.abs(totalVolume);
    }

    // Compute surface area from triangulated mesh
    function computeSurfaceArea(geometries) {
        let totalArea = 0;
        geometries.forEach(function (geometry) {
            if (!geometry.isBufferGeometry) return;
            const pos = geometry.getAttribute('position');
            const index = geometry.getIndex();
            if (!pos || !index) return;

            const positions = pos.array;
            const indices = index.array;

            for (let i = 0; i < indices.length; i += 3) {
                const a = indices[i] * 3;
                const b = indices[i + 1] * 3;
                const c = indices[i + 2] * 3;

                const ax = positions[a], ay = positions[a + 1], az = positions[a + 2];
                const bx = positions[b], by = positions[b + 1], bz = positions[b + 2];
                const cx = positions[c], cy = positions[c + 1], cz = positions[c + 2];

                const ux = bx - ax, uy = by - ay, uz = bz - az;
                const vx = cx - ax, vy = cy - ay, vz = cz - az;

                const crossX = uy * vz - uz * vy;
                const crossY = uz * vx - ux * vz;
                const crossZ = ux * vy - uy * vx;
                const area = Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ) / 2;
                totalArea += area;
            }
        });
        return totalArea;
    }

    // Create mesh from occt-import-js result
    function createMeshes(importResult) {
        const meshes = [];
        if (!importResult.meshes) return meshes;

        importResult.meshes.forEach(function (meshData) {
            const attributes = meshData.attributes;
            const indices = meshData.index;

            if (!attributes || !attributes.position || !attributes.position.array) return;

            const posArray = attributes.position.array;
            const normArray = attributes.normal ? attributes.normal.array : null;

            const geometry = new THREE.BufferGeometry();
            geometry.setAttribute(
                'position',
                new THREE.BufferAttribute(new Float32Array(posArray), 3)
            );
            if (normArray) {
                geometry.setAttribute(
                    'normal',
                    new THREE.BufferAttribute(new Float32Array(normArray), 3)
                );
            }
            if (indices) {
                geometry.setIndex(Array.from(indices.array || indices));
            }

            let color = 0x607D8B;
            if (attributes.color && attributes.color.array && attributes.color.array.length > 0) {
                const ca = attributes.color.array;
                const r = Math.round(ca[0] * 255);
                const g = Math.round(ca[1] * 255);
                const b = Math.round(ca[2] * 255);
                color = (r << 16) | (g << 8) | b;
            }

            geometry.computeVertexNormals();

            const matSolid = new THREE.MeshPhongMaterial({
                color: color,
                specular: 0x111111,
                shininess: 30,
                side: THREE.DoubleSide,
            });

            const matWireframe = new THREE.MeshBasicMaterial({
                color: 0x000000,
                wireframe: true,
                transparent: true,
                opacity: 0.3,
            });

            const matTransparent = new THREE.MeshPhongMaterial({
                color: color,
                specular: 0x111111,
                shininess: 30,
                side: THREE.DoubleSide,
                transparent: true,
                opacity: 0.4,
            });

            // Edge line for solid+edges mode
            const edgeGeo = new THREE.EdgesGeometry(geometry, 15);
            const edgeLine = new THREE.LineSegments(
                edgeGeo,
                new THREE.LineBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.5 })
            );
            edgeLine.visible = false;

            // Hidden-line: white solid with black edges on white background
            const matHiddenLine = new THREE.MeshBasicMaterial({ color: 0xFFFFFF });
            const hiddenLineEdges = new THREE.LineSegments(
                new THREE.EdgesGeometry(geometry, 15),
                new THREE.LineBasicMaterial({ color: 0x000000, linewidth: 1 })
            );
            hiddenLineEdges.visible = false;

            const mesh = new THREE.Mesh(geometry, matSolid);
            mesh.add(edgeLine);
            mesh.add(hiddenLineEdges);
            mesh.userData = {
                wireframeMaterial: matWireframe,
                solidMaterial: matSolid,
                transparentMaterial: matTransparent,
                hiddenLineMaterial: matHiddenLine,
                edgeLine: edgeLine,
                hiddenLineEdges: hiddenLineEdges,
                currentMode: 'solid',
            };

            meshes.push(mesh);
        });

        return meshes;
    }

    // Load CAD file from a local file path via bridge chunked reading
    function loadFileFromPath(filePath, fileName, format) {
        var loadingOverlay = document.getElementById('loading-overlay');
        var loadingText = document.getElementById('loading-text');
        loadingOverlay.style.display = 'flex';
        loadingText.textContent = 'Reading file...';

        Bridge.onLoadStart('Reading file...');

        setTimeout(function () {
            try {
                var handle = AndroidBridge.fileOpen(filePath);
                if (!handle) throw new Error('Cannot open file');

                var sizeStr = AndroidBridge.fileGetSize(handle);
                var fileSize = parseInt(sizeStr, 10);
                if (fileSize <= 0) throw new Error('File is empty or unreadable');

                loadingText.textContent = 'Loading ' + (fileSize / 1024 / 1024).toFixed(1) + ' MB...';

                var chunks = [];
                var totalRead = 0;
                var offset = 0;

                while (offset < fileSize) {
                    var chunkB64 = AndroidBridge.fileReadChunk(handle, String(offset));
                    if (!chunkB64) break;

                    var chunkBytes = atob(chunkB64);
                    chunks.push(chunkBytes);
                    var chunkLen = chunkBytes.length;
                    totalRead += chunkLen;
                    offset += chunkLen;

                    var pct = Math.round((totalRead / fileSize) * 100);
                    loadingText.textContent = 'Loading... ' + pct + '%';
                }

                AndroidBridge.fileClose(handle);

                if (totalRead === 0) throw new Error('No data read from file');

                // Assemble chunks into Uint8Array using typed array for speed
                var allBytes = new Uint8Array(totalRead);
                var pos = 0;
                for (var c = 0; c < chunks.length; c++) {
                    var chunkStr = chunks[c];
                    var chunkLen = chunkStr.length;
                    var chunkArr = new Uint8Array(chunkLen);
                    for (var i = 0; i < chunkLen; i++) {
                        chunkArr[i] = chunkStr.charCodeAt(i) & 0xFF;
                    }
                    allBytes.set(chunkArr, pos);
                    pos += chunkLen;
                }

                loadingText.textContent = 'Tessellating geometry...';

                var result;
                if (format === 'step' || format === 'stp') {
                    result = occtimportjs.ReadStepFile(allBytes, null);
                } else if (format === 'iges' || format === 'igs') {
                    result = occtimportjs.ReadIgesFile(allBytes, null);
                } else {
                    throw new Error('Unsupported format: ' + format);
                }

                if (!result || !result.success) {
                    throw new Error(result ? result.errorMessage || 'Unknown error' : 'Failed to parse file');
                }

                loadingText.textContent = 'Building 3D model...';
                buildScene(result, fileName, format);
            } catch (e) {
                loadingOverlay.style.display = 'none';
                Bridge.onError('Failed to load file: ' + e.message);
                console.error('Load error:', e);
            }
        }, 50);
    }

    // Load CAD file from a URL (served by WebViewAssetLoader)
    function loadFileFromUrl(modelUrl, fileName, format) {
        var loadingOverlay = document.getElementById('loading-overlay');
        var loadingText = document.getElementById('loading-text');
        loadingOverlay.style.display = 'flex';
        loadingText.textContent = 'Downloading file...';

        Bridge.onLoadStart('Downloading ' + format.toUpperCase() + ' file...');

        fetch(modelUrl)
            .then(function (response) {
                if (!response.ok) throw new Error('Failed to fetch: ' + response.status);
                return response.arrayBuffer();
            })
            .then(function (buffer) {
                var bytes = new Uint8Array(buffer);
                loadingText.textContent = 'Tessellating geometry...';
                return new Promise(function (resolve) {
                    setTimeout(function () { resolve(bytes); }, 50);
                });
            })
            .then(function (bytes) {
                var result;
                if (format === 'step' || format === 'stp') {
                    result = occtimportjs.ReadStepFile(bytes, null);
                } else if (format === 'iges' || format === 'igs') {
                    result = occtimportjs.ReadIgesFile(bytes, null);
                } else {
                    throw new Error('Unsupported format: ' + format);
                }

                if (!result || !result.success) {
                    throw new Error(result ? result.errorMessage || 'Unknown error' : 'Failed to parse file');
                }

                loadingText.textContent = 'Building 3D model...';
                buildScene(result, fileName, format);
            })
            .catch(function (e) {
                console.error('Failed to load file:', e.message);
                loadingOverlay.style.display = 'none';
                Bridge.onError('Failed to load file: ' + e.message);
            });
    }

    // Build Three.js scene from parsed result
    function buildScene(result, fileName, format) {
        var loadingOverlay = document.getElementById('loading-overlay');

        try {
            // Remove existing model safely
            if (state.modelGroup) {
                try {
                    state.scene.remove(state.modelGroup);
                    disposeGroup(state.modelGroup);
                } catch (e) {
                    console.error('Error removing previous model:', e.message);
                }
            }

            state.modelGroup = new THREE.Group();

            var meshes = createMeshes(result);
            var geometries = [];
            var totalFaceCount = 0;

            meshes.forEach(function (mesh) {
                state.modelGroup.add(mesh);
                geometries.push(mesh.geometry);
                totalFaceCount += mesh.geometry.index ?
                    mesh.geometry.index.count / 3 :
                    mesh.geometry.getAttribute('position').count / 3;
            });

            state.scene.add(state.modelGroup);

            // Compute bounding box
            var bbox = computeBoundingBox(geometries);
            state.bbox = bbox; // store for dimension display
            var size = new THREE.Vector3();
            bbox.getSize(size);
            var center = new THREE.Vector3();
            bbox.getCenter(center);

            var volume = computeVolume(geometries);
            var surfaceArea = computeSurfaceArea(geometries);

            var modelInfo = {
                fileName: fileName || 'Unknown',
                format: format.toUpperCase(),
                height: size.z,
                width: size.x,
                length: size.y,
                volume: volume,
                surfaceArea: surfaceArea,
                faceCount: Math.round(totalFaceCount),
            };

            fitView();
            Bridge.onModelLoaded(modelInfo);
        } catch (e) {
            console.error('Error building scene:', e.message);
            Bridge.onError('Failed to build 3D model: ' + e.message);
        } finally {
            loadingOverlay.style.display = 'none';
        }
    }

    // Dispose group recursively
    function disposeGroup(group) {
        group.traverse(function (obj) {
            if (obj.geometry) obj.geometry.dispose();
            if (obj.material) {
                if (Array.isArray(obj.material)) {
                    obj.material.forEach(function (m) { m.dispose(); });
                } else {
                    obj.material.dispose();
                }
            }
        });
    }

    // Set up the Three.js scene
    function initScene() {
        var container = document.body;
        var w = window.innerWidth;
        var h = window.innerHeight;
        var dpr = window.devicePixelRatio;

        // WebGL renderer (off-screen render target, copied to 2D canvas for compositing)
        state.renderer = new THREE.WebGLRenderer({
            antialias: true,
            alpha: false,
            preserveDrawingBuffer: true,
        });
        state.renderer.setPixelRatio(dpr);
        state.renderer.setSize(w, h);
        state.renderer.shadowMap.enabled = true;
        state.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
        state.renderer.setClearColor(0xE8ECF0);

        // Keep WebGL canvas off-screen — it's only a render target
        var glCanvas = state.renderer.domElement;
        glCanvas.style.position = 'absolute';
        glCanvas.style.left = '-9999px';
        glCanvas.style.top = '0';
        container.appendChild(glCanvas);

        // 2D display canvas (composites reliably in WebView across all GPU modes)
        var displayCanvas = document.createElement('canvas');
        displayCanvas.id = 'display-canvas';
        displayCanvas.width = Math.floor(w * dpr);
        displayCanvas.height = Math.floor(h * dpr);
        displayCanvas.style.position = 'fixed';
        displayCanvas.style.top = '0';
        displayCanvas.style.left = '0';
        displayCanvas.style.width = '100%';
        displayCanvas.style.height = '100%';
        displayCanvas.style.zIndex = '10';
        container.appendChild(displayCanvas);

        var displayCtx = displayCanvas.getContext('2d');
        state.displayCanvas = displayCanvas;
        state.displayCtx = displayCtx;

        // Scene
        state.scene = new THREE.Scene();
        state.scene.background = new THREE.Color(0xE8ECF0);

        // Camera
        state.camera = new THREE.PerspectiveCamera(45, w / h, 0.1, 10000);
        state.camera.position.set(200, 150, 200);
        state.camera.lookAt(0, 0, 0);

        // OrbitControls — attach to display canvas for touch/mouse input
        state.controls = new THREE.OrbitControls(state.camera, displayCanvas);
        state.controls.enableDamping = true;
        state.controls.dampingFactor = 0.1;
        state.controls.rotateSpeed = 0.5;
        state.controls.zoomSpeed = 1.0;
        state.controls.panSpeed = 0.5;
        state.controls.screenSpacePanning = true;
        state.controls.maxDistance = 5000;
        state.controls.minDistance = 1;
        state.controls.minPolarAngle = 0;
        state.controls.maxPolarAngle = Math.PI;
        state.controls.touches = {
            ONE: THREE.TOUCH.ROTATE,
            TWO: THREE.TOUCH.DOLLY_PAN,
        };

        // Lights
        state.scene.add(new THREE.AmbientLight(0xffffff, 0.6));
        var dl1 = new THREE.DirectionalLight(0xffffff, 0.8);
        dl1.position.set(1, 1, 1);
        state.scene.add(dl1);
        var dl2 = new THREE.DirectionalLight(0xffffff, 0.4);
        dl2.position.set(-1, -0.5, -0.5);
        state.scene.add(dl2);
        var dl3 = new THREE.DirectionalLight(0xffffff, 0.3);
        dl3.position.set(0, -1, 0);
        state.scene.add(dl3);

        // Helpers
        state.scene.add(new THREE.GridHelper(200, 20, 0xcccccc, 0xe0e0e0));
        state.scene.add(new THREE.AxesHelper(50));

        // Raycaster for measurement
        state.raycaster = new THREE.Raycaster();
        state.raycaster.params.Points.threshold = 2;
        state.raycaster.params.Line = { threshold: 2 };
        state.mouse = new THREE.Vector2();

        // Measurement group
        state.measurementGroup = new THREE.Group();
        state.scene.add(state.measurementGroup);

        // Pointer events on display canvas
        displayCanvas.addEventListener('pointerdown', onPointerDown, false);

        // Window resize
        window.addEventListener('resize', onResize, false);

        // Start animation loop
        animate();
    }

    // Animation loop — renders WebGL then copies to 2D display canvas for compositing
    function animate() {
        requestAnimationFrame(animate);
        state.controls.update();
        state.renderer.render(state.scene, state.camera);
        if (state.displayCtx && state.displayCanvas) {
            state.displayCtx.clearRect(0, 0, state.displayCanvas.width, state.displayCanvas.height);
            state.displayCtx.drawImage(state.renderer.domElement, 0, 0);
        }
    }

    // Fit camera to model
    function fitView() {
        if (!state.modelGroup) return;

        var box = new THREE.Box3().setFromObject(state.modelGroup);
        var size = new THREE.Vector3();
        box.getSize(size);
        var center = new THREE.Vector3();
        box.getCenter(center);

        var maxDim = Math.max(size.x, size.y, size.z);
        if (maxDim < 0.0001) return;

        var fov = state.camera.fov * (Math.PI / 180);
        var cameraZ = Math.abs(maxDim / 2 / Math.tan(fov / 2));
        cameraZ *= 1.5;

        state.camera.position.set(
            center.x + cameraZ * 0.5,
            center.y + cameraZ * 0.5,
            center.z + cameraZ
        );
        state.camera.lookAt(center);

        state.controls.target.copy(center);
        state.controls.update();
    }

    // Set measurement mode
    function setMeasurementMode(enabled) {
        state.isMeasuring = enabled !== false;
        clearMeasurementState();
        if (!state.isMeasuring) {
            clearAllMeasurements();
        }
    }

    function clearAllMeasurements() {
        var toRemove = [];
        if (state.measurementGroup) {
            state.measurementGroup.traverse(function (obj) {
                if (obj.userData && obj.userData.measurementId) {
                    toRemove.push(obj);
                }
            });
            toRemove.forEach(function (obj) {
                state.measurementGroup.remove(obj);
                if (obj.geometry) obj.geometry.dispose();
                if (obj.material) obj.material.dispose();
            });
        }
    }

    function clearMeasurementState() {
        window._measurePoints = [];
        window._pendingNodes = [];
        pendingPoints = [];
        if (window._previewNodes) {
            window._previewNodes.forEach(function (n) {
                if (n.parent) n.parent.remove(n);
                if (n.geometry) n.geometry.dispose();
                if (n.material) n.material.dispose();
            });
        }
        window._previewNodes = [];
    }

    // Handle pointer down for measurement
    var pendingPoints = [];
    function onPointerDown(event) {
        if (!state.isMeasuring) return;

        event.stopPropagation();

        var rect = state.displayCanvas.getBoundingClientRect();
        state.mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
        state.mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

        state.raycaster.setFromCamera(state.mouse, state.camera);

        if (!state.modelGroup) return;

        var intersects = state.raycaster.intersectObjects(state.modelGroup.children, true);

        if (intersects.length > 0) {
            var point = intersects[0].point.clone();

            // Snap to nearest vertex if enabled
            if (state.snapToVertex !== false && intersects[0].face) {
                var face = intersects[0].face;
                var geo = intersects[0].object.geometry;
                var pos = geo.getAttribute('position');
                var verts = [];
                if (geo.index) {
                    [face.a, face.b, face.c].forEach(function (idx) {
                        verts.push(new THREE.Vector3(
                            pos.getX(idx), pos.getY(idx), pos.getZ(idx)
                        ));
                    });
                } else {
                    [face.a, face.b, face.c].forEach(function (idx) {
                        verts.push(new THREE.Vector3(
                            pos.getX(idx), pos.getY(idx), pos.getZ(idx)
                        ));
                    });
                }
                var closestDist = Infinity;
                verts.forEach(function (v) {
                    var d = point.distanceTo(v);
                    if (d < closestDist) { closestDist = d; point = v.clone(); }
                });
            }

            pendingPoints.push(point);

            // Show node immediately on first click
            var sphereGeo = new THREE.SphereGeometry(0.4, 12, 12);
            var nodeColor = pendingPoints.length === 2 ? 0x1565C0 : 0xFF5722;
            var sphereMat = new THREE.MeshBasicMaterial({ color: nodeColor });
            var sphere = new THREE.Mesh(sphereGeo, sphereMat);
            sphere.position.copy(point);
            state.measurementGroup.add(sphere);
            if (!window._previewNodes) window._previewNodes = [];
            window._previewNodes.push(sphere);

            if (pendingPoints.length === 2) {
                var p1 = pendingPoints[0];
                var p2 = pendingPoints[1];
                var distance = p1.distanceTo(p2);

                var lineGeo = new THREE.BufferGeometry().setFromPoints([p1, p2]);
                var lineMat = new THREE.LineBasicMaterial({
                    color: 0x1565C0,
                    linewidth: 2,
                });
                var line = new THREE.Line(lineGeo, lineMat);
                var mId = 'm_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5);
                line.userData = { measurementId: mId };
                state.measurementGroup.add(line);

                // Re-color first node to match
                window._previewNodes.forEach(function (n, i) {
                    n.material.color.set(0x1565C0);
                });

                Bridge.onMeasurementResult({
                    id: mId,
                    p1: [p1.x, p1.y, p1.z],
                    p2: [p2.x, p2.y, p2.z],
                    distance: distance,
                });

                pendingPoints = [];
                window._previewNodes = [];
            }
        }
    }

    // Set view mode
    function setViewMode(mode) {
        if (!state.modelGroup) return;

        state.modelGroup.traverse(function (obj) {
            if (obj.isMesh && obj.userData) {
                // Hide all edge overlays first
                if (obj.userData.edgeLine) obj.userData.edgeLine.visible = false;
                if (obj.userData.hiddenLineEdges) obj.userData.hiddenLineEdges.visible = false;

                switch (mode) {
                    case 'solid':
                        obj.material = obj.userData.solidMaterial;
                        break;
                    case 'solid_edges':
                        obj.material = obj.userData.solidMaterial;
                        if (obj.userData.edgeLine) obj.userData.edgeLine.visible = true;
                        break;
                    case 'wireframe':
                        obj.material = obj.userData.wireframeMaterial;
                        break;
                    case 'transparent':
                        obj.material = obj.userData.transparentMaterial;
                        break;
                    case 'hidden_line':
                        obj.material = obj.userData.hiddenLineMaterial;
                        if (obj.userData.hiddenLineEdges) obj.userData.hiddenLineEdges.visible = true;
                        break;
                }
                obj.userData.currentMode = mode;
            }
        });

        // Update scene background for hidden line mode
        if (mode === 'hidden_line') {
            state.scene.background = new THREE.Color(0xFFFFFF);
            state.renderer.setClearColor(0xFFFFFF);
        } else {
            state.scene.background = new THREE.Color(0xE8ECF0);
            state.renderer.setClearColor(0xE8ECF0);
        }
    }

    // Theme setter
    function setTheme(isDark) {
        var canvas = document.createElement('canvas');
        canvas.width = 2;
        canvas.height = 512;
        var ctx = canvas.getContext('2d');
        var gradient = ctx.createLinearGradient(0, 0, 0, 512);

        if (isDark) {
            gradient.addColorStop(0, '#121417');
            gradient.addColorStop(1, '#5C6571');
        } else {
            gradient.addColorStop(0, '#E8ECF0');
            gradient.addColorStop(1, '#B0BEC5');
        }

        ctx.fillStyle = gradient;
        ctx.fillRect(0, 0, 2, 512);

        var texture = new THREE.CanvasTexture(canvas);
        state.scene.background = texture;
        state.renderer.setClearColor(isDark ? 0x121417 : 0xE8ECF0);
    }

    // Remove measurement by ID
    function removeMeasurement(id) {
        var toRemove = [];
        state.measurementGroup.traverse(function (obj) {
            if (obj.userData && obj.userData.measurementId === id) {
                toRemove.push(obj);
            }
        });
        toRemove.forEach(function (obj) {
            state.measurementGroup.remove(obj);
            if (obj.geometry) obj.geometry.dispose();
            if (obj.material) obj.material.dispose();
        });
    }

    // Show/hide 3D dimension indicators at bounding box extents
    function setShowDimensions(visible) {
        if (visible) {
            showDimensionIndicators();
        } else {
            hideDimensionIndicators();
        }
        state.showingDimensions = visible;
    }

    function showDimensionIndicators() {
        if (!state.bbox) return;
        hideDimensionIndicators();

        state.dimensionGroup = new THREE.Group();

        var min = state.bbox.min;
        var max = state.bbox.max;
        var colors = { X: 0xff4444, Y: 0x44ff44, Z: 0x4488ff };
        var labels = { X: 'L', Y: 'W', Z: 'H' };

        var axes = [
            { key: 'X', v1: new THREE.Vector3(min.x, min.y, min.z), v2: new THREE.Vector3(max.x, min.y, min.z) },
            { key: 'Y', v1: new THREE.Vector3(min.x, min.y, min.z), v2: new THREE.Vector3(min.x, max.y, min.z) },
            { key: 'Z', v1: new THREE.Vector3(min.x, min.y, min.z), v2: new THREE.Vector3(min.x, min.y, max.z) },
        ];

        axes.forEach(function (axis) {
            var mid = new THREE.Vector3().addVectors(axis.v1, axis.v2).multiplyScalar(0.5);
            var dir = new THREE.Vector3().subVectors(axis.v2, axis.v1).normalize();
            var len = axis.v1.distanceTo(axis.v2);

            // Endpoint spheres
            [axis.v1, axis.v2].forEach(function (pt) {
                var sphere = new THREE.Mesh(
                    new THREE.SphereGeometry(len * 0.012, 8, 8),
                    new THREE.MeshBasicMaterial({ color: colors[axis.key] })
                );
                sphere.position.copy(pt);
                state.dimensionGroup.add(sphere);
            });

            // Dashed line
            var lineGeo = new THREE.BufferGeometry();
            var points = [];
            var segments = 20;
            for (var i = 0; i <= segments; i++) {
                var t = i / segments;
                var pt = new THREE.Vector3().copy(axis.v1).addScaledVector(dir, t * len);
                // Offset slightly outward from the bbox
                var offset = new THREE.Vector3().copy(dir).multiplyScalar(len * 0.06);
                pt.add(offset);
                points.push(pt);
            }
            lineGeo.setFromPoints(points);
            var line = new THREE.Line(
                lineGeo,
                new THREE.LineDashedMaterial({ color: colors[axis.key], dashSize: len * 0.06, gapSize: len * 0.03 })
            );
            line.computeLineDistances();
            state.dimensionGroup.add(line);

            // Label as small sphere at midpoint
            var labelSphere = new THREE.Mesh(
                new THREE.SphereGeometry(len * 0.018, 8, 8),
                new THREE.MeshBasicMaterial({ color: colors[axis.key] })
            );
            labelSphere.position.copy(mid).add(
                new THREE.Vector3().copy(dir).multiplyScalar(len * 0.12)
            );
            state.dimensionGroup.add(labelSphere);
        });

        state.scene.add(state.dimensionGroup);
    }

    function hideDimensionIndicators() {
        if (state.dimensionGroup) {
            state.dimensionGroup.traverse(function (obj) {
                if (obj.geometry) obj.geometry.dispose();
                if (obj.material) obj.material.dispose();
            });
            state.scene.remove(state.dimensionGroup);
            state.dimensionGroup = null;
        }
    }

    // Capture screenshot as data URL
    function captureScreenshot() {
        state.renderer.render(state.scene, state.camera);
        if (state.displayCtx && state.displayCanvas) {
            state.displayCtx.clearRect(0, 0, state.displayCanvas.width, state.displayCanvas.height);
            state.displayCtx.drawImage(state.renderer.domElement, 0, 0);
        }
        return state.displayCanvas ? state.displayCanvas.toDataURL('image/png') : null;
    }

    // Handle window resize
    function onResize() {
        var w = window.innerWidth;
        var h = window.innerHeight;
        var dpr = window.devicePixelRatio;
        state.camera.aspect = w / h;
        state.camera.updateProjectionMatrix();
        state.renderer.setSize(w, h);
        if (state.displayCanvas) {
            state.displayCanvas.width = Math.floor(w * dpr);
            state.displayCanvas.height = Math.floor(h * dpr);
            state.displayCanvas.style.width = '100%';
            state.displayCanvas.style.height = '100%';
        }
    }

    // Initialize scene immediately (Three.js is loaded synchronously)
    function initWhenReady() {
        if (typeof THREE !== 'undefined') {
            initScene();
        } else {
            setTimeout(initWhenReady, 50);
        }
    }
    initWhenReady();

    // Expose viewer API
    window.Viewer = {
        init: function () {
            // Bridge.onReady() is deferred until WASM is initialized
            // (see waitForDeps callback below)
        },
        loadFileFromPath: loadFileFromPath,
        loadFileFromUrl: loadFileFromUrl,
        setMeasurementMode: setMeasurementMode,
        setSnapToVertex: function (enabled) {
            state.snapToVertex = enabled !== false;
        },
        setViewMode: setViewMode,
        setTheme: setTheme,
        fitView: fitView,
        removeMeasurement: removeMeasurement,
        setShowDimensions: setShowDimensions,
        captureScreenshot: captureScreenshot,
    };

    // Initialize WASM, then signal bridge ready (occtimportjs must be ready before loadFileFromUrl)
    waitForDeps(function () {
        if (Bridge.isReady()) {
            Bridge.onReady();
        } else {
            // Rare: WASM init finished before AndroidBridge is injected — poll
            var attempts = 0;
            var poll = setInterval(function () {
                if (Bridge.isReady() || ++attempts > 50) {
                    clearInterval(poll);
                    if (Bridge.isReady()) Bridge.onReady();
                }
            }, 100);
        }
    });
})();
