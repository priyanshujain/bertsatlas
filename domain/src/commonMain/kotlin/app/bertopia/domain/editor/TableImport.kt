package app.bertopia.domain.editor

import app.bertopia.domain.pack.BertNoteType

data class ParsedTable(val delimiter: Char, val rows: List<List<String>>) {
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0
}

data class TableImportRow(val rowNumber: Int, val draft: CardDraft, val problems: List<EditorProblem>) {
    val isValid: Boolean get() = problems.isEmpty()
}

data class TableImportPlan(
    val style: BertNoteType,
    val hasHeader: Boolean,
    val columnFields: Map<Int, String>,
    val rows: List<TableImportRow>,
) {
    val validDrafts: List<CardDraft> get() = rows.filter { it.isValid }.map { it.draft }
    val invalidRows: List<TableImportRow> get() = rows.filterNot { it.isValid }
}

object TableImport {
    const val TAGS_COLUMN = "Tags"
    const val SECTION_COLUMN = "Section"

    fun parse(text: String, delimiter: Char? = null): ParsedTable {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').removePrefix("﻿")
        val chosen = delimiter ?: detectDelimiter(normalized)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        fun endCell() {
            row += cell.toString()
            cell.clear()
        }
        fun endRow() {
            endCell()
            if (row.any { it.isNotBlank() }) rows += row
            row = mutableListOf()
        }
        while (index < normalized.length) {
            val char = normalized[index]
            when {
                quoted && char == '"' && normalized.getOrNull(index + 1) == '"' -> { cell.append('"'); index++ }
                quoted && char == '"' -> quoted = false
                quoted -> cell.append(char)
                char == '"' && cell.isBlank() -> { cell.clear(); quoted = true }
                char == chosen -> endCell()
                char == '\n' -> endRow()
                else -> cell.append(char)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return ParsedTable(chosen, rows.map { cells -> cells.map { it.trim() } })
    }

    fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        return listOf('\t', ',', ';', '|').maxByOrNull { candidate -> firstLine.count { it == candidate } }
            ?.takeIf { candidate -> firstLine.contains(candidate) } ?: ','
    }

    fun looksLikeHeader(table: ParsedTable, style: BertNoteType): Boolean {
        val first = table.rows.firstOrNull() ?: return false
        val names = headerNames(style)
        return first.any { normalize(it) in names }
    }

    fun suggestedColumnFields(table: ParsedTable, style: BertNoteType, hasHeader: Boolean): Map<Int, String> {
        val form = CardForms.forStyle(style)
        if (hasHeader) {
            val header = table.rows.first()
            return header.mapIndexedNotNull { column, title ->
                val key = normalize(title)
                val field = form.fields.firstOrNull { normalize(it.name) == key || normalize(it.label) == key }?.name
                    ?: TAGS_COLUMN.takeIf { normalize(it) == key }
                    ?: SECTION_COLUMN.takeIf { normalize(it) == key }
                field?.let { column to it }
            }.toMap()
        }
        return form.fields.take(table.columnCount).mapIndexed { column, field -> column to field.name }.toMap()
    }

    fun plan(table: ParsedTable, style: BertNoteType, hasHeader: Boolean? = null, columnFields: Map<Int, String>? = null): TableImportPlan {
        val header = hasHeader ?: looksLikeHeader(table, style)
        val mapping = columnFields ?: suggestedColumnFields(table, style, header)
        val form = CardForms.forStyle(style)
        val unknown = mapping.values.filter { it != TAGS_COLUMN && it != SECTION_COLUMN && form.field(it) == null }
        if (unknown.isNotEmpty()) throw EditorException.invalid("${form.title} cards have no field ${unknown.joinToString()}")
        val dataRows = if (header) table.rows.drop(1) else table.rows
        val firstRowNumber = if (header) 2 else 1
        val rows = dataRows.mapIndexed { offset, cells ->
            val values = mapping.mapNotNull { (column, field) -> cells.getOrNull(column)?.let { field to unescapeLines(it) } }.toMap()
            val fields = values.filterKeys { it != TAGS_COLUMN && it != SECTION_COLUMN }.filterValues { it.isNotEmpty() }
            val tags = values[TAGS_COLUMN].orEmpty().split(Regex("[\\s,]+")).filter { it.isNotBlank() }
            val draft = CardDraft(style.name, fields, tags, values[SECTION_COLUMN]?.takeIf { it.isNotBlank() })
            val problems = form.missingRequiredFields(fields).map { field ->
                EditorProblem("${field.label} is empty", index = offset, field = field.name)
            }
            TableImportRow(firstRowNumber + offset, draft, problems)
        }
        return TableImportPlan(style, header, mapping, rows)
    }

    private fun unescapeLines(value: String): String = value.replace("\\n", "\n")

    private fun headerNames(style: BertNoteType): Set<String> =
        CardForms.forStyle(style).fields.flatMap { listOf(normalize(it.name), normalize(it.label)) }.toSet() +
            normalize(TAGS_COLUMN) + normalize(SECTION_COLUMN)

    private fun normalize(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }
}
