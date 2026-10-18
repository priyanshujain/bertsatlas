package app.bertopia.domain.model

object BuiltInPacks {
    const val FLAGS = "flags"
    const val CAPITALS = "capitals"
    val GEOGRAPHY = setOf(FLAGS, CAPITALS)
}

val Skill.packId: String
    get() = when (this) {
        Skill.FlagRecognition, Skill.CountryToFlag, Skill.FlagSymbolism -> BuiltInPacks.FLAGS
        Skill.CapitalRecall, Skill.CapitalRecognition, Skill.MixedGeography -> BuiltInPacks.CAPITALS
        Skill.CardRecall -> error("Card recall belongs to whichever pack the card is in; use Attempt.packId")
    }

val GEOGRAPHY_SKILLS: List<Skill> = Skill.entries - Skill.CardRecall
