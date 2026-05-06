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
            callback();
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
        lightBackground: null,
        darkBackground: null,
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

                // Tetrahedron from origin: (a x b) · c / 6
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

                // Two edges
                const ux = bx - ax, uy = by - ay, uz = bz - az;
                const vx = cx - ax, vy = cy - ay, vz = cz - az;

                // Cross product magnitude / 2
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

            // Try to get face color
            let color = 0x607D8B; // default: blue-grey
            if (attributes.color && attributes.color.array && attributes.color.array.length > 0) {
                const ca = attributes.color.array;
                const r = Math.round(ca[0] * 255);
                const g = Math.round(ca[1] * 255);
                const b = Math.round(ca[2] * 255);
                color = (r << 16) | (g << 8) | b;
            }

            geometry.computeVertexNormals();

            const material = new THREE.MeshPhongMaterial({
                color: color,
                specular: 0x111111,
                shininess: 30,
                side: THREE.DoubleSide,
                flatShading: false,
            });

            // Also create wireframe material
            const matWireframe = new THREE.MeshBasicMaterial({
                color: 0x000000,
                wireframe: true,
                transparent: true,
                opacity: 0.3,
            });

            // Transparent material
            const matTransparent = new THREE.MeshPhongMaterial({
                color: color,
                specular: 0x111111,
                shininess: 30,
                side: THREE.DoubleSide,
                transparent: true,
                opacity: 0.4,
            });

            const mesh = new THREE.Mesh(geometry, material);
            mesh.userData = {
                wireframeMaterial: matWireframe,
                solidMaterial: material,
                transparentMaterial: matTransparent,
                currentMode: 'solid',
            };

            meshes.push(mesh);
        });

        return meshes;
    }

    // Load CAD file from base64 data
    function loadFile(base64Data, fileName, format) {
        var loadingOverlay = document.getElementById('loading-overlay');
        var loadingText = document.getElementById('loading-text');
        loadingOverlay.style.display = 'flex';
        loadingText.textContent = 'Parsing ' + format.toUpperCase() + ' file...';

        Bridge.onLoadStart('Parsing ' + format.toUpperCase() + ' file...');

        setTimeout(function () {
            try {
                // Decode base64 to ArrayBuffer
                var binaryStr = atob(base64Data);
                var bytes = new Uint8Array(binaryStr.length);
                for (var i = 0; i < binaryStr.length; i++) {
                    bytes[i] = binaryStr.charCodeAt(i);
                }
                var buffer = bytes.buffer;

                loadingText.textContent = 'Tessellating geometry...';

                // Parse with occt-import-js
                var fileBuffer = new occtimportjs.occtFileBuffer(buffer);
                var result;

                if (format === 'step' || format === 'stp') {
                    result = occtimportjs.ReadStepFile(fileBuffer);
                } else if (format === 'iges' || format === 'igs') {
                    result = occtimportjs.ReadIgesFile(fileBuffer);
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

    // Load CAD file from a local file path via bridge chunked reading
    // Avoids file:// XHR (blocked in modern WebViews) and base64 OOM
    function loadFileFromPath(filePath, fileName, format) {
        var loadingOverlay = document.getElementById('loading-overlay');
        var loadingText = document.getElementById('loading-text');
        loadingOverlay.style.display = 'flex';
        loadingText.textContent = 'Reading file...';

        Bridge.onLoadStart('Reading file...');

        setTimeout(function () {
            try {
                // Open file and get its size via bridge
                var handle = AndroidBridge.fileOpen(filePath);
                if (!handle) throw new Error('Cannot open file');

                var sizeStr = AndroidBridge.fileGetSize(handle);
                var fileSize = parseInt(sizeStr, 10);
                if (fileSize <= 0) throw new Error('File is empty or unreadable');

                loadingText.textContent = 'Loading ' + (fileSize / 1024 / 1024).toFixed(1) + ' MB...';

                // Read all chunks and assemble into ArrayBuffer
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

                    // Update progress
                    var pct = Math.round((totalRead / fileSize) * 100);
                    loadingText.textContent = 'Loading... ' + pct + '%';
                }

                AndroidBridge.fileClose(handle);

                if (totalRead === 0) throw new Error('No data read from file');

                // Assemble all chunks into a single Uint8Array
                var allBytes = new Uint8Array(totalRead);
                var pos = 0;
                for (var c = 0; c < chunks.length; c++) {
                    var chunk = chunks[c];
                    for (var i = 0; i < chunk.length; i++) {
                        allBytes[pos++] = chunk.charCodeAt(i);
                    }
                }

                loadingText.textContent = 'Tessellating geometry...';

                var fileBuffer = new occtimportjs.occtFileBuffer(allBytes.buffer);
                var result;

                if (format === 'step' || format === 'stp') {
                    result = occtimportjs.ReadStepFile(fileBuffer);
                } else if (format === 'iges' || format === 'igs') {
                    result = occtimportjs.ReadIgesFile(fileBuffer);
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

    // Build Three.js scene from parsed result
    function buildScene(result, fileName, format) {
        var loadingOverlay = document.getElementById('loading-overlay');

        // Remove existing model
        if (state.modelGroup) {
            state.scene.remove(state.modelGroup);
            disposeGroup(state.modelGroup);
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
        var size = new THREE.Vector3();
        bbox.getSize(size);

        var volume = computeVolume(geometries);
        var surfaceArea = computeSurfaceArea(geometries);

        // Build model info
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

        // Fit camera
        fitView();

        loadingOverlay.style.display = 'none';

        // Send to Android
        Bridge.onModelLoaded(modelInfo);
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

        // Renderer
        state.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false });
        state.renderer.setPixelRatio(window.devicePixelRatio);
        state.renderer.setSize(window.innerWidth, window.innerHeight);
        state.renderer.shadowMap.enabled = true;
        state.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
        state.renderer.setClearColor(0xE8ECF0);
        container.appendChild(state.renderer.domElement);

        // Scene
        state.scene = new THREE.Scene();

        // Set background to light gradient
        setBackgroundGradient(false);

        // Camera
        state.camera = new THREE.PerspectiveCamera(
            45,
            window.innerWidth / window.innerHeight,
            0.1,
            10000
        );
        state.camera.position.set(200, 150, 200);
        state.camera.lookAt(0, 0, 0);

        // OrbitControls
        state.controls = new THREE.OrbitControls(state.camera, state.renderer.domElement);
        state.controls.enableDamping = true;
        state.controls.dampingFactor = 0.1;
        state.controls.rotateSpeed = 0.5;
        state.controls.zoomSpeed = 1.0;
        state.controls.panSpeed = 0.5;
        state.controls.screenSpacePanning = true;
        state.controls.maxDistance = 5000;
        state.controls.minDistance = 1;
        // Disable default on mobile to avoid conflicts
        state.controls.touches = {
            ONE: THREE.TOUCH.ROTATE,
            TWO: THREE.TOUCH.DOLLY_PAN,
        };

        // Lights
        var ambientLight = new THREE.AmbientLight(0xffffff, 0.6);
        state.scene.add(ambientLight);

        var dirLight1 = new THREE.DirectionalLight(0xffffff, 0.8);
        dirLight1.position.set(1, 1, 1);
        state.scene.add(dirLight1);

        var dirLight2 = new THREE.DirectionalLight(0xffffff, 0.4);
        dirLight2.position.set(-1, -0.5, -0.5);
        state.scene.add(dirLight2);

        var dirLight3 = new THREE.DirectionalLight(0xffffff, 0.3);
        dirLight3.position.set(0, -1, 0);
        state.scene.add(dirLight3);

        // Grid helper
        var gridHelper = new THREE.GridHelper(200, 20, 0xcccccc, 0xe0e0e0);
        state.scene.add(gridHelper);

        // Axes helper
        var axesHelper = new THREE.AxesHelper(50);
        state.scene.add(axesHelper);

        // Raycaster for measurement
        state.raycaster = new THREE.Raycaster();
        state.raycaster.params.Points.threshold = 2;
        state.raycaster.params.Line = { threshold: 2 };
        state.mouse = new THREE.Vector2();

        // Measurement group
        state.measurementGroup = new THREE.Group();
        state.scene.add(state.measurementGroup);

        // Click/tap handler for measurement
        state.renderer.domElement.addEventListener('pointerdown', onPointerDown, false);

        // Window resize
        window.addEventListener('resize', onResize, false);

        // Start animation loop
        animate();
    }

    // Set background gradient (light or dark theme)
    function setBackgroundGradient(isDark) {
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

        // Update renderer clear color
        state.renderer.setClearColor(isDark ? 0x121417 : 0xE8ECF0);
    }

    // Animation loop
    function animate() {
        requestAnimationFrame(animate);
        state.controls.update();
        state.renderer.render(state.scene, state.camera);
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
        var fov = state.camera.fov * (Math.PI / 180);
        var cameraZ = Math.abs(maxDim / 2 / Math.tan(fov / 2));
        cameraZ *= 1.5; // padding

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
        if (!state.isMeasuring) {
            // Clear pending measurement state
            window._measurePoints = [];
            // Remove all measurement lines
            while (state.measurementGroup.children.length > 0) {
                state.measurementGroup.remove(state.measurementGroup.children[0]);
            }
        }
        window._measurePoints = [];
    }

    // Handle pointer down for measurement
    var pendingPoints = [];
    function onPointerDown(event) {
        if (!state.isMeasuring) return;

        // Prevent orbit controls interference
        event.stopPropagation();

        var rect = state.renderer.domElement.getBoundingClientRect();
        state.mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
        state.mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

        state.raycaster.setFromCamera(state.mouse, state.camera);

        if (!state.modelGroup) return;

        var intersects = state.raycaster.intersectObjects(state.modelGroup.children, true);

        if (intersects.length > 0) {
            var point = intersects[0].point.clone();
            pendingPoints.push(point);

            if (pendingPoints.length === 2) {
                var p1 = pendingPoints[0];
                var p2 = pendingPoints[1];
                var distance = p1.distanceTo(p2);

                // Draw measurement line
                var lineGeo = new THREE.BufferGeometry().setFromPoints([p1, p2]);
                var lineMat = new THREE.LineBasicMaterial({
                    color: 0x1565C0,
                    linewidth: 2,
                });
                var line = new THREE.Line(lineGeo, lineMat);
                line.userData = {
                    measurementId: 'm_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5),
                };
                state.measurementGroup.add(line);

                // Draw endpoint spheres
                var sphereGeo = new THREE.SphereGeometry(0.5, 8, 8);
                var sphereMat = new THREE.MeshBasicMaterial({ color: 0x1565C0 });
                var sphere1 = new THREE.Mesh(sphereGeo, sphereMat);
                sphere1.position.copy(p1);
                var sphere2 = new THREE.Mesh(sphereGeo, sphereMat);
                sphere2.position.copy(p2);
                state.measurementGroup.add(sphere1);
                state.measurementGroup.add(sphere2);

                // Send measurement to Android
                Bridge.onMeasurementResult({
                    id: line.userData.measurementId,
                    p1: [p1.x, p1.y, p1.z],
                    p2: [p2.x, p2.y, p2.z],
                    distance: distance,
                });

                pendingPoints = [];
            }
        }
    }

    // Set view mode
    function setViewMode(mode) {
        if (!state.modelGroup) return;

        state.modelGroup.traverse(function (obj) {
            if (obj.isMesh && obj.userData) {
                switch (mode) {
                    case 'solid':
                        obj.material = obj.userData.solidMaterial;
                        break;
                    case 'wireframe':
                        obj.material = obj.userData.wireframeMaterial;
                        break;
                    case 'transparent':
                        obj.material = obj.userData.transparentMaterial;
                        break;
                }
                obj.userData.currentMode = mode;
            }
        });
    }

    // Theme setter
    function setTheme(isDark) {
        setBackgroundGradient(isDark);
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

    // Capture screenshot as data URL
    function captureScreenshot() {
        state.renderer.render(state.scene, state.camera);
        return state.renderer.domElement.toDataURL('image/png');
    }

    // Handle window resize
    function onResize() {
        state.camera.aspect = window.innerWidth / window.innerHeight;
        state.camera.updateProjectionMatrix();
        state.renderer.setSize(window.innerWidth, window.innerHeight);
    }

    // ===== Initialize when dependencies are ready =====
    waitForDeps(function () {
        initScene();

        // Expose viewer API globally
        window.Viewer = {
            init: function () {
                Bridge.onReady();
            },
            loadFile: loadFile,
            loadFileFromPath: loadFileFromPath,
            setMeasurementMode: setMeasurementMode,
            setViewMode: setViewMode,
            setTheme: setTheme,
            fitView: fitView,
            removeMeasurement: removeMeasurement,
            captureScreenshot: captureScreenshot,
        };

        // Notify bridge that viewer is ready
        if (Bridge.isReady()) {
            Bridge.onReady();
        }
    });

    console.log('viewer.js loaded');
})();
