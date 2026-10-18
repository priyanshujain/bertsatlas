package app.bertsatlas.cli

import app.bertsatlas.anki.collection.renderCard
import app.bertsatlas.anki.model.Card
import app.bertsatlas.anki.model.CardData
import app.bertsatlas.anki.model.RevlogEntry
import app.bertsatlas.domain.CardMastery
import app.bertsatlas.domain.fsrs.CardScheduler
import app.bertsatlas.domain.fsrs.Fsrs
import app.bertsatlas.domain.fsrs.MemoryFromHistory
import app.bertsatlas.domain.fsrs.SchedulerTiming
import app.bertsatlas.domain.fsrs.StudyPreset
import app.bertsatlas.domain.fsrs.StudyQueue
import app.bertsatlas.domain.fsrs.memoryState
import app.bertsatlas.domain.model.MasteryLevel
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt

object StatsCommands {
    fun stats(context: Context): Output {
        val packId = context.arguments.positional(0, "pack")
        return context.library.read(packId) { pack ->
            val progress = PackProgress.read(pack, context.environment.nowMillis())
            val cards = progress.cards
            val states = cards.groupingBy { CardsCommands.cardState(it) }.eachCount()
            val levels = cards.groupingBy { CardMastery.level(it, progress.historyOf(it)) }.eachCount()
            val graded = progress.revlog.filter { it.ease > 0 }
            val startOfToday = (progress.timing.nextDayAtSecs - SECONDS_PER_DAY) * 1000
            val thirtyDaysAgo = progress.timing.nowMillis - 30L * SECONDS_PER_DAY * 1000
            val recentReviews = graded.filter { it.id >= thirtyDaysAgo && it.kind == REVLOG_REVIEW }
            val retention = recentReviews.takeIf { it.isNotEmpty() }?.let { reviews -> reviews.count { it.ease > 1 }.toDouble() / reviews.size }
            val retrievabilities = cards.mapNotNull { progress.retrievability(it) }
            val averageRetrievability = retrievabilities.takeIf { it.isNotEmpty() }?.average()
            val due = progress.queue.counts
            val json = buildJsonObject {
                put("pack", packId)
                put("title", pack.manifest.title)
                put("notes", cards.map { it.noteId }.toSet().size)
                put("cards", cards.size)
                putJsonObject("states") {
                    listOf("new", "learning", "relearning", "review", "suspended", "buried").forEach { put(it, states[it] ?: 0) }
                }
                putJsonObject("due") {
                    put("new", due.newCards)
                    put("learning", due.learning)
                    put("reviews", due.reviews)
                    put("total", due.total)
                }
                putJsonObject("mastery") {
                    MasteryLevel.entries.forEach { put(it.name.lowercase(), levels[it] ?: 0) }
                }
                putJsonObject("reviews") {
                    put("total", graded.size)
                    put("today", graded.count { it.id >= startOfToday })
                    put("last30Days", graded.count { it.id >= thirtyDaysAgo })
                    retention?.let { put("retention30Days", it.rounded()) }
                }
                averageRetrievability?.let { put("averageRetrievability", it.rounded()) }
            }
            val text = buildString {
                appendLine("${pack.manifest.title} ($packId)")
                appendLine("${cards.size} cards: " + listOf("new", "learning", "relearning", "review", "suspended").joinToString(", ") { "${states[it] ?: 0} $it" })
                appendLine("Due today: ${due.total} (${due.newCards} new, ${due.learning} learning, ${due.reviews} reviews)")
                appendLine("Mastery: " + MasteryLevel.entries.joinToString(", ") { "${levels[it] ?: 0} ${it.name.lowercase()}" })
                appendLine("Reviews: ${graded.size} total, ${graded.count { it.id >= startOfToday }} today")
                retention?.let { appendLine("Retention over 30 days: ${(it * 100).roundToInt()}%") }
                averageRetrievability?.let { append("Average recall chance: ${(it * 100).roundToInt()}%") }
            }.trimEnd()
            Output(json, text)
        }
    }

