package app.bertopia.domain.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

actual fun platformStoreTransport(): StoreTransport = HttpStoreTransport()

private class HttpStoreTransport : StoreTransport {
    override suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        openConnection(url).let { connection ->
            try {
                requireSuccess(connection, url)
                connection.inputStream.use { it.readBytes().decodeToString() }
            } finally {
                connection.disconnect()
            }
        }
    }

    override suspend fun download(url: String, destinationPath: String, onBytes: (received: Long, total: Long?) -> Unit) =
        withContext(Dispatchers.IO) {
            val connection = openConnection(url)
            try {
                requireSuccess(connection, url)
                val total = connection.contentLengthLong.takeIf { it >= 0 }
                File(destinationPath).parentFile?.mkdirs()
                connection.inputStream.use { input ->
                    File(destinationPath).outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var received = 0L
                        onBytes(0, total)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            received += read
                            onBytes(received, total)
                        }
                    }
                }
            } catch (failure: IOException) {
                throw StoreException(StoreFailureKind.Network, "Download failed: ${failure.message}", failure)
            } finally {
                connection.disconnect()
            }
        }

    private fun openConnection(url: String): HttpURLConnection = try {
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            useCaches = false
        }
    } catch (failure: Exception) {
        throw StoreException(StoreFailureKind.Network, "Could not reach $url: ${failure.message}", failure)
    }

    private fun requireSuccess(connection: HttpURLConnection, url: String) {
        val status = try {
            connection.responseCode
        } catch (failure: IOException) {
            throw StoreException(StoreFailureKind.Network, "Could not reach $url: ${failure.message}", failure)
        }
        if (status !in 200..299) throw StoreException(StoreFailureKind.HttpStatus, "$url answered $status")
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val TIMEOUT_MILLIS = 20_000
    }
}
