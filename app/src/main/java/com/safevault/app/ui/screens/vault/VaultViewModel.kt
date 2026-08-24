package com.safevault.app.ui.screens.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safevault.app.domain.model.Note
import com.safevault.app.domain.model.matches
import com.safevault.app.domain.usecase.NoteUseCases
import com.safevault.app.util.VaultLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface VaultUiState {
    data object Loading : VaultUiState
    data class Content(val notes: List<Note>) : VaultUiState
    data object Empty : VaultUiState
    data class Error(val message: String) : VaultUiState
}

@OptIn(FlowPreview::class)
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val noteUseCases: NoteUseCases,
    private val lockManager: VaultLockManager,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * The note stream is decrypted once per store change and filtered in
     * memory, so a keystroke costs zero Keystore operations. The debounce only
     * throttles recomposition while typing.
     */
    val uiState: StateFlow<VaultUiState> = combine(
        noteUseCases.observeNotes(),
        _query.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
    ) { notes, q -> notes.filter { it.matches(q) } }
        .map<List<Note>, VaultUiState> { notes ->
            if (notes.isEmpty()) VaultUiState.Empty
            else VaultUiState.Content(notes)
        }
        .onStart { emit(VaultUiState.Loading) }
        .catch { emit(VaultUiState.Error(it.message ?: "Unknown error")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = VaultUiState.Loading,
        )

    /** True only while a search query is active, to distinguish "no results"
     *  from "empty vault" in the UI. */
    val isSearching: StateFlow<Boolean> = _query
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun lockNow() = lockManager.lock()

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
