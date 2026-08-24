package com.safevault.app.domain.repository

import com.safevault.app.domain.model.Note
import kotlinx.coroutines.flow.Flow

/**
 * Repository abstraction over the encrypted note store.
 *
 * Implementations are responsible for encrypting content before persistence
 * and decrypting it on read; callers always work with plaintext [Note]s.
 */
interface NoteRepository {

    /** Observe all notes (newest first), each decrypted on the fly. */
    fun observeNotes(): Flow<List<Note>>

    /** Fetch a single decrypted note, or null if it does not exist. */
    suspend fun getNote(id: Long): Note?

    /** Insert a new note, returning its generated id. */
    suspend fun addNote(note: Note): Long

    /** Update an existing note. */
    suspend fun updateNote(note: Note)

    /** Delete the note with [id]. */
    suspend fun deleteNote(id: Long)
}
