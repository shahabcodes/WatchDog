package com.shahaabapps.watchdog.feature.guide

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahaabapps.core.ui.components.Celebrations
import com.shahaabapps.core.ui.components.CupertinoCard
import com.shahaabapps.core.ui.components.CupertinoPrimaryButton
import com.shahaabapps.core.ui.components.CupertinoTextButton
import com.shahaabapps.core.ui.components.CupertinoTextField
import com.shahaabapps.core.ui.components.GuardianHeader
import com.shahaabapps.core.ui.components.dashReveal
import com.shahaabapps.core.ui.components.rememberReducedMotion
import com.shahaabapps.core.ui.theme.AppFonts
import com.shahaabapps.core.ui.theme.AppTheme
import com.shahaabapps.core.ui.theme.LighthouseColors
import com.shahaabapps.core.ui.theme.Spacing
import com.shahaabapps.watchdog.R
import kotlinx.coroutines.launch

/**
 * Local, per-phone memory for the App guide: whether it has been shown once
 * ([seen], so it only opens by itself the first time) and which chapters the
 * parent has opened (ticked on the index).
 */
class AppGuidePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_guide", Context.MODE_PRIVATE)

    val seen: Boolean get() = prefs.getBoolean(KEY_SEEN, false)

    fun markSeen() {
        if (!seen) prefs.edit().putBoolean(KEY_SEEN, true).apply()
    }

    fun openedChapters(): Set<String> = prefs.getStringSet(KEY_OPENED, null)?.toSet() ?: emptySet()

    fun markOpened(chapterId: String) {
        val opened = openedChapters()
        if (chapterId !in opened) prefs.edit().putStringSet(KEY_OPENED, opened + chapterId).apply()
    }

    private companion object {
        const val KEY_SEEN = "seen_v1"
        const val KEY_OPENED = "opened_chapters"
    }
}

// ---- Index ----------------------------------------------------------------------

/**
 * The guide's index ("App guide"): search plus every chapter grouped by section.
 * With [showCover] (first launch, "Take the tour") a full-navy cover comes first.
 */
