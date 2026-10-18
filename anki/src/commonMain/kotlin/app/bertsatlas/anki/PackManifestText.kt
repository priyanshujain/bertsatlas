package app.bertsatlas.anki

object PackManifestText {
    private val commentBlock = Regex("<!--bert\\s*(.*?)\\s*-->", RegexOption.DOT_MATCHES_ALL)
    private val fencedBlock = Regex("```bert\\s*(.*?)\\s*```", RegexOption.DOT_MATCHES_ALL)

    fun read(deckDescription: String): String? =
        (commentBlock.find(deckDescription) ?: fencedBlock.find(deckDescription))?.groupValues?.get(1)

    fun write(deckDescription: String, manifestJson: String): String {
        val replacement = "<!--bert\n$manifestJson\n-->"
        return listOf(readableText(deckDescription), replacement).filter { it.isNotEmpty() }.joinToString("\n")
    }

    fun readableText(deckDescription: String): String =
        deckDescription.replace(commentBlock, "").replace(fencedBlock, "").trim()
}
