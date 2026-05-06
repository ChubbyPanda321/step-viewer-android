package com.stepviewer.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stepviewer.data.local.CustomMaterialDao
import com.stepviewer.data.local.CustomMaterialEntity
import com.stepviewer.data.model.Material
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaterialRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customMaterialDao: CustomMaterialDao,
) {
    companion object {
        private const val PRESETS_FILE = "materials.json"
    }

    /**
     * Load preset materials from assets/materials.json.
     */
    private suspend fun loadPresets(): List<Material> = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open(PRESETS_FILE).bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, Double>>() {}.type
            val map: Map<String, Double> = Gson().fromJson(json, type)
            map.entries.mapIndexed { index, (name, density) ->
                Material(
                    id = (-index - 1).toLong(),
                    name = name,
                    density = density,
                    isCustom = false,
                )
            }
        } catch (e: IOException) {
            // Fallback material list
            listOf(
                Material(id = -1, name = "Steel", density = 0.00785, isCustom = false),
            )
        }
    }

    val customMaterials: Flow<List<CustomMaterialEntity>> = customMaterialDao.getAll()

    /**
     * Combined flow: presets + custom materials as a single list.
     */
    fun allMaterials(): Flow<List<Material>> = combine(
        flowOf(emptyList<Material>()), // placeholder, overwritten
        customMaterials,
    ) { _, customs ->
        val presets = withContext(Dispatchers.IO) { loadPresets() }
        val customMats = customs.map { it.toMaterial() }
        presets + customMats
    }

    suspend fun addCustomMaterial(name: String, density: Double): Material {
        val id = customMaterialDao.insert(
            CustomMaterialEntity(name = name, density = density)
        )
        return Material(id = id, name = name, density = density, isCustom = true)
    }

    suspend fun updateCustomMaterial(id: Long, name: String, density: Double) {
        customMaterialDao.update(
            CustomMaterialEntity(id = id, name = name, density = density)
        )
    }

    suspend fun deleteCustomMaterial(id: Long) {
        customMaterialDao.deleteById(id)
    }

    suspend fun findMaterial(name: String): Material? {
        val all = allMaterials().first()
        return all.find { it.name == name }
    }

    private fun CustomMaterialEntity.toMaterial() = Material(
        id = id,
        name = name,
        density = density,
        isCustom = true,
    )
}
