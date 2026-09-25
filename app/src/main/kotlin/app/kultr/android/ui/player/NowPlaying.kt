package app.kultr.android.ui.player

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import app.kultr.android.playback.PlayerUiState
import app.kultr.android.playback.TransitionInfo
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.components.Artwork
import app.kultr.android.ui.components.ArtworkFill
import app.kultr.android.ui.components.Eyebrow
import app.kultr.android.ui.components.GlassPanel
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.Segmented
import app.kultr.android.ui.components.Tag
import app.kultr.android.ui.components.rememberArtworkUrl
import app.kultr.android.ui.starredShown
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Song
import app.kultr.core.dsp.TrackAnalysis
import app.kultr.core.util.Format
import app.kultr.core.util.LyricsDoc
import coil3.compose.AsyncImage
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

private enum class PlayerTab(val label: String) { QUEUE("Up next"), LYRICS("Lyrics"), INJEKT("InjeKt") }

/** Blurred artwork behind everything, darkened so text stays readable. */
@Composable
fun ArtworkBackdrop(coverId: String?, modifier: Modifier = Modifier) {
    val colors = Kultr.colors
    val url = rememberArtworkUrl(coverId, if (Build.VERSION.SDK_INT >= 31) 200 else 40)
    Box(modifier.fillMaxSize().background(colors.background)) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (colors.dark) 0.55f else 0.45f }
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(70.dp) else Modifier),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        colors.background.copy(alpha = 0.35f),
                        colors.accent.copy(alpha = 0.12f),
                        colors.background.copy(alpha = 0.92f),
                    ),
                ),
            ),
        )
    }
}

