package app.bertsatlas

import app.bertsatlas.domain.pack.Mix

data class MixSummary(
    val mixId: String,
    val title: String,
    val subtitle: String,
    val accentColor: String,
    val memberPackIds: List<String>,
    val missingPackIds: List<String>,
)

fun Mix.toSummary(installedPackIds: Set<String>) = MixSummary(
    mixId = mixId,
    title = title,
    subtitle = subtitle,
    accentColor = accentColor,
    memberPackIds = members.map { it.packId },
    missingPackIds = missingPackIds(installedPackIds),
)
