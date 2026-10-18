package app.bertsatlas

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun interface PackFilePicker {
    fun pickPackFile(onPicked: (String?) -> Unit)
}

object NoPackFilePicker : PackFilePicker {
    override fun pickPackFile(onPicked: (String?) -> Unit) = onPicked(null)
}

object IncomingPackFiles {
    private val pending = MutableStateFlow<String?>(null)
    val pendingPath: StateFlow<String?> = pending.asStateFlow()

    fun deliver(path: String) {
        pending.value = path
    }

    fun take(): String? = pending.value.also { pending.value = null }
}
