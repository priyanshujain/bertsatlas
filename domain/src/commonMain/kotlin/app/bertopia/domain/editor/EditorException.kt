package app.bertopia.domain.editor

enum class EditorFailureKind { NotFound, Invalid, ReadOnly, Conflict, Failure }

data class EditorProblem(val message: String, val index: Int? = null, val field: String? = null)

class EditorException(
    val kind: EditorFailureKind,
    message: String,
    val problems: List<EditorProblem> = emptyList(),
    val packId: String? = null,
) : Exception(message) {
    companion object {
        fun notFound(message: String) = EditorException(EditorFailureKind.NotFound, message)
        fun invalid(message: String, problems: List<EditorProblem> = emptyList()) = EditorException(EditorFailureKind.Invalid, message, problems)
        fun conflict(message: String) = EditorException(EditorFailureKind.Conflict, message)
        fun failure(message: String) = EditorException(EditorFailureKind.Failure, message)
        fun readOnly(packId: String) = EditorException(
            EditorFailureKind.ReadOnly,
            "$packId is an official pack and is read-only. Make your own copy to edit it.",
            packId = packId,
        )
    }
}
