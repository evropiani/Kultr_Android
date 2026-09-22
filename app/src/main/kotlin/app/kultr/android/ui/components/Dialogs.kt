package app.kultr.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.api.Song
import app.kultr.core.util.Format
import kotlinx.coroutines.launch

@Composable
fun AddToPlaylistDialog(songs: List<Song>, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val playlists by remember { actions.graph.library.playlists() }.collectAsStateWithLifecycle(emptyList())
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val label = if (songs.size == 1) "“${songs[0].title}”" else "${songs.size} tracks"

    fun finish(error: String?, success: String) {
        busy = false
        if (error != null) actions.graph.messages.error(error) else actions.graph.messages.success(success)
        if (error == null) onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $label to…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("New playlist") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = name.isNotBlank() && !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                finish(actions.graph.library.createPlaylist(name, songs), "Created “${name.trim()}”")
                            }
                        },
                    ) { Icon(Icons.Rounded.Add, contentDescription = "Create playlist") }
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    busy = true
                                    scope.launch {
                                        finish(actions.graph.library.addToPlaylist(playlist.id, songs), "Added $label to “${playlist.name}”")
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(playlist.coverArt, size = 40.dp, label = playlist.name)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    Format.count(playlist.songCount, "track"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Kultr.colors.ink3,
                                )
                            }
                        }
                    }
                }
                if (playlists.isEmpty()) {
                    Text("No playlists yet — name one above to create it.", color = Kultr.colors.ink3)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun RatingDialog(song: Song, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    var rating by remember { mutableIntStateOf(song.userRating ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate “${song.title}”") },
        text = {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                for (star in 1..5) {
                    IconButton(onClick = { rating = if (rating == star) 0 else star }) {
                        Icon(
                            if (star <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "$star stars",
                            tint = if (star <= rating) Kultr.colors.accent else Kultr.colors.ink3,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                actions.setRating(song, rating)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    val actions = LocalActions.current
    val hub = actions.graph.hub
    val timer by hub.sleepTimer.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                val current = timer
                if (current != null) {
                    Text(
                        if (current.endOfTrack) {
                            "Stopping at the end of this track."
                        } else {
                            "Stopping in ${(((current.endsAtMillis ?: 0) - System.currentTimeMillis()) / 60_000).coerceAtLeast(0) + 1} min."
                        },
                        color = Kultr.colors.accent,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                listOf(5, 15, 30, 45, 60, 90).forEach { minutes ->
                    Text(
                        "$minutes minutes",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                hub.setSleepTimer(minutes)
                                actions.graph.messages.show("Sleep timer: $minutes minutes.")
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                    )
                }
                Text(
                    "At the end of this track",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            hub.sleepAtEndOfTrack()
                            actions.graph.messages.show("Stopping after this track.")
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                )
            }
        },
        confirmButton = {
            if (timer != null) {
                TextButton(onClick = {
                    hub.clearSleepTimer()
                    onDismiss()
                }) { Text("Turn off") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Simple confirm dialog. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirm, color = Kultr.colors.danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Single text field dialog, for renaming. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = {
                onConfirm(value)
                onDismiss()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
