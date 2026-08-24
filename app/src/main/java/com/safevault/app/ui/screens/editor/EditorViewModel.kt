package com.safevault.app.ui.screens.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safevault.app.domain.model.Note
import com.safevault.app.domain.usecase.NoteUseCases
import com.safevault.app.domain.usecase.SaveNoteUseCase
import com.safevault.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val tagsInput: String = "",
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val showEmptyError: Boolean = false,
)

/** One-shot UI events the screen should react to exactly once. */
sealed interface EditorEvent {
    data object Saved : EditorEvent
    data object Deleted : EditorEvent
    data class Error(val message: String) : EditorEvent
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val noteUseCases: NoteUseCases,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val noteId: Long =
        savedStateHandle.get<Long>(Routes.EDITOR_ARG_ID)?.takeIf { it > 0L } ?: 0L

    private val _uiState = MutableStateFlow(EditorUiState(isNew = noteId == 0L, isLoading = noteId != 0L))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<EditorEvent?>(null)
    val events: StateFlow<EditorEvent?> = _events.asStateFlow()

    init {
        if (noteId != 0L) loadNote(noteId)
    }

    private fun loadNote(id: Long) {
        viewModelScope.launch {
            val note = runCatching { noteUseCases.getNote(id) }
                .getOrElse {
                    _events.value = EditorEvent.Error(it.message ?: "Failed to open note")
                    null
                }
            if (note == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            _uiState.value = EditorUiState(
                id = note.id,
                title = note.title,
                content = note.content,
                tagsInput = note.tags.joinToString(", "),
                isNew = false,
                isLoading = false,
            )
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, showEmptyError = false) }
    fun onContentChange(value: String) = _uiState.update { it.copy(content = value, showEmptyError = false) }
    fun onTagsChange(value: String) = _uiState.update { it.copy(tagsInput = value) }

    fun save() {
        val state = _uiState.value
        val now = System.currentTimeMillis()
        val note = Note(
            id = state.id,
            title = state.title,
            content = state.content,
            tags = state.tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            createdAt = if (state.isNew) now else 0L, // preserved below for existing notes
            updatedAt = now,
        )

        viewModelScope.launch {
            // For existing notes, keep the original createdAt.
            val toSave = if (!state.isNew) {
                val existing = runCatching { noteUseCases.getNote(state.id) }.getOrNull()
                note.copy(createdAt = existing?.createdAt ?: now)
            } else {
                note
            }

            when (val result = runCatching { noteUseCases.saveNote(toSave) }.getOrNull()) {
                is SaveNoteUseCase.Result.Success -> _events.value = EditorEvent.Saved
                SaveNoteUseCase.Result.Blank -> _uiState.update { it.copy(showEmptyError = true) }
                null -> _events.value = EditorEvent.Error("Could not save note")
            }
        }
    }

    fun delete() {
        val id = _uiState.value.id
        if (id == 0L) {
            _events.value = EditorEvent.Deleted
            return
        }
        viewModelScope.launch {
            runCatching { noteUseCases.deleteNote(id) }
                .onSuccess { _events.value = EditorEvent.Deleted }
                .onFailure { _events.value = EditorEvent.Error(it.message ?: "Could not delete note") }
        }
    }

    fun consumeEvent() {
        _events.value = null
    }
}
