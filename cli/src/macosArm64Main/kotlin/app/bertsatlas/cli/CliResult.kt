package app.bertsatlas.cli

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class ExitCode(val code: Int, val label: String) {
    Ok(0, "ok"),
    Failure(1, "failure"),
    Usage(2, "usage"),
    NotFound(3, "not_found"),
    Invalid(4, "invalid"),
    ReadOnly(5, "read_only"),
    Conflict(6, "conflict"),
}

class CliFailure(val exitCode: ExitCode, message: String, val details: JsonElement? = null) : Exception(message) {
    companion object {
        fun usage(message: String) = CliFailure(ExitCode.Usage, message)
        fun notFound(message: String) = CliFailure(ExitCode.NotFound, message)
        fun invalid(message: String, details: JsonElement? = null) = CliFailure(ExitCode.Invalid, message, details)
        fun conflict(message: String) = CliFailure(ExitCode.Conflict, message)
        fun readOnly(packId: String) = CliFailure(
            ExitCode.ReadOnly,
            "$packId is an official pack and is read-only. Run `bert packs copy $packId` to make an editable copy.",
        )
    }
}

class Output(val json: JsonElement, val text: String, val exitCode: ExitCode = ExitCode.Ok)

class CliResult(val exitCode: ExitCode, val stdout: String, val stderr: String)

fun errorJson(failure: CliFailure): JsonObject = buildJsonObject {
    put("error", buildJsonObject {
        put("code", failure.exitCode.label)
        put("exitCode", failure.exitCode.code)
        put("message", failure.message.orEmpty())
        failure.details?.let { put("details", it) }
    })
}
