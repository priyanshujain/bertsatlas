package app.bertopia.cardrender

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupportTest {
    @Test
    fun typedAnswerExactMatchIgnoresSurroundingWhitespace() {
        assertTrue(TypedAnswerComparison.compare("  Paris ", "Paris").isExactMatch)
        assertTrue(TypedAnswerComparison.compare("canberra", "Canberra").isExactMatch)
    }

    @Test
    fun typedAnswerDiffMarksWrongAndMissingCharacters() {
        val result = TypedAnswerComparison.compare("Pars", "Paris")
        assertFalse(result.isExactMatch)
        assertEquals(
            listOf(
                TypedSegment("Par", TypedSegmentKind.Correct),
                TypedSegment("-", TypedSegmentKind.Missing),
                TypedSegment("s", TypedSegmentKind.Correct),
            ),
            result.typedSegments,
        )
        val wrong = TypedAnswerComparison.compare("Lyon", "Paris")
        assertTrue(wrong.typedSegments.any { it.kind == TypedSegmentKind.Wrong })
    }

    @Test
    fun syntaxHighlighterFindsKeywordsStringsCommentsAndNumbers() {
        val tokens = SyntaxHighlighter.tokenize("val x = \"hi\" // note\nreturn 42", "kotlin")
        assertTrue(CodeToken("val", CodeTokenKind.Keyword) in tokens)
        assertTrue(CodeToken("\"hi\"", CodeTokenKind.String) in tokens)
        assertTrue(CodeToken("// note", CodeTokenKind.Comment) in tokens)
        assertTrue(CodeToken("42", CodeTokenKind.Number) in tokens)
        assertEquals("val x = \"hi\" // note\nreturn 42", tokens.joinToString("") { it.text })
    }

    @Test
    fun syntaxHighlighterHandlesPythonAndSql() {
        val python = SyntaxHighlighter.tokenize("def f():\n    # comment\n    return None", "py")
        assertTrue(CodeToken("def", CodeTokenKind.Keyword) in python)
        assertTrue(CodeToken("# comment", CodeTokenKind.Comment) in python)
        val sql = SyntaxHighlighter.tokenize("SELECT name FROM users -- all", "sql")
        assertTrue(CodeToken("SELECT", CodeTokenKind.Keyword) in sql)
        assertTrue(CodeToken("-- all", CodeTokenKind.Comment) in sql)
    }

    @Test
    fun cssColors() {
        assertEquals(0xFF112233L, CssColors.parse("#123"))
        assertEquals(0xFF336699L, CssColors.parse("#336699"))
        assertEquals(0xFFFF0000L, CssColors.parse("Red"))
        assertEquals(0x7FFF0000L, CssColors.parse("rgba(255, 0, 0, 0.5)"))
        assertNull(CssColors.parse("not-a-color"))
    }

    @Test
    fun occlusionShapeParsing() {
        val shape = OcclusionShapes.parse("image-occlusion:rect:left=.25:top=.5:width=.1:height=.2", 2, ClozeRole.Hidden)
        assertEquals(2, shape?.ordinal)
        assertFalse(shape!!.usesPixelCoordinates)
        assertNull(OcclusionShapes.parse("image-occlusion:star:left=1", 1, ClozeRole.Hidden))
        assertNull(OcclusionShapes.parse("not occlusion", 1, ClozeRole.Hidden))
    }
}
