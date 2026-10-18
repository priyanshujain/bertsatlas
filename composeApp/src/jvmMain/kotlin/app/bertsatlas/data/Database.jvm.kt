package app.bertsatlas.data

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

fun getDatabaseBuilder(databaseFile: File): RoomDatabase.Builder<BertsAtlasDatabase> {
    databaseFile.parentFile?.mkdirs()
    return Room.databaseBuilder<BertsAtlasDatabase>(name = databaseFile.absolutePath)
}
