package app.bertopia.anki.template

import kotlin.test.Test
import kotlin.test.assertEquals

class ClozeTest {
    @Test
    fun questionHidesActiveAndShowsInactive() {
        assertEquals(
            "<span class=\"cloze\" data-cloze=\"Paris\" data-ordinal=\"1\">[...]</span> is in " +
                "<span class=\"cloze-inactive\" data-ordinal=\"2\">France</span>",
            Cloze.renderQuestion("{{c1::Paris}} is in {{c2::France}}", 1),
        )
    }

    @Test
    fun questionShowsHint() {
        assertEquals(
            "<span class=\"cloze\" data-cloze=\"Paris\" data-ordinal=\"1\">[city]</span>",
            Cloze.renderQuestion("{{c1::Paris::city}}", 1),
        )
    }

    @Test
    fun answerRevealsActive() {
        assertEquals(
            "<span class=\"cloze-inactive\" data-ordinal=\"1\">Paris</span> is in " +
                "<span class=\"cloze\" data-ordinal=\"2\">France</span>",
            Cloze.renderAnswer("{{c1::Paris::city}} is in {{c2::France}}", 2),
        )
    }

    @Test
    fun repeatedOrdinalHidesEveryOccurrence() {
        assertEquals(
            "<span class=\"cloze\" data-cloze=\"a\" data-ordinal=\"1\">[...]</span>" +
                "<span class=\"cloze\" data-cloze=\"b\" data-ordinal=\"1\">[...]</span>",
            Cloze.renderQuestion("{{c1::a}}{{c1::b}}", 1),
        )
    }

    @Test
    fun missingOrdinalRendersEmptyCard() {
        assertEquals("", Cloze.renderQuestion("{{c1::a}}", 3))
        assertEquals("", Cloze.renderAnswer("no clozes", 1))
    }

    @Test
    fun nestedClozes() {
        val text = "{{c1::outer {{c2::inner}} end}}"
        assertEquals(setOf(1, 2), Cloze.ordinals(text))
        assertEquals(
            "<span class=\"cloze\" data-cloze=\"outer &lt;span class=&quot;cloze-inactive&quot; data-ordinal=&quot;2&quot;&gt;inner&lt;/span&gt; end\" data-ordinal=\"1\">[...]</span>",
            Cloze.renderQuestion(text, 1),
        )
        assertEquals(
            "<span class=\"cloze-inactive\" data-ordinal=\"1\">outer <span class=\"cloze\" data-cloze=\"inner\" data-ordinal=\"2\">[...]</span> end</span>",
            Cloze.renderQuestion(text, 2),
        )
        assertEquals(listOf("inner"), Cloze.activeAnswers(text, 2))
        assertEquals(listOf("outer inner end"), Cloze.activeAnswers(text, 1))
    }

    @Test
    fun unclosedClozeStaysAsText() {
        assertEquals("", Cloze.renderQuestion("{{c1::open", 1))
        assertEquals(emptySet(), Cloze.ordinals("{{c1::open"))
    }

    @Test
    fun ordinalsAreMultiDigit() {
        assertEquals(setOf(12), Cloze.ordinals("{{c12::x}}"))
    }
}
