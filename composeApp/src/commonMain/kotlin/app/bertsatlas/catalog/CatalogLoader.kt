package app.bertsatlas.catalog

import app.bertsatlas.domain.CatalogProvider
import app.bertsatlas.domain.model.AssetLicenseRecord
import app.bertsatlas.domain.model.CapitalArrangement
import app.bertsatlas.domain.model.Catalog
import app.bertsatlas.domain.model.Choice
import app.bertsatlas.domain.model.Continent
import app.bertsatlas.domain.model.CountryRecord
import app.bertsatlas.domain.model.DifficultyTier
import app.bertsatlas.domain.model.EditorialReviewStatus
import app.bertsatlas.domain.model.FactRecord
import app.bertsatlas.domain.model.HintType
import app.bertsatlas.domain.model.Question
import app.bertsatlas.domain.model.QuizTopic
import app.bertsatlas.domain.model.Skill
import app.bertsatlas.domain.model.SourceRecord
import berts_atlas.composeapp.generated.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class BundledCatalogProvider(
    private val readCatalogText: suspend () -> String = {
        Res.readBytes("files/catalog/catalog-v1.json").decodeToString()
    },
) : CatalogProvider {
    private var loadedCatalog: Catalog? = null

    override suspend fun loadCatalog(): Catalog {
        loadedCatalog?.let { return it }
        return CatalogLoader.load(readCatalogText()).also { loadedCatalog = it }
    }
}

object CatalogLoader {
    fun load(json: String): Catalog {
        val source = decoder.decodeFromString<SourceCatalog>(json)
        val countriesById = source.countries.associateBy(SourceCountry::id)
        val factsByCountry = source.facts
            .flatMap { fact -> fact.countryIds.map { it to fact } }
            .groupBy({ it.first }, { it.second })
        val similarityGroupsByCountry = source.similarityGroups
            .flatMap { group -> group.countryIds.map { it to group.id } }
            .groupBy({ it.first }, { it.second })
        val domainCountries = source.countries.map { country ->
            country.toDomain(similarityGroupsByCountry[country.id].orEmpty())
        }
        val generatedQuestions = source.countries.flatMapIndexed { index, country ->
            generatedQuestions(
                country = country,
                countryIndex = index,
                allCountries = source.countries,
                similarityGroupIds = similarityGroupsByCountry[country.id].orEmpty(),
                similarityGroups = source.similarityGroups,
                relatedFacts = factsByCountry[country.id].orEmpty(),
            )
        }
        val curatedQuestions = source.curatedQuestions.map { question ->
            question.toDomain(countriesById)
        }
        return Catalog(
            schemaVersion = source.schemaVersion,
            contentVersion = source.catalogVersion,
            countries = domainCountries,
            questions = generatedQuestions + curatedQuestions,
            facts = source.facts.map { it.toDomain() },
            sources = source.sources.map { it.toDomain() },
            assetLicenses = source.assets.map { it.toDomain() },
        )
    }

