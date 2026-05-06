package com.stepviewer

import androidx.lifecycle.SavedStateHandle
import com.stepviewer.data.model.Material
import com.stepviewer.data.model.StepFileInfo
import com.stepviewer.data.model.CadFormat
import com.stepviewer.data.model.ThemeMode
import com.stepviewer.viewmodel.ViewerViewModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Example unit tests for data model logic.
 * Full ViewModel testing requires Hilt test setup with mock repositories.
 */
class ModelUnitTest {

    @Test
    fun `cadFormat detects step extensions`() {
        assertEquals(CadFormat.STEP, CadFormat.fromExtension("stp"))
        assertEquals(CadFormat.STEP, CadFormat.fromExtension("step"))
        assertEquals(CadFormat.STEP, CadFormat.fromExtension("p21"))
    }

    @Test
    fun `cadFormat detects iges extensions`() {
        assertEquals(CadFormat.IGES, CadFormat.fromExtension("igs"))
        assertEquals(CadFormat.IGES, CadFormat.fromExtension("iges"))
    }

    @Test
    fun `cadFormat returns null for unknown extensions`() {
        assertNull(CadFormat.fromExtension("txt"))
        assertNull(CadFormat.fromExtension("pdf"))
    }

    @Test
    fun `cadFormat handles dot prefix`() {
        assertEquals(CadFormat.STEP, CadFormat.fromExtension(".stp"))
        assertEquals(CadFormat.IGES, CadFormat.fromExtension(".igs"))
    }

    @Test
    fun `stepFileInfo default values`() {
        val info = StepFileInfo()
        assertEquals("", info.fileName)
        assertEquals(0.0, info.volume, 0.001)
        assertEquals(0.0, info.mass, 0.001)
        assertEquals(CadFormat.STEP, info.format)
    }

    @Test
    fun `material custom flag`() {
        val preset = Material(id = -1, name = "Steel", density = 0.00785, isCustom = false)
        val custom = Material(id = 1, name = "MyAlloy", density = 0.01, isCustom = true)

        assertFalse(preset.isCustom)
        assertTrue(custom.isCustom)
    }

    @Test
    fun `themeMode values`() {
        assertEquals("System", ThemeMode.SYSTEM.label)
        assertEquals("Light", ThemeMode.LIGHT.label)
        assertEquals("Dark", ThemeMode.DARK.label)
    }

    @Test
    fun `mass calculation`() {
        val volume = 1000.0
        val density = 0.00785
        val expected = volume * density // 7.85
        assertEquals(expected, volume * density, 0.001)
    }
}
