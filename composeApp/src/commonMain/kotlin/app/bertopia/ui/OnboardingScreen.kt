package app.bertopia.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.Image
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bertopia.composeapp.generated.resources.Res
import bertopia.composeapp.generated.resources.logo_bert
import bertopia.composeapp.generated.resources.onboarding_capitals
import bertopia.composeapp.generated.resources.onboarding_dsa
import bertopia.composeapp.generated.resources.onboarding_flags
import bertopia.composeapp.generated.resources.onboarding_system_design
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private enum class OnboardingArt { Welcome, Packs, Streak }

private data class OnboardingSlide(val art: OnboardingArt, val title: String, val body: String)

private val onboardingSlides = listOf(
    OnboardingSlide(
        OnboardingArt.Welcome,
        "Welcome to Bertopia",
        "Bert's world of curious things. Explore topics, play quick quizzes and get a little smarter every day.",
    ),
    OnboardingSlide(
        OnboardingArt.Packs,
        "Explore anything",
        "Flags, capitals, coding interviews, system design and more. Pick a pack and dive in.",
    ),
    OnboardingSlide(
        OnboardingArt.Streak,
        "Learn a little every day",
        "Short runs build your streak, and Bert brings back what you missed.",
    ),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState { onboardingSlides.size }
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == onboardingSlides.lastIndex
    Column(modifier.fillMaxSize().background(BertopiaTheme.colors.parchment)) {
        HorizontalPager(pagerState, Modifier.fillMaxWidth().weight(1f)) { page ->
            OnboardingPage(onboardingSlides[page])
        }
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageDots(onboardingSlides.size, pagerState.currentPage)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                if (isLast) {
                    BertopiaButton("Start exploring", onFinish, Modifier.fillMaxWidth())
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OnboardingLink("Skip", WelcomeInk, onFinish)
                        Spacer(Modifier.weight(1f))
                        OnboardingLink("Next →", WelcomeDeepOrange) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingLink(label: String, color: Color, onClick: () -> Unit) {
    BertopiaText(
        label,
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        style = BertopiaTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
        color = color,
    )
}

@Composable
private fun OnboardingPage(slide: OnboardingSlide) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val artHeight = maxHeight * .62f
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(artHeight)
                    .background(artBackground(slide.art), RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(if (slide.art == OnboardingArt.Welcome) 14.dp else 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (slide.art) {
                    OnboardingArt.Welcome -> WelcomeArt()
                    OnboardingArt.Packs -> PackCollageArt()
                    OnboardingArt.Streak -> StreakArt()
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BertopiaText(
                    slide.title,
                    Modifier.semantics { heading() },
                    BertopiaTheme.typography.title,
                    color = if (slide.art == OnboardingArt.Welcome) WelcomeInk else BertopiaTheme.colors.plum,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                BertopiaText(
                    slide.body,
                    Modifier.widthIn(max = 460.dp),
                    color = if (slide.art == OnboardingArt.Welcome) WelcomeInk.copy(alpha = .78f) else BertopiaTheme.colors.mutedPlum,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun artBackground(art: OnboardingArt) = when (art) {
    OnboardingArt.Welcome -> BertopiaTheme.colors.orangeAccent
    OnboardingArt.Packs -> BertopiaTheme.colors.paper
    OnboardingArt.Streak -> BertopiaTheme.colors.mustard
}

private val WelcomeInk = Color(0xFF3B2A22)
private val WelcomeParchment = Color(0xFFF6E9E1)
private val WelcomeSand = Color(0xFFF1DCC7)
private val WelcomeOrange = Color(0xFFE1814E)
private val WelcomeDeepOrange = Color(0xFFD9703C)
private val WelcomeLightOrange = Color(0xFFEE9A63)

private enum class BertTopic { Flags, Globe, Math, Capitals, Atom, Code }

private const val WelcomeLoopMillis = 60_000

@Composable
private fun WelcomeArt() {
    val motion = !BertopiaTheme.reducedMotion
    val entrance = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(1_600, easing = LinearEasing)) }
    val loop = rememberInfiniteTransition(label = "Bertopia welcome")
    val loopProgress by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(WelcomeLoopMillis, easing = LinearEasing)),
        label = "Bertopia orbit",
    )
    val time = if (motion) loopProgress else 0f
    Column(
        Modifier.fillMaxSize().clearAndSetSemantics {
            contentDescription = "Bert, a curious brain with goggles, surrounded by a flag, a globe, math, a landmark, " +
                "an atom and a laptop with code. Bert plus utopia equals Bertopia."
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BertSpeechBubble(Modifier.welcomeEntrance(entrance.value, .3f))
        BertOrbit(time, entrance.value, Modifier.fillMaxWidth().weight(1f))
        BertopiaEquation(Modifier.welcomeEntrance(entrance.value, .72f))
    }
}

private fun Modifier.welcomeEntrance(progress: Float, start: Float, span: Float = .25f) = graphicsLayer {
    val eased = FastOutSlowInEasing.transform(((progress - start) / span).coerceIn(0f, 1f))
    alpha = eased
    scaleX = .6f + .4f * eased
    scaleY = .6f + .4f * eased
    translationY = (1f - eased) * 16.dp.toPx()
}

@Composable
private fun BertOrbit(time: Float, entrance: Float, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val badge = minOf(maxHeight * .2f, maxWidth * .15f).coerceIn(30.dp, 54.dp)
        val radiusX = minOf(maxWidth / 2 - badge / 2 - 2.dp, maxHeight * .95f)
        val radiusY = maxHeight / 2 - badge / 2 - 4.dp
        val bert = minOf(radiusY * 2 - badge * 1.25f, radiusX * 1.2f)
        val topics = BertTopic.entries
        val angles = topics.indices.map { index -> 2 * PI * (index.toDouble() / topics.size + time) - PI / 2 }
        Canvas(Modifier.fillMaxSize().welcomeEntrance(entrance, .1f, .35f)) {
            val rx = radiusX.toPx()
            val ry = radiusY.toPx()
            val bertRadius = bert.toPx() / 2
            val dots = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 7.dp.toPx()))
            drawCircle(WelcomeParchment.copy(alpha = .12f), bertRadius * 1.32f)
            drawCircle(WelcomeLightOrange, bertRadius * 1.12f)
            drawOval(
                WelcomeParchment.copy(alpha = .8f),
                topLeft = Offset(center.x - rx, center.y - ry),
                size = Size(rx * 2, ry * 2),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots),
            )
            angles.forEach { angle ->
                val direction = Offset(cos(angle).toFloat() * rx, sin(angle).toFloat() * ry)
                val length = direction.getDistance()
                val unit = direction / length
                drawLine(
                    WelcomeParchment.copy(alpha = .45f),
                    center + unit * (bertRadius * 1.2f),
                    center + direction - unit * (badge.toPx() * .55f),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = dots,
                )
            }
            listOf(Offset(-.92f, -.78f), Offset(.86f, -.9f), Offset(-.7f, .95f), Offset(.95f, .7f)).forEach { spot ->
                drawCircle(WelcomeSand.copy(alpha = .7f), 2.dp.toPx(), center + Offset(spot.x * rx, spot.y * ry))
            }
        }
        val bertBob = sin(2 * PI * time * 24).toFloat()
        Image(
            painterResource(Res.drawable.logo_bert),
            null,
            Modifier
                .size(bert)
                .welcomeEntrance(entrance, 0f, .3f)
                .graphicsLayer {
                    translationY = bertBob * 4.dp.toPx()
                    rotationZ = bertBob * 2f
                },
            contentScale = ContentScale.Fit,
        )
        topics.forEachIndexed { index, topic ->
            val angle = angles[index]
            val float = sin(2 * PI * (time * 30 + index.toDouble() / topics.size)).toFloat()
            TopicBadge(
                topic,
                Modifier
                    .offset(radiusX * cos(angle).toFloat(), radiusY * sin(angle).toFloat() + 3.dp * float)
                    .size(badge)
                    .welcomeEntrance(entrance, .2f + index * .06f),
            )
        }
    }
}

@Composable
private fun TopicBadge(topic: BertTopic, modifier: Modifier) {
    Box(
        modifier
            .shadow(6.dp, CircleShape, ambientColor = WelcomeInk, spotColor = WelcomeInk)
            .background(WelcomeParchment, CircleShape)
            .border(2.dp, WelcomeDeepOrange, CircleShape)
            .padding(7.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when (topic) {
                BertTopic.Flags -> drawFlagIcon()
                BertTopic.Capitals -> drawCapitalIcon()
                BertTopic.Globe -> drawGlobeIcon()
                BertTopic.Math -> drawMathIcon()
                BertTopic.Atom -> drawAtomIcon()
                BertTopic.Code -> drawCodeIcon()
            }
        }
    }
}

private fun DrawScope.grid(x: Float, y: Float) = Offset(x * size.minDimension / 24f, y * size.minDimension / 24f)

private fun DrawScope.gridSize(width: Float, height: Float) =
    Size(width * size.minDimension / 24f, height * size.minDimension / 24f)

private fun DrawScope.gridStroke(width: Float = 2f) =
    Stroke(width * size.minDimension / 24f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun pathThrough(points: List<Offset>) = Path().apply {
    moveTo(points.first().x, points.first().y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
}

private fun DrawScope.drawFlagIcon() {
    val cloth = Path().apply {
        val start = grid(6.5f, 4f)
        moveTo(start.x, start.y)
        val a = grid(10.5f, 1.8f); val b = grid(14f, 6.5f); val c = grid(20f, 4f)
        cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
        val d = grid(20f, 13f)
        lineTo(d.x, d.y)
        val e = grid(14f, 15.5f); val f = grid(10.5f, 10.8f); val g = grid(6.5f, 13f)
        cubicTo(e.x, e.y, f.x, f.y, g.x, g.y)
        close()
    }
    drawPath(cloth, WelcomeOrange)
    drawPath(cloth, WelcomeDeepOrange, style = gridStroke(1.6f))
    drawLine(WelcomeDeepOrange, grid(5f, 3f), grid(5f, 21.5f), gridStroke().width, StrokeCap.Round)
}

private fun DrawScope.drawGlobeIcon() {
    val center = grid(12f, 12f)
    val radius = grid(8.5f, 0f).x
    drawCircle(WelcomeSand, radius, center)
    val land = pathThrough(listOf(grid(7f, 7.5f), grid(11f, 6.5f), grid(12f, 10f), grid(9f, 12.5f), grid(9.5f, 16f), grid(6.5f, 14f), grid(4.5f, 10.5f))).apply { close() }
    drawPath(land, WelcomeOrange)
    drawCircle(WelcomeOrange, grid(2.2f, 0f).x, grid(15.5f, 14.5f))
    drawOval(WelcomeDeepOrange, grid(8f, 3.5f), gridSize(8f, 17f), style = gridStroke(1.2f))
    drawLine(WelcomeDeepOrange, grid(3.5f, 12f), grid(20.5f, 12f), gridStroke(1.2f).width)
    drawCircle(WelcomeDeepOrange, radius, center, style = gridStroke(1.8f))
}

private fun DrawScope.drawMathIcon() {
    val stroke = gridStroke(2.2f).width
    drawRoundRect(WelcomeSand, grid(2.5f, 2.5f), gridSize(19f, 19f), CornerRadius(grid(4f, 0f).x))
    drawLine(WelcomeDeepOrange, grid(5f, 8f), grid(11f, 8f), stroke, StrokeCap.Round)
    drawLine(WelcomeDeepOrange, grid(8f, 5f), grid(8f, 11f), stroke, StrokeCap.Round)
    drawLine(WelcomeOrange, grid(13f, 8f), grid(19f, 8f), stroke, StrokeCap.Round)
    drawLine(WelcomeOrange, grid(5.5f, 13.5f), grid(10.5f, 18.5f), stroke, StrokeCap.Round)
    drawLine(WelcomeOrange, grid(10.5f, 13.5f), grid(5.5f, 18.5f), stroke, StrokeCap.Round)
    drawLine(WelcomeDeepOrange, grid(13f, 14.5f), grid(19f, 14.5f), stroke, StrokeCap.Round)
    drawLine(WelcomeDeepOrange, grid(13f, 17.5f), grid(19f, 17.5f), stroke, StrokeCap.Round)
}

private fun DrawScope.drawCapitalIcon() {
    val roof = pathThrough(listOf(grid(3f, 9f), grid(12f, 3.5f), grid(21f, 9f))).apply { close() }
    drawPath(roof, WelcomeOrange)
    drawPath(roof, WelcomeDeepOrange, style = gridStroke(1.6f))
    listOf(6.5f, 12f, 17.5f).forEach { x ->
        drawLine(WelcomeDeepOrange, grid(x, 11.5f), grid(x, 17f), gridStroke().width, StrokeCap.Round)
    }
    drawRoundRect(WelcomeDeepOrange, grid(3.5f, 18.5f), gridSize(17f, 2.5f), CornerRadius(grid(1f, 0f).x))
}

private fun DrawScope.drawAtomIcon() {
    val center = grid(12f, 12f)
    val stroke = gridStroke(1.6f)
    listOf(0f, 60f, 120f).forEach { degrees ->
        rotate(degrees, center) { drawOval(WelcomeDeepOrange, grid(2.5f, 8.5f), gridSize(19f, 7f), style = stroke) }
    }
    drawCircle(WelcomeOrange, grid(2.8f, 0f).x, center)
    drawCircle(WelcomeLightOrange, grid(1.6f, 0f).x, grid(21f, 12f))
}

private fun DrawScope.drawCodeIcon() {
    drawRoundRect(WelcomeDeepOrange, grid(2.5f, 4f), gridSize(19f, 13f), CornerRadius(grid(2f, 0f).x))
    drawRoundRect(WelcomeLightOrange, grid(4f, 5.5f), gridSize(16f, 10f), CornerRadius(grid(1f, 0f).x))
    drawRoundRect(WelcomeOrange, grid(1f, 18f), gridSize(22f, 2.5f), CornerRadius(grid(1.2f, 0f).x))
    val stroke = gridStroke(1.6f)
    drawPath(pathThrough(listOf(grid(9f, 8f), grid(6.5f, 10.5f), grid(9f, 13f))), WelcomeParchment, style = stroke)
    drawPath(pathThrough(listOf(grid(15f, 8f), grid(17.5f, 10.5f), grid(15f, 13f))), WelcomeParchment, style = stroke)
    drawLine(WelcomeParchment, grid(13f, 7.5f), grid(11f, 13.5f), stroke.width, StrokeCap.Round)
}

@Composable
private fun BertSpeechBubble(modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .shadow(4.dp, RoundedCornerShape(16.dp), ambientColor = WelcomeInk, spotColor = WelcomeInk)
                .background(WelcomeParchment, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            BertopiaText(
                "Hi, I'm Bert! Welcome to my world.",
                style = BertopiaTheme.typography.detail.copy(fontWeight = FontWeight.SemiBold),
                color = WelcomeInk,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        Canvas(Modifier.size(16.dp, 8.dp)) {
            drawPath(
                pathThrough(listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(size.width / 2, size.height))).apply { close() },
                WelcomeParchment,
            )
        }
    }
}

@Composable
private fun BertopiaEquation(modifier: Modifier) {
    val style = BertopiaTheme.typography.detail.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Row(
        modifier
            .background(WelcomeDeepOrange, CircleShape)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EquationTerm("Bert", WelcomeParchment, WelcomeInk, style)
        BertopiaText("+", style = style, color = WelcomeParchment)
        EquationTerm("utopia", WelcomeSand, WelcomeInk, style)
        BertopiaText("=", style = style, color = WelcomeParchment)
        EquationTerm("Bertopia", WelcomeInk, WelcomeParchment, style)
    }
}

@Composable
private fun EquationTerm(text: String, fill: Color, ink: Color, style: TextStyle) {
    BertopiaText(
        text,
        Modifier.background(fill, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
        style = style,
        color = ink,
    )
}

@Composable
private fun PackCollageArt() {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val card = minOf(maxWidth * .44f, maxHeight * .46f)
        val spread = card * .55f
        PackCover(Res.drawable.onboarding_capitals, "Capitals pack", card, -spread, -spread * .7f, -8f)
        PackCover(Res.drawable.onboarding_dsa, "Coding interviews pack", card, spread, -spread * .6f, 7f)
        PackCover(Res.drawable.onboarding_system_design, "System design pack", card, spread * .9f, spread * .75f, -5f)
        PackCover(Res.drawable.onboarding_flags, "Flags pack", card, -spread * .8f, spread * .7f, 6f)
    }
}

@Composable
private fun PackCover(resource: DrawableResource, description: String, size: Dp, x: Dp, y: Dp, tilt: Float) {
    val shape = RoundedCornerShape(20.dp)
    Image(
        painterResource(resource),
        description,
        Modifier
            .offset(x, y)
            .rotate(tilt)
            .size(size)
            .shadow(10.dp, shape)
            .clip(shape),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun StreakArt() {
    val colors = BertopiaTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painterResource(Res.drawable.logo_bert),
            "Bert cheering your streak",
            Modifier.weight(1f, fill = false).aspectRatio(1f),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { day, label ->
                val done = day < 5
                Box(
                    Modifier
                        .size(36.dp)
                        .background(if (done) colors.orangeAccent else colors.parchment, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BertopiaText(
                        label,
                        style = BertopiaTheme.typography.detail,
                        color = if (done) colors.white else colors.plum,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        BertopiaText("5 day streak", style = BertopiaTheme.typography.label)
    }
}

@Composable
private fun PageDots(count: Int, selected: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { page ->
            val active = page == selected
            val width by animateDpAsState(if (active) 24.dp else 8.dp)
            val color by animateColorAsState(
                if (active) BertopiaTheme.colors.orangeAccent else BertopiaTheme.colors.outline.copy(alpha = .4f),
            )
            Box(Modifier.height(8.dp).width(width).background(color, CircleShape))
        }
    }
}
