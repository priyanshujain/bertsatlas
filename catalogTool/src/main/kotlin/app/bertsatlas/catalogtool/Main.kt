package app.bertsatlas.catalogtool

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val DEFAULT_CATALOG = "composeApp/src/commonMain/composeResources/files/catalog/catalog-v1.json"
private const val DEFAULT_REPORT = "catalogTool/build/reports/catalog-review.txt"
private val reviewStates = setOf("draft", "fact_checked", "copy_checked", "approved")
private val requiredFormats = setOf("flag_to_country", "country_to_flag", "letter_bank")
private val validHints = setOf("reveal_letter", "remove_choices", "remove_decoys", "reveal_continent", "factual_clue")
private val canon = """
ad ae af ag al am ao ar at au az ba bb bd be bf bg bh bi bj bn bo br bs bt bw by bz ca cd cf cg ch ci cl cm cn co cr cu cv cy cz de dj dk dm do dz ec ee eg er es et fi fj fm fr ga gb gd ge gh gm gn gq gr gt gw gy hn hr ht hu id ie il in iq ir is it jm jo jp ke kg kh ki km kn kp kr kw kz la lb lc li lk lr ls lt lu lv ly ma mc md me mg mh mk ml mm mn mr mt mu mv mw mx my mz na ne ng ni nl no np nr nz om pa pe pg ph pk pl ps pt pw py qa ro rs ru rw sa sb sc sd se sg si sk sl sm sn so sr ss st sv sy sz td tg th tj tl tm tn to tr tt tv tz ua ug us uy uz va vc ve vn vu ws ye za zm zw
""".trim().split(Regex("\\s+")).toSet()

fun main(args: Array<String>) {
    val options = Options.parse(args)
    val projectRoot = findProjectRoot(Path.of("").toAbsolutePath())
    val catalog = options.catalog.resolveFrom(projectRoot)
    val report = options.report.resolveFrom(projectRoot)
    val result = Validator(catalog).validate()
    report.parent?.let(Files::createDirectories)
    report.writeText(result.report(catalog, options.release))
    println(result.summary(catalog, report, options.release))
    if (result.errors.isNotEmpty()) kotlin.system.exitProcess(1)
    if (options.release && result.gaps.isNotEmpty()) kotlin.system.exitProcess(2)
}

private fun findProjectRoot(start: Path): Path {
    var candidate: Path? = start.normalize()
    while (candidate != null) {
        if (candidate.resolve("settings.gradle.kts").exists()) return candidate
        candidate = candidate.parent
    }
    error("could not locate project root from $start")
}

private fun Path.resolveFrom(root: Path): Path =
    (if (isAbsolute) this else root.resolve(this)).toAbsolutePath().normalize()

private data class Options(val catalog: Path, val report: Path, val release: Boolean) {
    companion object {
        fun parse(args: Array<String>): Options {
            var catalog = Path.of(DEFAULT_CATALOG)
            var report = Path.of(DEFAULT_REPORT)
            var release = false
            var i = 0
            while (i < args.size) {
                when (args[i]) {
                    "--catalog" -> catalog = Path.of(args.getOrNull(++i) ?: error("--catalog requires a path"))
                    "--report" -> report = Path.of(args.getOrNull(++i) ?: error("--report requires a path"))
                    "--release" -> release = true
                    "--help" -> {
                        println("usage: catalogTool [--catalog path] [--report path] [--release]")
                        kotlin.system.exitProcess(0)
                    }
                    else -> error("unknown argument: ${args[i]}")
                }
                i++
            }
            return Options(catalog, report, release)
        }
    }
}

private class Validator(private val path: Path) {
    private val errors = mutableListOf<String>()
    private val gaps = mutableListOf<String>()
    private val counts = linkedMapOf<String, Int>()
    private lateinit var resourceRoot: Path