@Composable
fun AppGuideIndexScreen(
    showCover: Boolean,
    onOpenChapter: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { AppGuidePrefs(context) }
    // Leaving the guide in any way (back, close, finishing) counts as having seen it.
    DisposableEffect(Unit) { onDispose { prefs.markSeen() } }

    var coverVisible by rememberSaveable { mutableStateOf(showCover) }
    val firstChapter = AppGuideContent.chapters.first().id
    if (coverVisible) {
        com.shahaabapps.core.ui.theme.LighthouseType {
            GuideHeroPage(
                eyebrow = "APP GUIDE",
                headline = "Every screen,\nexplained.",
                subtitle = "${AppGuideContent.chapters.size} short chapters",
                primaryText = "Start the tour",
                onPrimary = { coverVisible = false; onOpenChapter(firstChapter) },
                secondaryText = "Browse chapters",
                onSecondary = { coverVisible = false },
                onClose = onBack,
            )
        }
        return
    }

    // Re-read each time the index comes back into view, so new ticks show up.
    val opened = remember { prefs.openedChapters() }
    var query by rememberSaveable { mutableStateOf("") }
    // Edge-to-edge: keep the last results above the keyboard while searching.
    val bottomInset = maxOf(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.groupedBackground),
    ) {
        GuardianHeader(title = "App guide", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.xl + bottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "search") {
                CupertinoTextField(value = query, onValueChange = { query = it }, placeholder = "Search the guide")
            }
            if (query.isBlank()) {
                item(key = "tour") {
                    TourCard(onClick = { onOpenChapter(firstChapter) }, modifier = Modifier.dashReveal())
                }
                AppGuideContent.sections.forEachIndexed { si, section ->
                    item(key = section.label) {
                        GuideSectionBlock(
                            label = section.label,
                            rows = section.chapterIds.mapNotNull { id -> AppGuideContent.chapter(id)?.let { it to it.tagline } },
                            opened = opened,
                            onOpen = onOpenChapter,
                            modifier = Modifier.dashReveal(delayMillis = (si + 1) * 40),
                        )
                    }
                }
            } else {
                val results = searchGuide(query)
                item(key = "results") {
                    if (results.isEmpty()) {
                        Text(
                            "Nothing in the guide matches “${query.trim()}”. Try a screen name such as Map or Focus.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.secondaryLabel,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                        )
                    } else {
                        GuideSectionBlock(
                            label = if (results.size == 1) "1 chapter" else "${results.size} chapters",
                            rows = results,
                            opened = opened,
                            onOpen = onOpenChapter,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chapters whose text mentions every word of [query]. The subtitle is the first
 * matching step's title, or the tagline when the match is elsewhere.
 */
private fun searchGuide(query: String): List<Pair<GuideChapter, String>> {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return AppGuideContent.chapters.mapNotNull { ch ->
        val haystack = buildString {
            append(ch.title).append(' ').append(ch.tagline).append(' ').append(ch.whereToFind).append(' ')
            ch.steps.forEach { append(it.title).append(' ').append(it.body).append(' ') }
            append(ch.tip.orEmpty())
        }.lowercase()
        if (words.all { it in haystack }) {
            val step = ch.steps.firstOrNull { s -> words.any { it in s.title.lowercase() || it in s.body.lowercase() } }
            ch to (step?.let { "Step ${ch.steps.indexOf(it) + 1} · ${it.title}" } ?: ch.tagline)
        } else {
            null
        }
    }
}

/** The navy "Take the tour" card at the top of the index, styled like Home's banners. */
@Composable
private fun TourCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(LighthouseColors.NavyDeep)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 14.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_lighthouse),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(48.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Take the tour", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "${AppGuideContent.chapters.size} short chapters, start to finish",
                color = LighthouseColors.Subtitle,
                fontSize = 13.sp,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(AppTheme.colors.gold),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = AppTheme.colors.onGold,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A DashSection-style uppercase label above one card of chapter rows. */
@Composable
private fun GuideSectionBlock(
    label: String,
    rows: List<Pair<GuideChapter, String>>,
    opened: Set<String>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        GuideSectionLabel(label)
        CupertinoCard {
            rows.forEachIndexed { i, (chapter, subtitle) ->
                GuideChapterRow(
                    chapter = chapter,
                    subtitle = subtitle,
                    opened = chapter.id in opened,
                    showDivider = i < rows.lastIndex,
                    onClick = { onOpen(chapter.id) },
                )
            }
        }
    }
}

/** Uppercase accent section label, the same as Home's DashSection titles. */
@Composable
private fun GuideSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = AppTheme.colors.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(start = Spacing.xs).semantics { heading() },
    )
}

/**
 * One chapter on the index, laid out like a CupertinoListRow: a tinted icon
 * circle, title and tagline, then a read tick, the step count and a chevron.
 */
@Composable
private fun GuideChapterRow(
    chapter: GuideChapter,
    subtitle: String,
    opened: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = Spacing.rowMinHeight)
                .padding(vertical = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(AppTheme.colors.accent.copy(alpha = 0.12f)),
            ) {
                Icon(chapter.icon, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(20.dp))
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Text(
                    chapter.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (opened) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Read",
                    tint = AppTheme.colors.success,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                "${chapter.steps.size} steps",
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.secondaryLabel,
                maxLines = 1,
            )
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = AppTheme.colors.secondaryLabel,
                modifier = Modifier.size(20.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(color = AppTheme.colors.separator, modifier = Modifier.padding(start = 52.dp))
        }
    }
}

// ---- Chapters ---------------------------------------------------------------------

/**
 * The chapters as a swipeable pager, opened at [startChapterId]. Each page is a
 * navy header (illustration, eyebrow, title, tagline) over a sheet with where to
 * find it, the numbered steps and a tip. Finishing the last chapter celebrates and
 * shows the closing page. [onBack] returns to the index; [onExit] leaves the guide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGuideChapterScreen(
    startChapterId: String,
    childName: String?,
    onBack: () -> Unit,
    onExit: () -> Unit,
) {
    val chapters = AppGuideContent.chapters
    val context = LocalContext.current
    val prefs = remember { AppGuidePrefs(context) }
    DisposableEffect(Unit) { onDispose { prefs.markSeen() } }
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = AppGuideContent.indexOf(startChapterId).coerceAtLeast(0)) {
        chapters.size
    }
    var finished by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }

    // A chapter counts as read once its page has settled on screen.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { prefs.markOpened(chapters[it].id) }
    }

    fun goTo(page: Int) {
        scope.launch {
            if (reduced) pagerState.scrollToPage(page) else pagerState.animateScrollToPage(page)
        }
    }

    if (finished) {
        BackHandler { finished = false }
        com.shahaabapps.core.ui.theme.LighthouseType {
            GuideHeroPage(
                eyebrow = "YOU'RE ALL SET",
                headline = "You know Watch Dog inside out.",
                subtitle = "Open this guide again any time from Settings → App guide.",
                primaryText = "Done",
                onPrimary = onExit,
                secondaryText = "Browse chapters",
                onSecondary = onBack,
                onClose = onExit,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LighthouseColors.NavyDeep),
    ) {
        // Fixed top bar: back to the index, position, all chapters, close.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        ) {
            NavyCircleButton(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the guide's chapters", onClick = onBack)
            Text(
                "${pagerState.currentPage + 1} of ${chapters.size}",
                color = LighthouseColors.Subtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 14.dp),
            )
            NavyCircleButton(
                Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = "Jump to a chapter",
                onClick = { showAll = true },
            )
            Spacer(Modifier.width(10.dp))
            NavyCircleButton(Icons.Filled.Close, contentDescription = "Close the guide", onClick = onExit)
        }
        HorizontalPager(
            state = pagerState,
            key = { chapters[it].id },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val isLast = page == chapters.lastIndex
            ChapterPage(
                chapter = chapters[page],
                number = page + 1,
                childName = childName,
                nextTitle = chapters.getOrNull(page + 1)?.title,
                onNext = {
                    if (isLast) {
                        prefs.markSeen()
                        Celebrations.saved()
                        finished = true
                    } else {
                        goTo(page + 1)
                    }
                },
                onPrevious = if (page > 0) ({ goTo(page - 1) }) else null,
                onAllChapters = { showAll = true },
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.card)
                .navigationBarsPadding()
                .padding(vertical = 12.dp),
        ) {
            PageDots(count = chapters.size, current = pagerState.currentPage, reduced = reduced)
        }
    }

    if (showAll) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showAll = false }, sheetState = sheetState) {
            AllChaptersSheet(
                current = pagerState.currentPage,
                opened = remember(pagerState.settledPage) { prefs.openedChapters() },
                onPick = { index -> showAll = false; goTo(index) },
            )
        }
    }
}

/** One chapter: navy header with its illustration, then the sheet of steps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterPage(
    chapter: GuideChapter,
    number: Int,
    childName: String?,
    nextTitle: String?,
    onNext: () -> Unit,
    onPrevious: (() -> Unit)?,
    onAllChapters: () -> Unit,
) {
    val sheetColor = AppTheme.colors.card
    val section = AppGuideContent.sectionOf(chapter.id)?.label.orEmpty()
    Box(Modifier.fillMaxSize()) {
        // Sheet colour under the lower half, so a short chapter never shows navy below its sheet.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(sheetColor),
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.background(LighthouseColors.NavyDeep)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    GuideIllustration(
                        chapterId = chapter.id,
                        contentDescription = chapter.title,
                        childName = childName,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "CHAPTER %02d · %s".format(number, section.uppercase()),
                            color = AppTheme.colors.gold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            chapter.title,
                            color = Color.White,
                            fontFamily = AppFonts.lighthouse,
                            fontSize = 28.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.7).sp,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            AppGuideContent.personalise(chapter.tagline, childName),
                            color = LighthouseColors.Subtitle,
                            fontSize = 15.sp,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(sheetColor)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    GuideSectionLabel("Where to find it")
                    WhereToFind(chapter.whereToFind)
                    GuideSectionLabel("Step by step", Modifier.padding(top = Spacing.xs))
                    chapter.steps.forEachIndexed { i, step ->
                        StepCard(
                            number = i + 1,
                            step = step,
                            childName = childName,
                            modifier = Modifier.dashReveal(delayMillis = i * 40),
                        )
                    }
                    chapter.tip?.let { tip ->
                        TipCard(
                            text = AppGuideContent.personalise(tip, childName),
                            modifier = Modifier.dashReveal(delayMillis = chapter.steps.size * 40),
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    CupertinoPrimaryButton(
                        text = if (nextTitle == null) "Got it" else "Next: $nextTitle",
                        onClick = onNext,
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (onPrevious != null) {
                            CupertinoTextButton(text = "Previous", onClick = onPrevious, modifier = Modifier.weight(1f))
                        }
                        CupertinoTextButton(text = "All chapters", onClick = onAllChapters, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** The "→" path as accent pills with chevrons between, wrapping on narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhereToFind(path: String) {
    val parts = path.split("→").map { it.trim() }.filter { it.isNotEmpty() }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Where to find it: " + parts.joinToString(", then ") },
    ) {
        parts.forEachIndexed { i, part ->
            if (i > 0) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.size(16.dp).align(Alignment.CenterVertically),
                )
            }
            Text(
                part,
                style = MaterialTheme.typography.labelMedium,
                color = AppTheme.colors.accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppTheme.colors.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** A numbered step: gold number badge, title, and body with on-screen labels in accent. */
@Composable
private fun StepCard(number: Int, step: GuideStep, childName: String?, modifier: Modifier = Modifier) {
    CupertinoCard(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.gold)
                    .clearAndSetSemantics { contentDescription = "Step $number" },
            ) {
                Text("$number", color = AppTheme.colors.onGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    AppGuideContent.personalise(step.title, childName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    rememberQuotedLabels(AppGuideContent.personalise(step.body, childName)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.secondaryLabel,
                )
            }
        }
    }
}

/** The "GOOD TO KNOW" tip: accent-tinted card with a lightbulb. */
@Composable
private fun TipCard(text: String, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AppTheme.colors.accent.copy(alpha = 0.10f))
            .padding(Spacing.md),
    ) {
        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = AppTheme.colors.accent, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "GOOD TO KNOW",
                color = AppTheme.colors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
            Text(
                rememberQuotedLabels(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Shows "quoted" on-screen labels as accent SemiBold spans without the quote marks,
 * so they read exactly as the button or title does in the app.
 */
@Composable
private fun rememberQuotedLabels(text: String): AnnotatedString {
    val accent = AppTheme.colors.accent
    return remember(text, accent) {
        buildAnnotatedString {
            text.split('"').forEachIndexed { i, part ->
                if (i % 2 == 1) {
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(part) }
                } else {
                    append(part)
                }
            }
        }
    }
}

/** 6dp page dots: accent for the current chapter, separator for the rest. */
@Composable
private fun PageDots(count: Int, current: Int, reduced: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "Chapter ${current + 1} of $count" },
    ) {
        repeat(count) { i ->
            val color by animateColorAsState(
                if (i == current) AppTheme.colors.accent else AppTheme.colors.separator,
                animationSpec = if (reduced) tween(0) else tween(200),
                label = "guideDot",
            )
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        }
    }
}