    private fun generatedQuestions(
        country: SourceCountry,
        countryIndex: Int,
        allCountries: List<SourceCountry>,
        similarityGroupIds: List<String>,
        similarityGroups: List<SourceSimilarityGroup>,
        relatedFacts: List<SourceFact>,
    ): List<Question> {
        val distractors = chooseDistractors(
            country = country,
            allCountries = allCountries,
            similarityGroupIds = similarityGroupIds,
            similarityGroups = similarityGroups,
        )
        val choices = listOf(country) + distractors
        val capitalChoices = buildList {
            add(country)
            addAll(distractors)
            addAll(allCountries)
        }
            .distinctBy { it.capital.acceptedAnswer.lowercase() }
            .take(4)
        require(capitalChoices.size == 4) { "Not enough unique capital choices for ${country.id}" }
        val difficulty = generatedDifficulty(countryIndex)
        val continentHint = country.region.toContinent().displayName()
        val factualClue = relatedFacts.firstOrNull()?.text
        val commonHintText = buildMap {
            put(HintType.RevealContinent, continentHint)
            factualClue?.let { put(HintType.FunnyFactualClue, it) }
        }
        val factualHint = if (factualClue == null) emptySet() else setOf(HintType.FunnyFactualClue)
        val sharedHints = setOf(HintType.RevealContinent) + factualHint
        val flagToCountry = Question.MultipleChoice(
            id = "generated-flag-to-country-${country.id}",
            prompt = "Which country does this flag belong to?",
            countryIds = listOf(country.id),
            skill = Skill.FlagRecognition,
            topic = QuizTopic.MostlyFlags,
            difficulty = difficulty,
            explanation = "This is the flag of ${country.displayName}.",
            supportedHints = sharedHints + HintType.RemoveIncorrectOptions,
            hintText = commonHintText,
            choices = choices.map { Choice(id = it.id, label = it.displayName) },
            correctChoiceId = country.id,
            promptAssetId = country.flagAssetId,
        )
        val countryToFlag = Question.MultipleChoice(
            id = "generated-country-to-flag-${country.id}",
            prompt = "Which flag belongs to ${country.displayName}?",
            countryIds = listOf(country.id),
            skill = Skill.CountryToFlag,
            topic = QuizTopic.MostlyFlags,
            difficulty = difficulty,
            explanation = "This is the flag of ${country.displayName}.",
            supportedHints = sharedHints + HintType.RemoveIncorrectOptions,
            hintText = commonHintText,
            choices = choices.map {
                Choice(id = it.id, label = it.displayName, assetId = it.flagAssetId)
            },
            correctChoiceId = country.id,
        )
        val letterBank = Question.LetterBank(
            id = "generated-letter-bank-${country.id}",
            prompt = "Spell the country shown by this flag.",
            countryIds = listOf(country.id),
            skill = Skill.FlagRecognition,
            topic = QuizTopic.MostlyFlags,
            difficulty = difficulty,
            explanation = "The flag belongs to ${country.displayName}.",
            supportedHints = sharedHints + setOf(
                HintType.RevealCorrectLetter,
                HintType.RemoveIncorrectOptions,
            ),
            hintText = commonHintText,
            displayAnswer = country.displayName,
            acceptedAliases = country.acceptedAliases,
            promptAssetId = country.flagAssetId,
            decoyLetters = decoyLetters(country.id, countryIndex),
        )
        val countryToCapital = Question.MultipleChoice(
            id = "generated-country-to-capital-${country.id}",
            prompt = "What is the capital of ${country.displayName}?",
            countryIds = listOf(country.id),
            skill = Skill.CapitalRecall,
            topic = QuizTopic.Capitals,
            difficulty = difficulty,
            explanation = country.capital.note ?: "${country.capital.acceptedAnswer} is the capital answer used for ${country.displayName}.",
            supportedHints = sharedHints + HintType.RemoveIncorrectOptions,
            hintText = commonHintText,
            choices = capitalChoices.map { Choice(it.id, it.capital.acceptedAnswer) },
            correctChoiceId = country.id,
        )
        val capitalToCountry = Question.MultipleChoice(
            id = "generated-capital-to-country-${country.id}",
            prompt = "${country.capital.acceptedAnswer} is the capital answer for which country?",
            countryIds = listOf(country.id),
            skill = Skill.CapitalRecognition,
            topic = QuizTopic.Capitals,
            difficulty = difficulty,
            explanation = country.capital.note ?: "${country.capital.acceptedAnswer} is the capital answer used for ${country.displayName}.",
            supportedHints = sharedHints + HintType.RemoveIncorrectOptions,
            hintText = commonHintText,
            choices = choices.map { Choice(it.id, it.displayName) },
            correctChoiceId = country.id,
        )
        return listOf(flagToCountry, countryToFlag, letterBank, countryToCapital, capitalToCountry)
    }

