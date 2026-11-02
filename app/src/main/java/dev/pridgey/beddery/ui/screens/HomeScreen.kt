package dev.pridgey.beddery.ui.screens

import android.app.ActivityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pridgey.beddery.MainViewModel
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.savers.Saver
import dev.pridgey.beddery.savers.SaverCategory
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.Savers
import dev.pridgey.beddery.savers.rememberSaverContext
import dev.pridgey.beddery.standby.StandbyActivity
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** How long the gallery must sit still before thumbnails are drawn again. */
private const val STILL_SETTLE_MS = 450L

/** The gallery fills the screen; a sheet peeks up from the bottom with tonight's pick and setup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(settings: AppSettings, viewModel: MainViewModel, onOpenSaver: (String) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { checkForUpdatesIfDue(context) }
    val scope = rememberCoroutineScope()
    val sheet = rememberBottomSheetScaffoldState()
    val expanded = sheet.bottomSheetState.targetValue == SheetValue.Expanded
    val toggle: () -> Unit = {
        scope.launch { if (expanded) sheet.bottomSheetState.partialExpand() else sheet.bottomSheetState.expand() }
    }
    BackHandler(enabled = expanded) { scope.launch { sheet.bottomSheetState.partialExpand() } }
    val previewContext = rememberSaverContext(settings, preview = true)
    // Two pictures at once fills the screen about twice as fast; low-memory phones stay at one.
    val stills = remember {
        val lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice ?: true
        StillScheduler(parallel = if (lowRam) 1 else 2)
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalStillScheduler provides stills) {
        BottomSheetScaffold(
            scaffoldState = sheet,
            sheetPeekHeight = SHEET_PEEK + navBottom,
            sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            sheetShadowElevation = 8.dp,
            sheetDragHandle = {
                Box(
                    Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outline),
                )
            },
            sheetContent = {
                // Stop the open sheet just under the status bar instead of sliding beneath it.
                val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                BoxWithConstraints {
                    Box(Modifier.heightIn(max = maxHeight - statusTop - 32.dp)) {
                        TonightSheet(
                            settings, viewModel, previewContext,
                            onToggle = toggle,
                            onPreview = {
                                if (!settings.previewTipSeen) viewModel.update { it.copy(previewTipSeen = true) }
                                context.startActivity(StandbyActivity.intent(context, preview = true))
                            },
                            onOpenSettings = onOpenSettings,
                            onCollapse = { scope.launch { sheet.bottomSheetState.partialExpand() } },
                        )
                    }
                }
            },
        ) { padding ->
            GalleryGrid(
                settings, onOpenSaver, previewContext, stills, bottom = padding.calculateBottomPadding(),
                onToggleSize = { viewModel.update { it.copy(galleryDense = !it.galleryDense) } },
            )
        }
    }
}

/** Handle (20) + the peek row (68) + a sliver of air, so nothing below the row shows. The navigation bar is added on top. */
private val SHEET_PEEK = 92.dp

