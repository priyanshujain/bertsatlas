package app.bertopia.domain.pack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun choiceNote(choices: String, correct: String, guid: String = "c1", tags: List<String> = emptyList()) = PackNote(
        guid = guid,
        noteTypeName = BertNoteTypes.Choice.name,
        fields = BertNoteTypes.Choice.fields.associateWith { "" } +
            mapOf("Question" to "Which structure is FIFO?", "Choices" to choices, "Correct" to correct),
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

    @Test
    fun choiceNoteWithTwoToFiveDistinctChoicesAndOneCorrectIsClean() {
        assertEquals(emptyList(), validate(choiceNote("Queue\nStack", "Queue")))
        assertEquals(emptyList(), validate(choiceNote("Queue<br>Stack<br>Heap<br>Trie<br>Deque", "Deque")))
    }

    @Test
    fun choiceCountOutsideTwoToFiveIsAnError() {
        listOf("Queue", "Queue\nStack\nHeap\nTrie\nDeque\nGraph").forEach { choices ->
            val issue = validate(choiceNote(choices, "Queue")).single()
            assertEquals(IssueCode.ChoicesInvalid, issue.code)
            assertEquals(IssueSeverity.Error, issue.severity)
            assertTrue("needs 2 to 5" in issue.message, issue.message)
        }
    }

    @Test
    fun correctMustMatchExactlyOneChoice() {
        val missing = validate(choiceNote("Queue\nStack\nHeap", "Tree")).single()
        assertEquals(IssueCode.ChoicesInvalid, missing.code)
        assertEquals("Choices", missing.field)
        assertEquals(listOf(IssueCode.ChoicesInvalid), validate(choiceNote("Queue\nStack", "queue")).map { it.code })
    }

    @Test
    fun duplicateChoicesAreAnError() {
        val messages = validate(choiceNote("Queue\nStack\nQueue", "Stack")).map { it.message }
        assertEquals(listOf("Choices has duplicate lines"), messages)
        assertEquals(2, BertChoices.problems("Queue\nQueue", "Queue").size)
    }

    @Test
    fun levelTagsMustBeMidOrSeniorAndAreErrors() {
        assertEquals(emptyList(), validate(flagNote(tags = listOf("bert::level::mid", "bert::level::senior"))))
        val issue = validate(flagNote(tags = listOf("bert::level::junior"))).single()
        assertEquals(IssueCode.BertTagInvalid, issue.code)
        assertEquals(IssueSeverity.Error, issue.severity)
        assertTrue("level must be mid or senior" in issue.message)
        assertEquals(IssueSeverity.Warning, validate(flagNote(tags = listOf("bert::weird"))).single().severity)
    }

    @Test
    fun levelSettingAdmitsUntaggedCardsAtEveryLevel() {
        val untagged = listOf("geo")
        val mid = listOf("bert::level::mid")
        val both = listOf("bert::level::mid", "bert::level::senior")
        val unknownLevel = listOf("bert::level::junior")
        assertTrue(LevelSetting.entries.all { it.admits(untagged) && it.admits(unknownLevel) && it.admits(both) })
        assertTrue(LevelSetting.Mid.admits(mid))
        assertFalse(LevelSetting.Senior.admits(mid))
        assertTrue(LevelSetting.All.admits(mid))
        assertEquals(setOf(LevelSetting.Mid, LevelSetting.Senior), BertTags.levels(both + untagged))
        assertEquals("bert::level::senior", BertTags.levelTag(LevelSetting.Senior))
        assertEquals(null, BertTags.levelTag(LevelSetting.All))
    }

    @Test
    fun cardMetaReadsFlipOnlyAndDegradesWhenInvalid() {
        assertTrue(BertMetaCodec.flipOnly("""{"flipOnly":true}"""))
        assertFalse(BertMetaCodec.flipOnly("""{"flipOnly":false}"""))
        assertFalse(BertMetaCodec.flipOnly(""))
        assertFalse(BertMetaCodec.flipOnly("{not json"))
        assertFalse(BertMetaCodec.flipOnly("""{"flipOnly":"yes please"}"""))
        assertEquals(CardBertMeta(flipOnly = true), (BertMetaCodec.readCard("""{"flipOnly":true}""") as BertMetaReading.Valid).meta)
        assertIs<BertMetaReading.Invalid>(BertMetaCodec.readCard("{not json"))
        assertIs<BertMetaReading.Valid<*>>(BertMetaCodec.readFor(BertNoteTypes.Choice, """{"flipOnly":true}"""))
    }
}
