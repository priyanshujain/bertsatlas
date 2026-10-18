package app.bertopia.cardrender

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.bertopia.cardrender.resources.Res
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.decodeToSvgPainter
import platform.CoreGraphics.CGRectZero
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import kotlin.coroutines.resume

private data class RenderedMath(val svg: String, val widthDp: Float, val heightDp: Float)

@OptIn(ExperimentalForeignApi::class)
private object OffscreenMathJax {
    private val lock = Mutex()
    private val cache = mutableMapOf<MathRequest, RenderedMath?>()
    private var webView: WKWebView? = null

    suspend fun render(request: MathRequest): RenderedMath? = lock.withLock {
        if (cache.containsKey(request)) return@withLock cache[request]
        val view = webView ?: createWebView().also { webView = it }
        if (!awaitReady(view)) return@withLock null
        val argb = request.color.toArgb()
        val colorHex = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')
        val script = "renderSvg(${MathJaxPage.jsString(request.tex)}, ${request.display}, '$colorHex', ${request.fontSizeDp})"
        val result = evaluate(view, script)?.let(::parse)
        cache[request] = result
        result
    }

    private suspend fun createWebView(): WKWebView {
        val source = Res.readBytes(MathJaxPage.SCRIPT_PATH).decodeToString()
        val configuration = WKWebViewConfiguration()
        configuration.userContentController.addUserScript(
            WKUserScript(source, WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd, true),
        )
        return WKWebView(frame = CGRectZero.readValue(), configuration = configuration).apply {
            loadHTMLString(MathJaxPage.offscreenHtml(), baseURL = null)
        }
    }

    private suspend fun awaitReady(view: WKWebView): Boolean {
        repeat(100) {
            if (evaluate(view, "window.mathReady === true ? 'ready' : 'waiting'") == "ready") return true
            delay(50)
        }
        return false
    }

    private suspend fun evaluate(view: WKWebView, script: String): String? = suspendCancellableCoroutine { continuation ->
        view.evaluateJavaScript(script) { value, _ -> if (continuation.isActive) continuation.resume(value as? String) }
    }

    private fun parse(json: String): RenderedMath? = runCatching {
        val fields = Json.parseToJsonElement(json).jsonObject
        RenderedMath(
            svg = fields.getValue("svg").jsonPrimitive.content,
            widthDp = fields.getValue("w").jsonPrimitive.float,
            heightDp = fields.getValue("h").jsonPrimitive.float,
        )
    }.getOrNull()
}

@Composable
actual fun MathFormula(request: MathRequest, onMeasured: (widthDp: Float, heightDp: Float) -> Unit, modifier: Modifier) {
    val currentOnMeasured by rememberUpdatedState(onMeasured)
    val density = LocalDensity.current
    val rendered by produceState<RenderedMath?>(null, request) { value = OffscreenMathJax.render(request) }
    val math = rendered ?: return
    LaunchedEffect(math) { currentOnMeasured(math.widthDp, math.heightDp) }
    val painter = remember(math, density) { math.svg.encodeToByteArray().decodeToSvgPainter(density) }
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier.size(math.widthDp.dp, math.heightDp.dp),
        contentScale = ContentScale.Fit,
    )
}
