package com.safevault.app.data.repository

import com.safevault.app.data.crypto.CryptoException
import com.safevault.app.data.crypto.CryptoFailure
import com.safevault.app.data.crypto.CryptoManager
import com.safevault.app.data.crypto.EncryptedPayload
import com.safevault.app.data.db.NoteDao
import com.safevault.app.data.db.NoteEntity
import com.safevault.app.di.IoDispatcher
import com.safevault.app.domain.model.Note
import com.safevault.app.domain.repository.NoteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Repository that transparently encrypts note content on write and decrypts it
 * on read, bridging the encrypted [NoteEntity] store and the plaintext [Note]
 * domain model.
 */
class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
    private val crypto: CryptoManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NoteRepository {

    override fun observeNotes(): Flow<List<Note>> =
        dao.observeAll()
            .map { list -> list.mapNotNull { it.toDomainOrNull() } }
            .flowOn(ioDispatcher)

    override suspend fun getNote(id: Long): Note? = withContext(ioDispatcher) {
        dao.getById(id)?.toDomainOrNull()
    }

    override suspend fun addNote(note: Note): Long = withContext(ioDispatcher) {
        dao.insert(note.toEntity())
    }

    override suspend fun updateNote(note: Note): Unit = withContext(ioDispatcher) {
        dao.update(note.toEntity())
    }

    override suspend fun deleteNote(id: Long): Unit = withContext(ioDispatcher) {
        dao.deleteById(id)
    }

    // --- Mapping helpers ---------------------------------------------------

    private fun Note.toEntity(): NoteEntity {
        val payload = crypto.encrypt(content)
        return NoteEntity(
            id = id,
            title = title,
            encryptedContent = payload.ciphertext,
            iv = payload.iv,
            tags = tags,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    /**
     * Decrypts an entity into a domain [Note]. Returns null (rather than
     * throwing) when a single record is unreadable, so one corrupted row cannot
     * break the whole list. Session-wide failures (expired authentication, key
     * invalidation) are rethrown: swallowing those would silently show an empty
     * vault instead of sending the user back to the lock screen.
     */
    private fun NoteEntity.toDomainOrNull(): Note? = try {
        val content = crypto.decrypt(EncryptedPayload(iv = iv, ciphertext = encryptedContent))
        Note(
            id = id,
            title = title,
            content = content,
            tags = tags,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    } catch (e: CryptoException) {
        if (e.failure != CryptoFailure.UNREADABLE) throw e
        null
    }
}
