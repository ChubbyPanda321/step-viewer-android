/**
 * bridge.js - Communication layer between Kotlin (AndroidBridge) and viewer.js
 *
 * Provides:
 * - onAndroidReady() - called when Android bridge is available
 * - sendToAndroid.* - functions to send data to Kotlin
 * - Functions exposed to Android via window.*
 */

(function () {
    'use strict';

    // Check if Android bridge is available
    function isAndroidBridgeReady() {
        return typeof AndroidBridge !== 'undefined' && AndroidBridge !== null;
    }

    // Send data to Android bridge
    function sendToAndroid(method, data) {
        if (isAndroidBridgeReady()) {
            try {
                if (typeof data === 'string') {
                    AndroidBridge[method](data);
                } else {
                    AndroidBridge[method](JSON.stringify(data));
                }
            } catch (e) {
                console.error('Bridge error:', method, e);
            }
        }
    }

    // ===== Exported to global scope for viewer.js =====
    window.Bridge = {
        // Notify Kotlin that the bridge is ready
        onReady: function () {
            sendToAndroid('onBridgeReady', '');
        },

        // Called when loading starts
        onLoadStart: function (message) {
            sendToAndroid('onLoadStart', message);
        },

        // Called when a model is loaded and analyzed
        onModelLoaded: function (info) {
            sendToAndroid('onModelLoaded', info);
        },

        // Called with measurement result
        onMeasurementResult: function (result) {
            sendToAndroid('onMeasurementResult', result);
        },

        // Called on error
        onError: function (message) {
            sendToAndroid('onError', message);
        },

        // Called for async command results
        onCommandResult: function (data) {
            sendToAndroid('onCommandResult', data);
        },

        isReady: isAndroidBridgeReady,
    };

    // ===== Functions exposed for Kotlin to call via evaluateJavascript =====

    /**
     * Called by Android when bridge is ready.
     * Triggers Three.js scene initialization.
     */
    window.onAndroidReady = function () {
        console.log('Android bridge ready');
        if (window.Viewer && typeof window.Viewer.init === 'function') {
            window.Viewer.init();
        }
    };

    /**
     * Load a CAD file from base64-encoded data.
     * Called by Kotlin via evaluateJavascript.
     */
    window.loadFileFromBase64 = function (base64Data, fileName, format) {
        if (window.Viewer && typeof window.Viewer.loadFile === 'function') {
            window.Viewer.loadFile(base64Data, fileName, format);
        } else {
            Bridge.onError('Viewer not initialized');
        }
    };

    /**
     * Load a CAD file from a local file path.
     * Called by Kotlin via evaluateJavascript — avoids OOM from base64 encoding.
     */
    window.loadFileFromPath = function (filePath, fileName, format) {
        if (window.Viewer && typeof window.Viewer.loadFileFromPath === 'function') {
            window.Viewer.loadFileFromPath(filePath, fileName, format);
        } else {
            Bridge.onError('Viewer not initialized');
        }
    };

    /**
     * Set measurement mode on/off.
     */
    window.setMeasurementMode = function (enabled) {
        if (window.Viewer && typeof window.Viewer.setMeasurementMode === 'function') {
            window.Viewer.setMeasurementMode(enabled);
        }
    };

    /**
     * Set the view rendering mode.
     */
    window.setViewMode = function (mode) {
        if (window.Viewer && typeof window.Viewer.setViewMode === 'function') {
            window.Viewer.setViewMode(mode);
        }
    };

    /**
     * Reset camera to fit all geometry.
     */
    window.fitView = function () {
        if (window.Viewer && typeof window.Viewer.fitView === 'function') {
            window.Viewer.fitView();
        }
    };

    /**
     * Remove a measurement by its ID.
     */
    window.removeMeasurement = function (id) {
        if (window.Viewer && typeof window.Viewer.removeMeasurement === 'function') {
            window.Viewer.removeMeasurement(id);
        }
    };

    /**
     * Set the theme (light/dark) for the 3D viewport background.
     */
    window.setViewerTheme = function (isDark) {
        if (window.Viewer && typeof window.Viewer.setTheme === 'function') {
            window.Viewer.setTheme(isDark);
        }
    };

    /**
     * Export current view as a data URL screenshot.
     */
    window.captureScreenshot = function () {
        if (window.Viewer && typeof window.Viewer.captureScreenshot === 'function') {
            return window.Viewer.captureScreenshot();
        }
        return null;
    };

    console.log('bridge.js loaded');
})();
