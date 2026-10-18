package app.bertsatlas.cardrender

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bertsatlas.cardrender.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory

data class GallerySide(val label: String, val html: String)

data class GallerySample(
    val title: String,
    val sides: List<GallerySide>,
    val typedExpected: String? = null,
)

object GallerySamples {
    val mediaFiles = listOf("cell.png", "wide.png", "shape.svg", "tone.m4a", "tone.mp3", "tone.wav")

    const val CLOZE_TEXT = "{{c1::Paris}} is the capital of {{c2::France::country}}."
    const val CLOZE_QUESTION_ONE = "<span class=\"cloze\" data-cloze=\"Paris\" data-ordinal=\"1\">[...]</span> is the capital of " +
        "<span class=\"cloze-inactive\" data-ordinal=\"2\">France</span>."
    const val CLOZE_QUESTION_TWO = "<span class=\"cloze-inactive\" data-ordinal=\"1\">Paris</span> is the capital of " +
        "<span class=\"cloze\" data-cloze=\"France\" data-ordinal=\"2\">[country]</span>."
    const val CLOZE_ANSWER_ONE = "<span class=\"cloze\" data-ordinal=\"1\">Paris</span> is the capital of " +
        "<span class=\"cloze-inactive\" data-ordinal=\"2\">France</span>."

    const val OCCLUSION_MASKS = "{{c1::image-occlusion:rect:left=.1333:top=.2167:width=.2889:height=.25:oi=1}}" +
        "{{c2::image-occlusion:ellipse:left=.5778:top=.2167:rx=.1444:ry=.125:oi=1}}" +
        "{{c3::image-occlusion:polygon:points=.1333,.5833 .4222,.5833 .2778,.8333:oi=1}}" +
        "{{c4::image-occlusion:rect:left=520:top=350:width=260:height=150:oi=1}}"
    const val OCCLUSION_QUESTION_ONE = "<div class=occlusion>" +
        "<span class=\"cloze\" data-cloze=\"image-occlusion:rect:left=.1333:top=.2167:width=.2889:height=.25:oi=1\" data-ordinal=\"1\">[...]</span>" +
        "<span class=\"cloze-inactive\" data-ordinal=\"2\">image-occlusion:ellipse:left=.5778:top=.2167:rx=.1444:ry=.125:oi=1</span>" +
        "<span class=\"cloze-inactive\" data-ordinal=\"3\">image-occlusion:polygon:points=.1333,.5833 .4222,.5833 .2778,.8333:oi=1</span>" +
        "<span class=\"cloze-inactive\" data-ordinal=\"4\">image-occlusion:rect:left=520:top=350:width=260:height=150:oi=1</span>" +
        "</div>\n<img src=\"cell.png\">"
    const val OCCLUSION_ANSWER_ONE = "<div class=occlusion>" +
        "<span class=\"cloze\" data-ordinal=\"1\">image-occlusion:rect:left=.1333:top=.2167:width=.2889:height=.25:oi=1</span>" +
        "<span class=\"cloze-inactive\" data-ordinal=\"2\">image-occlusion:ellipse:left=.5778:top=.2167:rx=.1444:ry=.125:oi=1</span>" +
        "<span class=\"cloze-inactive\" data-ordinal=\"3\">image-occlusion:polygon:points=.1333,.5833 .4222,.5833 .2778,.8333:oi=1</span>" +
        "<span class=\"cloze-inactive\" data-ordinal=\"4\">image-occlusion:rect:left=520:top=350:width=260:height=150:oi=1</span>" +
        "</div>\n<img src=\"cell.png\">\n<div class=extra>Nucleus</div>"

