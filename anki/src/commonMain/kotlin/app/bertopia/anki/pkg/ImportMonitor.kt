package app.bertopia.anki.pkg

import kotlin.coroutines.cancellation.CancellationException

enum class ImportStage { ReadingPackage, CopyingCards, CopyingMedia }

data class ImportProgress(val stage: ImportStage, val completed: Long, val total: Long?)

class ImportCancelledException : CancellationException("Import cancelled")

class ImportMonitor(
    private val onProgress: (ImportProgress) -> Unit = {},
    private val isCancelled: () -> Boolean = { false },
) {
    fun report(stage: ImportStage, completed: Long, total: Long?) = onProgress(ImportProgress(stage, completed, total))

    fun checkCancelled() {
        if (isCancelled()) throw ImportCancelledException()
    }

    companion object {
        val None = ImportMonitor()
    }
}
