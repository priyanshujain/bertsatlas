package app.bertsatlas.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class QuizConfiguration(
    val duration: QuizDuration = QuizDuration.FiveMinutes,
    val topic: QuizTopic = QuizTopic.MostlyFlags,
)

@Serializable
enum class QuizDuration(val activeDurationMillis: Long?) {
    FiveMinutes(5 * 60 * 1_000L),
    TenMinutes(10 * 60 * 1_000L),
    TwentyMinutes(20 * 60 * 1_000L),
    Unlimited(null),
}

@Serializable
enum class QuizTopic {
    MostlyFlags,
    Capitals,
    MixedGeography,
}

@Serializable
enum class DifficultyTier {
    Casual,
    Tricky,
    Brutal,
}

@Serializable
enum class Skill {
    FlagRecognition,
    CountryToFlag,
    CapitalRecall,
    CapitalRecognition,
    MixedGeography,
    FlagSymbolism,
}

@Serializable
enum class Continent {
    Africa,
    Asia,
    Europe,
    NorthAmerica,
    SouthAmerica,
    Oceania,
    Antarctica,
}