    val all = listOf(
        GallerySample(
            "Text styles",
            listOf(
                GallerySide(
                    "Card",
                    "<b>Bold</b>, <i>italic</i>, <u>underline</u> and <s>strike</s>. " +
                        "<span style=\"color: #c0392b\">Red text</span>, <font color=\"#1f5f99\" size=\"5\">big blue</font>, " +
                        "<span style=\"font-size: 14px\">small</span>. H<sub>2</sub>O and E = mc<sup>2</sup>. " +
                        "<mark>Highlighted</mark> and <code>inline code</code>. White text stays readable: <font color=\"white\">hello</font>.",
                ),
            ),
        ),
        GallerySample(
            "Structure",
            listOf(
                GallerySide(
                    "Card",
                    "<h2>Fruit basics</h2><p>Paragraph one.</p><p>Paragraph two, then a list:</p>" +
                        "<ul><li>Apples</li><li>Pears<ol><li>Conference</li><li>Williams</li></ol></li></ul><hr>" +
                        "<table><tr><th>French</th><th>English</th></tr><tr><td>chat</td><td>cat</td></tr><tr><td>chien</td><td><i>dog</i></td></tr></table>" +
                        "<blockquote>A quoted line from a textbook.</blockquote>",
                ),
            ),
        ),
        GallerySample(
            "Images, tap to zoom",
            listOf(
                GallerySide(
                    "Card",
                    "Which organelle is orange?<br><img src=\"cell.png\" alt=\"Cell diagram\"><img src=\"wide.png\" alt=\"Wide image\"><img src=\"shape.svg\" alt=\"Vector shapes\">" +
                        "<img src=\"missing-picture.png\" alt=\"A picture that is not in the pack\">",
                ),
            ),
        ),
        GallerySample(
            "Audio",
            listOf(GallerySide("Card", "Listen: AAC [sound:tone.m4a] MP3 [sound:tone.mp3] WAV [sound:tone.wav] missing [sound:not-here.mp3]")),
        ),
        GallerySample(
            "Fill the blank",
            listOf(
                GallerySide("Blank 1", CLOZE_QUESTION_ONE),
                GallerySide("Blank 2 with hint", CLOZE_QUESTION_TWO),
                GallerySide("Answer 1", CLOZE_ANSWER_ONE),
            ),
        ),
        GallerySample(
            "Type the answer",
            listOf(
                GallerySide("Question", "What is the capital of Australia?<br>[[type:Answer]]"),
                GallerySide("Answer", "What is the capital of Australia?<hr id=answer>[[type:Answer]]"),
            ),
            typedExpected = "Canberra",
        ),
        GallerySample(
            "Code",
            listOf(
                GallerySide(
                    "Card",
                    "Reverse a list in Kotlin:<pre><code class=\"language-kotlin\">fun &lt;T&gt; reverse(items: List&lt;T&gt;): List&lt;T&gt; {\n" +
                        "    // walk from the end\n    val result = mutableListOf&lt;T&gt;()\n    for (i in items.indices.reversed()) result += items[i]\n" +
                        "    return result\n}</code></pre>" +
                        "<pre><code class=\"language-python\">def fib(n):\n    # classic\n    return n if n &lt; 2 else fib(n - 1) + fib(n - 2)</code></pre>" +
                        "<pre class=\"lang-sql\">SELECT name, COUNT(*) FROM users -- totals\nGROUP BY name;</pre>" +
                        "Use <code>git status</code> often.",
                ),
            ),
        ),
        GallerySample(
            "Image occlusion",
            listOf(
                GallerySide("Question 1", OCCLUSION_QUESTION_ONE),
                GallerySide("Answer 1", OCCLUSION_ANSWER_ONE),
            ),
        ),
        GallerySample(
            "Math",
            listOf(
                GallerySide(
                    "Card",
                    "Euler: \\(e^{i\\pi} + 1 = 0\\) is famous.<br>\\[\\int_0^1 x^2\\,dx = \\frac{1}{3}\\]" +
                        "Legacy inline [\$]a^2 + b^2 = c^2[/\$] and display:" +
                        "[\$\$]\\sum_{n=1}^{\\infty} \\frac{1}{n^2} = \\frac{\\pi^2}{6}[/\$\$][latex]\\alpha + \\beta = \\gamma[/latex]",
                ),
            ),
        ),
        GallerySample(
            "Hints and unsupported markup",
            listOf(
                GallerySide(
                    "Card",
                    "Which planet is red?<div class=hint><a class=\"hint\" data-field=\"Hint\">Hint</a>" +
                        "<div class=\"hint\" style=\"display: none\">Named after the Roman god of war</div></div>" +
                        "<div><marquee>Old marquee text still reads.</marquee></div><iframe src=\"https://example.com\"></iframe>" +
                        "<script>alert('ignored')</script><p><custom-tag>Custom tag text shows as plain text.</custom-tag></p>",
                ),
            ),
        ),
    )
}

private suspend fun prepareGalleryMedia(): String = withContext(Dispatchers.Default) {
    val directory = Path(SystemTemporaryDirectory, "bert-card-gallery")
    SystemFileSystem.createDirectories(directory)
    GallerySamples.mediaFiles.forEach { name ->
        val target = Path(directory, name)
        if (!SystemFileSystem.exists(target)) {
            val bytes = Res.readBytes("files/gallery/$name")
            SystemFileSystem.sink(target).buffered().use { it.write(bytes) }
        }
    }
    directory.toString()
}

@Composable
fun CardGallery(style: CardRenderStyle, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    val mediaDirectory by produceState<String?>(null) { value = prepareGalleryMedia() }
    Column(modifier.fillMaxSize().background(style.surface).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Card gallery", Modifier.weight(1f), style = style.body.copy(color = style.text, fontWeight = FontWeight.Bold, fontSize = style.body.fontSize * 1.3f))
            if (onClose != null) {
                BasicText(
                    "Done",
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onClose).padding(horizontal = 14.dp, vertical = 8.dp),
                    style = style.body.copy(color = style.accent, fontWeight = FontWeight.Bold),
                )
            }
        }
        val directory = mediaDirectory ?: return@Column
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(GallerySamples.all, key = { it.title }) { sample -> GallerySampleCard(sample, style, directory) }
        }
    }
}

@Composable
private fun GallerySampleCard(sample: GallerySample, style: CardRenderStyle, mediaDirectory: String) {
    var sideIndex by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    val side = sample.sides[sideIndex]
    val document = remember(side.html) { CardDocumentBuilder.build(side.html) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(style.codeBackground.copy(alpha = 0.5f))
            .border(1.dp, style.outline.copy(alpha = 0.4f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(sample.title, style = style.body.copy(color = style.mutedText, fontWeight = FontWeight.Bold, fontSize = style.body.fontSize * 0.8f))
        if (sample.sides.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sample.sides.forEachIndexed { index, option ->
                    val selected = index == sideIndex
                    BasicText(
                        option.label,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) style.accent else style.surface)
                            .border(1.dp, style.accent.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .clickable(role = Role.Tab) { sideIndex = index }
                            .semantics { this.selected = selected }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        style = style.body.copy(color = if (selected) style.onAccent else style.accent, fontSize = style.body.fontSize * 0.75f, fontWeight = FontWeight.SemiBold),
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            CardView(
                document = document,
                style = style,
                mediaDirectory = mediaDirectory,
                typeAnswer = sample.typedExpected?.let { expected ->
                    TypeAnswerState(typed = typed, onTypedChange = { typed = it }, expected = expected, revealed = sideIndex > 0)
                },
            )
        }
        Box(Modifier.height(2.dp))
    }
}
