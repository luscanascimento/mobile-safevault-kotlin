package com.safevault.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.safevault.app.data.db.NoteDao
import com.safevault.app.data.db.VaultDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Property-delegate factory for a single app-wide preferences DataStore.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "safevault_settings",
)

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): VaultDatabase = Room.databaseBuilder(
        context,
        VaultDatabase::class.java,
        VaultDatabase.NAME,
    )
        // Column data is already encrypted at the application layer; schema is
        // stable at v1. Add explicit Migrations here as the schema evolves.
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()

    @Provides
    fun provideNoteDao(database: VaultDatabase): NoteDao = database.noteDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore
}
