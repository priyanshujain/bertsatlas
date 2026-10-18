package app.bertopia.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class QuizConfiguration(
    val duration: QuizDuration = QuizDuration.FiveMinutes,
    val scope: PlayScope = PlayScope.Pack(BuiltInPacks.FLAGS),
)

@Serializable
enum class QuizDuration(
    val activeDurationMillis: Long?,
    val answeredQuestionLimit: Int?,
) {
    FiveMinutes(5 * 60 * 1_000L, 10),
    TenMinutes(10 * 60 * 1_000L, 20),
    TwentyMinutes(20 * 60 * 1_000L, 40),
    Unlimited(null, null),
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
    CardRecall,
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
