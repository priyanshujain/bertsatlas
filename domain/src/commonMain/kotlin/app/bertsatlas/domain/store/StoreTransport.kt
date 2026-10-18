package app.bertsatlas.domain.store

interface StoreTransport {
    suspend fun fetchText(url: String): String

    suspend fun download(url: String, destinationPath: String, onBytes: (received: Long, total: Long?) -> Unit)
}

expect fun platformStoreTransport(): StoreTransport

enum class StoreFailureKind { Network, HttpStatus, Tampered, Unsupported, NotInstalled, AlreadyInstalled, InvalidIndex, InvalidPack }

class StoreException(val kind: StoreFailureKind, message: String, cause: Throwable? = null) : Exception(message, cause)
