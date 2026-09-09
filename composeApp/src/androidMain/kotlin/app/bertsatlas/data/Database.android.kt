package app.bertsatlas.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<BertsAtlasDatabase> {
    val applicationContext = context.applicationContext
    val databaseFile = applicationContext.getDatabasePath("berts-atlas.db")
    return Room.databaseBuilder(
        context = applicationContext,
        name = databaseFile.absolutePath,
    )
}

