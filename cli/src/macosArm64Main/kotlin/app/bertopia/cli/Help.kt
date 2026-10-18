package app.bertopia.cli

object Help {
    private val overview = """
        bert: author and inspect Bertopia packs

        Usage: bert <command> [arguments] [--json] [--library <dir>]

        Packs
          packs list                                  List installed packs
          packs show <pack>                           Pack details
          packs create --title T --category C --subcategory S [--subtitle] [--accent #RRGGBB] [--id]
          packs rename <pack> --title T [--subtitle S]
          packs delete <pack> --yes
          packs copy <pack> [--title T] [--id ID]     Editable copy, progress kept

        Cards
          cards list <pack> [--section S] [--tag T] [--search Q] [--limit N]
          cards show <pack> <note>                    <note> is a note id or guid
          cards add <pack> [--file F]                 JSON card, array, or {"cards":[...]} on stdin
          cards import-table <pack> --style S [--file F] [--header|--no-header] [--dry-run]
                                                      CSV, TSV or pasted table; valid rows are added
          cards edit <pack> <note> [--file F] [--field Name=Value]...
          cards delete <pack> <note>...
          cards tag <pack> <note>... [--add T]... [--remove T]...
          cards move <pack> <note>... --section S     Empty section moves to the top of the pack

        Media
          media add <pack> <file>... [--name N] [--force]
          media list <pack>
          media remove <pack> <name>... [--force]

        Card styles
          styles list
          styles schema [style]                       JSON schema for card input

        Checking and files
          validate <pack | file.apkg | source dir>
          import <file> [--deck ID]... [--title T] [--category C --subcategory S]
          export <pack> [--out F] [--legacy] [--no-progress]
          build <source dir> [--out F] [--legacy]

        Progress
          stats <pack>
          weak <pack> [--limit N]

        Every command accepts --json. The library defaults to ~/Library/Application Support/Bertopia/packs
        and can be changed with --library or BERT_LIBRARY.

        Exit codes: 0 ok, 1 failure, 2 usage, 3 not found, 4 invalid, 5 read-only, 6 conflict or busy
    """.trimIndent()

    fun text(topic: String?): String = when (topic) {
        null -> overview
        "packs", "cards", "media", "styles", "validate", "import", "export", "build", "io", "stats", "weak" -> {
            val keys = if (topic == "io") listOf("import", "export", "build") else listOf(topic)
            val lines = overview.lines().filter { line -> keys.any { line.trimStart().startsWith("$it ") || line.trimStart() == it } }
            (listOf("Usage:") + lines).joinToString("\n")
        }
        else -> overview
    }
}
