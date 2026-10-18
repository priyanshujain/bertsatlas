package app.bertsatlas.domain.geography

import app.bertsatlas.anki.collection.AnkiCollection
import app.bertsatlas.anki.model.Note
import app.bertsatlas.anki.model.Notetype
import app.bertsatlas.domain.model.AssetLicenseRecord
import app.bertsatlas.domain.model.SourceRecord
import app.bertsatlas.domain.pack.BertMetaCodec
import app.bertsatlas.domain.pack.BertMetaReading
import app.bertsatlas.domain.pack.BertNoteTypes
import app.bertsatlas.domain.pack.BertTags
import app.bertsatlas.domain.pack.CapitalBertMeta
import app.bertsatlas.domain.pack.FlagBertMeta
import app.bertsatlas.domain.pack.ManifestReading
import app.bertsatlas.domain.pack.PackManifest
import app.bertsatlas.domain.pack.PackManifestCodec

class GeographyPack(val manifest: PackManifest, val collection: AnkiCollection)

object GeographyPackReader {
    fun read(flags: GeographyPack, capitals: GeographyPack): GeographyContent {
        val flagNotes = flags.collection.notesOf(BertNoteTypes.Flag.name).map { FlagNote(it.first, it.second) }
        val capitalsByCountry = capitals.collection.notesOf(BertNoteTypes.Capital.name)
            .map { CapitalNote(it.first, it.second) }
            .associateBy { it.meta.countryId }
        val countries = flagNotes.map { flag ->
            val capital = requireNotNull(capitalsByCountry[flag.meta.countryId]) { "Capitals pack has no card for ${flag.meta.countryId}" }
            GeographyCountry(
                id = flag.meta.countryId,
                displayName = flag.field("Country"),
                acceptedAliases = flag.field("Aliases").lines().map(String::trim).filter(String::isNotEmpty),
                region = requireNotNull(flag.meta.region) { "Flag card ${flag.meta.countryId} has no region" },
                capital = capital.toCapital(),
                flagAssetId = flag.meta.flagAsset?.id ?: "flag-${flag.meta.countryId}",
                flagClues = flag.clues(),
                capitalClues = capital.clues(),
            )
        }
        val facts = flagNotes.mapNotNull { flag ->
            val fact = flag.meta.fact ?: return@mapNotNull null
            GeographyFact(
                id = fact.id,
                countryIds = listOf(flag.meta.countryId),
                heading = fact.heading.orEmpty(),
                text = flag.field("Fact"),
                sourceIds = fact.sourceIds,
                asOf = fact.asOf,
                reviewState = fact.review?.state ?: "draft",
            )
        }
        val curatedQuestions = capitalsByCountry.values.mapNotNull { capital ->
            val question = capital.meta.curatedQuestion ?: return@mapNotNull null
            GeographyCuratedQuestion(
                id = question.id,
                topic = question.topic ?: "mixed_geography",
                difficulty = question.difficulty ?: "casual",
                format = question.format ?: "multiple_choice",
                prompt = question.prompt,
                answer = question.answer,
                choices = question.choices,
                countryIds = listOf(capital.meta.countryId),
                supportedHints = question.supportedHints,
                explanation = question.explanation.orEmpty(),
            )
        }
        return GeographyContent(
            schemaVersion = flags.manifest.bertSchemaVersion,
            contentVersion = flags.manifest.version,
            sources = flags.manifest.sources.map { source ->
                SourceRecord(
                    id = source.id,
                    title = source.title,
                    url = source.url.orEmpty(),
                    revision = source.revision,
                    accessedOn = source.retrievedAt,
                )
            },
            countries = countries,
            facts = facts,
            curatedQuestions = curatedQuestions,
            similarityGroups = flags.manifest.similarGroups.map { GeographySimilarityGroup(it.id, it.countryIds) },
            assets = flags.manifest.assetLicenses.map { asset ->
                AssetLicenseRecord(
                    assetId = asset.assetId,
                    sourceId = asset.sourceId,
                    licenseName = asset.license,
                    modified = asset.modified,
                    checksum = asset.sha256.orEmpty(),
                )
            },
        )
    }

    fun manifestOf(collection: AnkiCollection): PackManifest? = collection.decks()
        .filter { it.isTopLevel }
        .firstNotNullOfOrNull { deck ->
            (PackManifestCodec.readDeckDescription(deck.description) as? ManifestReading.Found)?.manifest
        }

    private fun AnkiCollection.notesOf(notetypeName: String): List<Pair<Notetype, Note>> {
        val notetype = requireNotNull(notetypeByName(notetypeName)) { "Pack has no $notetypeName cards" }
        return notes().filter { it.notetypeId == notetype.id && BertTags.RETIRED !in it.tags }.map { notetype to it }
    }

    private fun PackNoteFields.clues() = GeographyClues(
        continent = field("Continent"),
        factualClue = field("Hint").takeIf(String::isNotBlank),
    )

    private interface PackNoteFields {
        fun field(name: String): String
    }

    private class FlagNote(val notetype: Notetype, val note: Note) : PackNoteFields {
        val meta: FlagBertMeta = when (val reading = BertMetaCodec.readFlag(field("BertMeta"))) {
            is BertMetaReading.Valid -> reading.meta
            else -> error("Flag card ${note.guid} has unreadable BertMeta")
        }

        override fun field(name: String): String = note.fields[notetype.fieldNames.indexOf(name)]
    }

    private class CapitalNote(val notetype: Notetype, val note: Note) : PackNoteFields {
        val meta: CapitalBertMeta = when (val reading = BertMetaCodec.readCapital(field("BertMeta"))) {
            is BertMetaReading.Valid -> reading.meta
            else -> error("Capital card ${note.guid} has unreadable BertMeta")
        }

        override fun field(name: String): String = note.fields[notetype.fieldNames.indexOf(name)]

        fun toCapital(): GeographyCapital {
            val names = field("Capitals").lines().map(String::trim).filter(String::isNotEmpty)
            return GeographyCapital(
                kind = field("CapitalArrangement"),
                names = names,
                acceptedAnswer = meta.capital?.acceptedAnswer ?: names.first(),
                questionEligible = meta.capital?.questionEligible ?: true,
                note = meta.capital?.note,
            )
        }
    }
}
