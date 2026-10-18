package app.bertsatlas.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArgumentsTest {
    @Test
    fun separatesPositionalsValuesAndSwitches() {
        val arguments = Arguments.parse(listOf("cards", "list", "dsa", "--section", "Arrays", "--json", "--limit=5"))
        assertEquals(listOf("cards", "list", "dsa"), arguments.positionals)
        assertEquals("Arrays", arguments.value("section"))
        assertEquals(5, arguments.intValue("limit", 20))
        assertTrue(arguments.has("json"))
    }

    @Test
    fun collectsRepeatedOptionsInOrder() {
        val arguments = Arguments.parse(listOf("cards", "tag", "p", "1", "--add", "a", "--add", "b", "--remove", "c"))
        assertEquals(listOf("a", "b"), arguments.values("add"))
        assertEquals(listOf("c"), arguments.values("remove"))
    }

    @Test
    fun treatsEverythingAfterDoubleDashAsPositional() {
        val arguments = Arguments.parse(listOf("cards", "show", "p", "--", "--json"))
        assertEquals(listOf("cards", "show", "p", "--json"), arguments.positionals)
        assertTrue(!arguments.has("json"))
    }

    @Test
    fun rejectsUnknownOptionsAndMissingValues() {
        assertEquals(ExitCode.Usage, assertFailsWith<CliFailure> { Arguments.parse(listOf("--colour")) }.exitCode)
        assertEquals(ExitCode.Usage, assertFailsWith<CliFailure> { Arguments.parse(listOf("packs", "create", "--title")) }.exitCode)
        assertEquals(ExitCode.Usage, assertFailsWith<CliFailure> { Arguments.parse(listOf("--json=yes")) }.exitCode)
    }

    @Test
    fun rejectsNonPositiveLimits() {
        assertFailsWith<CliFailure> { Arguments.parse(listOf("--limit", "0")).intValue("limit", 1) }
        assertNull(Arguments.parse(emptyList()).value("limit"))
    }
}
