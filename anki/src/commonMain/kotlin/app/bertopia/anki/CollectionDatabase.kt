package app.bertopia.anki

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun openCollectionDatabase(path: String): SQLiteConnection = BundledSQLiteDriver().open(path)
