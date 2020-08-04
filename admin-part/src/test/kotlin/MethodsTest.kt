package com.epam.drill.plugins.test2code

import kotlin.test.*

class MethodsTest {
    @Test
    fun `simple case for all diff categories`() {
        val new = Method(
            ownerClass = "foo/bar/Baz",
            name = "new",
            desc = "(Z)Z",
            hash = "0"
        )
        val modified = Method(
            ownerClass = "foo/bar/Baz",
            name = "modified",
            desc = "(I)V",
            hash = "11"
        )
        val unaffected = Method(
            ownerClass = "foo/bar/Baz",
            name = "unaffected",
            desc = "(Z)Z",
            hash = "0"
        )
        val deleted = Method(
            ownerClass = "foo/bar/Baz",
            name = "deleted",
            desc = "(I)V",
            hash = "2"
        )
        val old = listOf(
            unaffected,
            Method(
                ownerClass = "foo/bar/Baz",
                name = "modified",
                desc = "(I)V",
                hash = "1"
            ),
            deleted
        ).sorted()
        val current = listOf(
            new,
            unaffected,
            modified
        ).sorted()
        val diff = current.diff(old)
        assertEquals(listOf(new), diff.new)
        assertEquals(listOf(modified), diff.modified)
        assertEquals(listOf(deleted), diff.deleted)
        assertEquals(listOf(unaffected), diff.unaffected)
    }
}
