package com.safevault.app.di

import com.safevault.app.data.crypto.CryptoManager
import com.safevault.app.data.crypto.KeystoreCryptoManager
import com.safevault.app.data.repository.NoteRepositoryImpl
import com.safevault.app.data.repository.SettingsRepositoryImpl
import com.safevault.app.domain.repository.NoteRepository
import com.safevault.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCryptoManager(impl: KeystoreCryptoManager): CryptoManager

    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