    fun validate(): Result {
        if (!path.exists()) return Result(listOf("catalog file does not exist: $path"), emptyList(), emptyMap())
        val root = try {
            Json.parseToJsonElement(Files.readString(path)).jsonObject
        } catch (exception: Exception) {
            return Result(listOf("catalog is not valid JSON: ${exception.message}"), emptyList(), emptyMap())
        }
        resourceRoot = path.parent.parent
        expectInt(root, "schemaVersion", 1, "catalog")
        text(root, "catalogVersion", "catalog")
        text(root, "countryCanon", "catalog")
        text(root, "editorialPolicyVersion", "catalog")

        val sources = objects(root, "sources")
        val countries = objects(root, "countries")
        val facts = objects(root, "facts")
        val questions = objects(root, "curatedQuestions")
        val groups = objects(root, "similarityGroups")
        val explanations = objects(root, "confusionExplanations")
        val assets = objects(root, "assets")
        counts.putAll(mapOf("sources" to sources.size, "countries" to countries.size, "facts" to facts.size,
            "curated questions" to questions.size, "similarity groups" to groups.size,
            "confusion explanations" to explanations.size, "assets" to assets.size))

        val sourceIds = ids("source", sources)
        val countryIds = ids("country", countries)
        val factIds = ids("fact", facts)
        ids("curated question", questions)
        ids("similarity group", groups)
        val explanationIds = ids("confusion explanation", explanations)
        val assetIds = ids("asset", assets)

        sources.forEach { source ->
            val label = "source ${id(source)}"
            listOf("title", "url", "publisher", "retrievedAt", "revision").forEach { text(source, it, label) }
        }
        countries.forEach { country -> validateCountry(country, factIds, sourceIds, assetIds) }
        facts.forEach { fact ->
            val label = "fact ${id(fact)}"
            expectInt(fact, "schemaVersion", 1, label)
            listOf("heading", "text").forEach { text(fact, it, label) }
            refs(label, strings(fact, "countryIds"), countryIds, "country")
            val sourcesForFact = strings(fact, "sourceIds")
            if (sourcesForFact.isEmpty()) errors += "$label has no factual source"
            refs(label, sourcesForFact, sourceIds, "source")
            review(fact, label)
        }
        questions.forEach { question -> validateQuestion(question, countryIds, sourceIds) }
        groups.forEach { group ->
            val label = "similarity group ${id(group)}"
            val members = strings(group, "countryIds")
            if (members.size < 2) errors += "$label needs at least two distinct countries"
            refs(label, members, countryIds, "country")
            if (value(group, "confusionExplanationId") !in explanationIds) errors += "$label references a missing explanation"
        }
        explanations.forEach { explanation ->
            val label = "confusion explanation ${id(explanation)}"
            text(explanation, "text", label)
            refs(label, strings(explanation, "countryIds"), countryIds, "country")
            refs(label, strings(explanation, "sourceIds"), sourceIds, "source")
            review(explanation, label)
        }
        assets.forEach { asset -> validateAsset(asset, sourceIds) }
        review(root, "catalog", "catalogReview")

        if (countryIds != canon) {
            (canon - countryIds).takeIf(Set<String>::isNotEmpty)?.let { errors += "canon missing: ${it.sorted().joinToString()}" }
            (countryIds - canon).takeIf(Set<String>::isNotEmpty)?.let { errors += "canon has unexpected entries: ${it.sorted().joinToString()}" }
        }
        if (countries.size != 195) errors += "expected 195 countries, found ${countries.size}"
        if (questions.size < 80) errors += "expected at least 80 curated questions, found ${questions.size}"
        if (explanations.size < 30) errors += "expected at least 30 confusion explanations, found ${explanations.size}"
        if (countries.map { value(it, "alpha2") }.toSet().size != countries.size) errors += "country alpha2 values are not unique"
        if (countries.map { value(it, "alpha3") }.toSet().size != countries.size) errors += "country alpha3 values are not unique"
        return Result(errors.sorted(), gaps.sorted(), counts)
    }

