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
    val seaGlass: Color,
    val orangeAccent: Color,
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

private val AtlasDeepGreen = Color(0xFF27635B)
private val AtlasBlueGray = Color(0xFF778BA5)
private val AtlasSeaGlass = Color(0xFFB7CFC8)
private val AtlasShell = Color(0xFFF6E9E1)
private val AtlasSand = Color(0xFFF1DCC7)
private val AtlasPink = Color(0xFFF7BFBE)
private val AtlasPeach = Color(0xFFE9AF8B)
private val AtlasOrange = Color(0xFFE1814E)

private val LightAtlasColors = AtlasColors(
    parchment = AtlasShell,
    paper = AtlasSand,
    plum = AtlasDeepGreen,
    mutedPlum = AtlasDeepGreen,
    atlasBlue = AtlasDeepGreen,
    atlasBluePressed = AtlasDeepGreen,
    seaGlass = AtlasSeaGlass,
    orangeAccent = AtlasOrange,
    coral = AtlasPink,
    mustard = AtlasPeach,
    success = AtlasDeepGreen,
    error = AtlasDeepGreen,
    outline = AtlasBlueGray,
    white = AtlasShell,
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