@Composable
private fun TonightSheet(
    settings: AppSettings,
    viewModel: MainViewModel,
    previewContext: SaverContext,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
    onOpenSettings: () -> Unit,
    onCollapse: () -> Unit,
) {
    // Null until a way to start is chosen; 0 once setup is done.
    val stepsLeft = rememberStepsLeft(settings)
    val ready = stepsLeft == 0
    val now = rememberMinute()
    // Day: the single pick, or a few from the shuffle mix.
    val dayPool = if (settings.shuffleEnabled) {
        settings.shuffleIds.mapNotNull { Savers.findOrNull(it) }.ifEmpty { Savers.all }
    } else listOf(Savers.find(settings.selectedSaverId))
    val dayName = if (settings.shuffleEnabled) {
        pluralStringResource(R.plurals.shown_shuffling, dayPool.size, dayPool.size)
    } else saverName(dayPool.first())
    val dayNameShort = if (settings.shuffleEnabled) stringResource(R.string.sheet_shuffle) else dayName
    // Same rule as the screensaver itself: the night pick takes over during the night hours.
    val night = settings.nightSaverId?.let { Savers.findOrNull(it) }
    val inNightHours = settings.isNightHours(now)
    val nightActive = night != null && inNightHours
    val nowSavers = if (nightActive) listOf(night!!) else dayPool.take(3)
    val nowName = if (nightActive) saverName(night!!) else dayName
    val nextSavers = when {
        night == null -> emptyList()
        nightActive -> dayPool.take(3)
        else -> listOf(night)
    }

    // Second line: setup comes first while it's unfinished, otherwise what happens next.
    val warn = MaterialTheme.colorScheme.tertiary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val nextAt = nightTime(if (inNightHours) settings.nightEndMinutes else settings.nightStartMinutes)
    val line: Triple<ImageVector, String, Color> = when {
        stepsLeft == null -> Triple(Icons.Rounded.Schedule, stringResource(R.string.setup_status_choose), warn)
        !ready -> Triple(Icons.Rounded.Schedule, pluralStringResource(R.plurals.setup_status_steps, stepsLeft, stepsLeft), warn)
        night != null && nightActive -> Triple(Icons.Rounded.LightMode, stringResource(R.string.sheet_next_at, dayNameShort, nextAt), muted)
        night != null -> Triple(Icons.Rounded.Bedtime, stringResource(R.string.sheet_next_at, saverName(night), nextAt), muted)
        !settings.nightModeEnabled -> Triple(Icons.Rounded.CheckCircle, stringResource(R.string.sheet_ready), muted)
        inNightHours -> Triple(Icons.Rounded.Bedtime, stringResource(R.string.sheet_night_mode_until, nextAt), muted)
        else -> Triple(Icons.Rounded.Bedtime, stringResource(R.string.sheet_night_mode_at, nextAt), muted)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 20.dp, end = 12.dp)
                .height(68.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PickStack(nowSavers, nextSavers, ready, settings, previewContext)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(nowName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(line.first, null, tint = line.third, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(line.second, style = MaterialTheme.typography.bodyMedium, color = line.third, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.tab_settings))
            }
            Spacer(Modifier.width(4.dp))
            FilledIconButton(onClick = onPreview, shape = RoundedCornerShape(16.dp), modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_preview))
            }
        }
        // Keeps the gesture-bar strip under the peeking row empty.
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        // Always built: the sheet needs its full height for a swipe up to have somewhere to go.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            LaunchSection(settings, viewModel)
            ShownSection(settings, viewModel, onOpenGallery = onCollapse)
            TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)) {
                Text(stringResource(R.string.sheet_all_settings))
            }
        }
    }
}

/**
 * What's showing now, up front, with what comes next dimmed beside or behind it. A shuffle mix
 * shows as a small fan. The badge says whether setup is finished.
 */
@Composable
private fun PickStack(now: List<Saver>, next: List<Saver>, ready: Boolean, settings: AppSettings, context: SaverContext) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            // One single pick tucks behind another; a fan sits beside instead, so neither gets lost.
            if (next.size == 1 && now.size == 1) {
                Thumb(next[0], settings, context, Modifier.padding(start = 14.dp).graphicsLayer { alpha = 0.6f; rotationZ = 6f })
            }
            Fan(now, settings, context)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (ready) Icons.Rounded.Check else Icons.Rounded.Schedule,
                    contentDescription = if (ready) stringResource(R.string.sheet_ready) else null,
                    tint = if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
        if (next.size > 1 || (next.size == 1 && now.size > 1)) {
            Spacer(Modifier.width(10.dp))
            Fan(next, settings, context, Modifier.graphicsLayer { alpha = 0.6f; scaleX = 0.85f; scaleY = 0.85f })
        }
    }
}

/** One to three thumbnails, fanned out a little when there's more than one. */
@Composable
private fun Fan(savers: List<Saver>, settings: AppSettings, context: SaverContext, modifier: Modifier = Modifier) {
    val n = savers.size
    Box(modifier.width(28.dp + 9.dp * (n - 1))) {
        savers.forEachIndexed { i, saver ->
            val tilt = if (n == 1) 0f else (i - (n - 1) / 2f) * 7f
            Thumb(saver, settings, context, Modifier.padding(start = 9.dp * i).graphicsLayer { rotationZ = tilt })
        }
    }
}

@Composable
private fun Thumb(saver: Saver, settings: AppSettings, context: SaverContext, modifier: Modifier = Modifier) {
    SaverStill(
        saver, settings, context,
        modifier
            .width(28.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(7.dp)),
        corner = 6.dp,
    )
}

/** The current time, refreshed at the top of every minute. */
@Composable
private fun rememberMinute(): LocalTime {
    val now by produceState(LocalTime.now()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = LocalTime.now()
        }
    }
    return now
}

