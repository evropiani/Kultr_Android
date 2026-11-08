package app.kultr.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.kultr.android.data.VersionNotes
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.WideButton
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.util.t

/** Settings → About → Update log: what changed in every version, the newest open. */
@Composable
fun UpdateLogScreen() {
    val log = LocalActions.current.graph.updateLog
    var open by rememberSaveable { mutableStateOf(setOfNotNull(log.all.firstOrNull()?.version)) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromePadding(24.dp))) {
        item {
            BackBar()
            Text(t("Update log"), style = MaterialTheme.typography.headlineMedium, color = Kultr.colors.ink, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        }
        items(log.all, key = { it.version }) { notes ->
            val expanded = notes.version in open
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { open = if (expanded) open - notes.version else open + notes.version }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("Kultr %@", notes.version), style = MaterialTheme.typography.titleMedium, color = Kultr.colors.ink, modifier = Modifier.weight(1f))
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = Kultr.colors.ink3)
                }
                AnimatedVisibility(expanded) { Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { Notes(notes.markdown) } }
                HorizontalDivider(color = Kultr.colors.line, modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

/** The first start after an update: what changed, in every version since the one last opened. */
@Composable
fun WhatsNewDialog(versions: List<VersionNotes>, onDone: () -> Unit) {
    val colors = Kultr.colors
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (versions.size == 1) t("What's new in Kultr %@", versions[0].version) else t("What's new since you last opened Kultr"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.ink,
                    )
                }
                items(versions, key = { it.version }) { notes ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (versions.size > 1) Text(t("Kultr %@", notes.version), style = MaterialTheme.typography.titleLarge, color = colors.accent)
                        Notes(notes.markdown)
                    }
                }
            }
            WideButton(t("Continue"), Icons.Rounded.AutoAwesome, onClick = onDone, modifier = Modifier.fillMaxWidth().padding(16.dp), accent = true)
        }
    }
}
