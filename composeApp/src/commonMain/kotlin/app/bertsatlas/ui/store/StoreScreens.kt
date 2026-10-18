package app.bertsatlas.ui.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bertsatlas.cardrender.CardDocumentBuilder
import app.bertsatlas.cardrender.CardView
import app.bertsatlas.domain.model.BuiltInPacks
import app.bertsatlas.domain.pack.BundledMixes
import app.bertsatlas.domain.store.DownloadStage
import app.bertsatlas.domain.store.PackAvailability
import app.bertsatlas.domain.store.ShelfScope
import app.bertsatlas.domain.store.StoreCategory
import app.bertsatlas.domain.store.StoreIndex
import app.bertsatlas.domain.store.StoreItem
import app.bertsatlas.domain.store.StoreMix
import app.bertsatlas.domain.store.StorePack
import app.bertsatlas.domain.store.StoreShelf
import app.bertsatlas.store.PackDownload
import app.bertsatlas.store.StoreController
import app.bertsatlas.store.StorePage
import app.bertsatlas.store.StoreState
import app.bertsatlas.ui.AtlasButton
import app.bertsatlas.ui.AtlasButtonStyle
import app.bertsatlas.ui.AtlasCard
import app.bertsatlas.ui.AtlasScreenScaffold
import app.bertsatlas.ui.AtlasText
import app.bertsatlas.ui.AtlasTheme
import app.bertsatlas.ui.BertIllustration
import app.bertsatlas.ui.BertPose
import app.bertsatlas.ui.PackCover
import app.bertsatlas.ui.PackCoverArt
import app.bertsatlas.ui.WIDE_CONTENT_WIDTH
import app.bertsatlas.ui.WIDE_LAYOUT_MIN_WIDTH
import app.bertsatlas.ui.atlasCardRenderStyle

data class StoreNavigation(
    val onBack: () -> Unit,
    val onOpenInstalledPack: (String) -> Unit,
    val onOpenMix: (String) -> Unit,
)

@Composable
fun StoreDestination(store: StoreController, state: StoreState, navigation: StoreNavigation) {
    when (val page = state.page) {
        StorePage.Home -> StoreHomeScreen(store, state, navigation)
        is StorePage.Category -> StoreCategoryScreen(store, state, navigation, page.categoryId)
        is StorePage.Subcategory -> StoreSubcategoryScreen(store, state, navigation, page.categoryId, page.subcategoryId)
        is StorePage.PackDetail -> StorePackScreen(store, state, navigation, page.packId)
        StorePage.Search -> StoreSearchScreen(store, state, navigation)
    }
}

@Composable
private fun StoreHomeScreen(store: StoreController, state: StoreState, navigation: StoreNavigation) {
    StoreScaffold("Store", navigation.onBack, trailing = { SearchButton(store::openSearch) }, onPullToRefresh = store::retry) {
        val index = state.index
        StoreStatusBanner(state, store::retry)
        if (state.loading && index != null) AtlasText("Checking for new packs…", color = AtlasTheme.colors.mutedPlum)
        if (index == null) {
            if (state.loading) StoreLoading() else StoreUnavailable(store::retry)
            return@StoreScaffold
        }
        index.featured.firstNotNullOfOrNull { featured -> index.resolve(featured.item)?.let { featured to it } }?.let { (featured, item) ->
            FeaturedHero(featured.headline, featured.blurb, item, state, store, navigation)
        }
        val categories = index.orderedCategories()
        if (categories.isNotEmpty()) {
            ShelfTitle("Browse by topic")
            CategoryRow(categories) { store.openCategory(it.id) }
        }
        index.shelvesFor(ShelfScope.All).forEach { shelf -> ShelfRow(shelf, index, state, store, navigation) }
    }
}

