package app.bertopia.domain.pack

enum class IssueSeverity { Error, Warning }

enum class IssueCode {
    ManifestMissing,
    ManifestUnreadable,
    ManifestFieldInvalid,
    UnsupportedSchemaVersion,
    StampRuleUnreadable,
    DuplicateGuid,
    FieldsMismatch,
    RequiredFieldEmpty,
    BertMetaInvalid,
    TagMalformed,
    BertTagInvalid,
    SimilarGroupUnknown,
    MediaMissing,
    ChoicesInvalid,
}

data class ValidationIssue(
    val severity: IssueSeverity,
    val code: IssueCode,
    val message: String,
    val noteGuid: String? = null,
    val field: String? = null,
)

data class PackNote(
    val guid: String,
    val noteTypeName: String,
    val fields: Map<String, String>,
    val tags: List<String>,
)

data class PackContents(
    val manifest: ManifestReading,
    val notes: List<PackNote>,
    val mediaFiles: Set<String>,
)

object PackValidator {
    private val packIdPattern = Regex("^[a-z0-9][a-z0-9-]*$")
    private val accentPattern = Regex("^#[0-9A-Fa-f]{6}$")
    private val imageSource = Regex("""<img[^>]*?\ssrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^"'\s>]+))""", RegexOption.IGNORE_CASE)
    private val soundReference = Regex("""\[sound:([^\]]+)]""")

    fun validate(contents: PackContents): List<ValidationIssue> {
        val manifest = (contents.manifest as? ManifestReading.Found)?.manifest
        return validateManifest(contents.manifest) + validateNotes(contents.notes, manifest, contents.mediaFiles)
    }

    fun validateManifest(reading: ManifestReading): List<ValidationIssue> = when (reading) {
        is ManifestReading.Missing -> listOf(warning(IssueCode.ManifestMissing, "pack has no Bert manifest"))
        is ManifestReading.Unreadable -> listOf(warning(IssueCode.ManifestUnreadable, reading.reason))
        is ManifestReading.Found -> validateManifest(reading.manifest)
    }

    fun validateManifest(manifest: PackManifest): List<ValidationIssue> = buildList {
        if (!packIdPattern.matches(manifest.packId)) add(manifestField("packId", "must be lowercase letters, digits and dashes"))
        if (manifest.version.isBlank()) add(manifestField("version", "must not be blank"))
        if (manifest.title.isBlank()) add(manifestField("title", "must not be blank"))
        manifest.accentColor?.let { if (!accentPattern.matches(it)) add(manifestField("accentColor", "must be #RRGGBB")) }
        if (manifest.bertSchemaVersion > CURRENT_BERT_SCHEMA_VERSION) {
            add(warning(IssueCode.UnsupportedSchemaVersion, "bertSchemaVersion ${manifest.bertSchemaVersion} is newer than $CURRENT_BERT_SCHEMA_VERSION"))
        }
        manifest.stamps.filter { it.decodeRule() == null }.forEach {
            add(warning(IssueCode.StampRuleUnreadable, "stamp ${it.id} has an unknown or malformed rule"))
        }
    }

    fun validateNotes(notes: List<PackNote>, manifest: PackManifest?, mediaFiles: Set<String>): List<ValidationIssue> = buildList {
        notes.groupBy { it.guid }.filterValues { it.size > 1 }.keys.forEach {
            add(error(IssueCode.DuplicateGuid, "guid is used by more than one note", it))
        }
        val knownGroups = manifest?.similarGroups?.map { it.id }?.toSet().orEmpty()
        notes.forEach { addAll(validateNote(it, knownGroups, mediaFiles)) }
    }

    fun validateNote(note: PackNote, knownSimilarGroups: Set<String>, mediaFiles: Set<String>): List<ValidationIssue> = buildList {
        val noteType = BertNoteTypes.byName(note.noteTypeName)
        if (noteType != null) {
            if (note.fields.keys != noteType.fields.toSet()) {
                add(error(IssueCode.FieldsMismatch, "fields do not match ${noteType.name}", note.guid))
            }
            noteType.requiredFields.filter { note.fields[it].isNullOrBlank() }.forEach {
                add(error(IssueCode.RequiredFieldEmpty, "$it is empty", note.guid, it))
            }
            note.fields[BertNoteTypes.BERT_META_FIELD]?.let { meta ->
                val reading = BertMetaCodec.readFor(noteType, meta)
                if (reading is BertMetaReading.Invalid) {
                    add(warning(IssueCode.BertMetaInvalid, reading.reason, note.guid, BertNoteTypes.BERT_META_FIELD))
                }
            }
            if (noteType == BertNoteTypes.Choice) {
                BertChoices.problems(note.fields["Choices"].orEmpty(), note.fields["Correct"].orEmpty()).forEach {
                    add(error(IssueCode.ChoicesInvalid, it, note.guid, "Choices"))
                }
            }
        }
        note.tags.forEach { tag ->
            if (!BertTags.isWellFormed(tag)) add(warning(IssueCode.TagMalformed, "tag '$tag' is malformed", note.guid))
            BertTags.bertTagProblem(tag)?.let { problem ->
                val issue = if (tag.startsWith(BertTags.LEVEL_PREFIX)) ::error else ::warning
                add(issue(IssueCode.BertTagInvalid, "tag '$tag': $problem", note.guid, null))
            }
        }
        if (knownSimilarGroups.isNotEmpty()) {
            BertTags.similarGroupIds(note.tags).filterNot { it in knownSimilarGroups }.forEach {
                add(warning(IssueCode.SimilarGroupUnknown, "similar group '$it' is not in the manifest", note.guid))
            }
        }
        note.fields.forEach { (name, value) ->
            mediaReferences(value).filterNot { it in mediaFiles }.forEach {
                add(warning(IssueCode.MediaMissing, "media '$it' is missing", note.guid, name))
            }
        }
    }

    fun mediaReferences(fieldValue: String): List<String> =
        imageSource.findAll(fieldValue).mapNotNull { match -> match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }.toList() +
            soundReference.findAll(fieldValue).map { it.groupValues[1] }.toList()

    private fun manifestField(field: String, problem: String) =
        error(IssueCode.ManifestFieldInvalid, "$field $problem", field = field)

    private fun error(code: IssueCode, message: String, noteGuid: String? = null, field: String? = null) =
        ValidationIssue(IssueSeverity.Error, code, message, noteGuid, field)

    private fun warning(code: IssueCode, message: String, noteGuid: String? = null, field: String? = null) =
        ValidationIssue(IssueSeverity.Warning, code, message, noteGuid, field)
}
