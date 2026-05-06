package com.stepviewer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FileHistoryEntity::class,
        CustomMaterialEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileHistoryDao(): FileHistoryDao
    abstract fun customMaterialDao(): CustomMaterialDao
}