@Composable
private fun StoreCategoryScreen(store: StoreController, state: StoreState, navigation: StoreNavigation, categoryId: String) {
    val index = state.index
    val category = index?.category(categoryId)
    StoreScaffold(category?.name ?: "Store", store::backFromPage, trailing = { SearchButton(store::openSearch) }) {
        if (index == null || category == null) {
            StoreUnavailable(store::retry)
            return@StoreScaffold
        }
        val packs = index.packsIn(category.id)
        CategoryHero(category, packs.size)
        index.shelvesFor(ShelfScope.category(category.id)).forEach { shelf -> ShelfRow(shelf, index, state, store, navigation) }
        val topics = category.subcategories.sortedBy { it.order }.map { it to rankedPacks(index, category.id, it.id).size }
        if (topics.isNotEmpty()) {
            ShelfTitle("Topics")
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AtlasTheme.colors.paper),
            ) {
                topics.forEachIndexed { position, (subcategory, count) ->
                    TopicRow(subcategory.name, count, showDivider = position > 0) { store.openSubcategory(category.id, subcategory.id) }
                }
            }
        }
        if (packs.isNotEmpty()) {
            ShelfTitle("Top in ${category.name}")
            RankedList(rankedPacks(index, category.id, null), state, store)
        } else {
            AtlasCard {
                AtlasText("Packs for ${category.name} are on their way.", style = AtlasTheme.typography.label)
                AtlasText("Bert is still writing these. Check back soon.", style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
            }
        }
    }
}

@Composable
private fun StoreSubcategoryScreen(store: StoreController, state: StoreState, navigation: StoreNavigation, categoryId: String, subcategoryId: String) {
    val index = state.index
    val category = index?.category(categoryId)
    val subcategory = category?.subcategories?.firstOrNull { it.id == subcategoryId }
    StoreScaffold(subcategory?.name ?: "Store", store::backFromPage) {
        if (index == null || subcategory == null) {
            StoreUnavailable(store::retry)
            return@StoreScaffold
        }
        AtlasText(category.name, style = AtlasTheme.typography.label, color = AtlasTheme.colors.mutedPlum)
        val ranked = rankedPacks(index, categoryId, subcategoryId)
        if (ranked.isEmpty()) {
            AtlasText("Nothing here yet.", color = AtlasTheme.colors.mutedPlum)
        } else {
            RankedList(ranked, state, store)
        }
    }
}

@Composable
private fun StorePackScreen(store: StoreController, state: StoreState, navigation: StoreNavigation, packId: String) {
    val pack = state.index?.pack(packId)
    StoreScaffold("", store::backFromPage) {
        if (pack == null) {
            StoreUnavailable(store::retry)
            return@StoreScaffold
        }
        val index = requireNotNull(state.index)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { PackHeader(pack, index, state, store, navigation) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { PackBody(pack, state) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PackHeader(pack, index, state, store, navigation)
                    PackBody(pack, state)
                }
            }
        }
    }
}

