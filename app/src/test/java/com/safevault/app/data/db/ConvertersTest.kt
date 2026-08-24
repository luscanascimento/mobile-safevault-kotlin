package com.safevault.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `round trips a tag list`() {
        val tags = listOf("work", "personal", "finance")
        val stored = converters.fromTags(tags)
        assertEquals(tags, converters.toTags(stored))
    }

    @Test
    fun `empty list maps to empty string and back`() {
        assertEquals("", converters.fromTags(emptyList()))
        assertEquals(emptyList<String>(), converters.toTags(""))
    }
}
