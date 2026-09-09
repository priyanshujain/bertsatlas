package app.bertsatlas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.bertsatlas.data.buildBertsAtlasDatabase
import app.bertsatlas.data.createPreferencesStore
import app.bertsatlas.data.getDatabaseBuilder

class MainActivity : ComponentActivity() {
    private var controller: AppController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = getDatabaseBuilder(this).buildBertsAtlasDatabase()
        val preferences = createPreferencesStore(filesDir.resolve("berts-atlas.preferences_pb").absolutePath)
        val feedback = AndroidDeviceFeedback(this)
        setContent {
            App(database, preferences, BuildConfig.DEBUG, feedback) { controller = it }
        }
    }

    override fun onStop() {
        controller?.pauseForInterruption()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        controller?.resumeAfterInterruption()
    }
}
