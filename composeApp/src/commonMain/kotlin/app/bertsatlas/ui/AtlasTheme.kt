package app.bertsatlas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import berts_atlas.composeapp.generated.resources.Res
import berts_atlas.composeapp.generated.resources.atkinson_hyperlegible_next_bold
import berts_atlas.composeapp.generated.resources.atkinson_hyperlegible_next_regular
import berts_atlas.composeapp.generated.resources.atkinson_hyperlegible_next_semibold
import org.jetbrains.compose.resources.Font

@Immutable
data class AtlasColors(
    val parchment: Color,
    val paper: Color,
    val plum: Color,
    val mutedPlum: Color,
    val atlasBlue: Color,
    val atlasBluePressed: Color,
    val coral: Color,
    val mustard: Color,
    val success: Color,
    val error: Color,
    val outline: Color,
    val white: Color,
)

@Immutable
data class AtlasTypography(
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val detail: TextStyle,
    val timer: TextStyle,
)

private val LightAtlasColors = AtlasColors(
    parchment = Color(0xFFF7F0DF),
    paper = Color(0xFFFFFBF2),
    plum = Color(0xFF3E2447),
    mutedPlum = Color(0xFF69536F),
    atlasBlue = Color(0xFF17647E),
    atlasBluePressed = Color(0xFF0E4B60),
    coral = Color(0xFFE96E5B),
    mustard = Color(0xFFE3AD35),
    success = Color(0xFF147568),
    error = Color(0xFF9C3E32),
    outline = Color(0xFF8A748E),
    white = Color(0xFFFFFFFF),
)

private fun atlasTypography(fontFamily: FontFamily) = AtlasTypography(
    display = TextStyle(fontFamily = fontFamily, fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    title = TextStyle(fontFamily = fontFamily, fontSize = 30.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold),
    heading = TextStyle(fontFamily = fontFamily, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
    body = TextStyle(fontFamily = fontFamily, fontSize = 18.sp, lineHeight = 26.sp),
    label = TextStyle(fontFamily = fontFamily, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    detail = TextStyle(fontFamily = fontFamily, fontSize = 15.sp, lineHeight = 21.sp),
    timer = TextStyle(fontFamily = fontFamily, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
)

private val LocalAtlasColors = staticCompositionLocalOf { LightAtlasColors }
private val LocalAtlasTypography = staticCompositionLocalOf { atlasTypography(FontFamily.Default) }
private val LocalReducedMotion = staticCompositionLocalOf { false }

object AtlasTheme {
    val colors: AtlasColors
        @Composable get() = LocalAtlasColors.current

    val typography: AtlasTypography
        @Composable get() = LocalAtlasTypography.current

    val reducedMotion: Boolean
        @Composable get() = LocalReducedMotion.current
}

@Composable
fun BertsAtlasTheme(
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val fontFamily = FontFamily(
        Font(Res.font.atkinson_hyperlegible_next_regular, FontWeight.Normal),
        Font(Res.font.atkinson_hyperlegible_next_semibold, FontWeight.SemiBold),
        Font(Res.font.atkinson_hyperlegible_next_bold, FontWeight.Bold),
    )
    CompositionLocalProvider(
        LocalAtlasColors provides LightAtlasColors,
        LocalAtlasTypography provides atlasTypography(fontFamily),
        LocalReducedMotion provides reducedMotion,
        content = content,
    )
}
