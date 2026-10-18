package app.bertopia.cardrender

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bertopia.cardrender.resources.Res
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.decodeToSvgPainter

private data class RenderedMath(val svg: String, val widthDp: Float, val heightDp: Float)

private sealed interface MathOutcome {
    data object Rendering : MathOutcome
    data object Unavailable : MathOutcome
    data class Rendered(val math: RenderedMath) : MathOutcome
}

private class MathJaxProcess(val process: Process, val requests: BufferedWriter, val responses: BufferedReader)

private object JavaScriptCoreMathJax {
    private const val JAVASCRIPT_CORE = "/System/Library/Frameworks/JavaScriptCore.framework/Versions/A/Helpers/jsc"
    private val lock = Mutex()
    private val cache = mutableMapOf<MathRequest, RenderedMath?>()
    private var running: MathJaxProcess? = null
    private var unavailable = false

    suspend fun render(request: MathRequest): RenderedMath? = lock.withLock {
        if (cache.containsKey(request)) return@withLock cache[request]
        val result = withContext(Dispatchers.IO) { runCatching { renderWithProcess(request) }.getOrNull() }
        cache[request] = result
        result
    }

    private suspend fun renderWithProcess(request: MathRequest): RenderedMath? {
        val mathJax = running?.takeIf { it.process.isAlive } ?: start() ?: return null
        val argb = request.color.toArgb()
        val line = buildJsonObject {
            put("tex", JsonPrimitive(request.tex))
            put("display", JsonPrimitive(request.display))
            put("color", JsonPrimitive("#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')))
            put("size", JsonPrimitive(request.fontSizeDp))
        }.toString()
        mathJax.requests.write(line)
        mathJax.requests.newLine()
        mathJax.requests.flush()
        return mathJax.responses.readLine()?.let(::parse)
    }

    private suspend fun start(): MathJaxProcess? {
        if (unavailable || !File(JAVASCRIPT_CORE).canExecute()) return null
        val directory = Files.createTempDirectory("bert-mathjax").toFile().apply { deleteOnExit() }
        File(directory, "tex-svg.js").writeBytes(Res.readBytes(MathJaxPage.SCRIPT_PATH))
        File(directory, "adaptors").mkdirs()
        File(directory, "adaptors/liteDOM.js").writeBytes(bundledScript("liteDOM.js"))
        val server = File(directory, "server.js").apply { writeBytes(bundledScript("server.js")) }
        val process = ProcessBuilder(JAVASCRIPT_CORE, server.absolutePath, "--", directory.absolutePath)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        val responses = process.errorStream.bufferedReader()
        if (responses.readLine() != "ready") {
            process.destroy()
            unavailable = true
            return null
        }
        return MathJaxProcess(process, process.outputStream.bufferedWriter(), responses).also { running = it }
    }

    private fun bundledScript(name: String): ByteArray =
        requireNotNull(JavaScriptCoreMathJax::class.java.getResourceAsStream("/mathjax/$name")) { "Missing $name" }.use { it.readBytes() }

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
    val outcome by produceState<MathOutcome>(MathOutcome.Rendering, request) {
        value = JavaScriptCoreMathJax.render(request)?.let(MathOutcome::Rendered) ?: MathOutcome.Unavailable
    }
    val math = when (val current = outcome) {
        MathOutcome.Rendering -> return
        MathOutcome.Unavailable -> return TexSourceText(request, currentOnMeasured, modifier)
        is MathOutcome.Rendered -> current.math
    }
    LaunchedEffect(math) { currentOnMeasured(math.widthDp, math.heightDp) }
    val painter = remember(math, density) { math.svg.encodeToByteArray().decodeToSvgPainter(density) }
    Image(
        painter = painter,
        contentDescription = MathSpeech.speakable(request.tex),
        modifier = modifier.size(math.widthDp.dp, math.heightDp.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun TexSourceText(request: MathRequest, onMeasured: (widthDp: Float, heightDp: Float) -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    BasicText(
        text = request.tex,
        modifier = modifier,
        style = TextStyle(color = request.color, fontFamily = FontFamily.Monospace, fontSize = (request.fontSizeDp * 0.9f).sp),
        softWrap = false,
        onTextLayout = { layout ->
            with(density) { onMeasured(layout.size.width.toDp().value, layout.size.height.toDp().value) }
        },
    )
}
