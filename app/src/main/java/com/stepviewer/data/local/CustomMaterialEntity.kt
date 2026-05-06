package com.stepviewer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_materials")
data class CustomMaterialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val density: Double,
    val createdAt: Long = System.currentTimeMillis(),
)
