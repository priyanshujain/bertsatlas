package app.bertopia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import bertopia.composeapp.generated.resources.Res
import bertopia.composeapp.generated.resources.atkinson_hyperlegible_next_bold
import bertopia.composeapp.generated.resources.atkinson_hyperlegible_next_regular
import bertopia.composeapp.generated.resources.atkinson_hyperlegible_next_semibold
import org.jetbrains.compose.resources.Font

@Immutable
data class BertopiaColors(
    val parchment: Color,
    val paper: Color,
    val plum: Color,
    val mutedPlum: Color,
    val bertopiaBlue: Color,
    val bertopiaBluePressed: Color,
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
data class BertopiaTypography(
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val detail: TextStyle,
    val timer: TextStyle,
)

private val BertopiaDeepGreen = Color(0xFF27635B)
private val BertopiaBlueGray = Color(0xFF778BA5)
private val BertopiaSeaGlass = Color(0xFFB7CFC8)
private val BertopiaShell = Color(0xFFF6E9E1)
private val BertopiaSand = Color(0xFFF1DCC7)
private val BertopiaPink = Color(0xFFF7BFBE)
private val BertopiaPeach = Color(0xFFE9AF8B)
private val BertopiaOrange = Color(0xFFE1814E)

private val LightBertopiaColors = BertopiaColors(
    parchment = BertopiaShell,
    paper = BertopiaSand,
    plum = BertopiaDeepGreen,
    mutedPlum = BertopiaDeepGreen,
    bertopiaBlue = BertopiaDeepGreen,
    bertopiaBluePressed = BertopiaDeepGreen,
    seaGlass = BertopiaSeaGlass,
    orangeAccent = BertopiaOrange,
    coral = BertopiaPink,
    mustard = BertopiaPeach,
    success = BertopiaDeepGreen,
    error = BertopiaDeepGreen,
    outline = BertopiaBlueGray,
    white = BertopiaShell,
)

private fun bertopiaTypography(fontFamily: FontFamily) = BertopiaTypography(
    display = TextStyle(fontFamily = fontFamily, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    title = TextStyle(fontFamily = fontFamily, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    heading = TextStyle(fontFamily = fontFamily, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    body = TextStyle(fontFamily = fontFamily, fontSize = 16.sp, lineHeight = 24.sp),
    label = TextStyle(fontFamily = fontFamily, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    detail = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp),
    timer = TextStyle(fontFamily = fontFamily, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
)

private val LocalBertopiaColors = staticCompositionLocalOf { LightBertopiaColors }
private val LocalBertopiaTypography = staticCompositionLocalOf { bertopiaTypography(FontFamily.Default) }
private val LocalReducedMotion = staticCompositionLocalOf { false }

object BertopiaTheme {
    val colors: BertopiaColors
        @Composable get() = LocalBertopiaColors.current

    val typography: BertopiaTypography
        @Composable get() = LocalBertopiaTypography.current

    val reducedMotion: Boolean
        @Composable get() = LocalReducedMotion.current
}

@Composable
fun BertopiaTheme(
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val fontFamily = FontFamily(
        Font(Res.font.atkinson_hyperlegible_next_regular, FontWeight.Normal),
        Font(Res.font.atkinson_hyperlegible_next_semibold, FontWeight.SemiBold),
        Font(Res.font.atkinson_hyperlegible_next_bold, FontWeight.Bold),
    )
    CompositionLocalProvider(
        LocalBertopiaColors provides LightBertopiaColors,
        LocalBertopiaTypography provides bertopiaTypography(fontFamily),
        LocalReducedMotion provides reducedMotion,
        content = content,
    )
}