    private fun chooseDistractors(
        country: SourceCountry,
        allCountries: List<SourceCountry>,
        similarityGroupIds: List<String>,
        similarityGroups: List<SourceSimilarityGroup>,
    ): List<SourceCountry> {
        val countryIds = buildList {
            similarityGroups
                .filter { it.id in similarityGroupIds }
                .flatMapTo(this) { it.countryIds }
            allCountries.filter { it.region == country.region }.mapTo(this) { it.id }
            allCountries.mapTo(this) { it.id }
        }
            .asSequence()
            .filter { it != country.id }
            .distinct()
            .take(3)
            .toList()
        val countriesById = allCountries.associateBy(SourceCountry::id)
        return countryIds.map { countriesById.getValue(it) }
    }

    private fun SourceCuratedQuestion.toDomain(
        countriesById: Map<String, SourceCountry>,
    ): Question {
        require(format == "multiple_choice") { "Unsupported curated question format: $format" }
        val domainChoices = choices.mapIndexed { index, choice ->
            Choice(id = "$id:choice:$index", label = choice)
        }
        val answerIndex = choices.indexOf(answer)
        require(answerIndex >= 0) { "Curated question $id does not contain its answer" }
        val primaryCountry = countryIds.firstNotNullOfOrNull(countriesById::get)
        val mappedHints = supportedHints.mapTo(linkedSetOf()) { it.toHintType() }
        val hintText = buildMap {
            if (HintType.RevealContinent in mappedHints && primaryCountry != null) {
                put(HintType.RevealContinent, primaryCountry.region.toContinent().displayName())
            }
            if (HintType.FunnyFactualClue in mappedHints) {
                put(HintType.FunnyFactualClue, explanation)
            }
        }
        return Question.MultipleChoice(
            id = id,
            prompt = prompt,
            countryIds = countryIds,
            skill = if (topic == "flag_symbolism") Skill.FlagSymbolism else Skill.MixedGeography,
            topic = QuizTopic.MixedGeography,
            difficulty = difficulty.toDifficulty(),
            explanation = explanation,
            supportedHints = mappedHints,
            hintText = hintText,
            choices = domainChoices,
            correctChoiceId = domainChoices[answerIndex].id,
        )
    }

    private fun SourceCountry.toDomain(similarityGroupIds: List<String>): CountryRecord = CountryRecord(
        id = id,
        displayName = displayName,
        acceptedAliases = acceptedAliases,
        continentIds = listOf(region.toContinent()),
        flagAssetId = flagAssetId,
        capitalArrangement = capital.toDomain(),
        similarityGroupIds = similarityGroupIds,
    )

    private fun SourceCapital.toDomain(): CapitalArrangement = when {
        kind == "single" -> CapitalArrangement.Single(acceptedAnswer)
        names.size > 1 -> CapitalArrangement.Multiple(
            capitals = names,
            explanation = requireNotNull(note) { "An exceptional capital arrangement requires a note" },
        )
        else -> CapitalArrangement.Exception(
            explanation = note ?: "$acceptedAnswer has an exceptional capital arrangement.",
        )
    }

    private fun SourceFact.toDomain() = FactRecord(
        id = id,
        heading = heading,
        body = text,
        countryIds = countryIds,
        sourceIds = sourceIds,
        asOf = asOf,
        reviewStatus = review.state.toReviewStatus(),
    )

    private fun SourceReference.toDomain() = SourceRecord(
        id = id,
        title = title,
        url = url,
        revision = revision,
        accessedOn = retrievedAt,
    )

    private fun SourceAsset.toDomain() = AssetLicenseRecord(
        assetId = id,
        sourceId = sourceId,
        licenseName = license,
        modified = modified,
        checksum = sha256,
    )