@Composable
private fun GalleryGrid(
    settings: AppSettings,
    onOpenSaver: (String) -> Unit,
    previewContext: SaverContext,
    stills: StillScheduler,
    bottom: Dp,
    onToggleSize: () -> Unit,
) {
    val dense = settings.galleryDense
    var filter by rememberSaveable { mutableStateOf<SaverCategory?>(null) }
    var shuffleOnly by rememberSaveable { mutableStateOf(false) }
    // With nothing marked, shuffle uses every screensaver, so the filter would show them all.
    val hasShuffleList = settings.shuffleIds.isNotEmpty()
    val savers = Savers.all.filter {
        (filter == null || it.category == filter) && (!shuffleOnly || !hasShuffleList || it.id in settings.shuffleIds)
    }
    val showUpdate = rememberUpdateBannerVisible()
    val grid = rememberLazyGridState()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LaunchedEffect(grid) {
        snapshotFlow { grid.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling) {
                stills.paused = true
            } else {
                // People often flick again right away: wait for the list to settle first.
                delay(STILL_SETTLE_MS)
                stills.paused = false
            }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (dense) 104.dp else 152.dp),
        state = grid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (dense) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (dense) 10.dp else 16.dp),
    ) {
        if (showUpdate) item(span = { GridItemSpan(maxLineSpan) }, key = "update") { UpdateBanner() }
        item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
            // Chips scroll; the card-size toggle stays pinned at the end.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).fadeEnd(24.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filter == null && !(shuffleOnly && hasShuffleList),
                        onClick = { filter = null; shuffleOnly = false },
                        label = { Text(stringResource(R.string.category_all)) },
                    )
                    if (hasShuffleList) {
                        FilterChip(
                            selected = shuffleOnly,
                            onClick = { shuffleOnly = !shuffleOnly },
                            leadingIcon = { Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp)) },
                            label = { Text(stringResource(R.string.state_in_shuffle)) },
                        )
                    }
                    SaverCategory.entries.forEach { c ->
                        FilterChip(
                            selected = filter == c,
                            onClick = { filter = if (filter == c) null else c },
                            label = { Text(stringResource(c.label)) },
                        )
                    }
                }
                IconButton(onClick = onToggleSize) {
                    Icon(
                        if (dense) Icons.Rounded.GridView else Icons.Rounded.ViewModule,
                        contentDescription = stringResource(if (dense) R.string.action_cards_larger else R.string.action_cards_smaller),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(savers, key = { it.id }) { saver ->
            SaverCard(saver, settings, previewContext, dense, onClick = { onOpenSaver(saver.id) })
        }
    }
}

fun AppSettings.isInRotation(id: String): Boolean =
    if (shuffleEnabled) shuffleIds.isEmpty() || id in shuffleIds else selectedSaverId == id

@Composable
private fun SaverCard(saver: Saver, settings: AppSettings, context: SaverContext, dense: Boolean, onClick: () -> Unit) {
    val active = settings.isInRotation(saver.id)
    val activeLabel = stringResource(if (settings.shuffleEnabled) R.string.state_in_shuffle else R.string.state_current)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(if (dense) 18.dp else 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.semantics { if (active) stateDescription = activeLabel },
    ) {
        Box {
            SaverStill(saver, settings, context, Modifier.fillMaxWidth().padding(if (dense) 6.dp else 8.dp), corner = if (dense) 12.dp else 16.dp)
            if (saver.refined && !dense) RefinedBadge(Modifier.align(Alignment.TopStart).padding(16.dp), onDark = true)
            if (active) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(if (dense) 11.dp else 16.dp)
                        .size(if (dense) 22.dp else 28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (settings.shuffleEnabled) Icons.Rounded.Shuffle else Icons.Rounded.Check, null,
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(if (dense) 13.dp else 16.dp),
                    )
                }
            }
        }
        if (dense) {
            // Small cards keep just the name; the group chips already say which group it's in.
            Text(
                saverName(saver), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 0.dp),
            )
        } else {
            Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(saver.icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(saver.title), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(saver.category.label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** "Aurora", or "Aurora (Refined)" for the polished version, so the two are never confused. */
@Composable
fun saverName(saver: Saver): String {
    val title = stringResource(saver.title)
    return if (saver.refined) stringResource(R.string.saver_refined_name, title) else title
}

/** Fades the last [width] of the content to transparent, so a scrolling row doesn't end in a hard cut. */
private fun Modifier.fadeEnd(width: Dp) = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx()
        drawRect(
            Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
            blendMode = BlendMode.DstIn,
        )
    }
