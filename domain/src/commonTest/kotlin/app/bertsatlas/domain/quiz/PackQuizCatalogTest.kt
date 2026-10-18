package app.bertsatlas.domain.quiz

import app.bertsatlas.anki.model.CardTemplate
import app.bertsatlas.anki.model.Note
import app.bertsatlas.anki.model.Notetype
import app.bertsatlas.anki.model.NotetypeField
import app.bertsatlas.anki.model.NotetypeKind
import app.bertsatlas.domain.MasteryRules
import app.bertsatlas.domain.PassportEvidence
import app.bertsatlas.domain.PassportRules
import app.bertsatlas.domain.QuizEngine
import app.bertsatlas.domain.model.HintType
import app.bertsatlas.domain.model.PassportStampKind
import app.bertsatlas.domain.model.PlayScope
import app.bertsatlas.domain.model.Question
import app.bertsatlas.domain.model.QuizConfiguration
import app.bertsatlas.domain.model.QuizDuration
import app.bertsatlas.domain.model.RunPhase
import app.bertsatlas.domain.model.Skill
import app.bertsatlas.domain.model.Streak
import app.bertsatlas.domain.model.SubmittedAnswer
import app.bertsatlas.domain.pack.BertNoteTypes
import app.bertsatlas.domain.pack.toAnkiNotetype
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PackQuizCatalogTest {
    private val basic = standardNotetype(
        "Basic",
        listOf("Front", "Back"),
        listOf(CardTemplate("Card 1", 0, "{{Front}}", "{{FrontSide}}<hr id=answer>{{Back}}")),
    )
    private val basicReversed = standardNotetype(
        "Basic (and reversed card)",
        listOf("Front", "Back"),
        listOf(
            CardTemplate("Card 1", 0, "{{Front}}", "{{FrontSide}}<hr id=answer>{{Back}}"),
            CardTemplate("Card 2", 1, "{{Back}}", "{{FrontSide}}<hr id=answer>{{Front}}"),
        ),
    )
    private val cloze = Notetype(
        id = 3,
        name = "Cloze",
        kind = NotetypeKind.Cloze,
        fields = listOf(NotetypeField("Text", 0), NotetypeField("Back Extra", 1)),
        templates = listOf(CardTemplate("Cloze", 0, "{{cloze:Text}}", "{{cloze:Text}}<br>{{Back Extra}}")),
        css = "",
    )

    @Test
    fun basicCardsWithShortAnswersBecomeMultipleChoiceAndLetterBank() {
        val cards = elements.mapIndexed { index, (symbol, name) -> card(basic, "el$index", listOf(symbol, name)) }
        val catalog = PackQuizCatalog.build("1", cards)
        val hydrogen = catalog.questions.filter { it.countryIds == listOf("el0:0") }
        val choice = hydrogen.filterIsInstance<Question.MultipleChoice>().single()
        assertEquals("Hydrogen", choice.choices.single().label)
        assertEquals(Skill.CardRecall, choice.skill)
        assertEquals("science", choice.packId)
        assertTrue(choice.distractorPoolsNearestFirst.flatten().size >= PackQuizCatalog.MINIMUM_DISTRACTORS)
        assertTrue(choice.distractorPoolsNearestFirst.flatten().none { it.label == "Hydrogen" })
        val letters = hydrogen.filterIsInstance<Question.LetterBank>().single()
        assertEquals("Hydrogen", letters.displayAnswer)
        assertEquals(3, letters.decoyLetters.size)
        assertTrue(letters.decoyLetters.none { it in "HYDROGEN" })
        assertTrue(HintType.RevealCorrectLetter in letters.supportedHints)
        assertFalse(HintType.FunnyFactualClue in choice.supportedHints)
    }

    @Test
    fun reversedCardsAnswerWithTheOtherSideAndNeverOfferTheirOwnNote() {
        val cards = elements.flatMapIndexed { index, (symbol, name) ->
            listOf(card(basicReversed, "el$index", listOf(symbol, name), 0), card(basicReversed, "el$index", listOf(symbol, name), 1))
        }
        val forward = cards.first { it.itemId == "el0:0" }
        val backward = cards.first { it.itemId == "el0:1" }
        assertEquals("Hydrogen", forward.shortAnswer)
        assertEquals("H", backward.shortAnswer)
        val catalog = PackQuizCatalog.build("1", cards)
        val backwardChoice = catalog.questions.filterIsInstance<Question.MultipleChoice>().single { it.countryIds == listOf("el0:1") }
        val offered = backwardChoice.distractorPoolsNearestFirst.flatten().map { it.label }
        assertTrue(offered.none { it == "Hydrogen" || it == "H" })
        val exactShape = backwardChoice.distractorPoolsNearestFirst[1].map { it.label }
        assertTrue(exactShape.size >= PackQuizCatalog.MINIMUM_DISTRACTORS)
        assertTrue(exactShape.all { it.length <= 2 })
    }

    @Test
    fun clozeUsesTheDeletionTextForEachOrdinal() {
        val note = Note(1, "cz", cloze.id, listOf("{{c1::Paris}} is the capital of {{c2::France::country}}", ""), emptyList())
        val first = PackCardReader.read("geo", cloze, note, 0, "Geo")
        val second = PackCardReader.read("geo", cloze, note, 1, "Geo")
        assertEquals("Paris", first.shortAnswer)
        assertEquals("France", second.shortAnswer)
        assertTrue(second.frontText.contains("[country]"))
    }

    @Test
    fun clozeWithSeveralDifferentDeletionsInOneOrdinalHasNoShortAnswer() {
        val note = Note(1, "cz", cloze.id, listOf("{{c1::red}}, {{c1::green}} and blue", ""), emptyList())
        assertNull(PackCardReader.read("colors", cloze, note, 0, "Colors").shortAnswer)
    }

    @Test
    fun vocabularyWithLongOrRichBacksFallsBackToFlipAndGrade() {
        val cards = listOf(
            card(basic, "v1", listOf("ephemeral", "Lasting for a very short time; transitory, as in mayflies or fashions.")),
            card(basic, "v2", listOf("ubiquitous", "Present<br>everywhere")),
            card(basic, "v3", listOf("lucid", "<img src=\"clear.png\">")),
            card(basic, "v4", listOf("<b>bold</b>", "Strong")),
        )
        val catalog = PackQuizCatalog.build("1", cards)
        val formats = cards.associate { it.itemId to PackQuizCatalog.formatsFor(it, cards) }
        assertEquals(setOf(QuizFormat.FlipAndGrade), formats["v1:0"])
        assertEquals(setOf(QuizFormat.FlipAndGrade), formats["v2:0"])
        assertEquals(setOf(QuizFormat.FlipAndGrade), formats["v3:0"])
        assertEquals(setOf(QuizFormat.LetterBank), formats["v4:0"])
        val flip = catalog.questions.filterIsInstance<Question.FlipAndGrade>().first { it.countryIds == listOf("v1:0") }
        assertTrue(flip.answerHtml.contains("transitory"))
        assertEquals("ephemeral", flip.prompt)
    }

    @Test
    fun numericAnswersOnlyDrawNumericDistractors() {
        val years = listOf(
            "Battle of Hastings" to "1066", "Magna Carta" to "1215", "Fall of Constantinople" to "1453",
            "Columbus reaches America" to "1492", "French Revolution begins" to "1789",
            "Moon landing" to "1969", "Who painted the Mona Lisa" to "Leonardo da Vinci",
        )
        val cards = years.mapIndexed { index, (question, answer) -> card(basic, "y$index", listOf(question, answer)) }
        val catalog = PackQuizCatalog.build("1", cards)
        val hastings = catalog.questions.filterIsInstance<Question.MultipleChoice>().single { it.countryIds == listOf("y0:0") }
        val labels = hastings.distractorPoolsNearestFirst.flatten().map { it.label }
        assertTrue(labels.isNotEmpty())
        assertTrue(labels.all { label -> label.all { it.isDigit() } })
        assertEquals(AnswerKind.Number, AnswerShapes.of("1066").kind)
        assertEquals(AnswerKind.Date, AnswerShapes.of("1969-07-20").kind)
        assertEquals(AnswerKind.Date, AnswerShapes.of("July 4, 1776").kind)
        assertTrue(catalog.questions.none { it is Question.LetterBank && it.countryIds == listOf("y0:0") })
    }

    @Test
    fun nonLatinScriptsMatchScriptAndUseTheirOwnLetterBankAlphabet() {
        val russian = listOf("cat" to "кошка", "dog" to "собака", "house" to "дом", "water" to "вода", "bread" to "хлеб")
        val japanese = listOf("water" to "みず", "fire" to "ひ", "tree" to "き", "mountain" to "やま")
        val cards = russian.mapIndexed { index, (en, ru) -> card(basic, "ru$index", listOf(en, ru)) } +
            japanese.mapIndexed { index, (en, ja) -> card(basic, "ja$index", listOf(en, ja)) }
        val catalog = PackQuizCatalog.build("1", cards)
        val cat = catalog.questions.filterIsInstance<Question.MultipleChoice>().single { it.countryIds == listOf("ru0:0") }
        assertTrue(cat.distractorPoolsNearestFirst.flatten().all { AnswerShapes.scriptOf(it.label) == AnswerScript.Cyrillic })
        val catLetters = catalog.questions.filterIsInstance<Question.LetterBank>().single { it.countryIds == listOf("ru0:0") }
        assertTrue(catLetters.decoyLetters.all { AnswerShapes.scriptOf(it.toString()) == AnswerScript.Cyrillic })
        assertTrue(catalog.questions.none { it is Question.LetterBank && it.countryIds == listOf("ja0:0") })
        assertTrue(catalog.questions.any { it is Question.MultipleChoice && it.countryIds == listOf("ja0:0") })
    }

    @Test
    fun smallMixedPacksNeverOfferAnswersOfAnotherKind() {
        val facts = listOf(
            "Largest planet" to "Jupiter", "Ringed planet" to "Saturn", "Red planet" to "Mars",
            "Moons of Mars" to "Two", "Planets in the solar system" to "Eight", "Earth's place from the sun" to "Third",
            "Year of the first Moon landing" to "1969", "First person on the Moon" to "Neil Armstrong",
        )
        val cards = facts.mapIndexed { index, (question, answer) -> card(basic, "s$index", listOf(question, answer)) }
        val questions = PackQuizCatalog.build("1", cards).questions
        assertEquals(AnswerKind.Number, AnswerShapes.of("Two").kind)
        assertEquals(AnswerKind.Number, AnswerShapes.of("twenty-one").kind)
        assertEquals(AnswerKind.Word, AnswerShapes.of("Jupiter").kind)
        questions.filterIsInstance<Question.MultipleChoice>().forEach { question ->
            val kind = AnswerShapes.of(question.choices.single().label).kind
            assertTrue(question.distractorPoolsNearestFirst.flatten().all { AnswerShapes.of(it.label).kind == kind }, question.prompt)
        }
        val jupiter = questions.filter { it.countryIds == listOf("s0:0") }
        assertTrue(jupiter.none { it is Question.MultipleChoice })
        assertTrue(jupiter.any { it is Question.LetterBank })
        val armstrong = questions.filter { it.countryIds == listOf("s7:0") }
        assertTrue(armstrong.none { it is Question.MultipleChoice })
        assertTrue(armstrong.any { it is Question.LetterBank })
        val landing = questions.filter { it.countryIds == listOf("s6:0") }
        assertEquals(listOf(Question.FlipAndGrade::class), landing.map { it::class })
    }

    @Test
    fun cardsWithoutThreeDistractorsAreNotMultipleChoice() {
        val cards = elements.take(3).mapIndexed { index, (symbol, name) -> card(basic, "el$index", listOf(symbol, name)) }
        assertEquals(setOf(QuizFormat.LetterBank), PackQuizCatalog.formatsFor(cards.first(), cards))
    }

    @Test
    fun similarGroupsComeFirstAndSectionsPreferTheirOwnAnswers() {
        val bertBasic = BertNoteTypes.Basic.toAnkiNotetype()
        fun bert(guid: String, question: String, answer: String, deck: String, tags: List<String> = emptyList()) = PackCardReader.read(
            "langs",
            bertBasic,
            Note(1, guid, bertBasic.id, listOf(question, answer, "", "", "", "", ""), tags),
            0,
            deck,
        )
        val cards = listOf(
            bert("a", "Language of Java", "Java", "Langs::JVM", listOf("bert::similar::script-names")),
            bert("b", "Scripting for browsers", "JavaScript", "Langs::Web", listOf("bert::similar::script-names")),
            bert("c", "Kotlin's platform", "Kotlin", "Langs::JVM"),
            bert("d", "Groovy's platform", "Groovy", "Langs::JVM"),
            bert("e", "Scala's platform", "Scala", "Langs::JVM"),
            bert("f", "Ruby", "Rubyist", "Langs::Web"),
        )
        assertEquals("JVM", cards.first().section)
        val java = PackQuizCatalog.build("1", cards).questions
            .filterIsInstance<Question.MultipleChoice>().single { it.countryIds == listOf("a:0") }
        assertEquals(listOf("JavaScript"), java.distractorPoolsNearestFirst[0].map { it.label })
        val sectionLabels = (java.distractorPoolsNearestFirst[1] + java.distractorPoolsNearestFirst[2]).map { it.label }
        assertEquals(setOf("Kotlin", "Groovy", "Scala"), sectionLabels.toSet())
    }

    @Test
    fun choicesNeverRepeatTheAnswerOrEachOther() {
        val bertBasic = BertNoteTypes.Basic.toAnkiNotetype()
        fun bert(guid: String, question: String, answer: String, aliases: String = "") = PackCardReader.read(
            "dsa",
            bertBasic,
            Note(1, guid, bertBasic.id, listOf(question, answer, aliases, "", "", "", ""), listOf("bert::similar::structures")),
            0,
            "DSA",
        )
        val cards = listOf(
            bert("fifo", "First in, first out structure", "Queue"),
            bert("bfs", "Structure behind breadth first search", "Queue"),
            bert("printer", "Structure for a print spooler", "queue "),
            bert("lifo", "Last in, first out structure", "Stack"),
            bert("dict", "Key value structure with O(1) lookup", "Hash map", "Hash table"),
            bert("table", "Array of buckets keyed by hash", "Hash table", "Hash map"),
            bert("search", "Binary search runs in", "O(log n)"),
            bert("height", "Height of a balanced tree", "O(logn)"),
            bert("heap", "Priority queue backing structure", "Heap"),
            bert("trie", "Prefix tree", "Trie"),
            bert("deque", "Double ended queue", "Deque"),
            bert("chain", "Nodes that point to the next node", "Linked list"),
            bert("bst", "Ordered tree of left and right children", "Binary tree"),
            bert("graph", "Vertices joined by one way edges", "Directed graph"),
        )
        val catalog = PackQuizCatalog.build("1", cards)
        val engine = QuizEngine(catalog, { 0L }, { 1_000L }, { 20_000 }, allowUnapprovedContent = true)
        catalog.questions.filterIsInstance<Question.MultipleChoice>().forEach { question ->
            val card = cards.single { it.itemId == question.countryIds.single() }
            val forbidden = (card.aliases + card.shortAnswer!!).map(ChoiceLabels::key).toSet()
            val distractors = question.distractorPoolsNearestFirst.flatten()
            assertTrue(distractors.none { ChoiceLabels.key(it.label) in forbidden }, "${card.itemId} offers a correct answer as a distractor")
            assertEquals(distractors.size, distractors.map { ChoiceLabels.key(it.label) }.distinct().size)
        }
        val dictionary = catalog.questions.filterIsInstance<Question.MultipleChoice>().single { it.countryIds == listOf("dict:0") }
        assertTrue(dictionary.distractorPoolsNearestFirst.flatten().none { it.label == "Hash table" })
        repeat(20) { seed ->
            val state = engine.startRun("run$seed", QuizConfiguration(QuizDuration.Unlimited, PlayScope.Pack("dsa")), seed.toLong(), emptyList())
            val question = state.activeQuestion.question as? Question.MultipleChoice ?: return@repeat
            val labels = state.activeQuestion.orderedChoices.map { ChoiceLabels.key(it.label) }
            assertEquals(4, labels.distinct().size, "choices repeat for ${question.id}: $labels")
        }
    }

    @Test
    fun hintsAreOfferedOnlyWhenTheirDataExists() {
        val flag = BertNoteTypes.Flag.toAnkiNotetype()
        val note = Note(1, "fr", flag.id, listOf("France", "<img src=\"fr.png\">", "", "Europe", "Its tricolour inspired many others", "", "", ""), emptyList())
        val card = PackCardReader.read("flags", flag, note, 0, "Flags")
        assertEquals("France", card.shortAnswer)
        assertEquals("Europe", card.packClue)
        val questions = PackQuizCatalog.build("1", listOf(card)).questions
        val letters = questions.filterIsInstance<Question.LetterBank>().single()
        assertEquals("Europe", letters.hintText[HintType.RevealContinent])
        assertTrue(HintType.FunnyFactualClue in letters.supportedHints)
    }

    @Test
    fun genericRunsPlayEveryFormatThroughTheExistingEngine() {
        val cards = elements.mapIndexed { index, (symbol, name) -> card(basic, "el$index", listOf(symbol, name)) } +
            card(basic, "long", listOf("Why is the sky blue?", "Rayleigh scattering of sunlight by the molecules of the atmosphere."))
        val catalog = PackQuizCatalog.build("1", cards)
        var now = 0L
        val engine = QuizEngine(catalog, { now }, { 1_000L }, { 20_000 }, allowUnapprovedContent = true)
        var state = engine.startRun("run", QuizConfiguration(QuizDuration.Unlimited, PlayScope.Pack("science")), 42L, emptyList())
        val seenFormats = mutableSetOf<String>()
        repeat(60) {
            val question = state.activeQuestion.question
            seenFormats += question::class.simpleName.orEmpty()
            now += 2_000
            state = when (question) {
                is Question.MultipleChoice -> engine.answerQuestion(state, SubmittedAnswer.ChoiceAnswer(question.correctChoiceId))
                is Question.LetterBank -> {
                    var placing = state
                    val expected = question.displayAnswer.filter { it.isLetter() }.uppercase()
                    expected.forEachIndexed { index, _ -> placing = engine.placeLetter(placing, "${question.id}:required:$index") }
                    engine.submitLetters(placing)
                }
                is Question.FlipAndGrade -> engine.answerQuestion(state, SubmittedAnswer.SelfGrade(remembered = true))
            }
            val attempt = state.attempts.last()
            assertTrue(attempt.correct)
            assertEquals("science", attempt.packId)
            state = engine.dismissFeedback(state, emptyList())
            while (state.phase == RunPhase.FunFact) state = engine.continueFromFact(state, emptyList())
        }
        assertTrue(state.score > 0)
        assertEquals(setOf("MultipleChoice", "LetterBank", "FlipAndGrade"), seenFormats)
        val mastery = state.attempts.fold(emptyList<app.bertsatlas.domain.model.Mastery>()) { acc, attempt ->
            MasteryRules.applyAttemptToCountries(acc, attempt)
        }
        assertTrue(mastery.all { it.skill == Skill.CardRecall })
    }

    @Test
    fun flipAndGradeForgottenCountsAsIncorrect() {
        val cards = listOf(card(basic, "only", listOf("What is love?", "Baby don't hurt me, don't hurt me, no more, a long answer")))
        val engine = QuizEngine(PackQuizCatalog.build("1", cards), { 0L }, { 0L }, { 0 })
        val state = engine.startRun("run", QuizConfiguration(QuizDuration.Unlimited, PlayScope.Pack("science")), 7L, emptyList())
        val answered = engine.answerQuestion(state, SubmittedAnswer.SelfGrade(remembered = false))
        assertFalse(answered.attempts.single().correct)
        assertEquals(0, answered.score)
        assertTrue(answered.feedback!!.correctAnswer.startsWith("Baby"))
    }

    @Test
    fun genericStampsCoverEveryPack() {
        val evidence = PassportEvidence(
            completedRunCount = 1,
            recognizedFlagContinents = emptySet(),
            totalAnswered = 60,
            totalCorrect = 50,
            confusedFlagMastery = emptyList(),
            streak = Streak(currentDays = 7, bestDays = 7, lastCompletedEpochDay = 10),
            packCardCount = 12,
            packMasteredCardCount = 12,
        )
        val earned = PassportRules.newlyEarned(evidence, emptyList(), 0L).map { it.kind }.toSet()
        assertEquals(
            setOf(
                PassportStampKind.FirstCompletedRun,
                PassportStampKind.SustainedAccuracy,
                PassportStampKind.SevenActiveDays,
                PassportStampKind.PackMastered,
            ),
            earned,
        )
        val partial = PassportRules.newlyEarned(evidence.copy(packMasteredCardCount = 11), emptyList(), 0L)
        assertTrue(partial.none { it.kind == PassportStampKind.PackMastered })
        val empty = PassportRules.newlyEarned(evidence.copy(packCardCount = 0, packMasteredCardCount = 0), emptyList(), 0L)
        assertTrue(empty.none { it.kind == PassportStampKind.PackMastered })
    }

    @Test
    fun choiceCardsOfferOnlyTheirAuthoredChoices() {
        val choice = choiceCard("q", "Queue\nStack\nHeap\nTrie", "Queue", explanation = "FIFO means queue.")
        val basics = elements.mapIndexed { index, (symbol, name) -> card(basic, "el$index", listOf(symbol, name)) }
        val questions = PackQuizCatalog.build("1", basics + choice).questions.filter { it.countryIds == listOf(choice.itemId) }
        val question = questions.single() as Question.MultipleChoice
        assertEquals(listOf("Queue", "Stack", "Heap", "Trie"), question.choices.map { it.label })
        assertEquals("Queue", question.choices.single { it.id == question.correctChoiceId }.label)
        assertTrue(question.distractorPoolsNearestFirst.all { it.isEmpty() })
        assertEquals("Which structure is FIFO?", question.prompt)
        assertEquals("FIFO means queue.", question.explanation)
        assertEquals(setOf(QuizFormat.MultipleChoice), PackQuizCatalog.formatsFor(choice, basics + choice))
    }

    @Test
    fun choiceCardsNeverFeedOtherCardsDistractors() {
        val choices = listOf(
            choiceCard("c1", "Zebra\nYak", "Zebra"),
            choiceCard("c2", "Walrus\nVole", "Walrus"),
        )
        val basics = elements.mapIndexed { index, (symbol, name) -> card(basic, "el$index", listOf(symbol, name)) }
        val catalog = PackQuizCatalog.build("1", basics, distractorOnlyCards = choices)
        val offered = catalog.questions.filterIsInstance<Question.MultipleChoice>().flatMap { it.distractorPoolsNearestFirst.flatten() }.map { it.label }
        assertTrue(offered.isNotEmpty())
        assertTrue(offered.none { it in setOf("Zebra", "Yak", "Walrus", "Vole") })
        val mixed = PackQuizCatalog.build("1", basics + choices).questions.filterIsInstance<Question.MultipleChoice>()
            .filter { it.distractorPoolsNearestFirst.any { pool -> pool.isNotEmpty() } }
            .flatMap { it.distractorPoolsNearestFirst.flatten() }.map { it.label }
        assertTrue(mixed.none { it in setOf("Zebra", "Yak", "Walrus", "Vole") })
    }

    @Test
    fun brokenChoiceCardsFallBackToFlip() {
        val noMatch = choiceCard("nomatch", "Queue\nStack", "Heap")
        val duplicate = choiceCard("dup", "Queue\nqueue\nStack", "Stack")
        val tooMany = choiceCard("many", "A1\nB2\nC3\nD4\nE5\nF6", "A1")
        listOf(noMatch, duplicate, tooMany).forEach { card ->
            assertEquals(setOf(QuizFormat.FlipAndGrade), PackQuizCatalog.formatsFor(card, listOf(card)), card.itemId)
        }
    }

    @Test
    fun flipOnlyCardsOnlyFlip() {
        val bertBasic = BertNoteTypes.Basic.toAnkiNotetype()
        val flipOnly = PackCardReader.read(
            "science",
            bertBasic,
            Note(1, "fo", bertBasic.id, listOf("Symbol for gold", "Au", "", "", "", "", """{"flipOnly":true}"""), emptyList()),
            0,
            "Science",
        )
        assertTrue(flipOnly.flipOnly)
        val basics = elements.mapIndexed { index, (symbol, name) -> card(basic, "el$index", listOf(symbol, name)) }
        val questions = PackQuizCatalog.build("1", basics + flipOnly).questions.filter { it.countryIds == listOf(flipOnly.itemId) }
        assertEquals(listOf(Question.FlipAndGrade::class), questions.map { it::class })
        assertEquals(setOf(QuizFormat.FlipAndGrade), PackQuizCatalog.formatsFor(flipOnly, basics + flipOnly))
        val choiceFlipOnly = choiceCard("cfo", "Queue\nStack", "Queue", meta = """{"flipOnly":true}""")
        assertEquals(setOf(QuizFormat.FlipAndGrade), PackQuizCatalog.formatsFor(choiceFlipOnly, listOf(choiceFlipOnly)))
        val invalidMeta = choiceCard("bad", "Queue\nStack", "Queue", meta = "{not json")
        assertFalse(invalidMeta.flipOnly)
        assertEquals(setOf(QuizFormat.MultipleChoice), PackQuizCatalog.formatsFor(invalidMeta, listOf(invalidMeta)))
    }

    @Test
    fun engineAcceptsTwoToFiveAuthoredChoicesAndRemovesIncorrectOnlyFromFourUp() {
        val labels = listOf("Queue", "Stack", "Heap", "Trie", "Deque")
        (2..5).forEach { count ->
            val card = choiceCard("c$count", labels.take(count).joinToString("\n"), "Queue")
            val engine = QuizEngine(PackQuizCatalog.build("1", listOf(card)), { 0L }, { 0L }, { 0 }, allowUnapprovedContent = true)
            val state = engine.startRun("run$count", QuizConfiguration(QuizDuration.Unlimited, PlayScope.Pack("science")), 3L, emptyList())
            val question = state.activeQuestion.question as Question.MultipleChoice
            assertEquals(labels.take(count).toSet(), state.activeQuestion.orderedChoices.map { it.label }.toSet())
            assertEquals(count >= 4, HintType.RemoveIncorrectOptions in question.supportedHints, "$count choices")
            if (count >= 4) {
                val hinted = engine.chooseHint(engine.openHintChooser(state), HintType.RemoveIncorrectOptions)
                assertTrue(hinted.activeQuestion.removedChoiceIds.isNotEmpty())
                assertTrue(question.correctChoiceId !in hinted.activeQuestion.removedChoiceIds)
            }
            val answered = engine.answerQuestion(state, SubmittedAnswer.ChoiceAnswer(question.correctChoiceId))
            assertTrue(answered.attempts.single().correct)
        }
    }

    @Test
    fun flipTimerStopsOnceTheAnswerIsRevealed() {
        val cards = listOf(card(basic, "only", listOf("What is love?", "Baby don't hurt me, don't hurt me, no more, a long answer")))
        var now = 0L
        val engine = QuizEngine(PackQuizCatalog.build("1", cards), { now }, { 0L }, { 0 }, allowUnapprovedContent = true)
        val started = engine.startRun("run", QuizConfiguration(QuizDuration.Unlimited, PlayScope.Pack("science")), 7L, emptyList())
        now = 5_000
        val revealed = engine.revealFlipAnswer(started)
        assertTrue(revealed.activeQuestion.answerRevealed)
        val remainingAtReveal = revealed.activeQuestion.questionRemainingMillis
        assertEquals(QuizEngine.FLIP_AND_GRADE_WINDOW_MILLIS - 5_000, remainingAtReveal)
        now = 60_000
        val later = engine.advanceClocks(revealed)
        assertEquals(remainingAtReveal, later.activeQuestion.questionRemainingMillis)
        assertEquals(5_000, later.activeQuestion.questionElapsedMillis)
        val answered = engine.answerQuestion(later, SubmittedAnswer.SelfGrade(remembered = true))
        assertTrue(answered.attempts.single().correct)
    }

    private fun standardNotetype(name: String, fields: List<String>, templates: List<CardTemplate>) = Notetype(
        id = name.hashCode().toLong(),
        name = name,
        kind = NotetypeKind.Standard,
        fields = fields.mapIndexed { index, field -> NotetypeField(field, index) },
        templates = templates,
        css = "",
    )

    private fun choiceCard(guid: String, choices: String, correct: String, explanation: String = "", meta: String = ""): PackCard {
        val notetype = BertNoteTypes.Choice.toAnkiNotetype()
        val fields = listOf("Which structure is FIFO?", choices, correct, explanation, "", "", meta)
        return PackCardReader.read("science", notetype, Note(1, guid, notetype.id, fields, emptyList()), 0, "Science")
    }

    private fun card(notetype: Notetype, guid: String, fields: List<String>, ordinal: Int = 0): PackCard =
        PackCardReader.read("science", notetype, Note(1, guid, notetype.id, fields, emptyList()), ordinal, "Science")

    private val elements = listOf(
        "H" to "Hydrogen", "He" to "Helium", "Li" to "Lithium", "Be" to "Beryllium",
        "B" to "Boron", "C" to "Carbon", "N" to "Nitrogen", "O" to "Oxygen",
    )
}
