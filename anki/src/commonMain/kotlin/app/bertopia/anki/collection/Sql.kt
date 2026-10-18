package app.bertopia.anki.collection

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

internal fun <T> SQLiteConnection.rows(sql: String, vararg arguments: Any?, read: (SQLiteStatement) -> T): List<T> =
    prepare(sql).use { statement ->
        statement.bindAll(arguments)
        buildList { while (statement.step()) add(read(statement)) }
    }

internal fun SQLiteConnection.execute(sql: String, vararg arguments: Any?) {
    prepare(sql).use { statement ->
        statement.bindAll(arguments)
        statement.step()
    }
}

internal fun SQLiteConnection.longValue(sql: String, vararg arguments: Any?): Long =
    rows(sql, *arguments) { if (it.isNull(0)) 0L else it.getLong(0) }.firstOrNull() ?: 0L

private fun SQLiteStatement.bindAll(arguments: Array<out Any?>) {
    arguments.forEachIndexed { index, argument ->
        val position = index + 1
        when (argument) {
            null -> bindNull(position)
            is Long -> bindLong(position, argument)
            is Int -> bindLong(position, argument.toLong())
            is Double -> bindDouble(position, argument)
            is String -> bindText(position, argument)
            is ByteArray -> bindBlob(position, argument)
            is Boolean -> bindLong(position, if (argument) 1 else 0)
            else -> error("Unsupported SQL argument ${argument::class}")
        }
    }
}
