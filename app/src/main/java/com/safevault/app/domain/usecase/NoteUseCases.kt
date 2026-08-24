package com.safevault.app.domain.usecase

import com.safevault.app.domain.model.Note
import com.safevault.app.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Cohesive bundle of note interactions exposed to the presentation layer.
 * Grouping related use-cases keeps ViewModel constructors small while still
 * isolating business rules from Android/framework concerns.
 */
class NoteUseCases @Inject constructor(
    val observeNotes: ObserveNotesUseCase,
    val getNote: GetNoteUseCase,
    val saveNote: SaveNoteUseCase,
    val deleteNote: DeleteNoteUseCase,
)

class ObserveNotesUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    operator fun invoke(): Flow<List<Note>> = repository.observeNotes()
}

class GetNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(id: Long): Note? = repository.getNote(id)
}

/**
 * Validates and persists a note. Returns the id of the stored note.
 * A note is considered blank when both title and content are empty.
 */
class SaveNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    sealed interface Result {
        data class Success(val id: Long) : Result
        data object Blank : Result
    }

    suspend operator fun invoke(note: Note): Result {
        if (note.title.isBlank() && note.content.isBlank()) return Result.Blank

        val normalized = note.copy(
            title = note.title.trim(),
            tags = note.tags.map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
        )

        val id = if (normalized.id == 0L) {
            repository.addNote(normalized)
        } else {
            repository.updateNote(normalized)
            normalized.id
        }
        return Result.Success(id)
    }
}

class DeleteNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(id: Long) = repository.deleteNote(id)
}
