package app.kultr.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kultr.android.AppGraph
import app.kultr.android.playback.KarouselMusic
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Song
import app.kultr.core.util.Format
import app.kultr.core.util.t
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Suggested songs at the bottom of a playlist, as on Spotify. Each has a +
 * to add it; once one is added, the suggestions turn to songs like it.
 * Refresh offers different ones. The rules are PlaylistSuggestions in core,
 * the same as Kultr for iOS and the web.
 */
@Stable
class PlaylistSuggestionState(private val scope: CoroutineScope, private val karousel: KarouselMusic) {
    /** The suggestions showing; null until the first ones arrive. */
    var songs by mutableStateOf<List<Song>?>(null)
        private set

    /** The song the suggestions are like, once one has been added. */
    var basis by mutableStateOf<Song?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    /** The suggestion being added right now. */
    var adding by mutableStateOf<String?>(null)

    private var job: Job? = null

    fun load(playlist: List<Song>, added: Song?, leaveOut: List<Song> = emptyList()) {
        job?.cancel()
        busy = true
        job = scope.launch {
            val picks = try {
                karousel.suggest(playlist, added, leaveOut)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                songs ?: emptyList()
            }
            songs = picks
            basis = added
            busy = false
        }
    }

    /** Takes [song] off the list once it is in the playlist. */
    fun added(song: Song) {
        songs = songs?.filterNot { it.id == song.id }
    }
}

/** Suggestions for the playlist [id], loaded once its songs are known. */
@Composable
fun rememberPlaylistSuggestions(graph: AppGraph, id: String, playlist: List<Song>?): PlaylistSuggestionState {
    val scope = rememberCoroutineScope()
    val state = remember(id) { PlaylistSuggestionState(scope, KarouselMusic(graph)) }
    val known = playlist != null
    LaunchedEffect(id, known) {
        if (playlist != null && state.songs == null && !state.busy) state.load(playlist, null)
    }
    return state
}

/** The suggestions section, for the end of a playlist's list. */
fun LazyListScope.playlistSuggestions(
    state: PlaylistSuggestionState,
    playlist: List<Song>,
    onAdd: (Song) -> Unit,
    onPlay: (Song) -> Unit,
) {
    item(key = "suggestions-head") {
        Column(Modifier.padding(top = 20.dp)) {
            SectionHeader(t("Recommended songs"), icon = Icons.Rounded.AutoAwesome) {
                if (state.busy && state.songs != null) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Kultr.colors.accent, strokeWidth = 2.dp)
                }
            }
            val basis = state.basis
            Text(
                when {
                    basis != null -> t("More like “%@”", basis.title)
                    playlist.isNotEmpty() -> t("Based on the songs in this playlist")
                    else -> t("Based on what you play most")
                },
                color = Kultr.colors.ink3,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
        }
    }
    val songs = state.songs
    when {
        songs == null -> item(key = "suggestions-loading") { SkeletonRows(count = 4) }
        songs.isEmpty() -> item(key = "suggestions-empty") {
            Text(
                t("Nothing to suggest yet. Add a few songs, or play some music, and suggestions follow."),
                color = Kultr.colors.ink2,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        else -> items(songs, key = { "suggestion-${it.id}" }) { song ->
            SuggestionRow(
                song,
                adding = state.adding == song.id,
                enabled = state.adding == null,
                onAdd = { onAdd(song) },
                onPlay = { onPlay(song) },
                modifier = Modifier.animateItem(),
            )
        }
    }
    if (songs != null) {
        item(key = "suggestions-refresh") {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Pill(
                    t("Refresh"),
                    icon = Icons.Rounded.Refresh,
                    enabled = !state.busy,
                    onClick = { state.load(playlist, state.basis, state.songs.orEmpty()) },
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    song: Song,
    adding: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Kultr.colors
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.artworkId, size = 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = colors.ink,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(song.artist, song.album).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, color = colors.ink3, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        song.duration?.let { Text(Format.time(it.toDouble()), color = colors.ink3, fontSize = 12.sp) }
        IconButton(onClick = onAdd, enabled = enabled) {
            if (adding) {
                CircularProgressIndicator(Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Rounded.AddCircleOutline,
                    contentDescription = t("Add “%@” to this playlist", song.title),
                    tint = colors.ink2,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
