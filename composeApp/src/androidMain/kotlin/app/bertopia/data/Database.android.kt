package app.bertopia.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<BertopiaDatabase> {
    val applicationContext = context.applicationContext
    val databaseFile = applicationContext.getDatabasePath("bertopia.db")
    return Room.databaseBuilder(
        context = applicationContext,
        name = databaseFile.absolutePath,
    )
}

