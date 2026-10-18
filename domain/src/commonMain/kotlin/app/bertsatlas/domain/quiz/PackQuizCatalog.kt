package app.bertsatlas.domain.quiz

import app.bertsatlas.domain.SeededRandomSource
import app.bertsatlas.domain.model.Catalog
import app.bertsatlas.domain.model.Choice
import app.bertsatlas.domain.model.DifficultyTier
import app.bertsatlas.domain.model.EditorialReviewStatus
import app.bertsatlas.domain.model.FactRecord
import app.bertsatlas.domain.model.HintType
import app.bertsatlas.domain.model.Question
import app.bertsatlas.domain.model.Skill
import app.bertsatlas.domain.model.SourceRecord
import app.bertsatlas.domain.pack.EditorialStatus
import app.bertsatlas.domain.shuffled

enum class QuizFormat { MultipleChoice, LetterBank, FlipAndGrade }

object PackQuizCatalog {
    const val MINIMUM_DISTRACTORS = 3
    const val MAX_LETTER_BANK_LETTERS = 18
    const val MAX_LETTER_BANK_WORDS = 4
    private const val POOL_SAMPLE_SIZE = 12
    private const val SAMPLE_HEADROOM = 4
    private const val CATALOG_SCHEMA_VERSION = 1

    private val letterBankCharacters = Regex("^[\\p{L} '’.-]+$")
    private val sourceWithUrl = Regex("^(.*?)\\s*\\((https?://[^)\\s]+)\\)\\s*$")
    private val decoyAlphabets = mapOf(
        AnswerScript.Latin to "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        AnswerScript.Greek to "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩ",
        AnswerScript.Cyrillic to "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЫЭЮЯ",
    )

    fun build(contentVersion: String, cards: List<PackCard>, distractorOnlyCards: List<PackCard> = emptyList()): Catalog {
        val distractors = DistractorIndex(cards + distractorOnlyCards)
        val questions = cards.flatMap { card -> questionsFor(card, distractors) }
        val sourcesByItem = cards.associate { card -> card.itemId to sourceRecords(card) }
        return Catalog(
            schemaVersion = CATALOG_SCHEMA_VERSION,
            contentVersion = contentVersion,
            countries = emptyList(),
            questions = questions,
            facts = cards.mapNotNull { card -> factRecord(card, sourcesByItem.getValue(card.itemId)) },
            sources = sourcesByItem.values.flatten(),
            assetLicenses = emptyList(),
        )
    }

    fun formatsFor(card: PackCard, cards: List<PackCard>): Set<QuizFormat> =
        questionsFor(card, DistractorIndex(cards)).mapTo(linkedSetOf()) { question ->
            when (question) {
                is Question.MultipleChoice -> QuizFormat.MultipleChoice
                is Question.LetterBank -> QuizFormat.LetterBank
                is Question.FlipAndGrade -> QuizFormat.FlipAndGrade
            }
        }

    fun isLetterBankEligible(answer: String): Boolean {
        val text = answer.trim()
        if (!letterBankCharacters.matches(text)) return false
        val letters = text.count { it.isLetter() }
        if (letters < 2 || letters > MAX_LETTER_BANK_LETTERS) return false
        if (text.split(Regex("\\s+")).size > MAX_LETTER_BANK_WORDS) return false
        return AnswerShapes.isSingleScript(text) && AnswerShapes.scriptOf(text) in decoyAlphabets
    }

