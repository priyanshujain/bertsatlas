package app.bertopia.data

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

fun getDatabaseBuilder(databaseFile: File): RoomDatabase.Builder<BertopiaDatabase> {
    databaseFile.parentFile?.mkdirs()
    return Room.databaseBuilder<BertopiaDatabase>(name = databaseFile.absolutePath)
}
