package app.bertsatlas.cli

import app.bertsatlas.domain.editor.EditorProblem
import app.bertsatlas.domain.pack.ValidationIssue
import app.bertsatlas.domain.quiz.PackCardReader
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.content == "true"

fun issueJson(issue: ValidationIssue, index: Int? = null): JsonObject = buildJsonObject {
    index?.let { put("index", it) }
    put("severity", issue.severity.name.lowercase())
    put("code", issue.code.name)
    put("message", issue.message)
    issue.noteGuid?.let { put("guid", it) }
    issue.field?.let { put("field", it) }
}

fun issuesJson(issues: List<ValidationIssue>): JsonArray = JsonArray(issues.map { issueJson(it) })

fun issueLine(issue: ValidationIssue): String =
    "${issue.severity.name.lowercase()} ${issue.code.name}" + (issue.noteGuid?.let { " [$it]" } ?: "") + (issue.field?.let { " $it" } ?: "") + ": ${issue.message}"

private val hiddenHintMarkup = Regex("""(?s)<a class="hint"[^>]*>.*?</a>|<div class="hint" style="display: none">.*?</div>""")

fun readableText(html: String): String =
    PackCardReader.plainText(html.replace(hiddenHintMarkup, " ")).lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

private val answerDivider = Regex("""(?i)<hr[^>]*id\s*=\s*["']?answer["']?[^>]*>""")

fun answerSideText(answerHtml: String): String = readableText(answerDivider.split(answerHtml, limit = 2).last())

fun problemsJson(problems: List<EditorProblem>): JsonArray = JsonArray(problems.map { problem ->
    buildJsonObject {
        problem.index?.let { put("index", it) }
        put("severity", "error")
        put("message", problem.message)
        problem.field?.let { put("field", it) }
    }
})