/** Bottom sheet listing every chapter by section, to jump straight to one. */
@Composable
private fun AllChaptersSheet(current: Int, opened: Set<String>, onPick: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            "All chapters",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = Spacing.xs).semantics { heading() },
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AppGuideContent.sections.forEach { section ->
                item(key = "label-${section.label}") {
                    GuideSectionLabel(section.label, Modifier.padding(top = Spacing.sm, bottom = Spacing.xs))
                }
                section.chapterIds.forEach { id ->
                    val index = AppGuideContent.indexOf(id)
                    val chapter = AppGuideContent.chapter(id) ?: return@forEach
                    item(key = id) {
                        val selected = index == current
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) AppTheme.colors.accent.copy(alpha = 0.10f) else Color.Transparent)
                                .clickable(role = Role.Button) { onPick(index) }
                                .heightIn(min = Spacing.rowMinHeight)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) AppTheme.colors.gold else AppTheme.colors.accent.copy(alpha = 0.12f)),
                            ) {
                                Text(
                                    "${index + 1}",
                                    color = if (selected) AppTheme.colors.onGold else AppTheme.colors.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                chapter.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            )
                            if (chapter.id in opened) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Read",
                                    tint = AppTheme.colors.success,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Navy hero pages and controls ---------------------------------------------------

/**
 * The full-navy hero page (as on Welcome and the Connected screen): lighthouse
 * scene, gold eyebrow, big white headline, subtitle, and the CTAs at the bottom.
 * Scrolls when the screen is short or the font is large.
 */
@Composable
private fun GuideHeroPage(
    eyebrow: String,
    headline: String,
    subtitle: String,
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String?,
    onSecondary: () -> Unit,
    onClose: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(LighthouseColors.NavyDeep),
    ) {
        val wide = maxWidth >= 600.dp
        val minHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    NavyCircleButton(Icons.Filled.Close, contentDescription = "Close the guide", onClick = onClose)
                }
                GuideLighthouseScene(Modifier.fillMaxWidth().height(260.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(eyebrow, color = AppTheme.colors.gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(
                        headline,
                        color = Color.White,
                        style = if (wide) {
                            MaterialTheme.typography.displayLarge
                        } else {
                            MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp, lineHeight = 36.sp)
                        },
                        letterSpacing = (-0.8).sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(subtitle, color = LighthouseColors.Subtitle, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NavyGoldButton(primaryText, onPrimary)
                    if (secondaryText != null) NavyOutlineButton(secondaryText, onSecondary)
                }
            }
        }
    }
}

