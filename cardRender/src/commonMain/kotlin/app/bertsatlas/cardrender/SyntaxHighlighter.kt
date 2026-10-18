package app.bertsatlas.cardrender

enum class CodeTokenKind { Plain, Keyword, String, Comment, Number, Type, Function, Punctuation }

data class CodeToken(val text: String, val kind: CodeTokenKind)

object SyntaxHighlighter {
    private val cLikeKeywords = setOf(
        "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return",
        "try", "catch", "finally", "throw", "new", "class", "interface", "enum", "struct", "public", "private",
        "protected", "static", "final", "const", "void", "this", "super", "null", "true", "false", "import",
        "package", "extends", "implements", "abstract", "virtual", "override", "using", "namespace", "typedef",
        "sizeof", "goto", "volatile", "extern", "inline", "template", "typename", "operator", "delete", "auto",
        "int", "long", "short", "char", "float", "double", "boolean", "bool", "byte", "unsigned", "signed",
    )
    private val languageKeywords = mapOf(
        "kotlin" to setOf(
            "fun", "val", "var", "when", "is", "in", "object", "companion", "data", "sealed", "open", "internal",
            "suspend", "inline", "reified", "lateinit", "by", "init", "constructor", "typealias", "as", "out",
        ),
        "java" to setOf("throws", "synchronized", "instanceof", "native", "transient", "record", "var", "yield"),
        "javascript" to setOf(
            "function", "var", "let", "const", "async", "await", "yield", "typeof", "instanceof", "undefined",
            "of", "in", "export", "from", "as", "get", "set", "delete", "void", "debugger", "with",
        ),
        "typescript" to setOf(
            "function", "let", "const", "async", "await", "type", "interface", "readonly", "keyof", "typeof",
            "undefined", "any", "unknown", "never", "string", "number", "export", "from", "as", "declare", "namespace",
        ),
        "python" to setOf(
            "def", "class", "if", "elif", "else", "for", "while", "in", "not", "and", "or", "is", "return",
            "import", "from", "as", "with", "try", "except", "finally", "raise", "lambda", "yield", "pass",
            "break", "continue", "None", "True", "False", "global", "nonlocal", "assert", "del", "async", "await", "self",
        ),
        "go" to setOf(
            "func", "package", "import", "var", "const", "type", "struct", "interface", "map", "chan", "go",
            "defer", "select", "range", "fallthrough", "nil", "string", "error", "any",
        ),
        "rust" to setOf(
            "fn", "let", "mut", "impl", "trait", "pub", "crate", "mod", "use", "match", "loop", "where", "move",
            "ref", "self", "Self", "dyn", "unsafe", "async", "await", "Some", "None", "Ok", "Err", "as", "in",
        ),
        "swift" to setOf(
            "func", "let", "var", "guard", "struct", "protocol", "extension", "init", "deinit", "self", "Self",
            "nil", "in", "as", "is", "inout", "mutating", "lazy", "weak", "where", "some", "any", "async", "await",
        ),
        "sql" to setOf(
            "select", "from", "where", "join", "inner", "left", "right", "outer", "on", "group", "by", "order",
            "having", "insert", "into", "values", "update", "set", "delete", "create", "table", "index", "drop",
            "alter", "primary", "key", "foreign", "references", "and", "or", "not", "null", "as", "distinct",
            "limit", "offset", "union", "all", "exists", "in", "like", "between", "case", "when", "then", "end",
            "count", "sum", "avg", "min", "max", "with", "is", "asc", "desc",
        ),
        "bash" to setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac", "function",
            "in", "echo", "export", "local", "return", "exit", "cd", "set", "unset", "source", "read",
        ),
    )
    private val languageAliases = mapOf(
        "kt" to "kotlin", "kts" to "kotlin", "js" to "javascript", "jsx" to "javascript", "ts" to "typescript",
        "tsx" to "typescript", "py" to "python", "python3" to "python", "golang" to "go", "rs" to "rust",
        "sh" to "bash", "shell" to "bash", "zsh" to "bash", "postgresql" to "sql", "mysql" to "sql",
        "sqlite" to "sql", "c++" to "cpp", "cc" to "cpp", "h" to "c", "hpp" to "cpp", "cs" to "csharp",
    )
    private val hashCommentLanguages = setOf("python", "bash", "ruby", "yaml", "perl", "r")
    private val caseInsensitiveLanguages = setOf("sql")

    fun canonicalLanguage(language: String?): String? = language?.lowercase()?.let { languageAliases[it] ?: it }

    fun tokenize(code: String, language: String?): List<CodeToken> {
        val canonical = canonicalLanguage(language)
        val keywords = cLikeKeywords + languageKeywords[canonical].orEmpty()
        val hashComments = canonical in hashCommentLanguages
        val dashComments = canonical == "sql"
        val caseInsensitive = canonical in caseInsensitiveLanguages
        val tokens = mutableListOf<CodeToken>()
        var position = 0

        fun add(text: String, kind: CodeTokenKind) {
            val last = tokens.lastOrNull()
            if (last != null && last.kind == kind && kind == CodeTokenKind.Plain) tokens[tokens.lastIndex] = CodeToken(last.text + text, kind)
            else tokens += CodeToken(text, kind)
        }

        while (position < code.length) {
            val character = code[position]
            when {
                code.startsWith("//", position) && !hashComments -> {
                    val end = code.indexOf('\n', position).let { if (it < 0) code.length else it }
                    add(code.substring(position, end), CodeTokenKind.Comment)
                    position = end
                }
                code.startsWith("/*", position) -> {
                    val end = code.indexOf("*/", position + 2).let { if (it < 0) code.length else it + 2 }
                    add(code.substring(position, end), CodeTokenKind.Comment)
                    position = end
                }
                (character == '#' && hashComments) || (dashComments && code.startsWith("--", position)) -> {
                    val end = code.indexOf('\n', position).let { if (it < 0) code.length else it }
                    add(code.substring(position, end), CodeTokenKind.Comment)
                    position = end
                }
                character == '"' || character == '\'' || character == '`' -> {
                    val tripleQuote = character != '`' && code.startsWith("$character$character$character", position)
                    val end = if (tripleQuote) {
                        code.indexOf("$character$character$character", position + 3).let { if (it < 0) code.length else it + 3 }
                    } else {
                        var cursor = position + 1
                        while (cursor < code.length && code[cursor] != character && (character == '`' || code[cursor] != '\n')) {
                            if (code[cursor] == '\\') cursor++
                            cursor++
                        }
                        (cursor + 1).coerceAtMost(code.length)
                    }
                    add(code.substring(position, end), CodeTokenKind.String)
                    position = end
                }
                character.isDigit() -> {
                    var end = position + 1
                    while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '.' || code[end] == '_')) end++
                    add(code.substring(position, end), CodeTokenKind.Number)
                    position = end
                }
                character.isLetter() || character == '_' || character == '$' || character == '@' -> {
                    var end = position + 1
                    while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_' || code[end] == '$')) end++
                    val word = code.substring(position, end)
                    val lookup = if (caseInsensitive) word.lowercase() else word
                    val nextNonSpace = code.substring(end).firstOrNull { it != ' ' }
                    val kind = when {
                        lookup in keywords -> CodeTokenKind.Keyword
                        nextNonSpace == '(' -> CodeTokenKind.Function
                        word.first().isUpperCase() && word.any { it.isLowerCase() } -> CodeTokenKind.Type
                        else -> CodeTokenKind.Plain
                    }
                    add(word, kind)
                    position = end
                }
                character in "{}[]()<>;,.:=+-*/%!&|^~?" -> {
                    add(character.toString(), CodeTokenKind.Punctuation)
                    position++
                }
                else -> {
                    add(character.toString(), CodeTokenKind.Plain)
                    position++
                }
            }
        }
        return tokens
    }
}
