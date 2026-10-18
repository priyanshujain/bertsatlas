package app.bertopia.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.bertopia.cardrender.CardRenderStyle

val CardGalleryAccent = Color(0xFFB4532A)

@Composable
fun bertopiaCardRenderStyle(accent: Color = BertopiaTheme.colors.bertopiaBlue): CardRenderStyle {
    val colors = BertopiaTheme.colors
    return CardRenderStyle(
        text = colors.plum,
        mutedText = colors.mutedPlum.copy(alpha = 0.78f),
        accent = accent,
        onAccent = colors.white,
        surface = colors.parchment,
        outline = colors.outline,
        body = BertopiaTheme.typography.body.copy(fontSize = BertopiaTheme.typography.body.fontSize * 1.1f, lineHeight = BertopiaTheme.typography.body.lineHeight * 1.1f),
    )
}
