package app.bertsatlas.domain.pack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PackValidatorTest {
    private val manifest = PackManifest(
        packId = "flags",
        version = "1",
        title = "Flags",
        accentColor = "#27635B",
        similarGroups = listOf(SimilarGroup(id = "chad-romania")),
    )

    private fun flagNote(guid: String = "g1", meta: String = """{"countryId":"td"}""", tags: List<String> = emptyList()) = PackNote(
        guid = guid,
        noteTypeName = BertNoteTypes.Flag.name,
        fields = BertNoteTypes.Flag.fields.associateWith { "" } +
            mapOf("Country" to "Chad", "Flag" to "<img src=\"td.png\">", BertNoteTypes.BERT_META_FIELD to meta),
        tags = tags,
    )

    private fun validate(vararg notes: PackNote, media: Set<String> = setOf("td.png")) =
        PackValidator.validate(PackContents(ManifestReading.Found(manifest, ""), notes.toList(), media))

    @Test
    fun cleanPackHasNoIssues() {
        assertEquals(emptyList(), validate(flagNote(tags = listOf("bert::similar::chad-romania", "bert::status::approved"))))
    }

    @Test
    fun brokenBertMetaIsAWarningNotAnError() {
        val issues = validate(flagNote(meta = "{not json"))
        assertEquals(listOf(IssueCode.BertMetaInvalid), issues.map { it.code })
        assertEquals(IssueSeverity.Warning, issues.single().severity)
    }

    @Test
    fun reportsTagsMediaAndDuplicates() {
        val codes = validate(
            flagNote(tags = listOf("bert::status::done", "bert::similar::nope", "bert::weird")),
            flagNote(),
            media = emptySet(),
        ).map { it.code }.toSet()
        assertEquals(
            setOf(IssueCode.DuplicateGuid, IssueCode.BertTagInvalid, IssueCode.SimilarGroupUnknown, IssueCode.MediaMissing),
            codes,
        )
    }

    @Test
    fun missingRequiredFieldAndWrongFieldsAreErrors() {
        val note = flagNote().let { it.copy(fields = it.fields + ("Country" to "") - "Continent") }
        val codes = validate(note).map { it.code }
        assertTrue(IssueCode.RequiredFieldEmpty in codes && IssueCode.FieldsMismatch in codes)
    }

    @Test
    fun invalidManifestFields() {
        val issues = PackValidator.validateManifest(manifest.copy(packId = "Bad Id", accentColor = "red"))
        assertEquals(setOf("packId", "accentColor"), issues.map { it.field }.toSet())
    }

    @Test
    fun tagHelpers() {
        val tags = listOf("bert::similar::a", "bert::status::factchecked", "geo")
        assertEquals(listOf("a"), BertTags.similarGroupIds(tags))
        assertEquals(EditorialStatus.FactChecked, BertTags.editorialStatus(tags))
        assertEquals("bert::similar::b", BertTags.similarTag("b"))
        assertEquals(listOf("a", "b"), BertTags.parseAnkiTags(" a b "))
        assertIs<BertMetaReading.Empty>(BertMetaCodec.readFlag("  "))
    }
}
