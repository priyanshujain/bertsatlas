package app.bertsatlas.domain.store

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.dataTaskWithURL
import platform.Foundation.downloadTaskWithURL
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun platformStoreTransport(): StoreTransport = AppleStoreTransport()

@OptIn(ExperimentalForeignApi::class)
private class AppleStoreTransport : StoreTransport {
    private val session = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
            timeoutIntervalForRequest = TIMEOUT_SECONDS
        },
    )

    override suspend fun fetchText(url: String): String = suspendCancellableCoroutine { continuation ->
        val task = session.dataTaskWithURL(nsUrl(url)) { data: NSData?, response: NSURLResponse?, error: NSError? ->
            val failure = failureOf(url, response, error)
            if (failure != null) continuation.resumeWithException(failure)
            else continuation.resume(data?.toByteArray()?.decodeToString().orEmpty())
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }

    override suspend fun download(url: String, destinationPath: String, onBytes: (received: Long, total: Long?) -> Unit) {
        val finished = CompletableDeferred<Unit>()
        val task = session.downloadTaskWithURL(nsUrl(url)) { location: NSURL?, response: NSURLResponse?, error: NSError? ->
            val failure = failureOf(url, response, error)
            when {
                failure != null -> finished.completeExceptionally(failure)
                location == null -> finished.completeExceptionally(StoreException(StoreFailureKind.Network, "Download of $url produced no file"))
                else -> {
                    val manager = NSFileManager.defaultManager
                    manager.removeItemAtPath(destinationPath, null)
                    val moved = manager.moveItemAtURL(location, NSURL.fileURLWithPath(destinationPath), null)
                    if (moved) finished.complete(Unit)
                    else finished.completeExceptionally(StoreException(StoreFailureKind.Network, "Could not save the download of $url"))
                }
            }
        }
        task.resume()
        try {
            onBytes(0, null)
            while (withTimeoutOrNull(PROGRESS_INTERVAL_MILLIS) { finished.await() } == null) {
                onBytes(task.countOfBytesReceived, task.countOfBytesExpectedToReceive.takeIf { it > 0 })
            }
            onBytes(task.countOfBytesReceived, task.countOfBytesExpectedToReceive.takeIf { it > 0 })
        } finally {
            if (!finished.isCompleted) task.cancel()
        }
    }

    private fun nsUrl(url: String): NSURL =
        NSURL.URLWithString(url) ?: throw StoreException(StoreFailureKind.Network, "Not a valid address: $url")

    private fun failureOf(url: String, response: NSURLResponse?, error: NSError?): StoreException? {
        if (error != null) return StoreException(StoreFailureKind.Network, "Could not reach $url: ${error.localizedDescription}")
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: return null
        return if (status in 200..299) null else StoreException(StoreFailureKind.HttpStatus, "$url answered $status")
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
        return bytes
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20.0
        const val PROGRESS_INTERVAL_MILLIS = 100L
    }
}