@Composable
private fun PackHeader(pack: StorePack, index: StoreIndex, state: StoreState, store: StoreController, navigation: StoreNavigation) {
    PackCoverArt(pack.accent(), pack.cover(), Modifier.fillMaxWidth().aspectRatio(1.9f), RoundedCornerShape(28.dp), pack.title)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AtlasText(pack.title, Modifier.semantics { heading() }, AtlasTheme.typography.display)
        if (pack.subtitle.isNotBlank()) AtlasText(pack.subtitle, color = AtlasTheme.colors.mutedPlum)
        AtlasText(index.placementName(pack), style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
    }
    PackAction(pack, state, store, navigation, large = true)
    DisposableEffect(pack.packId) { onDispose { store.clearPackMessage(pack.packId) } }
    state.packMessages[pack.packId]?.let { message ->
        AtlasCard(contentDescription = message) { AtlasText(message, style = AtlasTheme.typography.detail) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FactCell(pack.cardCount.toString(), if (pack.cardCount == 1L) "card" else "cards", Modifier.weight(1f))
        FactCell(pack.sizeBytes.readableSize(), "download", Modifier.weight(1f))
        FactCell(pack.version.substringBefore('-'), "version", Modifier.weight(1f))
    }
}

@Composable
private fun PackBody(pack: StorePack, state: StoreState) {
    if (pack.description.isNotBlank()) {
        ShelfTitle("What you'll learn")
        AtlasText(pack.description)
    }
    if (pack.sampleCards.isNotEmpty()) {
        ShelfTitle("Sample cards")
        val mediaDirectory = state.sampleMediaDirectories[pack.packId]
        pack.sampleCards.forEachIndexed { position, sample ->
            SampleCardView(position + 1, sample.frontHtml, sample.backHtml, mediaDirectory.orEmpty(), pack.accent())
        }
    }
    if (pack.whatsNew.isNotBlank()) {
        ShelfTitle("What's new")
        AtlasText("Version ${pack.version}", style = AtlasTheme.typography.label, color = AtlasTheme.colors.mutedPlum)
        AtlasText(pack.whatsNew)
    }
}

@Composable
private fun SampleCardView(number: Int, frontHtml: String, backHtml: String, mediaDirectory: String, accent: Color) {
    var showAnswer by remember(frontHtml) { mutableStateOf(false) }
    val html = if (showAnswer) backHtml.afterAnswerDivider() else frontHtml
    val document = remember(html) { CardDocumentBuilder.build(html) }
    Column(
        Modifier.fillMaxWidth()
            .background(AtlasTheme.colors.paper, RoundedCornerShape(22.dp))
            .border(1.dp, accent.copy(alpha = .35f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AtlasText(if (showAnswer) "Card $number · answer" else "Card $number", style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
        CardView(document, atlasCardRenderStyle(accent), mediaDirectory, Modifier.fillMaxWidth())
        AtlasButton(
            if (showAnswer) "Show the question" else "Show the answer",
            { showAnswer = !showAnswer },
            Modifier.fillMaxWidth(),
            AtlasButtonStyle.Quiet,
        )
    }
}

@Composable
private fun StoreSearchScreen(store: StoreController, state: StoreState, navigation: StoreNavigation) {
    StoreScaffold("Search", store::backFromPage) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(
            Modifier.fillMaxWidth()
                .heightIn(min = 54.dp)
                .background(AtlasTheme.colors.paper, RoundedCornerShape(16.dp))
                .border(1.5.dp, AtlasTheme.colors.outline.copy(alpha = .6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (state.query.isEmpty()) AtlasText("Packs, topics, subjects", color = AtlasTheme.colors.mutedPlum.copy(alpha = .7f))
            BasicTextField(
                value = state.query,
                onValueChange = store::search,
                singleLine = true,
                textStyle = AtlasTheme.typography.body.copy(color = AtlasTheme.colors.plum),
                cursorBrush = SolidColor(AtlasTheme.colors.plum),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Search the store" },
            )
        }
        val index = state.index
        val query = state.query.trim()
        when {
            index == null -> StoreUnavailable(store::retry)
            query.isEmpty() -> {
                ShelfTitle("Topics")
                CategoryRow(index.orderedCategories()) { store.openCategory(it.id) }
            }
            else -> {
                val results = index.search(query)
                if (results.isEmpty()) {
                    AtlasText("No packs match \"$query\" yet.", color = AtlasTheme.colors.mutedPlum)
                } else {
                    results.forEach { item ->
                        when (item) {
                            is StoreItem.Pack -> PackRow(null, item.pack, index, state, store)
                            is StoreItem.Mix -> MixRow(item.mix, navigation)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreScaffold(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    onPullToRefresh: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val pullModifier = if (onPullToRefresh == null) Modifier else Modifier.nestedScroll(rememberPullToRefresh(onPullToRefresh))
    AtlasScreenScaffold(title, onBack, pullModifier, maxContentWidth = WIDE_CONTENT_WIDTH, trailing = trailing) {
        Column(
            Modifier.widthIn(max = WIDE_CONTENT_WIDTH).fillMaxWidth().align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}

@Composable
private fun rememberPullToRefresh(onRefresh: () -> Unit): NestedScrollConnection {
    val threshold = with(LocalDensity.current) { PULL_TO_REFRESH_DISTANCE.toPx() }
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    return remember(threshold) {
        object : NestedScrollConnection {
            var pulled = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f) pulled = 0f
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) pulled += available.y
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled >= threshold) currentOnRefresh()
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
}

@Composable
private fun StoreStatusBanner(state: StoreState, onRetry: () -> Unit) {
    if (!state.showingSavedCopy) return
    Row(
        Modifier.fillMaxWidth()
            .background(AtlasTheme.colors.seaGlass.copy(alpha = .55f), RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { contentDescription = "You're offline. Showing the store as it was last time. Your packs still work." }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            AtlasText("You're offline", style = AtlasTheme.typography.label)
            AtlasText("This is the store as it was last time. Your packs still work.", style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
        }
        AtlasButton("Retry", onRetry, style = AtlasButtonStyle.Quiet)
    }
}

@Composable
private fun StoreLoading() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BertIllustration(BertPose.Idle, Modifier.size(120.dp))
        AtlasText("Bert is stocking the shelves…", color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StoreUnavailable(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BertIllustration(BertPose.Idle, Modifier.size(120.dp))
        AtlasText("The store is out of reach", style = AtlasTheme.typography.heading, textAlign = TextAlign.Center)
        AtlasText(
            "Bert needs the internet to fetch new packs. Everything you already have works offline.",
            color = AtlasTheme.colors.mutedPlum,
            textAlign = TextAlign.Center,
        )
        AtlasButton("Try again", onRetry, Modifier.widthIn(min = 200.dp))
    }
}

@Composable
private fun FeaturedHero(headline: String, blurb: String, item: StoreItem, state: StoreState, store: StoreController, navigation: StoreNavigation) {
    val accent = item.accent()
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(accent)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "$headline. ${item.title()}. $blurb" }
            .clickable { item.open(store, navigation) }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AtlasText(headline.uppercase(), style = AtlasTheme.typography.detail, color = AtlasTheme.colors.white.copy(alpha = .85f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PackCoverArt(
                accent = AtlasTheme.colors.white.copy(alpha = .18f).compositeOver(accent),
                cover = item.cover(),
                modifier = Modifier.size(110.dp),
                shape = RoundedCornerShape(24.dp),
                title = item.title(),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AtlasText(item.title(), style = AtlasTheme.typography.title, color = AtlasTheme.colors.white)
                if (blurb.isNotBlank()) AtlasText(blurb, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.white.copy(alpha = .92f))
            }
        }
        if (item is StoreItem.Pack) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PackAction(item.pack, state, store, navigation, large = false, onAccent = true)
            }
        }
    }
}

@Composable
private fun CategoryHero(category: StoreCategory, packCount: Int) {
    val accent = category.accentColor.toColorOr(AtlasTheme.colors.atlasBlue)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(accent).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AtlasText(category.icon.iconGlyph(), style = AtlasTheme.typography.display, color = AtlasTheme.colors.white)
        AtlasText(category.name, Modifier.semantics { heading() }, AtlasTheme.typography.title, AtlasTheme.colors.white)
        AtlasText(
            when (packCount) { 0 -> "Coming soon"; 1 -> "1 pack"; else -> "$packCount packs" } + " · ${category.subcategories.size} ${if (category.subcategories.size == 1) "topic" else "topics"}",
            style = AtlasTheme.typography.detail,
            color = AtlasTheme.colors.white.copy(alpha = .9f),
        )
    }
}

@Composable
private fun CategoryRow(categories: List<StoreCategory>, onOpen: (StoreCategory) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        categories.forEach { category ->
            val accent = category.accentColor.toColorOr(AtlasTheme.colors.atlasBlue)
            Column(
                Modifier.width(160.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AtlasTheme.colors.paper)
                    .border(1.dp, accent.copy(alpha = .4f), RoundedCornerShape(20.dp))
                    .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = category.name }
                    .clickable { onOpen(category) }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(40.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                    AtlasText(category.icon.iconGlyph(), style = AtlasTheme.typography.label, color = AtlasTheme.colors.white)
                }
                ClampedText(category.name, style = AtlasTheme.typography.label, maxLines = 2)
            }
        }
    }
}

@Composable
private fun ShelfRow(shelf: StoreShelf, index: StoreIndex, state: StoreState, store: StoreController, navigation: StoreNavigation) {
    val items = index.resolveItems(shelf)
    if (items.isEmpty()) return
    ShelfTitle(shelf.title)
    HorizontalTiles(items, state, store, navigation)
}

@Composable
private fun HorizontalTiles(items: List<StoreItem>, state: StoreState, store: StoreController, navigation: StoreNavigation) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { item -> StoreTile(item, state, store, navigation) }
    }
}

@Composable
private fun StoreTile(item: StoreItem, state: StoreState, store: StoreController, navigation: StoreNavigation) {
    Column(
        Modifier.width(168.dp)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "${item.title()}. ${item.subtitle()}" }
            .clip(RoundedCornerShape(18.dp))
            .clickable { item.open(store, navigation) }
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PackCoverArt(item.accent(), item.cover(), Modifier.size(168.dp), RoundedCornerShape(26.dp), item.title())
        ClampedText(item.title(), style = AtlasTheme.typography.label, maxLines = 2)
        ClampedText(item.subtitle(), style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, maxLines = 2)
        when (item) {
            is StoreItem.Pack -> PackAction(item.pack, state, store, navigation, large = false)
            is StoreItem.Mix -> AtlasText("Mix", style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
        }
    }
}

@Composable
private fun RankedList(packs: List<StorePack>, state: StoreState, store: StoreController) {
    val index = state.index ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        packs.forEachIndexed { position, pack -> PackRow(position + 1, pack, index, state, store) }
    }
}

@Composable
private fun PackRow(rank: Int?, pack: StorePack, index: StoreIndex, state: StoreState, store: StoreController) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AtlasTheme.colors.paper)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = listOfNotNull(rank?.let { "Number $it" }, pack.title, pack.subtitle).joinToString(". ") }
            .clickable { store.openPack(pack.packId) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rank?.let { AtlasText(it.toString(), Modifier.width(28.dp), AtlasTheme.typography.heading, AtlasTheme.colors.mutedPlum, TextAlign.Center) }
        PackCoverArt(pack.accent(), pack.cover(), Modifier.size(64.dp), RoundedCornerShape(16.dp), pack.title)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ClampedText(pack.title, style = AtlasTheme.typography.label, maxLines = 2)
            ClampedText(pack.subtitle.ifBlank { index.placementName(pack) }, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, maxLines = 2)
        }
        PackAction(pack, state, store, null, large = false)
    }
}

@Composable
private fun MixRow(mix: StoreMix, navigation: StoreNavigation) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AtlasTheme.colors.paper)
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "Mix. ${mix.title}. ${mix.subtitle}" }
            .clickable { navigation.onOpenMix(mix.mixId) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PackCoverArt(mix.accentColor.toColorOr(AtlasTheme.colors.outline), StoreItem.Mix(mix).cover(), Modifier.size(64.dp), RoundedCornerShape(16.dp), mix.title)
        Column(Modifier.weight(1f)) {
            AtlasText(mix.title, style = AtlasTheme.typography.label)
            AtlasText("Mix · ${mix.subtitle}", style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
        }
    }
}

