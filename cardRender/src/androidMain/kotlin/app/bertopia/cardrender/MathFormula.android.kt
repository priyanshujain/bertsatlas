package app.bertopia.cardrender

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.bertopia.cardrender.resources.Res

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun MathFormula(request: MathRequest, onMeasured: (widthDp: Float, heightDp: Float) -> Unit, modifier: Modifier) {
    val currentOnMeasured = rememberUpdatedState(onMeasured)
    val scriptUri = Res.getUri(MathJaxPage.SCRIPT_PATH)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                isFocusable = false
                isClickable = false
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                val mainThread = Handler(Looper.getMainLooper())
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun size(width: Float, height: Float) {
                            mainThread.post { currentOnMeasured.value(width, height) }
                        }
                    },
                    MathJaxPage.ANDROID_BRIDGE,
                )
                setOnTouchListener { _, _ -> false }
            }
        },
        update = { webView ->
            val html = MathJaxPage.html(request, scriptUri)
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
            }
        },
    )
}
