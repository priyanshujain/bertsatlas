package app.bertsatlas.domain.pack

import app.bertsatlas.domain.model.BuiltInPacks
import app.bertsatlas.domain.model.PlayScope

data class MixMember(
    val packId: String,
    val sections: List<String> = emptyList(),
)

data class Mix(
    val mixId: String,
    val title: String,
    val subtitle: String,
    val accentColor: String,
    val members: List<MixMember>,
) {
    fun missingPackIds(installedPackIds: Set<String>): List<String> =
        members.map { it.packId }.filterNot { it in installedPackIds }

    fun playScope(installedPackIds: Set<String>): PlayScope.Mix? {
        val playable = members.map { it.packId }.filter { it in installedPackIds }
        return if (playable.isEmpty()) null else PlayScope.Mix(mixId, playable)
    }
}

object BundledMixes {
    val Geography = Mix(
        mixId = "geography",
        title = "Mixed Geography",
        subtitle = "Flags and capitals in one run",
        accentColor = "#778BA5",
        members = listOf(MixMember(BuiltInPacks.FLAGS), MixMember(BuiltInPacks.CAPITALS)),
    )

    val all = listOf(Geography)
}