@Composable
private fun PackAction(
    pack: StorePack,
    state: StoreState,
    store: StoreController,
    navigation: StoreNavigation?,
    large: Boolean,
    onAccent: Boolean = false,
) {
    val download = state.downloads[pack.packId]
    if (download != null) {
        DownloadProgressView(pack, download, large) { store.cancelDownload(pack.packId) }
        return
    }
    val availability = state.availability(pack)
    val (label, action) = when (availability) {
        PackAvailability.NotInstalled -> "Get" to { store.get(pack.packId) }
        is PackAvailability.UpdateAvailable -> "Update" to { store.update(pack.packId) }
        is PackAvailability.UpToDate -> "Open" to { navigation?.onOpenInstalledPack?.invoke(pack.packId) ?: store.openPack(pack.packId) }
        is PackAvailability.NeedsNewerApp -> "Needs a newer app" to {}
    }
    val enabled = availability !is PackAvailability.NeedsNewerApp
    val description = when (availability) {
        PackAvailability.NotInstalled -> "Get ${pack.title}"
        is PackAvailability.UpdateAvailable -> "Update ${pack.title} to version ${availability.availableVersion}"
        is PackAvailability.UpToDate -> "Open ${pack.title}"
        is PackAvailability.NeedsNewerApp -> "${pack.title} needs a newer version of Bert's Atlas"
    }
    if (large) {
        AtlasButton(label, action, Modifier.fillMaxWidth(), enabled = enabled, contentDescription = description)
        if (availability is PackAvailability.UpdateAvailable) {
            AtlasText(
                "You have version ${availability.installedVersion}. Updating keeps your progress.",
                style = AtlasTheme.typography.detail,
                color = AtlasTheme.colors.mutedPlum,
            )
        }
    } else {
        PillButton(label, action, enabled, description, onAccent)
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit, enabled: Boolean, description: String, onAccent: Boolean) {
    val colors = AtlasTheme.colors
    val background = if (onAccent) colors.white else colors.atlasBlue.copy(alpha = .12f)
    val foreground = if (onAccent) colors.plum else colors.atlasBlue
    Box(
        Modifier.heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(if (enabled) background else colors.outline.copy(alpha = .2f))
            .semantics { role = Role.Button; contentDescription = description }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        AtlasText(label, style = AtlasTheme.typography.label, color = foreground)
    }
}

@Composable
private fun DownloadProgressView(pack: StorePack, download: PackDownload, large: Boolean, onCancel: () -> Unit) {
    val label = when (download.stage) {
        DownloadStage.Downloading -> download.fraction?.let { "Downloading ${(it * 100).toInt()}%" } ?: "Downloading"
        DownloadStage.Verifying -> "Checking the file"
        DownloadStage.Installing -> "Adding to your library"
    }
    Row(
        Modifier.then(if (large) Modifier.fillMaxWidth() else Modifier.widthIn(min = 120.dp))
            .semantics(mergeDescendants = true) { contentDescription = "$label for ${pack.title}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f, fill = large).then(if (large) Modifier else Modifier.width(110.dp)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ClampedText(label, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, maxLines = 1)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(AtlasTheme.colors.outline.copy(alpha = .25f))) {
                Box(Modifier.fillMaxWidth(download.fraction ?: .15f).fillMaxHeight().clip(CircleShape).background(AtlasTheme.colors.atlasBlue))
            }
        }
        if (download.stage == DownloadStage.Downloading) {
            AtlasButton("Cancel", onCancel, style = AtlasButtonStyle.Quiet, contentDescription = "Cancel downloading ${pack.title}")
        }
    }
}

