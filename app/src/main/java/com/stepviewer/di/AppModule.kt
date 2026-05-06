package com.stepviewer.di

import android.content.Context
import androidx.room.Room
import com.stepviewer.data.local.AppDatabase
import com.stepviewer.data.local.CustomMaterialDao
import com.stepviewer.data.local.FileHistoryDao
import com.stepviewer.data.preferences.AppPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "step_viewer.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideFileHistoryDao(db: AppDatabase): FileHistoryDao = db.fileHistoryDao()

    @Provides
    @Singleton
    fun provideCustomMaterialDao(db: AppDatabase): CustomMaterialDao = db.customMaterialDao()

    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferences =
        AppPreferences(context)
}
