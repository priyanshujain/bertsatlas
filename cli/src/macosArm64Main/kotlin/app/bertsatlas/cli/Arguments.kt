package app.bertsatlas.cli

class Arguments private constructor(
    val positionals: List<String>,
    private val values: Map<String, List<String>>,
    private val switches: Set<String>,
) {
    fun has(name: String): Boolean = name in switches

    fun value(name: String): String? = values[name]?.lastOrNull()

    fun values(name: String): List<String> = values[name].orEmpty()

    fun required(name: String): String = value(name) ?: throw CliFailure.usage("--$name is required")

    fun positional(index: Int, label: String): String = positionals.getOrNull(index) ?: throw CliFailure.usage("missing <$label>")

    fun positionalsFrom(index: Int, label: String): List<String> =
        positionals.drop(index).ifEmpty { throw CliFailure.usage("missing <$label>") }

    fun intValue(name: String, default: Int): Int {
        val raw = value(name) ?: return default
        return raw.toIntOrNull()?.takeIf { it > 0 } ?: throw CliFailure.usage("--$name must be a positive whole number")
    }

    fun shifted(count: Int): Arguments = Arguments(positionals.drop(count), values, switches)

    companion object {
        val VALUE_OPTIONS = setOf(
            "library", "file", "title", "subtitle", "id", "category", "subcategory", "accent",
            "section", "tag", "search", "limit", "out", "add", "remove", "field", "name", "deck", "style",
        )
        val SWITCHES = setOf("json", "legacy", "no-progress", "yes", "force", "help", "version", "header", "no-header", "dry-run")

        fun parse(arguments: List<String>): Arguments {
            val positionals = mutableListOf<String>()
            val values = mutableMapOf<String, MutableList<String>>()
            val switches = mutableSetOf<String>()
            var index = 0
            var optionsEnded = false
            while (index < arguments.size) {
                val argument = arguments[index]
                when {
                    optionsEnded || !argument.startsWith("-") || argument == "-" -> positionals += argument
                    argument == "--" -> optionsEnded = true
                    argument == "-h" -> switches += "help"
                    argument.startsWith("--") -> {
                        val name = argument.removePrefix("--").substringBefore('=')
                        val inlineValue = argument.substringAfter('=', missingDelimiterValue = "").takeIf { '=' in argument }
                        when (name) {
                            in SWITCHES -> {
                                if (inlineValue != null) throw CliFailure.usage("--$name does not take a value")
                                switches += name
                            }
                            in VALUE_OPTIONS -> {
                                val value = inlineValue ?: arguments.getOrNull(++index) ?: throw CliFailure.usage("--$name needs a value")
                                values.getOrPut(name) { mutableListOf() } += value
                            }
                            else -> throw CliFailure.usage("unknown option --$name")
                        }
                    }
                    else -> throw CliFailure.usage("unknown option $argument")
                }
                index++
            }
            return Arguments(positionals, values, switches)
        }
    }
}