@Composable
private fun FactCell(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.background(AtlasTheme.colors.paper, RoundedCornerShape(16.dp)).semantics(mergeDescendants = true) { contentDescription = "$value $label" }.padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ClampedText(value, style = AtlasTheme.typography.heading, textAlign = TextAlign.Center, maxLines = 1)
        AtlasText(label, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ShelfTitle(text: String) {
    Spacer(Modifier.height(4.dp))
    AtlasText(text, Modifier.semantics { heading() }, AtlasTheme.typography.heading)
}

@Composable
private fun TopicRow(name: String, packCount: Int, showDivider: Boolean, onOpen: () -> Unit) {
    val countLabel = when (packCount) { 0 -> "Coming soon"; 1 -> "1 pack"; else -> "$packCount packs" }
    Column(Modifier.fillMaxWidth()) {
        if (showDivider) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(AtlasTheme.colors.outline.copy(alpha = .2f)))
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 60.dp)
                .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "$name, $countLabel" }
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AtlasText(name, Modifier.weight(1f), AtlasTheme.typography.label)
            AtlasText(countLabel, style = AtlasTheme.typography.detail, color = AtlasTheme.colors.mutedPlum)
            AtlasText("  ›", style = AtlasTheme.typography.heading, color = AtlasTheme.colors.mutedPlum)
        }
    }
}