    fun weak(context: Context): Output {
        val packId = context.arguments.positional(0, "pack")
        val limit = context.arguments.intValue("limit", DEFAULT_WEAK_LIMIT)
        return context.library.read(packId) { pack ->
            val progress = PackProgress.read(pack, context.environment.nowMillis())
            val withRecall = progress.cards.mapNotNull { card -> progress.retrievability(card)?.let { card to it } }
            val lowest = withRecall.sortedBy { it.second }.take(limit).map { (card, recall) -> weakCard(pack, progress, card, recall) }
            val mostLapses = progress.cards
                .filter { it.scheduling.lapses > 0 || progress.historyOf(it).any { entry -> entry.ease == 1 } }
                .sortedWith(compareByDescending<Card> { it.scheduling.lapses }.thenByDescending { card -> progress.historyOf(card).count { it.ease == 1 } })
                .take(limit)
                .map { card -> weakCard(pack, progress, card, progress.retrievability(card)) }
            val json = buildJsonObject {
                put("pack", packId)
                put("lowestRetrievability", JsonArray(lowest))
                put("mostLapses", JsonArray(mostLapses))
            }
            val text = buildString {
                appendLine("Lowest recall chance:")
                lowest.forEach { appendLine("  ${percent(it)}  ${it.string("prompt")}  (note ${it.long("noteId")})") }
                if (lowest.isEmpty()) appendLine("  none reviewed yet")
                appendLine("Most forgotten:")
                mostLapses.forEach { appendLine("  ${it.long("lapses")} lapses, ${it.long("forgotten")} misses  ${it.string("prompt")}  (note ${it.long("noteId")})") }
                if (mostLapses.isEmpty()) append("  none forgotten yet")
            }.trimEnd()
            Output(json, text)
        }
    }

    private fun weakCard(pack: OpenPack, progress: PackProgress, card: Card, recall: Double?): JsonObject {
        val note = pack.collection.note(card.noteId)
        val history = progress.historyOf(card)
        val data = CardData.decode(card.scheduling.data)
        return buildJsonObject {
            put("noteId", card.noteId)
            note?.let { put("guid", it.guid) }
            put("cardId", card.id)
            put("prompt", pack.collection.renderCard(card)?.let { readableText(it.question).take(PROMPT_LENGTH) }.orEmpty())
            recall?.let { put("retrievability", it.rounded()) }
            data.stability?.let { put("stability", it.toDouble().rounded()) }
            put("lapses", card.scheduling.lapses)
            put("reviews", history.count { it.ease > 0 })
            put("forgotten", history.count { it.ease == 1 })
        }
    }

    private fun percent(card: JsonObject): String =
        (card["retrievability"] as? JsonPrimitive)?.doubleOrNull?.let { "${(it * 100).roundToInt()}%" } ?: "?"

    private fun Double.rounded(): Double = (this * 1000).roundToInt() / 1000.0

    private const val SECONDS_PER_DAY = 86_400L
    private const val REVLOG_REVIEW = 1
    private const val DEFAULT_WEAK_LIMIT = 20
    private const val PROMPT_LENGTH = 80
}

class PackProgress private constructor(
    private val pack: OpenPack,
    val cards: List<Card>,
    val revlog: List<RevlogEntry>,
    val timing: SchedulerTiming,
    val queue: StudyQueue,
) {
    private val historyByCard = revlog.groupBy { it.cardId }
    private val presets = mutableMapOf<Long, StudyPreset>()

    fun historyOf(card: Card): List<RevlogEntry> = historyByCard[card.id].orEmpty()

    fun retrievability(card: Card): Double? {
        if (card.scheduling.type == CardScheduler.TYPE_NEW) return null
        val preset = presetFor(card.deckId)
        val history = historyOf(card)
        val withMemory = CardScheduler(preset).withMemoryState(card, timing, history)
        val data = CardData.decode(withMemory.scheduling.data)
        val memory = data.memoryState() ?: return null
        val lastReview = data.lastReviewSecs ?: MemoryFromHistory.lastReviewSecs(history) ?: return null
        return Fsrs(preset.fsrsParameters).retrievability(memory, timing.daysSince(lastReview).toFloat()).toDouble()
    }

    private fun presetFor(deckId: Long): StudyPreset {
        val deck = pack.collection.deck(deckId) ?: pack.rootDeck
        return presets.getOrPut(deck.configId) {
            val config = pack.collection.deckConfig(deck.configId) ?: pack.collection.deckConfig(pack.rootDeck.configId) ?: pack.collection.deckConfigs().first()
            StudyPreset.from(config, deck)
        }
    }

    companion object {
        fun read(pack: OpenPack, nowMillis: Long): PackProgress {
            val collection = pack.collection
            val cards = collection.cardsInDecks(pack.deckIds(null))
            val cardIds = cards.map { it.id }.toSet()
            val revlog = collection.revlog().filter { it.cardId in cardIds }
            val timing = SchedulerTiming.forCollection(collection.creationSecs, collection.configuration(), nowMillis, TimeZone.currentSystemDefault())
            val config = collection.deckConfig(pack.rootDeck.configId) ?: collection.deckConfigs().first()
            val queue = StudyQueue.build(cards, StudyPreset.from(config, pack.rootDeck), collection.studiedToday(pack.rootDeck.id, timing.today), timing)
            return PackProgress(pack, cards, revlog, timing, queue)
        }
    }
}