    private fun String.toContinent(): Continent = when (this) {
        "africa" -> Continent.Africa
        "asia" -> Continent.Asia
        "europe" -> Continent.Europe
        "north_america" -> Continent.NorthAmerica
        "south_america" -> Continent.SouthAmerica
        "oceania" -> Continent.Oceania
        else -> error("Unknown catalog region: $this")
    }

    private fun Continent.displayName(): String = when (this) {
        Continent.Africa -> "Africa"
        Continent.Asia -> "Asia"
        Continent.Europe -> "Europe"
        Continent.NorthAmerica -> "North America"
        Continent.SouthAmerica -> "South America"
        Continent.Oceania -> "Oceania"
        Continent.Antarctica -> "Antarctica"
    }

    private fun String.toDifficulty(): DifficultyTier = when (this) {
        "casual" -> DifficultyTier.Casual
        "tricky" -> DifficultyTier.Tricky
        "brutal" -> DifficultyTier.Brutal
        else -> error("Unknown question difficulty: $this")
    }

    private fun String.toHintType(): HintType = when (this) {
        "reveal_letter" -> HintType.RevealCorrectLetter
        "remove_choices", "remove_decoys" -> HintType.RemoveIncorrectOptions
        "reveal_continent" -> HintType.RevealContinent
        "factual_clue" -> HintType.FunnyFactualClue
        else -> error("Unknown hint type: $this")
    }

    private fun String.toReviewStatus(): EditorialReviewStatus = when (this) {
        "draft" -> EditorialReviewStatus.Draft
        "fact_checked" -> EditorialReviewStatus.FactChecked
        "copy_checked" -> EditorialReviewStatus.CopyChecked
        "approved" -> EditorialReviewStatus.Approved
        else -> error("Unknown editorial review state: $this")
    }

    private fun generatedDifficulty(index: Int): DifficultyTier = when (index % 3) {
        0 -> DifficultyTier.Casual
        1 -> DifficultyTier.Tricky
        else -> DifficultyTier.Brutal
    }

    private fun decoyLetters(countryId: String, index: Int): List<Char> = List(3) { offset ->
        DECOY_ALPHABET[(countryId.hashCode() + index + offset * 11).mod(DECOY_ALPHABET.length)]
    }

    private const val DECOY_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val decoder = Json { ignoreUnknownKeys = true }
}

@Serializable
private data class SourceCatalog(
    val schemaVersion: Int,
    val catalogVersion: String,
    val sources: List<SourceReference>,
    val countries: List<SourceCountry>,
    val facts: List<SourceFact>,
    val curatedQuestions: List<SourceCuratedQuestion>,
    val similarityGroups: List<SourceSimilarityGroup>,
    val assets: List<SourceAsset>,
)

@Serializable
private data class SourceReference(
    val id: String,
    val title: String,
    val url: String,
    val retrievedAt: String,
    val revision: String,
)

@Serializable
private data class SourceCountry(
    val id: String,
    val displayName: String,
    val acceptedAliases: List<String>,
    val region: String,
    val capital: SourceCapital,
    val flagAssetId: String,
)

@Serializable
private data class SourceCapital(
    val kind: String,
    val names: List<String>,
    val acceptedAnswer: String,
    val note: String?,
)

@Serializable
private data class SourceFact(
    val id: String,
    val countryIds: List<String>,
    val heading: String,
    val text: String,
    val sourceIds: List<String>,
    val asOf: String?,
    val review: SourceReview,
)

@Serializable
private data class SourceCuratedQuestion(
    val id: String,
    val topic: String,
    val difficulty: String,
    val format: String,
    val prompt: String,
    val answer: String,
    val choices: List<String>,
    val countryIds: List<String>,
    val supportedHints: List<String>,
    val explanation: String,
)

@Serializable
private data class SourceSimilarityGroup(
    val id: String,
    val countryIds: List<String>,
)

@Serializable
private data class SourceAsset(
    val id: String,
    val sourceId: String,
    val license: String,
    val sha256: String,
    val modified: Boolean,
)

@Serializable
private data class SourceReview(val state: String)
