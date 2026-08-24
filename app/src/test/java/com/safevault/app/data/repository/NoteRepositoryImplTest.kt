package com.safevault.app.data.repository

import app.cash.turbine.test
import com.safevault.app.data.crypto.CryptoManager
import com.safevault.app.data.crypto.EncryptedPayload
import com.safevault.app.data.db.NoteDao
import com.safevault.app.data.db.NoteEntity
import com.safevault.app.domain.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the repository transparently encrypts on write and decrypts on read
 * using an in-memory fake DAO and a reversible fake crypto (XOR-with-IV) that
 * mimics AES-GCM's IV+ciphertext shape without needing the Android Keystore.
 */
class NoteRepositoryImplTest {

    // Reversible fake: not secure, but round-trips like the real CryptoManager.
    private val fakeCrypto = object : CryptoManager {
        override fun encrypt(plaintext: String): EncryptedPayload {
            val iv = byteArrayOf(0x2A)
            val bytes = plaintext.toByteArray(Charsets.UTF_8)
                .map { (it.toInt() xor 0x2A).toByte() }
                .toByteArray()
            return EncryptedPayload(iv = iv, ciphertext = bytes)
        }

        override fun decrypt(payload: EncryptedPayload): String {
            val bytes = payload.ciphertext
                .map { (it.toInt() xor 0x2A).toByte() }
                .toByteArray()
            return String(bytes, Charsets.UTF_8)
        }
    }

    private val store = MutableStateFlow<List<NoteEntity>>(emptyList())

    private val fakeDao = object : NoteDao {
        override fun observeAll(): Flow<List<NoteEntity>> = store

        override suspend fun getById(id: Long): NoteEntity? = store.value.firstOrNull { it.id == id }

        override suspend fun insert(note: NoteEntity): Long {
            val id = (store.value.maxOfOrNull { it.id } ?: 0L) + 1L
            store.value = store.value + note.copy(id = id)
            return id
        }

        override suspend fun update(note: NoteEntity) {
            store.value = store.value.map { if (it.id == note.id) note else it }
        }

        override suspend fun deleteById(id: Long) {
            store.value = store.value.filterNot { it.id == id }
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private val repository = NoteRepositoryImpl(fakeDao, fakeCrypto, dispatcher)

    @Test
    fun `content is stored encrypted and read back decrypted`() = runTest(dispatcher) {
        val id = repository.addNote(
            Note(title = "Bank", content = "PIN 1234", tags = listOf("finance"), createdAt = 1, updatedAt = 1),
        )

        // Raw store must not contain the plaintext.
        val raw = store.value.first()
        assertEquals(false, String(raw.encryptedContent, Charsets.UTF_8).contains("PIN 1234"))

        val loaded = repository.getNote(id)
        assertEquals("PIN 1234", loaded?.content)
    }

    @Test
    fun `observeNotes emits decrypted notes`() = runTest(dispatcher) {
        repository.addNote(Note(title = "A", content = "alpha", createdAt = 1, updatedAt = 1))

        repository.observeNotes().test {
            val emission = awaitItem()
            assertEquals(1, emission.size)
            assertEquals("alpha", emission.first().content)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
