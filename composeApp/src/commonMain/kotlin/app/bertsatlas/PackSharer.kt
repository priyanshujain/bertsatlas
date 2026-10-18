package app.bertsatlas

import app.bertsatlas.store.ShareOutcome

fun interface PackSharer {
    fun share(path: String, fileName: String, onDone: (ShareOutcome) -> Unit)
}

object NoPackSharer : PackSharer {
    override fun share(path: String, fileName: String, onDone: (ShareOutcome) -> Unit) = onDone(ShareOutcome.Failed)
}
