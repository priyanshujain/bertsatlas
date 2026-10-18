package app.bertopia.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PlayScope {
    val packIds: List<String>
    val progressKey: String

    @Serializable
    @SerialName("pack")
    data class Pack(val packId: String, val sections: List<String> = emptyList()) : PlayScope {
        override val packIds: List<String> get() = listOf(packId)
        override val progressKey: String get() = packId
    }

    @Serializable
    @SerialName("mix")
    data class Mix(val mixId: String, override val packIds: List<String>) : PlayScope {
        override val progressKey: String get() = MIX_PROGRESS_PREFIX + mixId
    }
}

const val MIX_PROGRESS_PREFIX = "mix:"
