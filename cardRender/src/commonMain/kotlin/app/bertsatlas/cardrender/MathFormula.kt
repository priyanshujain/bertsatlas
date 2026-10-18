package app.bertsatlas.cardrender

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class MathRequest(val tex: String, val display: Boolean, val color: Color, val fontSizeDp: Float)

@Composable
expect fun MathFormula(request: MathRequest, onMeasured: (widthDp: Float, heightDp: Float) -> Unit, modifier: Modifier)

object MathJaxPage {
    const val SCRIPT_PATH = "files/mathjax/tex-svg.js"
    const val ANDROID_BRIDGE = "BertMath"
    const val IOS_HANDLER = "bertMath"

    fun html(request: MathRequest, scriptSource: String?): String {
        val argb = request.color.toArgb()
        val colorHex = "#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0')
        val scriptTag = scriptSource?.let { "<script src=\"$it\"></script>" }.orEmpty()
        return """
            <!DOCTYPE html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
            <style>
            html, body { margin: 0; padding: 0; background: transparent; color: $colorHex; font-size: ${request.fontSizeDp}px; overflow: hidden; }
            #m { display: inline-block; padding: 2px 1px; white-space: nowrap; }
            mjx-container { margin: 0 !important; }
            </style>
            <script>
            function post(w, h) {
              if (window.$ANDROID_BRIDGE && window.$ANDROID_BRIDGE.size) { window.$ANDROID_BRIDGE.size(w, h); }
              else if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.$IOS_HANDLER) { window.webkit.messageHandlers.$IOS_HANDLER.postMessage(w + ',' + h); }
            }
            function render() {
              var target = document.getElementById('m');
              try { target.appendChild(MathJax.tex2svg(${jsString(request.tex)}, { display: ${request.display} })); document.head.appendChild(MathJax.svgStylesheet()); }
              catch (e) { target.textContent = ${jsString(request.tex)}; }
              var box = target.getBoundingClientRect();
              post(Math.ceil(box.width), Math.ceil(box.height));
            }
            window.MathJax = { options: { enableAssistiveMml: false }, svg: { fontCache: 'none' }, startup: { typeset: false, ready: function () { MathJax.startup.defaultReady(); MathJax.startup.promise.then(render); } } };
            </script>
            $scriptTag
            </head><body><span id="m"></span></body></html>
        """.trimIndent()
    }

    fun offscreenHtml(): String = """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>body { margin: 0; }</style>
        <script>
        window.MathJax = { options: { enableAssistiveMml: false }, svg: { fontCache: 'none' }, startup: { typeset: false, ready: function () { MathJax.startup.defaultReady(); MathJax.startup.promise.then(function () { window.mathReady = true; }); } } };
        function renderSvg(tex, display, color, size) {
          var holder = document.createElement('div');
          holder.style.fontSize = size + 'px';
          holder.style.position = 'absolute';
          holder.style.whiteSpace = 'nowrap';
          document.body.appendChild(holder);
          var node;
          try { node = MathJax.tex2svg(tex, { display: display }); } catch (e) { holder.remove(); return null; }
          holder.appendChild(node);
          var svg = node.querySelector('svg');
          var box = svg.getBoundingClientRect();
          svg.setAttribute('width', box.width);
          svg.setAttribute('height', box.height);
          svg.removeAttribute('style');
          var markup = new XMLSerializer().serializeToString(svg).split('currentColor').join(color);
          holder.remove();
          return JSON.stringify({ svg: markup, w: box.width, h: box.height });
        }
        </script>
        </head><body></body></html>
    """.trimIndent()

    fun estimatedSize(request: MathRequest): Pair<Float, Float> {
        val width = (request.tex.length * 0.5f + 1.2f).coerceIn(1.5f, 18f) * request.fontSizeDp
        val height = (if (request.display) 2.4f else 1.5f) * request.fontSizeDp
        return width to height
    }

    fun jsString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '<' -> append("\\u003c")
                '>' -> append("\\u003e")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> append(character)
            }
        }
        append('"')
    }
}

object MathSpeech {
    private val words = linkedMapOf(
        "\\times" to " times ", "\\cdot" to " times ", "\\div" to " divided by ", "\\pm" to " plus or minus ",
        "\\leq" to " less than or equal to ", "\\le" to " less than or equal to ", "\\geq" to " greater than or equal to ", "\\ge" to " greater than or equal to ",
        "\\neq" to " not equal to ", "\\ne" to " not equal to ", "\\approx" to " approximately ", "\\to" to " to ", "\\rightarrow" to " to ",
        "\\infty" to " infinity ", "\\sum" to " sum ", "\\prod" to " product ", "\\int" to " integral ", "\\lim" to " limit ", "\\partial" to " partial ",
        "\\sin" to " sine ", "\\cos" to " cosine ", "\\tan" to " tangent ", "\\log" to " log ", "\\ln" to " natural log ",
        "\\in" to " in ", "\\forall" to " for all ", "\\exists" to " there exists ",
    )
    private val greek = listOf(
        "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "lambda", "mu", "nu", "xi",
        "pi", "rho", "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega",
    )

    fun speakable(tex: String): String {
        var text = tex
        repeat(4) {
            text = text.replace(Regex("""\\[dt]?frac\{([^{}]*)\}\{([^{}]*)\}""")) { "${it.groupValues[1]} over ${it.groupValues[2]}" }
            text = text.replace(Regex("""\\sqrt\{([^{}]*)\}""")) { "square root of ${it.groupValues[1]}" }
        }
        text = text.replace(Regex("""\^\{?2\}?""")) { " squared " }
        text = text.replace(Regex("""\^\{?3\}?""")) { " cubed " }
        text = text.replace(Regex("""\^\{([^{}]*)\}|\^(\S)""")) { " to the power of ${it.groupValues[1].ifEmpty { it.groupValues[2] }} " }
        text = text.replace(Regex("""_\{([^{}]*)\}|_(\S)""")) { " sub ${it.groupValues[1].ifEmpty { it.groupValues[2] }} " }
        words.forEach { (command, word) -> text = text.replace(Regex(Regex.escape(command) + "(?![a-zA-Z])"), word) }
        greek.forEach { letter ->
            text = text.replace(Regex("\\\\" + letter + "(?![a-zA-Z])"), " $letter ")
            text = text.replace(Regex("\\\\" + letter.replaceFirstChar { it.uppercase() } + "(?![a-zA-Z])"), " capital $letter ")
        }
        text = text.replace(Regex("""\\(left|right|big|Big|bigg|Bigg|displaystyle|mathrm|text|mathbf|operatorname)"""), " ")
        text = text.replace("=", " equals ").replace("+", " plus ").replace(Regex("(^|\\s)-"), "$1 minus ").replace(Regex("(\\w)-(\\w)"), "$1 minus $2")
        text = text.replace(Regex("""\\[a-zA-Z]+"""), " ").replace(Regex("""[{}\\$]"""), " ")
        return text.replace(Regex("\\s+"), " ").trim().ifEmpty { "math formula" }
    }
}