    private fun questionsFor(card: PackCard, distractors: DistractorIndex): List<Question> {
        val fieldHints = fieldHintTypes(card)
        val fieldHintText = fieldHintText(card)
        val answer = card.shortAnswer
        val pools = answer?.let { distractors.poolsFor(card, it) }.orEmpty()
        val distinctDistractorCount = pools.flatten().distinctBy { ChoiceLabels.key(it.label) }.size
        val multipleChoice = if (answer != null && distinctDistractorCount >= MINIMUM_DISTRACTORS) {
            Question.MultipleChoice(
                id = "card-choice-${card.itemId}",
                prompt = card.frontText,
                countryIds = listOf(card.itemId),
                skill = Skill.CardRecall,
                packId = card.packId,
                difficulty = if (pools.first().isNotEmpty()) DifficultyTier.Tricky else DifficultyTier.Casual,
                supportedHints = fieldHints + HintType.RemoveIncorrectOptions,
                hintText = fieldHintText,
                choices = listOf(Choice(id = choiceId(card), label = answer)),
                correctChoiceId = choiceId(card),
                distractorPoolsNearestFirst = pools,
                promptHtml = card.frontHtml,
            )
        } else {
            null
        }
        val letterBank = if (answer != null && isLetterBankEligible(answer)) {
            Question.LetterBank(
                id = "card-letters-${card.itemId}",
                prompt = card.frontText,
                countryIds = listOf(card.itemId),
                skill = Skill.CardRecall,
                packId = card.packId,
                difficulty = DifficultyTier.Brutal,
                supportedHints = fieldHints + HintType.RevealCorrectLetter + HintType.RemoveIncorrectOptions,
                hintText = fieldHintText,
                displayAnswer = answer,
                acceptedAliases = card.aliases,
                decoyLetters = decoyLetters(card.itemId, answer),
                promptHtml = card.frontHtml,
            )
        } else {
            null
        }
        val playable = listOfNotNull(multipleChoice, letterBank)
        if (playable.isNotEmpty()) return playable
        return listOf(
            Question.FlipAndGrade(
                id = "card-flip-${card.itemId}",
                prompt = card.frontText,
                countryIds = listOf(card.itemId),
                skill = Skill.CardRecall,
                packId = card.packId,
                difficulty = DifficultyTier.Tricky,
                supportedHints = fieldHints,
                hintText = fieldHintText,
                promptHtml = card.frontHtml,
                answerHtml = card.backHtml,
                answerText = answer ?: card.backText,
            ),
        )
    }

    private fun fieldHintTypes(card: PackCard): Set<HintType> = buildSet {
        if (card.packClue != null) add(HintType.RevealContinent)
        if (card.hint != null) add(HintType.FunnyFactualClue)
    }

    private fun fieldHintText(card: PackCard): Map<HintType, String> = buildMap {
        card.packClue?.let { put(HintType.RevealContinent, it) }
        card.hint?.let { put(HintType.FunnyFactualClue, it) }
    }

    private fun choiceId(card: PackCard): String = "choice-${card.itemId}"

    private fun decoyLetters(itemId: String, answer: String): List<Char> {
        val alphabet = decoyAlphabets.getValue(AnswerShapes.scriptOf(answer))
        val answerLetters = answer.uppercase().toSet()
        val candidates = alphabet.filter { it !in answerLetters }.ifEmpty { alphabet }.toList()
        val shuffled = candidates.shuffled(SeededRandomSource, seedFor(itemId))
        return List(3) { index -> shuffled.first[index % shuffled.first.size] }
    }

    private fun factRecord(card: PackCard, sources: List<SourceRecord>): FactRecord? {
        val fact = card.fact ?: return null
        return FactRecord(
            id = "fact-${card.itemId}",
            heading = card.shortAnswer ?: card.frontText.take(60),
            body = fact,
            countryIds = listOf(card.itemId),
            sourceIds = sources.map { it.id },
            reviewStatus = card.editorialStatus?.toReviewStatus() ?: EditorialReviewStatus.Approved,
        )
    }

    private fun sourceRecords(card: PackCard): List<SourceRecord> = card.sources.mapIndexed { index, line ->
        val match = sourceWithUrl.matchEntire(line)
        val url = match?.groupValues?.get(2) ?: line.takeIf { it.startsWith("http") }.orEmpty()
        val title = match?.groupValues?.get(1)?.ifBlank { null } ?: line
        SourceRecord(id = "source-${card.itemId}-$index", title = title, url = url)
    }