    private fun validateCountry(country: JsonObject, factIds: Set<String>, sourceIds: Set<String>, assetIds: Set<String>) {
        val label = "country ${id(country)}"
        listOf("alpha2", "alpha3", "displayName", "region", "unM49Subregion").forEach { text(country, it, label) }
        if (!strings(country, "supportedQuestionFormats").containsAll(requiredFormats)) errors += "$label lacks a required flag direction or letter bank"
        if (value(country, "flagAssetId") !in assetIds) errors += "$label references a missing flag asset"
        val linkedFacts = strings(country, "factIds")
        if (linkedFacts.isEmpty()) errors += "$label has no linked fact"
        refs(label, linkedFacts, factIds, "fact")
        review(country, label)
        val capital = country["capital"] as? JsonObject
        if (capital == null) {
            errors += "$label has no capital arrangement"
            return
        }
        val kind = text(capital, "kind", "$label capital")
        val names = strings(capital, "names")
        if (names.isEmpty() || names.any(String::isBlank)) errors += "$label capital has no usable names"
        if (value(capital, "acceptedAnswer") !in names) errors += "$label capital answer is not in its names"
        if (capital["questionEligible"]?.jsonPrimitive?.booleanOrNull == null) errors += "$label capital questionEligible is not boolean"
        if (kind != "single" && value(capital, "note").isBlank()) errors += "$label exceptional capital needs a note"
        refs("$label capital", strings(capital, "sourceIds"), sourceIds, "source")
        review(capital, "$label capital")
    }

    private fun validateQuestion(question: JsonObject, countryIds: Set<String>, sourceIds: Set<String>) {
        val label = "question ${id(question)}"
        expectInt(question, "schemaVersion", 1, label)
        if (text(question, "topic", label) !in setOf("mixed_geography", "flag_symbolism")) errors += "$label has the wrong topic"
        text(question, "prompt", label)
        val answer = text(question, "answer", label)
        val format = text(question, "format", label)
        val choices = listStrings(question, "choices")
        if (format == "multiple_choice") {
            if (choices.size != 4) errors += "$label must have four choices"
            if (choices.toSet().size != choices.size) errors += "$label has duplicate choices"
            if (choices.count { it == answer } != 1) errors += "$label must contain its answer once"
        }
        if (format == "letter_bank") {
            val tiles = listStrings(question, "letterTiles")
            val answerLetters = answer.normalizedLetters().groupingBy { it }.eachCount()
            val tileLetters = tiles.joinToString("").normalizedLetters().groupingBy { it }.eachCount()
            if (answerLetters.any { (letter, count) -> tileLetters.getOrDefault(letter, 0) < count }) {
                errors += "$label letter tiles cannot construct its answer"
            }
            if (tileLetters.values.sum() - answerLetters.values.sum() != 3) errors += "$label letter bank must contain three decoy letters"
        }
        val hints = strings(question, "supportedHints")
        (hints - validHints).forEach { errors += "$label has unsupported hint $it" }
        if (format != "letter_bank" && "reveal_letter" in hints) errors += "$label has an incompatible letter hint"
        if (format != "multiple_choice" && "remove_choices" in hints) errors += "$label has incompatible choice removal"
        refs(label, strings(question, "countryIds"), countryIds, "country")
        val questionSources = strings(question, "sourceIds")
        if (questionSources.isEmpty()) errors += "$label has no factual source"
        refs(label, questionSources, sourceIds, "source")
        review(question, label)
    }

    private fun validateAsset(asset: JsonObject, sourceIds: Set<String>) {
        val label = "asset ${id(asset)}"
        val relativePath = text(asset, "path", label)
        if (text(asset, "sourceId", label) !in sourceIds) errors += "$label references a missing source"
        text(asset, "license", label)
        val expectedHash = text(asset, "sha256", label)
        val file = resourceRoot.resolve(relativePath).normalize()
        when {
            !file.startsWith(resourceRoot) -> errors += "$label path escapes the resource root"
            !file.exists() -> errors += "$label file is missing: $relativePath"
            file.readBytes().sha256() != expectedHash -> errors += "$label checksum differs: $relativePath"
        }
        if (value(asset, "manualAudit") != "approved") gaps += "$label manual audit is ${value(asset, "manualAudit").ifBlank { "missing" }}"
    }