/**
 * The lighthouse scene with its beams glowing 0.18↔0.42 over 1.6s, as on Welcome.
 * Holds a still mid-glow (0.32) when animations are off.
 */
@Composable
private fun GuideLighthouseScene(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val beamAlpha = if (reduced) {
        0.32f
    } else {
        rememberInfiniteTransition(label = "guideBeam").animateFloat(
            initialValue = 0.18f,
            targetValue = 0.42f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "guideBeamAlpha",
        ).value
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_lighthouse_scene),
            contentDescription = "A lighthouse shining over the sea at night",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.ic_lighthouse_beams),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alpha = beamAlpha,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 56dp gold CTA on navy; shrinks to 0.96 while pressed (no ripple), like the auth GoldButton. */
@Composable
private fun NavyGoldButton(text: String, onClick: () -> Unit) {
    val reduced = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !reduced) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "guideCtaScale",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.gold)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Text(text, color = AppTheme.colors.onGold, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** 56dp outline button on navy (1.5dp border, white text), like "I already have an account". */
@Composable
private fun NavyOutlineButton(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.5.dp, LighthouseColors.Outline), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** The 44dp outlined circle button used on navy headers (back, list, close). */
@Composable
private fun NavyCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(BorderStroke(1.5.dp, LighthouseColors.Outline), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