@Composable
fun NowPlayingScreen(state: PlayerUiState, onClose: () -> Unit) {
    val actions = LocalActions.current
    val graph = actions.graph
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val transition by graph.hub.transition.collectAsStateWithLifecycle()
    val downloaded by graph.offline.downloadedIds.collectAsStateWithLifecycle()
    val song = state.current
    val colors = Kultr.colors
    var tab by rememberSaveable { mutableStateOf(PlayerTab.QUEUE) }
    val listState = rememberLazyListState()
    val pull = rememberPullToDismiss(onClose)

    Box(
        Modifier
            .fillMaxSize()
            // Pulled down, the player is one card over the app, which dims behind it.
            .drawBehind {
                val shown = (pull.offset / 48.dp.toPx()).coerceIn(0f, 1f) * (1f - (pull.offset / size.height).coerceIn(0f, 1f))
                if (shown > 0f) drawRect(Color.Black.copy(alpha = 0.5f * shown))
            }
            .graphicsLayer {
                translationY = pull.offset
                val shrink = (pull.offset / size.height).coerceIn(0f, 1f) * 0.08f
                scaleX = 1f - shrink
                scaleY = 1f - shrink
                // The screen's own rounded corners and a shadow, as soon as it moves.
                val lifted = (pull.offset / 24.dp.toPx()).coerceIn(0f, 1f)
                if (lifted > 0f) {
                    shape = RoundedCornerShape((34 * lifted).dp)
                    clip = true
                    shadowElevation = 24.dp.toPx() * lifted
                } else {
                    clip = false
                    shadowElevation = 0f
                }
            }
            .nestedScroll(pull.connection),
    ) {
        ArtworkBackdrop(song?.artworkId)
        if (song == null) {
            Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.Start)) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player")
                }
                Spacer(Modifier.weight(1f))
                Text("Nothing is playing.", color = colors.ink2)
                Spacer(Modifier.weight(1f))
            }
            return@Box
        }
        val live by remember(song.id) { graph.library.song(song.id) }.collectAsStateWithLifecycle(null)
        val current = live ?: song
        val position by rememberPosition()
        val plan = transition.upcoming?.takeIf { transition.fromSongId == song.id }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "header") {
                Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp)) {
                    PlayerTopBar(current, settings.injektEnabled, downloaded.contains(current.id), onClose)
                    Spacer(Modifier.height(8.dp))
                    SwipeArtwork(current)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        current.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(
                            current.artist.orEmpty(),
                            color = colors.ink2,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            modifier = Modifier.clickable(enabled = current.artistId != null) {
                                onClose()
                                actions.openArtist(current.artistId)
                            },
                        )
                        if (!current.album.isNullOrEmpty() && !current.isRadio) {
                            Text(" — ", color = colors.ink3)
                            Text(
                                current.album.orEmpty(),
                                color = colors.ink2,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).clickable(enabled = current.albumId != null) {
                                    onClose()
                                    actions.openAlbum(current.albumId)
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        current.year?.let { Tag("$it") }
                        current.genre?.let { Tag(it) { onClose(); actions.openGenre(it) } }
                        current.suffix?.let { Tag(it.uppercase()) }
                        current.bitRate?.let { Tag("$it kbps") }
                        if ((current.userRating ?: 0) > 0) Tag("${current.userRating} ★") { actions.rate(current) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Scrubber(
                        positionMs = position,
                        durationMs = state.durationMs,
                        onSeek = { graph.player.seekTo(it) },
                        style = settings.playhead,
                        plan = plan,
                        countDown = settings.timeRemaining,
                        onToggleCountDown = { graph.settings.update { it.copy(timeRemaining = !it.timeRemaining) } },
                        reduceMotion = settings.reduceMotion,
                    )
                    Transport(state, current)
                    Spacer(Modifier.height(12.dp))
                    Segmented(
                        options = PlayerTab.entries.filter { it != PlayerTab.LYRICS || settings.showLyrics }.map { it to it.label },
                        selected = tab,
                        onSelect = { tab = it },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            when (tab) {
                PlayerTab.QUEUE -> queueItems(state)
                PlayerTab.LYRICS -> item(key = "lyrics") { LyricsPanel(current, position) }
                PlayerTab.INJEKT -> item(key = "injekt") { InjektPanel(state, transition) }
            }
        }
    }
}

@Composable
private fun PlayerTopBar(song: Song, injektOn: Boolean, downloaded: Boolean, onClose: () -> Unit) {
    val actions = LocalActions.current
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player") }
        Eyebrow(if (song.isRadio) "Internet radio" else song.album ?: "Now playing", Modifier.weight(1f))
        Pill(
            if (injektOn) "InjeKt on" else "InjeKt off",
            onClick = { actions.graph.settings.update { it.copy(injektEnabled = !it.injektEnabled) } },
            icon = Icons.Rounded.AutoAwesome,
            accent = injektOn,
        )
        CastButton(tint = Kultr.colors.ink)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Sleep timer…") },
                    leadingIcon = { Icon(Icons.Rounded.Bedtime, null) },
                    onClick = { menu = false; actions.dialogs.sleepTimer = true },
                )
                if (!song.isRadio) {
                    DropdownMenuItem(
                        text = { Text("Add to playlist…") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                        onClick = { menu = false; actions.addToPlaylist(listOf(song)) },
                    )
                    DropdownMenuItem(
                        text = { Text("Rate…") },
                        leadingIcon = { Icon(Icons.Rounded.Star, null) },
                        onClick = { menu = false; actions.rate(song) },
                    )
                    if (song.albumId != null) {
                        DropdownMenuItem(
                            text = { Text("Go to album") },
                            leadingIcon = { Icon(Icons.Rounded.Album, null) },
                            onClick = { menu = false; onClose(); actions.openAlbum(song.albumId) },
                        )
                    }
                    if (song.artistId != null) {
                        DropdownMenuItem(
                            text = { Text("Go to artist") },
                            leadingIcon = { Icon(Icons.Rounded.Person, null) },
                            onClick = { menu = false; onClose(); actions.openArtist(song.artistId) },
                        )
                    }
                    if (!downloaded) {
                        DropdownMenuItem(
                            text = { Text("Download") },
                            leadingIcon = { Icon(Icons.Rounded.Download, null) },
                            onClick = { menu = false; actions.download(listOf(song), song.title) },
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text("Stop and clear queue") },
                    leadingIcon = { Icon(Icons.Rounded.Close, null) },
                    onClick = { menu = false; actions.graph.player.stop(); onClose() },
                )
            }
        }
    }
}

/** The big artwork; swipe it sideways to skip. */
@Composable
private fun SwipeArtwork(song: Song) {
    val actions = LocalActions.current
    var drag by remember(song.id) { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(drag, label = "swipe")
    val threshold = with(LocalDensity.current) { 90.dp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .aspectRatio(1f)
            .graphicsLayer {
                translationX = offset
                rotationZ = offset / 60f
            }
            .pointerInput(song.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            drag > threshold -> actions.graph.player.previous()
                            drag < -threshold -> actions.graph.player.next()
                        }
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                    onHorizontalDrag = { _, amount -> drag += amount },
                )
            },
    ) {
        ArtworkFill(
            song.artworkId,
            Modifier
                .fillMaxSize()
                .shadow(24.dp, RoundedCornerShape(Kultr.radii.xl)),
            shape = RoundedCornerShape(Kultr.radii.xl),
            label = if (song.isRadio) song.title else null,
            pixels = 800,
        )
    }
}