    private fun review(owner: JsonObject, label: String, key: String = "review") {
        val review = owner[key] as? JsonObject
        if (review == null) {
            errors += "$label has no review metadata"
            return
        }
        val state = value(review, "state")
        if (state !in reviewStates) errors += "$label has invalid review state '$state'"
        else if (state != "approved") gaps += "$label review state is $state"
        if (state == "approved" && (value(review, "reviewer").isBlank() || value(review, "reviewedAt").isBlank())) {
            errors += "$label is approved without reviewer and date evidence"
        }
    }

    private fun ids(kind: String, records: List<JsonObject>): Set<String> {
        val values = records.map(::id)
        values.filter(String::isBlank).forEach { errors += "$kind has a blank id" }
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { errors += "duplicate $kind id: $it" }
        return values.filter(String::isNotBlank).toSet()
    }

    private fun refs(label: String, refs: Set<String>, valid: Set<String>, kind: String) {
        (refs - valid).forEach { errors += "$label references missing $kind $it" }
    }

    private fun text(owner: JsonObject, key: String, label: String): String =
        value(owner, key).also { if (it.isBlank()) errors += "$label has no $key" }

    private fun expectInt(owner: JsonObject, key: String, expected: Int, label: String) {
        val actual = owner[key]?.jsonPrimitive?.intOrNull
        if (actual != expected) errors += "$label $key must be $expected, found ${actual ?: "missing"}"
    }
}

private data class Result(val errors: List<String>, val gaps: List<String>, val counts: Map<String, Int>) {
    fun summary(catalog: Path, report: Path, release: Boolean): String = buildString {
        appendLine("catalog: $catalog")
        counts.forEach { (name, count) -> appendLine("$name: $count") }
        appendLine("structural errors: ${errors.size}")
        appendLine("editorial gaps: ${gaps.size}")
        appendLine("mode: ${if (release) "release gate" else "structural"}")
        appendLine("result: ${outcome(release)}")
        append("report: ${report.toAbsolutePath().normalize()}")
    }

    fun report(catalog: Path, release: Boolean): String = buildString {
        appendLine("Bert's Atlas catalog review report")
        appendLine("Catalog: $catalog")
        appendLine("Mode: ${if (release) "release gate" else "structural"}")
        appendLine("Result: ${outcome(release)}")
        appendLine()
        appendLine("Inventory")
        counts.forEach { (name, count) -> appendLine("- $name: $count") }
        appendLine()
        appendLine("Structural errors (${errors.size})")
        if (errors.isEmpty()) appendLine("- none") else errors.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Editorial gaps (${gaps.size})")
        if (gaps.isEmpty()) appendLine("- none") else gaps.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Structural mode proves the data is internally safe to load. Release mode also requires genuine approval evidence for all editorial records and manual flag audits.")
    }

    private fun outcome(release: Boolean) = when {
        errors.isNotEmpty() -> "failed structural validation"
        release && gaps.isNotEmpty() -> "blocked by editorial review"
        else -> "passed"
    }
}

private fun objects(owner: JsonObject, key: String): List<JsonObject> =
    (owner[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

private fun id(owner: JsonObject): String = value(owner, "id")

private fun value(owner: JsonObject, key: String): String {
    val element = owner[key] ?: return ""
    if (element is JsonNull) return ""
    return (element as? JsonPrimitive)?.contentOrNull.orEmpty()
}

private fun strings(owner: JsonObject, key: String): Set<String> =
    listStrings(owner, key).toSet()

private fun listStrings(owner: JsonObject, key: String): List<String> =
    (owner[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

private fun String.normalizedLetters(): String = lowercase().filter(Char::isLetter)

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
