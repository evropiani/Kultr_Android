package app.kultr.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.KultrApp
import app.kultr.android.data.LocalFolder
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.theme.Kultr

/**
 * True while the library in use is the music on the phone rather than a
 * server: Cast, downloads, internet radio and server search have nothing to
 * work with then, so they are not shown.
 */
val LocalMusicMode = staticCompositionLocalOf { false }

/**
 * The system's folder picker. A folder picked there is kept as a music folder
 * (with lasting access to it, and nothing else), and passed to [onAdded].
 */
@Composable
fun rememberFolderPicker(onAdded: (LocalFolder) -> Unit = {}): () -> Unit {
    val graph = KultrApp.graph
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val folder = graph.local.addFolder(uri)
        if (folder == null) graph.messages.error("Kultr was not given access to that folder.") else onAdded(folder)
    }
    return {
        runCatching { launcher.launch(null) }
            .onFailure { graph.messages.error("This phone has no folder picker Kultr can use.") }
    }
}

/** The chosen music folders, each with a button to stop using it, and one to add another. */
@Composable
fun MusicFolders(modifier: Modifier = Modifier, onChanged: () -> Unit = {}) {
    val graph = KultrApp.graph
    val colors = Kultr.colors
    val folders by graph.local.folders.collectAsStateWithLifecycle()
    val pick = rememberFolderPicker { onChanged() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        folders.forEach { folder ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    folder.name,
                    color = colors.ink,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    graph.local.removeFolder(folder)
                    onChanged()
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Stop using ${folder.name}", tint = colors.ink3)
                }
            }
        }
        Pill(
            if (folders.isEmpty()) "Choose a folder" else "Add another folder",
            icon = Icons.Rounded.CreateNewFolder,
            accent = folders.isEmpty(),
            onClick = pick,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