@Composable
private fun Transport(state: PlayerUiState, song: Song) {
    val actions = LocalActions.current
    val player = actions.graph.player
    val colors = Kultr.colors
    // The library's copy, so the heart follows the track as it is now, not as it was queued.
    val live by remember(song.id) { actions.graph.library.song(song.id) }.collectAsStateWithLifecycle(null)
    val starred = starredShown(live ?: song)
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { player.setShuffle(!state.shuffle) }) {
            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (state.shuffle) colors.accent else colors.ink2)
        }
        IconButton(onClick = { player.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = colors.ink, modifier = Modifier.size(34.dp))
        }
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(colors.ink)
                .clickable { player.toggle() },
            contentAlignment = Alignment.Center,
        ) {
            if (state.buffering && state.playWhenReady) {
                CircularProgressIndicator(color = colors.background, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
            } else {
                Icon(
                    if (state.playWhenReady && !state.ended) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.playWhenReady) "Pause" else "Play",
                    tint = colors.background,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        IconButton(onClick = { player.next() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = colors.ink, modifier = Modifier.size(34.dp))
        }
        IconButton(onClick = { player.cycleRepeat() }) {
            Icon(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Repeat",
                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) colors.accent else colors.ink2,
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        if (!song.isRadio) {
            IconButton(onClick = { actions.setFavourite(live ?: song, !starred) }) {
                Icon(
                    if (starred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (starred) "Remove from favourites" else "Add to favourites",
                    tint = if (starred) colors.accent else colors.ink2,
                )
            }
        }
        val timer by actions.graph.hub.sleepTimer.collectAsStateWithLifecycle()
        IconButton(onClick = { actions.dialogs.sleepTimer = true }) {
            Icon(Icons.Rounded.Bedtime, contentDescription = "Sleep timer", tint = if (timer != null) colors.accent else colors.ink2)
        }
    }
}

// --------------------------------------------------------------- queue --

private fun androidx.compose.foundation.lazy.LazyListScope.queueItems(state: PlayerUiState) {
    val upNext = state.upNext
    item(key = "queue-head") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (upNext.isEmpty()) "Nothing queued after this track." else "${upNext.size} up next",
                color = Kultr.colors.ink3,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (upNext.isNotEmpty()) {
                val actions = LocalActions.current
                IconButton(onClick = { actions.graph.player.clearUpcoming() }) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear up next", tint = Kultr.colors.ink2)
                }
            }
        }
    }
    itemsIndexed(upNext, key = { _, entry -> entry.key }) { _, entry ->
        QueueRow(entry.index, entry.song, state.queue.size)
    }
}

private val QueueRowHeight = 64.dp

@Composable
private fun QueueRow(index: Int, song: Song, size: Int) {
    val actions = LocalActions.current
    val colors = Kultr.colors
    var drag by remember(index, song.id) { mutableFloatStateOf(0f) }
    var menu by remember { mutableStateOf(false) }
    val rowPx = with(LocalDensity.current) { QueueRowHeight.toPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(QueueRowHeight)
            .graphicsLayer {
                translationY = drag
                shadowElevation = if (drag != 0f) 12f else 0f
            }
            .background(if (drag != 0f) colors.elevated else Color.Transparent)
            .clickable { actions.graph.player.jumpTo(index) }
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.artworkId, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(song.artist.orEmpty(), color = colors.ink3, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        song.duration?.let { Text(Format.time(it.toDouble()), color = colors.ink3, fontSize = 12.sp) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = colors.ink3) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play now") }, onClick = { menu = false; actions.graph.player.jumpTo(index) })
                DropdownMenuItem(text = { Text("Remove from queue") }, onClick = { menu = false; actions.graph.player.remove(index) })
                DropdownMenuItem(text = { Text("Move to top") }, onClick = {
                    menu = false
                    val target = actions.graph.player.state.value.index + 1
                    if (target in 0 until size) actions.graph.player.move(index, target)
                })
            }
        }
        Icon(
            Icons.Rounded.DragHandle,
            contentDescription = "Drag to reorder",
            tint = colors.ink3,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .pointerInput(index) {
                    detectDragGestures(
                        onDragEnd = {
                            val steps = (drag / rowPx).roundToInt()
                            val current = actions.graph.player.state.value.index
                            val target = (index + steps).coerceIn(current + 1, size - 1)
                            if (target != index) actions.graph.player.move(index, target)
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            drag += amount.y
                        },
                    )
                },
        )
    }
}

// -------------------------------------------------------------- lyrics --

