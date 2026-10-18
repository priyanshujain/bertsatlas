package app.bertsatlas.domain.geography

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
import app.bertsatlas.domain.model.BuiltInPacks
import app.bertsatlas.domain.model.packId
import app.bertsatlas.domain.model.Skill

object GeographyCatalog {
    fun build(source: GeographyContent): Catalog {
        val countriesById = source.countries.associateBy(GeographyCountry::id)
        val similarityGroupsByCountry = source.similarityGroups
            .flatMap { group -> group.countryIds.map { it to group.id } }
            .groupBy({ it.first }, { it.second })
        val curatedCapitalCountryIds = source.curatedQuestions
            .filter { it.id.startsWith("mixed-capital-") }
            .flatMapTo(mutableSetOf()) { it.countryIds }
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
                hasCuratedCapitalQuestion = country.id in curatedCapitalCountryIds,
            )
        }
        val curatedQuestions = source.curatedQuestions.map { question -> question.toDomain(countriesById) }
        return Catalog(
            schemaVersion = source.schemaVersion,
            contentVersion = source.contentVersion,
            countries = domainCountries,
            questions = generatedQuestions + curatedQuestions,
            facts = source.facts.map { it.toDomain() },
            sources = source.sources,
            assetLicenses = source.assets,
        )
    }

    private fun generatedQuestions(
        country: GeographyCountry,
        countryIndex: Int,
        allCountries: List<GeographyCountry>,
        similarityGroupIds: List<String>,
        similarityGroups: List<GeographySimilarityGroup>,
        hasCuratedCapitalQuestion: Boolean,
    ): List<Question> {
        val similarDistractors = similarDistractors(
            country = country,
            allCountries = allCountries,
            similarityGroupIds = similarityGroupIds,
            similarityGroups = similarityGroups,
        )
        val distractorPools = fallbackDistractorPools(country, allCountries, similarDistractors)
        val similarCapitalDistractors = similarDistractors
            .filterNot { it.capital.acceptedAnswer.equals(country.capital.acceptedAnswer, ignoreCase = true) }
            .distinctBy { it.capital.acceptedAnswer.lowercase() }
        val difficulty = generatedDifficulty(countryIndex)
        val factualClue = country.flagClues.factualClue
        val capitalFactualClue = country.capitalClues.factualClue
        val commonHintText = buildMap {
            put(HintType.RevealContinent, country.flagClues.continent)
            factualClue?.let { put(HintType.FunnyFactualClue, it) }
        }
        val factualHint = if (factualClue == null) emptySet() else setOf(HintType.FunnyFactualClue)
        val sharedHints = setOf(HintType.RevealContinent) + factualHint
        val capitalHintText = buildMap {
            put(HintType.RevealContinent, country.capitalClues.continent)
            capitalFactualClue?.let { put(HintType.FunnyFactualClue, it) }
        }
        val capitalHints = setOf(HintType.RevealContinent) +
            if (capitalFactualClue == null) emptySet() else setOf(HintType.FunnyFactualClue)
        val flagToCountry = Question.MultipleChoice(
            id = "generated-flag-to-country-${country.id}",
            prompt = "Which country does this flag belong to?",
            countryIds = listOf(country.id),
            skill = Skill.FlagRecognition,
            packId = BuiltInPacks.FLAGS,
            difficulty = difficulty,
            explanation = "This is the flag of ${country.displayName}.",
            supportedHints = sharedHints + HintType.RemoveIncorrectOptions,
            hintText = commonHintText,
            choices = (listOf(country) + similarDistractors).map { Choice(id = it.id, label = it.displayName) },
            correctChoiceId = country.id,
            promptAssetId = country.flagAssetId,
            distractorPoolsNearestFirst = distractorPools.map { pool -> pool.map { Choice(id = it.id, label = it.displayName) } },
        )
        val countryToFlag = Question.MultipleChoice(
            id = "generated-country-to-flag-${country.id}",
            prompt = "Which flag belongs to ${country.displayName}?",
            countryIds = listOf(country.id),
            skill = Skill.CountryToFlag,
            packId = BuiltInPacks.FLAGS,
            difficulty = difficulty,
            explanation = "This is the flag of ${country.displayName}.",
            supportedHints = sharedHints + HintType.RemoveIncorrectOptions,
            hintText = commonHintText,
            choices = (listOf(country) + similarDistractors).map { it.flagChoice() },
            correctChoiceId = country.id,
            distractorPoolsNearestFirst = distractorPools.map { pool -> pool.map { it.flagChoice() } },
        )
        val letterBank = Question.LetterBank(
            id = "generated-letter-bank-${country.id}",
            prompt = "Spell the country shown by this flag.",
            countryIds = listOf(country.id),
            skill = Skill.FlagRecognition,
            packId = BuiltInPacks.FLAGS,
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
            packId = BuiltInPacks.CAPITALS,
            difficulty = difficulty,
            explanation = country.capital.note ?: "${country.capital.acceptedAnswer} is the capital of ${country.displayName}.",
            supportedHints = capitalHints + HintType.RemoveIncorrectOptions,
            hintText = capitalHintText,
            choices = (listOf(country) + similarCapitalDistractors).map { it.capitalChoice() },
            correctChoiceId = country.id,
            distractorPoolsNearestFirst = distractorPools.map { pool -> pool.map { it.capitalChoice() } },
        )
        val capitalToCountry = Question.MultipleChoice(
            id = "generated-capital-to-country-${country.id}",
            prompt = "${country.capital.acceptedAnswer} is the capital of which country?",
            countryIds = listOf(country.id),
            skill = Skill.CapitalRecognition,
            packId = BuiltInPacks.CAPITALS,
            difficulty = difficulty,
            explanation = country.capital.note ?: "${country.capital.acceptedAnswer} is the capital of ${country.displayName}.",
            supportedHints = capitalHints + HintType.RemoveIncorrectOptions,
            hintText = capitalHintText,
            choices = (listOf(country) + similarDistractors).map { Choice(it.id, it.displayName) },
            correctChoiceId = country.id,
            distractorPoolsNearestFirst = distractorPools.map { pool -> pool.map { Choice(it.id, it.displayName) } },
        )
        return buildList {
            add(flagToCountry)
            add(countryToFlag)
            add(letterBank)
            if (country.capital.questionEligible) {
                if (!hasCuratedCapitalQuestion) add(countryToCapital)
                add(capitalToCountry)
            }
        }
    }

    private fun similarDistractors(
        country: GeographyCountry,
        allCountries: List<GeographyCountry>,
        similarityGroupIds: List<String>,
        similarityGroups: List<GeographySimilarityGroup>,
    ): List<GeographyCountry> {
        val countriesById = allCountries.associateBy(GeographyCountry::id)
        return similarityGroups
            .filter { it.id in similarityGroupIds }
            .flatMap { it.countryIds }
            .filter { it != country.id }
            .distinct()
            .take(MAX_DISTRACTORS)
            .map { countriesById.getValue(it) }
    }

    private fun fallbackDistractorPools(
        country: GeographyCountry,
        allCountries: List<GeographyCountry>,
        similarDistractors: List<GeographyCountry>,
    ): List<List<GeographyCountry>> {
        val excluded = similarDistractors.map { it.id }.toSet() + country.id
        val candidates = allCountries.filterNot { it.id in excluded }
        val sameRegion = candidates.filter { it.region == country.region }
        val elsewhere = candidates.filter { it.region != country.region }
        return listOf(sameRegion, elsewhere)
    }

    private fun GeographyCountry.flagChoice() = Choice(id = id, label = displayName, assetId = flagAssetId)

    private fun GeographyCountry.capitalChoice() = Choice(id, capital.acceptedAnswer)

    private fun GeographyCuratedQuestion.toDomain(
        countriesById: Map<String, GeographyCountry>,
    ): Question {
        require(format == "multiple_choice") { "Unsupported curated question format: $format" }
        val domainChoices = choices.mapIndexed { index, choice ->
            Choice(id = "$id:choice:$index", label = choice)
        }
        val answerIndex = choices.indexOf(answer)
        require(answerIndex >= 0) { "Curated question $id does not contain its answer" }
        val curatedSkill = if (topic == "flag_symbolism") Skill.FlagSymbolism else Skill.MixedGeography
        val primaryCountry = countryIds.firstNotNullOfOrNull(countriesById::get)
        val mappedHints = supportedHints
            .mapTo(linkedSetOf()) { it.toHintType() }
            .apply {
                if (explanation.contains(answer, ignoreCase = true)) remove(HintType.FunnyFactualClue)
            }
        val hintText = buildMap {
            if (HintType.RevealContinent in mappedHints && primaryCountry != null) {
                put(HintType.RevealContinent, primaryCountry.capitalClues.continent)
            }
            if (HintType.FunnyFactualClue in mappedHints) {
                put(HintType.FunnyFactualClue, explanation)
            }
        }
        return Question.MultipleChoice(
            id = id,
            prompt = prompt,
            countryIds = countryIds,
            skill = curatedSkill,
            packId = curatedSkill.packId,
            difficulty = difficulty.toDifficulty(),
            explanation = explanation,
            supportedHints = mappedHints,
            hintText = hintText,
            choices = domainChoices,
            correctChoiceId = domainChoices[answerIndex].id,
        )
    }

    private fun GeographyCountry.toDomain(similarityGroupIds: List<String>): CountryRecord = CountryRecord(
        id = id,
        displayName = displayName,
        acceptedAliases = acceptedAliases,
        continentIds = listOf(region.toContinent()),
        flagAssetId = flagAssetId,
        capitalArrangement = capital.toDomain(),
        similarityGroupIds = similarityGroupIds,
    )

    private fun GeographyCapital.toDomain(): CapitalArrangement = when {
        kind == "single" -> CapitalArrangement.Single(acceptedAnswer)
        names.size > 1 -> CapitalArrangement.Multiple(
            capitals = names,
            explanation = requireNotNull(note) { "An exceptional capital arrangement requires a note" },
        )
        else -> CapitalArrangement.Exception(
            explanation = note ?: "$acceptedAnswer has an exceptional capital arrangement.",
        )
    }

    private fun GeographyFact.toDomain() = FactRecord(
        id = id,
        heading = heading,
        body = text,
        countryIds = countryIds,
        sourceIds = sourceIds,
        asOf = asOf,
        reviewStatus = reviewState.toReviewStatus(),
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
    private const val MAX_DISTRACTORS = 3

}