@Composable
private fun SearchButton(onClick: () -> Unit) {
    AtlasButton("Search", onClick, style = AtlasButtonStyle.Quiet, contentDescription = "Search the store")
}

@Composable
private fun ClampedText(text: String, style: androidx.compose.ui.text.TextStyle, maxLines: Int, color: Color = AtlasTheme.colors.plum, textAlign: TextAlign? = null) {
    androidx.compose.foundation.text.BasicText(
        text,
        style = if (textAlign == null) style.copy(color = color) else style.copy(color = color, textAlign = textAlign),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun StoreController.backFromPage() {
    back()
}

private fun StoreItem.open(store: StoreController, navigation: StoreNavigation) = when (this) {
    is StoreItem.Pack -> store.openPack(pack.packId)
    is StoreItem.Mix -> navigation.onOpenMix(mix.mixId)
}

private fun StoreItem.title() = when (this) {
    is StoreItem.Pack -> pack.title
    is StoreItem.Mix -> mix.title
}

private fun StoreItem.subtitle() = when (this) {
    is StoreItem.Pack -> pack.subtitle
    is StoreItem.Mix -> mix.subtitle
}

@Composable
private fun StoreItem.accent(): Color = when (this) {
    is StoreItem.Pack -> pack.accent()
    is StoreItem.Mix -> mix.accentColor.toColorOr(AtlasTheme.colors.outline)
}

private fun StoreItem.cover(): PackCover = when (this) {
    is StoreItem.Pack -> pack.cover()
    is StoreItem.Mix -> if (mix.mixId == BundledMixes.Geography.mixId) PackCover.Mix else PackCover.Generic
}

@Composable
private fun StorePack.accent(): Color = accentColor.toColorOr(AtlasTheme.colors.atlasBlue)

private fun StorePack.cover(): PackCover = when (packId) {
    BuiltInPacks.FLAGS -> PackCover.Flags
    BuiltInPacks.CAPITALS -> PackCover.Capitals
    else -> PackCover.Generic
}

private fun StoreIndex.placementName(pack: StorePack): String {
    val category = category(pack.category) ?: return ""
    val subcategory = category.subcategories.firstOrNull { it.id == pack.subcategory }
    return listOfNotNull(category.name, subcategory?.name).joinToString(" · ")
}

private fun rankedPacks(index: StoreIndex, categoryId: String, subcategoryId: String?): List<StorePack> {
    val scope = if (subcategoryId == null) ShelfScope.category(categoryId) else ShelfScope.subcategory(categoryId, subcategoryId)
    val curated = index.shelvesFor(scope).flatMap { index.resolveItems(it) }.filterIsInstance<StoreItem.Pack>().map { it.pack }
    val inScope = index.packsIn(categoryId, subcategoryId)
    return (curated.filter { it in inScope } + inScope).distinctBy { it.packId }
}

private fun String.toColorOr(fallback: Color): Color =
    removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: fallback

private fun Color.compositeOver(background: Color): Color {
    val alpha = this.alpha
    return Color(
        red = red * alpha + background.red * (1 - alpha),
        green = green * alpha + background.green * (1 - alpha),
        blue = blue * alpha + background.blue * (1 - alpha),
    )
}

private fun String.iconGlyph(): String = when (this) {
    "globe" -> "◍"
    "speech" -> "❝"
    "code" -> "</>"
    "flask", "science" -> "⚗"
    "book", "history" -> "❦"
    "music" -> "♪"
    "calculator", "math" -> "∑"
    else -> "✦"
}

private fun Long.readableSize(): String = when {
    this >= 1_000_000 -> "${(this / 100_000) / 10.0} MB"
    this >= 1_000 -> "${this / 1_000} KB"
    else -> "$this B"
}

private val answerDivider = Regex("(?i)<hr[^>]*id\\s*=\\s*[\"']?answer[\"']?[^>]*>")

private fun String.afterAnswerDivider(): String = answerDivider.split(this, limit = 2).getOrNull(1)?.takeIf { it.isNotBlank() } ?: this
private val PULL_TO_REFRESH_DISTANCE = 80.dp
