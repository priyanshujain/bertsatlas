package app.bertsatlas

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import app.bertsatlas.data.BertsAtlasDatabase
import app.bertsatlas.data.PreferencesStore
import app.bertsatlas.data.buildBertsAtlasDatabase
import app.bertsatlas.data.createPreferencesStore
import app.bertsatlas.data.getDatabaseBuilder
import androidx.core.content.FileProvider
import app.bertsatlas.store.ShareOutcome
import java.io.File

private object AppStorage {
    private var database: BertsAtlasDatabase? = null
    private var preferences: PreferencesStore? = null

    @Synchronized
    fun database(context: Context): BertsAtlasDatabase =
        database ?: getDatabaseBuilder(context.applicationContext).buildBertsAtlasDatabase().also { database = it }

    @Synchronized
    fun preferences(context: Context): PreferencesStore =
        preferences ?: createPreferencesStore(context.applicationContext.filesDir.resolve("berts-atlas.preferences_pb").absolutePath).also { preferences = it }
}

class MainActivity : ComponentActivity() {
    private var controller: AppController? = null
    private var pickedFileCallback: ((String?) -> Unit)? = null
    private val appBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (controller?.handleBack() == true) return
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = pickedFileCallback
        pickedFileCallback = null
        if (uri == null) callback?.invoke(null) else copyIncomingFile(uri) { path -> callback?.invoke(path) }
    }
    private val packSharer = PackSharer { path, fileName, onDone ->
        val outcome = runCatching {
            val uri = FileProvider.getUriForFile(this, "app.bertsatlas.packs", File(path))
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share $fileName"))
        }
        onDone(if (outcome.isSuccess) ShareOutcome.Shared else ShareOutcome.Failed)
    }
    private var pickedImageCallback: ((String?) -> Unit)? = null
    private val pickVisualMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val callback = pickedImageCallback
        pickedImageCallback = null
        if (uri == null) callback?.invoke(null) else copyPickedImage(uri) { path -> callback?.invoke(path) }
    }
    private val imagePicker = ImagePicker { onPicked ->
        pickedImageCallback = onPicked
        pickVisualMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    private val filePicker = PackFilePicker { onPicked ->
        pickedFileCallback = onPicked
        openDocument.launch(arrayOf("*/*"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, appBackCallback)
        val database = AppStorage.database(this)
        val preferences = AppStorage.preferences(this)
        val feedback = AndroidDeviceFeedback(this)
        setContent {
            App(database, preferences, filesDir.resolve("packs").absolutePath, BuildConfig.DEBUG, feedback, filePicker, packSharer, imagePicker = imagePicker) { controller = it }
        }
        if (savedInstanceState == null) receivePackFile(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receivePackFile(intent)
    }

    override fun onStop() {
        controller?.pauseForInterruption()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        controller?.resumeAfterInterruption()
    }

    private fun receivePackFile(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.streamExtra()
            else -> null
        } ?: return
        copyIncomingFile(uri) { path -> if (path != null) IncomingPackFiles.deliver(path) }
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamExtra(): Uri? = getParcelableExtra(Intent.EXTRA_STREAM)

    private fun copyIncomingFile(uri: Uri, onCopied: (String?) -> Unit) {
        Thread {
            val path = runCatching {
                val directory = cacheDir.resolve("incoming").apply { mkdirs() }
                val target = File(directory, displayName(uri))
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Nothing to read" }
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target.absolutePath
            }.getOrNull()
            runOnUiThread { onCopied(path) }
        }.start()
    }

    private fun copyPickedImage(uri: Uri, onCopied: (String?) -> Unit) {
        Thread {
            val path = runCatching {
                val extension = when (contentResolver.getType(uri)) {
                    "image/png" -> "png"
                    "image/gif" -> "gif"
                    "image/webp" -> "webp"
                    "image/svg+xml" -> "svg"
                    else -> "jpg"
                }
                val directory = cacheDir.resolve("picked-images").apply { mkdirs() }
                val target = File(directory, "picture-${System.currentTimeMillis()}.$extension")
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Nothing to read" }
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target.absolutePath
            }.getOrNull()
            runOnUiThread { onCopied(path) }
        }.start()
    }

    private fun displayName(uri: Uri): String {
        val queried = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        val name = (queried ?: uri.lastPathSegment?.substringAfterLast('/') ?: "deck.apkg").replace(Regex("[^A-Za-z0-9._ -]"), "_")
        return if (name.endsWith(".apkg", true) || name.endsWith(".colpkg", true)) name else "$name.apkg"
    }
}
