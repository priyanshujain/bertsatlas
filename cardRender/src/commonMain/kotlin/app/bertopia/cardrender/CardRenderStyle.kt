package app.bertopia.cardrender

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

@Immutable
data class CardRenderStyle(
    val text: Color,
    val mutedText: Color,
    val accent: Color,
    val onAccent: Color,
    val surface: Color,
    val outline: Color,
    val body: TextStyle,
    val codeBackground: Color = lerp(surface, text, 0.07f),
    val correct: Color = Color(0xFF2E7D4F),
    val wrong: Color = Color(0xFFC0392B),
    val hiddenMask: Color = Color(0xFFFF8E8E),
    val inactiveMask: Color = Color(0xFFFFEBA2),
    val codeFamily: FontFamily? = null,
    val syntaxColors: Map<CodeTokenKind, Color> = mapOf(
        CodeTokenKind.Keyword to Color(0xFF8E3B7A),
        CodeTokenKind.String to Color(0xFF2E7D32),
        CodeTokenKind.Comment to Color(0xFF7D7D7D),
        CodeTokenKind.Number to Color(0xFFB5531F),
        CodeTokenKind.Type to Color(0xFF1F5F99),
        CodeTokenKind.Function to Color(0xFF27635B),
    ),
)