@Composable
private fun LyricsPanel(song: Song, positionMs: Long) {
    val actions = LocalActions.current
    val colors = Kultr.colors
    var doc by remember(song.id) { mutableStateOf<LyricsDoc?>(null) }
    var loading by remember(song.id) { mutableStateOf(true) }
    LaunchedEffect(song.id) {
        loading = true
        doc = try {
            actions.graph.library.lyrics(song)
        } catch (err: CancellationException) {
            throw err
        } catch (_: Exception) {
            null
        }
        loading = false
    }
    val lyrics = doc
    val active = lyrics?.activeIndex(positionMs / 1000.0) ?: -1
    GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        when {
            loading -> Text("Looking for lyrics…", color = colors.ink3)
            lyrics == null || lyrics.lines.isEmpty() -> Text("No lyrics for this track.", color = colors.ink3)
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                lyrics.lines.forEachIndexed { i, line ->
                    val isActive = i == active
                    Text(
                        line.text.ifBlank { "♪" },
                        color = when {
                            !lyrics.synced -> colors.ink
                            isActive -> colors.accent
                            i < active -> colors.ink3
                            else -> colors.ink2
                        },
                        fontSize = if (isActive) 22.sp else 18.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.clickable(enabled = lyrics.synced && line.start != null) {
                            line.start?.let { actions.graph.player.seekTo((it * 1000).toLong()) }
                        },
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------- injekt --

@Composable
private fun InjektPanel(state: PlayerUiState, transition: TransitionInfo) {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val current = state.current ?: return
    val next = state.queue.getOrNull(state.index + 1)?.song
    val currentAnalysis by remember(current.id) { graph.analysis.observe(current.id) }.collectAsStateWithLifecycle(null)
    val nextAnalysis by remember(next?.id) { next?.let { graph.analysis.observe(it.id) } ?: kotlinx.coroutines.flow.flowOf(null) }
        .collectAsStateWithLifecycle(null)
    var analysing by remember(current.id, next?.id) { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow("The next transition")
                val plan = transition.active ?: transition.upcoming?.takeIf { transition.fromSongId == current.id }
                if (!settings.injektEnabled) {
                    Text("InjeKt is off, so tracks are handed over with a plain crossfade.", color = colors.ink2)
                } else if (plan == null) {
                    Text(
                        if (next == null) "Nothing is queued after this track." else "Planned about 35 seconds before the end of the track.",
                        color = colors.ink2,
                    )
                } else {
                    Text(plan.label, style = MaterialTheme.typography.titleMedium, color = colors.accent)
                    Text(plan.reason, color = colors.ink2, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Starts at ${Format.time(plan.startAt)} · lasts ${"%.1f".format(plan.duration)}s" +
                            if (plan.inStartOffset > 1) " · next track enters at ${Format.time(plan.inStartOffset)}" else "",
                        color = colors.ink3,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        AnalysisCard("This track", current, currentAnalysis)
        if (next != null) AnalysisCard("Up next", next, nextAnalysis)
        if (currentAnalysis == null || (next != null && nextAnalysis == null)) {
            Pill(
                if (analysing) "Analysing…" else "Analyse now",
                icon = Icons.Rounded.AutoAwesome,
                enabled = !analysing,
                onClick = {
                    analysing = true
                    actions.launch {
                        graph.analysis.getOrAnalyse(current)
                        next?.let { graph.analysis.getOrAnalyse(it) }
                        analysing = false
                    }
                },
            )
        }
    }
}

@Composable
private fun AnalysisCard(title: String, song: Song, analysis: TrackAnalysis?) {
    val colors = Kultr.colors
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.artworkId, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Eyebrow(title)
                    Text(song.title, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                }
            }
            if (analysis == null) {
                Text("Not analysed yet.", color = colors.ink3, style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Tempo", "%.1f".format(analysis.bpm), if (analysis.bpmSource.name == "TAG") "from tag" else "${(analysis.bpmConfidence * 100).roundToInt()}% sure")
                    Stat("Key", analysis.keyName, analysis.camelot)
                    Stat("Energy", "${(analysis.energy * 100).roundToInt()}", "of 100")
                }
                Text(
                    "Intro ends ${Format.time(analysis.introEnd)} · outro from ${Format.time(analysis.outroStart)}",
                    color = colors.ink3,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(label: String, value: String, note: String) {
    val colors = Kultr.colors
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(Kultr.radii.sm))
            .background(colors.glass)
            .border(BorderStroke(1.dp, colors.line), RoundedCornerShape(Kultr.radii.sm))
            .padding(10.dp),
    ) {
        Text(label.uppercase(), color = colors.ink3, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = colors.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(note, color = colors.ink3, fontSize = 11.sp)
    }
}