    private fun EditorialStatus.toReviewStatus(): EditorialReviewStatus = when (this) {
        EditorialStatus.Draft -> EditorialReviewStatus.Draft
        EditorialStatus.FactChecked -> EditorialReviewStatus.FactChecked
        EditorialStatus.CopyChecked -> EditorialReviewStatus.CopyChecked
        EditorialStatus.Approved -> EditorialReviewStatus.Approved
    }

    internal fun seedFor(itemId: String): Long = itemId.hashCode().toLong() * 2_654_435_761L + 1

    private class DistractorIndex(cards: List<PackCard>) {
        private val answerCards = cards.filter { it.shortAnswer != null }
        private val shapes = answerCards.associate { it.itemId to AnswerShapes.of(it.shortAnswer!!) }
        private val bySimilarGroup = answerCards
            .flatMap { card -> card.similarGroupIds.map { (card.packId to it) to card } }
            .groupBy({ it.first }, { it.second })
        private val bySectionAndShape = answerCards.groupBy { Triple(it.packId, it.section, shapes.getValue(it.itemId)) }
        private val byPackKindAndScript = answerCards.groupBy {
            val shape = shapes.getValue(it.itemId)
            Triple(it.packId, shape.kind, shape.script)
        }

        fun poolsFor(card: PackCard, answer: String): List<List<Choice>> {
            val shape = AnswerShapes.of(answer)
            val excluded = (card.aliases + answer).mapTo(mutableSetOf(), ChoiceLabels::key)
            val similar = card.similarGroupIds.flatMap { bySimilarGroup[card.packId to it].orEmpty() }
                .filter { other -> shapes.getValue(other.itemId).sameKindAndScript(shape) }
            val sectionExactShape = bySectionAndShape[Triple(card.packId, card.section, shape)].orEmpty()
            val sectionNearShape = (shape.lengthBand - 1..shape.lengthBand + 1)
                .filter { it != shape.lengthBand }
                .flatMap { band -> bySectionAndShape[Triple(card.packId, card.section, shape.copy(lengthBand = band))].orEmpty() }
            val pack = byPackKindAndScript[Triple(card.packId, shape.kind, shape.script)].orEmpty()
            val used = excluded.toMutableSet()
            var seed = seedFor(card.itemId)
            return listOf(similar, sectionExactShape, sectionNearShape, pack).map { candidates ->
                val sampled = sample(candidates, POOL_SAMPLE_SIZE * SAMPLE_HEADROOM, seed)
                seed = sampled.second
                sampled.first.asSequence()
                    .filter { other -> other.noteGuid != card.noteGuid }
                    .filter { other -> other.aliases.none { ChoiceLabels.key(it) in excluded } }
                    .filter { other -> used.add(ChoiceLabels.key(other.shortAnswer!!)) }
                    .take(POOL_SAMPLE_SIZE)
                    .map { other -> Choice(id = "choice-${other.itemId}", label = other.shortAnswer!!) }
                    .toList()
            }
        }

        private fun sample(candidates: List<PackCard>, count: Int, seed: Long): Pair<List<PackCard>, Long> {
            if (candidates.size <= count) return candidates.shuffled(SeededRandomSource, seed)
            val picked = linkedSetOf<Int>()
            var state = seed
            while (picked.size < count) {
                val random = SeededRandomSource.nextInt(state, candidates.size)
                state = random.nextState
                picked.add(random.value)
            }
            return picked.map { candidates[it] } to state
        }
    }
}

fun combineCatalogs(contentVersion: String, catalogs: List<Catalog>): Catalog = Catalog(
    schemaVersion = catalogs.maxOfOrNull { it.schemaVersion } ?: 1,
    contentVersion = contentVersion,
    countries = catalogs.flatMap { it.countries },
    questions = catalogs.flatMap { it.questions },
    facts = catalogs.flatMap { it.facts },
    sources = catalogs.flatMap { it.sources },
    assetLicenses = catalogs.flatMap { it.assetLicenses },
)
