package com.safevault.app.domain.usecase

import com.safevault.app.domain.model.Note
import com.safevault.app.domain.repository.NoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveNoteUseCaseTest {

    private lateinit var repository: NoteRepository
    private lateinit var useCase: SaveNoteUseCase

    private fun note(
        id: Long = 0L,
        title: String = "Title",
        content: String = "Secret",
        tags: List<String> = emptyList(),
    ) = Note(
        id = id,
        title = title,
        content = content,
        tags = tags,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        useCase = SaveNoteUseCase(repository)
    }

    @Test
    fun `blank title and content returns Blank`() = runTest {
        val result = useCase(note(title = "  ", content = ""))
        assertTrue(result is SaveNoteUseCase.Result.Blank)
    }

    @Test
    fun `new note is inserted and returns generated id`() = runTest {
        coEvery { repository.addNote(any()) } returns 42L

        val result = useCase(note(id = 0L))

        assertEquals(SaveNoteUseCase.Result.Success(42L), result)
        coVerify(exactly = 1) { repository.addNote(any()) }
    }

    @Test
    fun `existing note is updated and keeps its id`() = runTest {
        val result = useCase(note(id = 7L))

        assertEquals(SaveNoteUseCase.Result.Success(7L), result)
        coVerify(exactly = 1) { repository.updateNote(any()) }
    }

    @Test
    fun `tags are trimmed deduplicated and emptied entries removed`() = runTest {
        coEvery { repository.addNote(any()) } returns 1L
        val slot = mutableListOf<Note>()
        coEvery { repository.addNote(capture(slot)) } returns 1L

        useCase(note(tags = listOf(" work ", "work", "", "personal")))

        assertEquals(listOf("work", "personal"), slot.first().tags)
    }
}
