package app.bertsatlas.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.bertsatlas.cardrender.CardRenderStyle

val CardGalleryAccent = Color(0xFFB4532A)

@Composable
fun atlasCardRenderStyle(accent: Color = AtlasTheme.colors.atlasBlue): CardRenderStyle {
    val colors = AtlasTheme.colors
    return CardRenderStyle(
        text = colors.plum,
        mutedText = colors.mutedPlum.copy(alpha = 0.78f),
        accent = accent,
        onAccent = colors.white,
        surface = colors.parchment,
        outline = colors.outline,
        body = AtlasTheme.typography.body.copy(fontSize = AtlasTheme.typography.body.fontSize * 1.1f, lineHeight = AtlasTheme.typography.body.lineHeight * 1.1f),
    )
}
