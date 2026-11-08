package app.kultr.android.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.data.Connection
import app.kultr.android.data.db.Counts
import app.kultr.android.data.db.DownloadUsage
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.LocalMusicMode
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.Eyebrow
import app.kultr.android.ui.components.GlassPanel
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.SettingRow
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.sync.SyncMode
import app.kultr.core.sync.SyncPhase
import app.kultr.core.sync.SyncState
import app.kultr.core.util.Format
import app.kultr.core.util.t

@Composable
fun SyncScreen() {
    val actions = LocalActions.current
    val graph = actions.graph
    val counts by graph.library.counts.collectAsStateWithLifecycle(Counts(0, 0, 0, 0, 0))
    val state by graph.library.syncState.collectAsStateWithLifecycle(SyncState())
    val running by graph.sync.running.collectAsStateWithLifecycle()
    val progress by graph.sync.progress.collectAsStateWithLifecycle()
    val summary by graph.sync.summary.collectAsStateWithLifecycle()
    val error by graph.sync.error.collectAsStateWithLifecycle()
    val connection by graph.auth.connection.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val analysed by graph.analysis.analysedCount.collectAsStateWithLifecycle(0)
    val missing by graph.analysis.missingCount.collectAsStateWithLifecycle(0)
    val bulk by graph.analysis.bulkProgress.collectAsStateWithLifecycle()
    val usage by graph.offline.usage.collectAsStateWithLifecycle(DownloadUsage(0, 0))
    val download by graph.offline.progress.collectAsStateWithLifecycle()
    val colors = Kultr.colors
    val local = LocalMusicMode.current
    val folders by graph.local.folders.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BackBar(t("Sync"))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = chromePadding(16.dp)),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow(t("Your library on this phone"))
                        Text(
                            "${Format.count(counts.artists, "artist")} · ${Format.count(counts.albums, "album")} · ${Format.count(counts.songs, "track")}",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        Text(
                            "${Format.count(counts.playlists, "playlist")} · ${Format.count(counts.genres, "genre")}",
                            color = colors.ink2,
                        )
                        Text(
                            t("Last checked %@ · last full sync %@", Format.relative(state.lastCheck), Format.relative(state.lastFullSync)),
                            color = colors.ink3,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            if (local) {
                                t("Read from %@ on this phone: %@", Format.count(folders.size, "folder"), folders.joinToString { it.name })
                            } else {
                                when (val c = connection) {
                                    is Connection.Online -> t("Connected to %@", c.info.description)
                                    is Connection.Offline -> t("Server unreachable: %@", c.message)
                                    Connection.Connecting -> t("Connecting…")
                                    Connection.Idle -> t("Not connected")
                                }
                            },
                            color = if (connection is Connection.Offline && !local) colors.warning else colors.ink3,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val p = progress
                        if (running && p != null) {
                            LinearProgressIndicator(progress = { p.percent.toFloat() }, modifier = Modifier.fillMaxWidth())
                            Text(p.message, color = colors.ink2, style = MaterialTheme.typography.bodySmall)
                        } else if (!running && p?.phase == SyncPhase.DONE) {
                            summary?.let { s ->
                                Text(
                                    if (s.upToDate) t("Everything was already up to date.") else
                                        t("%@ albums added, %@ changed, %@ removed.", s.albumsAdded, s.albumsUpdated, s.albumsRemoved),
                                    color = colors.success,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (s.errors.isNotEmpty()) {
                                    Text(
                                        if (local) s.errors.take(3).joinToString(" ") else t("%@ albums could not be read: %@", s.errors.size, s.errors.take(3).joinToString("; ")),
                                        color = colors.warning,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        error?.let { Text(it, color = colors.danger, style = MaterialTheme.typography.bodySmall) }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (running) {
                                Pill(t("Stop"), icon = Icons.Rounded.Stop, onClick = { graph.sync.cancel() })
                            } else {
                                Pill(
                                    when {
                                        local && counts.albums == 0 -> t("Scan my folders")
                                        local -> t("Scan for changes")
                                        counts.albums == 0 -> t("Sync my library")
                                        else -> t("Check for updates")
                                    },
                                    icon = Icons.Rounded.Sync,
                                    accent = true,
                                    onClick = { graph.sync.start(if (counts.albums == 0) SyncMode.FULL else SyncMode.CHECK) },
                                )
                                if (counts.albums > 0) {
                                    Pill(
                                        if (local) t("Read all tags again") else t("Full resync"),
                                        icon = Icons.Rounded.CloudSync,
                                        onClick = { graph.sync.start(SyncMode.FULL) },
                                    )
                                }
                            }
                        }
                    }
                }

                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow(t("Keeping it fresh"))
                        SettingRow(t("Check when Kultr starts"), hint = t("Pulls in only what changed."), modifier = Modifier.padding(0.dp)) {
                            Switch(checked = settings.autoSyncOnStart, onCheckedChange = { v -> graph.settings.update { it.copy(autoSyncOnStart = v) } })
                        }
                        SettingRow(t("Check in the background"), hint = if (settings.autoSyncMinutes > 0) t("Every %@ minutes.", settings.autoSyncMinutes) else t("Off.")) {
                            Switch(checked = settings.autoSyncMinutes > 0, onCheckedChange = { v ->
                                graph.settings.update { it.copy(autoSyncMinutes = if (v) 60 else 0) }
                                graph.sync.schedulePeriodic(graph.app)
                            })
                        }
                        SettingRow(t("Sync playlist contents"), hint = t("Reads every playlist's tracks, so they open instantly and offline.")) {
                            Switch(checked = settings.syncPlaylistContents, onCheckedChange = { v -> graph.settings.update { it.copy(syncPlaylistContents = v) } })
                        }
                    }
                }

                GlassPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow(t("InjeKt analysis"))
                        Text(t("%@ tracks analysed · %@ to go", analysed, missing), style = MaterialTheme.typography.titleMedium, color = colors.ink)
                        Text(
                            t("InjeKt measures tempo, key, energy and structure to plan beat-matched transitions. It analyses tracks just before they are needed anyway; doing it up front means every transition is planned from the first play. Each track is streamed once at a low bitrate."),
                            color = colors.ink2,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val b = bulk
                        if (b != null) {
                            LinearProgressIndicator(progress = { if (b.total > 0) b.done.toFloat() / b.total else 0f }, modifier = Modifier.fillMaxWidth())
                            Text(t("Analysing “%@”", b.current), color = colors.ink3, style = MaterialTheme.typography.bodySmall)
                            Pill(t("Stop"), icon = Icons.Rounded.Stop, onClick = { graph.analysis.cancelBulk() })
                        } else {
                            Pill(
                                t("Analyse missing"),
                                icon = Icons.Rounded.AutoAwesome,
                                accent = missing > 0,
                                enabled = missing > 0,
                                onClick = { graph.analysis.startBulk() },
                            )
                        }
                    }
                }

                // Music on the phone is there already: nothing to download.
                if (!local) GlassPanel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow(t("Offline"))
                        Text(
                            t("%@ downloaded · %@", Format.count(usage.count, "track"), Format.bytes(usage.bytes)),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        val d = download
                        if (d != null) {
                            LinearProgressIndicator(progress = { if (d.total > 0) d.done.toFloat() / d.total else 0f }, modifier = Modifier.fillMaxWidth())
                            Text("${d.done} of ${d.total}", color = colors.ink3, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(t("Download favourites"), icon = Icons.Rounded.Download, onClick = {
                                actions.launch { graph.offline.download(graph.library.starredSongsNow(), t("Your favourites")) }
                            })
                            Pill(t("Download everything"), icon = Icons.Rounded.Download, onClick = {
                                actions.launch { graph.offline.download(graph.library.allSongs(), t("Your library")) }
                            })
                            Pill(t("Open downloads"), onClick = { actions.openDownloads() })
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
