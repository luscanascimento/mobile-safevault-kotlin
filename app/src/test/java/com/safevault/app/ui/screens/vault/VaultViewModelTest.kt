package com.safevault.app.ui.screens.vault

import app.cash.turbine.test
import com.safevault.app.domain.model.AppSettings
import com.safevault.app.domain.model.AutoLockTimeout
import com.safevault.app.domain.model.Note
import com.safevault.app.domain.model.ThemeMode
import com.safevault.app.domain.repository.NoteRepository
import com.safevault.app.domain.repository.SettingsRepository
import com.safevault.app.domain.usecase.DeleteNoteUseCase
import com.safevault.app.domain.usecase.GetNoteUseCase
import com.safevault.app.domain.usecase.NoteUseCases
import com.safevault.app.domain.usecase.ObserveNotesUseCase
import com.safevault.app.domain.usecase.SaveNoteUseCase
import com.safevault.app.util.VaultLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The vault list pipeline: combine(notes, debounced query) -> filter -> state,
 * plus the error and lock paths.
 *
 * `viewModelScope` runs on [Dispatchers.Main], so the main dispatcher is
 * replaced with the test scheduler and virtual time is advanced explicitly.
 * That is also what makes the debounce assertions meaningful: the tests step
 * to 249 ms and then to 250 ms, so removing (or changing) the debounce breaks
 * them instead of silently passing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val alpha = Note(id = 1, title = "Bank", content = "PIN", tags = listOf("finance"), createdAt = 1, updatedAt = 1)
    private val beta = Note(id = 2, title = "Wifi", content = "hunter2", tags = listOf("home"), createdAt = 2, updatedAt = 2)

    private val settingsRepository = object : SettingsRepository {
        override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setDynamicColor(enabled: Boolean) = Unit
        override suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) = Unit
    }

    private val lockManager = VaultLockManager(settingsRepository)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Only [NoteRepository.observeNotes] matters here; the rest is unused. */
    private fun viewModel(notes: Flow<List<Note>>): VaultViewModel {
        val repository = object : NoteRepository {
            override fun observeNotes(): Flow<List<Note>> = notes
            override suspend fun getNote(id: Long): Note? = null
            override suspend fun addNote(note: Note): Long = 0L
            override suspend fun updateNote(note: Note) = Unit
            override suspend fun deleteNote(id: Long) = Unit
        }
        return VaultViewModel(
            noteUseCases = NoteUseCases(
                observeNotes = ObserveNotesUseCase(repository),
                getNote = GetNoteUseCase(repository),
                saveNote = SaveNoteUseCase(repository),
                deleteNote = DeleteNoteUseCase(repository),
            ),
            lockManager = lockManager,
        )
    }

    @Test
    fun `an empty store settles on Empty`() = runTest(dispatcher) {
        viewModel(MutableStateFlow(emptyList())).uiState.test {
            assertEquals(VaultUiState.Loading, awaitItem())
            assertEquals(VaultUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `notes are emitted as Content`() = runTest(dispatcher) {
        viewModel(MutableStateFlow(listOf(alpha, beta))).uiState.test {
            assertEquals(VaultUiState.Loading, awaitItem())
            assertEquals(VaultUiState.Content(listOf(alpha, beta)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `store updates flow through without a query`() = runTest(dispatcher) {
        val store = MutableStateFlow(listOf(alpha))

        viewModel(store).uiState.test {
            assertEquals(VaultUiState.Loading, awaitItem())
            assertEquals(VaultUiState.Content(listOf(alpha)), awaitItem())

            store.value = listOf(alpha, beta)
            advanceUntilIdle()

            assertEquals(VaultUiState.Content(listOf(alpha, beta)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a query only filters after the debounce elapses`() = runTest(dispatcher) {
        val viewModel = viewModel(MutableStateFlow(listOf(alpha, beta)))

        viewModel.uiState.test {
            assertEquals(VaultUiState.Loading, awaitItem())
            assertEquals(VaultUiState.Content(listOf(alpha, beta)), awaitItem())

            viewModel.onQueryChange("hunter")
            advanceTimeBy(249)
            runCurrent()
            expectNoEvents()

            advanceTimeBy(1)
            runCurrent()
            assertEquals(VaultUiState.Content(listOf(beta)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rapid typing only emits the last query`() = runTest(dispatcher) {
        val viewModel = viewModel(MutableStateFlow(listOf(alpha, beta)))

        viewModel.uiState.test {
            assertEquals(VaultUiState.Loading, awaitItem())
            assertEquals(VaultUiState.Content(listOf(alpha, beta)), awaitItem())

            viewModel.onQueryChange("h")
            advanceTimeBy(100)
            runCurrent()
            viewModel.onQueryChange("hu")
            advanceTimeBy(100)
            runCurrent()
            viewModel.onQueryChange("bank")
            advanceUntilIdle()

            // Only the final query reached the filter: no intermediate emission.
            assertEquals(VaultUiState.Content(listOf(alpha)), awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a query matching nothing yields Empty`() = runTest(dispatcher) {
        val viewModel = viewModel(MutableStateFlow(listOf(alpha, beta)))

        viewModel.uiState.test {
            assertEquals(VaultUiState.Loading, awaitItem())
            assertEquals(VaultUiState.Content(listOf(alpha, beta)), awaitItem())

            viewModel.onQueryChange("nothing matches this")
            advanceUntilIdle()

            assertEquals(VaultUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing the query restores the full list without waiting`() = runTest(dispatcher) {
        val viewModel = viewModel(MutableStateFlow(listOf(alpha, beta)))

        viewModel.uiState.test {
            assertEquals(VaultUiState.Loading, awaitItem())
            assertEquals(VaultUiState.Content(listOf(alpha, beta)), awaitItem())

            viewModel.onQueryChange("bank")
            advanceUntilIdle()
            assertEquals(VaultUiState.Content(listOf(alpha)), awaitItem())

            // An empty query is debounced by 0 ms, so clearing is immediate.
            viewModel.onQueryChange("")
            runCurrent()
            assertEquals(VaultUiState.Content(listOf(alpha, beta)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an upstream failure becomes Error instead of crashing`() = runTest(dispatcher) {
        val failing = flow<List<Note>> { throw IllegalStateException("keystore gone") }

        viewModel(failing).uiState.test {
            assertEquals(VaultUiState.Loading, awaitItem())
            assertEquals(VaultUiState.Error("keystore gone"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isSearching tracks a non-blank query`() = runTest(dispatcher) {
        val viewModel = viewModel(MutableStateFlow(listOf(alpha)))

        viewModel.isSearching.test {
            assertFalse(awaitItem())

            viewModel.onQueryChange("bank")
            advanceUntilIdle()
            assertTrue(awaitItem())

            // Whitespace is not a search.
            viewModel.onQueryChange("   ")
            advanceUntilIdle()
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lockNow locks the vault`() = runTest(dispatcher) {
        val viewModel = viewModel(MutableStateFlow(listOf(alpha)))
        lockManager.unlock()
        assertTrue(lockManager.isUnlocked.value)

        viewModel.lockNow()

        assertFalse(lockManager.isUnlocked.value)
    }
}
