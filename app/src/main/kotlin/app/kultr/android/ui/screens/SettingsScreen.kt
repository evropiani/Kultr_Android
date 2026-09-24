package app.kultr.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.BuildConfig
import app.kultr.android.data.ServerProfile
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.BrandIcons
import app.kultr.android.ui.components.ConfirmDialog
import app.kultr.android.ui.components.GlassPanel
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.SettingRow
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.settings.AccentMode
import app.kultr.core.settings.CornerStyle
import app.kultr.core.settings.CrossfadeCurve
import app.kultr.core.settings.EQ_BANDS
import app.kultr.core.settings.EQ_PRESETS
import app.kultr.core.settings.GridSize
import app.kultr.core.settings.PlayheadStyle
import app.kultr.core.settings.ReplayGainMode
import app.kultr.core.settings.Settings
import app.kultr.core.settings.SettingsFile
import app.kultr.core.settings.SurfaceBorder
import app.kultr.core.settings.ThemeMode
import app.kultr.core.settings.availableHomeTiles
import app.kultr.core.settings.resolveHomeTiles
import app.kultr.core.util.ArtworkColor
import java.time.Instant
import kotlin.math.roundToInt

private val ACCENTS = listOf("#7c8cff", "#ff6b9a", "#ff9f43", "#ffd166", "#45d67a", "#2ec4b6", "#4cc9f0", "#b388ff", "#f5f5f7")

private val BITRATES = listOf(0 to "Original", 320 to "320 kbps", 256 to "256 kbps", 192 to "192 kbps", 128 to "128 kbps", 96 to "96 kbps")

@Composable
fun SettingsScreen(onAddServer: () -> Unit, onSignIn: (ServerProfile) -> Unit) {
    val actions = LocalActions.current
    val graph = actions.graph
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(setOf<String>()) }
    fun update(transform: (Settings) -> Settings) = graph.settings.update(transform)

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = chromePadding(32.dp))) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = Kultr.colors.ink,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
            )
        }
        fun section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
            item(key = title) {
                Section(title, icon, expanded = title in open, onToggle = { open = if (title in open) open - title else open + title }, content = content)
            }
        }
        section("Appearance", Icons.Rounded.Palette) { AppearanceSettings(settings, ::update) }
        section("Home page", Icons.Rounded.Home) { HomeSettings(settings, ::update) }
        section("Playback", Icons.Rounded.PlayCircle) { PlaybackSettings(settings, ::update) }
        section("InjeKt", Icons.Rounded.AutoAwesome) { InjektSettings(settings, ::update) }
        section("Audio", Icons.Rounded.GraphicEq) { AudioSettings(settings, ::update) }
        section("Equaliser", Icons.Rounded.Equalizer) { EqualiserSettings(settings, ::update) }
        section("Offline and cache", Icons.Rounded.DownloadForOffline) { OfflineSettings(settings, ::update) }
        section("Servers", Icons.Rounded.Dns) { ServerSettings(onAddServer, onSignIn) }
        section("Backup and reset", Icons.Rounded.Backup) { BackupSettings(settings) }
        section("About", Icons.Rounded.Info) { AboutSection() }
    }
}

@Composable
private fun Section(title: String, icon: ImageVector, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), padding = PaddingValues(0.dp)) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = Kultr.colors.accent)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = Kultr.colors.ink, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = Kultr.colors.ink3)
            }
            AnimatedVisibility(expanded) { Column(Modifier.padding(bottom = 8.dp)) { content() } }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, hint: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    SettingRow(label, hint = hint, onClick = if (enabled) ({ onChange(!checked) }) else null) {
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun <T> Choice(label: String, options: List<Pair<T, String>>, selected: T, hint: String? = null, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Kultr.colors.ink)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Kultr.colors.ink3)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, text) -> Pill(text, onClick = { onSelect(value) }, accent = value == selected) }
        }
    }
}

/** A slider that keeps its own value while dragging and commits when released. */
@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    hint: String? = null,
    steps: Int = 0,
    enabled: Boolean = true,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Kultr.colors.ink else Kultr.colors.ink3, modifier = Modifier.weight(1f))
            Text(format(local), style = MaterialTheme.typography.bodyMedium, color = Kultr.colors.accent, fontWeight = FontWeight.SemiBold)
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Kultr.colors.ink3)
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
            steps = steps,
            enabled = enabled,
        )
    }
}

// ----------------------------------------------------------- appearance --

@Composable
private fun AppearanceSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    Choice("Theme", listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "System"), s.theme) { v -> update { it.copy(theme = v) } }
    Toggle("Colour from artwork", s.accentMode == AccentMode.ARTWORK, hint = "The interface takes its colour from whatever is playing.") { v ->
        update { it.copy(accentMode = if (v) AccentMode.ARTWORK else AccentMode.FIXED) }
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(if (s.accentMode == AccentMode.ARTWORK) "Your colour" else "Accent colour", color = Kultr.colors.ink)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ACCENTS.forEach { hex ->
                val color = Color(ArtworkColor.parseHex(hex) ?: 0)
                val selected = s.accent.equals(hex, ignoreCase = true)
                Box(
                    Modifier
                        .size(36.dp)
                        .background(color, CircleShape)
                        .border(if (selected) 3.dp else 1.dp, if (selected) Kultr.colors.ink else Kultr.colors.line, CircleShape)
                        .clickable { update { it.copy(accent = hex) } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.Black.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    if (s.accentMode == AccentMode.ARTWORK) {
        SettingSlider(
            "How much of your colour",
            s.accentBlend.toFloat(),
            0f..100f,
            { "${it.roundToInt()}%" },
            hint = "0% is pure artwork colour; 100% ignores the artwork.",
        ) { v -> update { it.copy(accentBlend = v.roundToInt()) } }
    }
    Toggle("Blurred artwork background", s.backdropArtwork) { v -> update { it.copy(backdropArtwork = v) } }
    Choice("Corners", listOf(CornerStyle.SHARP to "Sharp", CornerStyle.SOFT to "Soft", CornerStyle.ROUND to "Round"), s.corners) { v -> update { it.copy(corners = v) } }
    Choice("Panel borders", listOf(SurfaceBorder.NEUTRAL to "Neutral", SurfaceBorder.ACCENT to "Accent"), s.surfaceBorder) { v -> update { it.copy(surfaceBorder = v) } }
    SettingSlider("Border opacity", s.borderOpacity.toFloat(), 0f..100f, { "${it.roundToInt()}%" }) { v -> update { it.copy(borderOpacity = v.roundToInt()) } }
    SettingSlider("Panel opacity", s.surfaceOpacity.toFloat(), 0f..200f, { "${it.roundToInt()}%" }) { v -> update { it.copy(surfaceOpacity = v.roundToInt()) } }
    Choice("Grid size", listOf(GridSize.SMALL to "Small", GridSize.MEDIUM to "Medium", GridSize.LARGE to "Large"), s.gridSize) { v -> update { it.copy(gridSize = v) } }
    Toggle("Compact track rows", s.compactRows) { v -> update { it.copy(compactRows = v) } }
    Choice("Playhead", PlayheadStyle.entries.map { it to it.label }, s.playhead, hint = s.playhead.note) { v -> update { it.copy(playhead = v) } }
    Toggle("Count time down", s.timeRemaining, hint = "Show how much of the track is left. Tapping the time in the player switches it too.") { v ->
        update { it.copy(timeRemaining = v) }
    }
    Toggle("Show the lyrics tab", s.showLyrics) { v -> update { it.copy(showLyrics = v) } }
    Toggle("Reduce motion", s.reduceMotion, hint = "Stops animated colour changes and playhead effects.") { v -> update { it.copy(reduceMotion = v) } }
}

// ------------------------------------------------------------ home page --

@Composable
private fun HomeSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val on = resolveHomeTiles(s.homeTiles)
    val off = availableHomeTiles(s.homeTiles)
    var adding by remember { mutableStateOf(false) }
    Text(
        "Shelves are shown in this order. One with nothing in it is skipped.",
        color = Kultr.colors.ink3,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    on.forEachIndexed { index, tile ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(tile.title, color = Kultr.colors.ink)
                Text(tile.note, color = Kultr.colors.ink3, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(enabled = index > 0, onClick = {
                update { st -> st.copy(homeTiles = on.map { it.id }.toMutableList().apply { add(index - 1, removeAt(index)) }) }
            }) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up") }
            IconButton(enabled = index < on.size - 1, onClick = {
                update { st -> st.copy(homeTiles = on.map { it.id }.toMutableList().apply { add(index + 1, removeAt(index)) }) }
            }) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down") }
            IconButton(onClick = { update { st -> st.copy(homeTiles = on.map { it.id } - tile.id) } }) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove from the home page")
            }
        }
    }
    if (off.isNotEmpty()) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Pill("Add a shelf", icon = Icons.Rounded.Add, onClick = { adding = true })
            DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                off.forEach { tile ->
                    DropdownMenuItem(
                        text = { Column { Text(tile.title); Text(tile.note, style = MaterialTheme.typography.bodySmall, color = Kultr.colors.ink3) } },
                        onClick = {
                            adding = false
                            update { st -> st.copy(homeTiles = on.map { it.id } + tile.id) }
                        },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------- playback --

@Composable
private fun PlaybackSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    Toggle("Crossfade between tracks", s.crossfadeEnabled, hint = "Two decks play at once for the length of the fade.") { v -> update { it.copy(crossfadeEnabled = v) } }
    SettingSlider("Crossfade length", s.crossfadeSeconds.toFloat(), 1f..20f, { "%.0f s".format(it) }, steps = 18, enabled = s.crossfadeEnabled) { v ->
        update { it.copy(crossfadeSeconds = v.roundToInt().toDouble()) }
    }
    Choice("Fade shape", CrossfadeCurve.entries.map { it to it.label }, s.crossfadeCurve) { v -> update { it.copy(crossfadeCurve = v) } }
    Toggle("Also fade when you skip", s.crossfadeOnSkip, hint = "A short fade instead of a hard cut on next and previous.") { v -> update { it.copy(crossfadeOnSkip = v) } }
    Toggle("Gapless playback", s.gapless, hint = "With crossfade off, the next track starts the instant this one ends.") { v -> update { it.copy(gapless = v) } }
    Toggle("Resume where you left off", s.resumeOnStart, hint = "Restores the queue and position when Kultr opens.") { v -> update { it.copy(resumeOnStart = v) } }
    Toggle("Scrobble plays", s.scrobble, hint = "Tells your server what you listened to. Plays made offline are sent later.") { v -> update { it.copy(scrobble = v) } }
}

// --------------------------------------------------------------- injekt --

@Composable
private fun InjektSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    Toggle("Enable InjeKt", s.injektEnabled, hint = "Beat-matched, key-aware transitions planned from each track's tempo, key and structure.") { v ->
        update { it.copy(injektEnabled = v) }
    }
    val on = s.injektEnabled
    Toggle("Beat-match", s.injektBeatMatch, enabled = on, hint = "Bring both tracks to a shared tempo and line up their bars.") { v -> update { it.copy(injektBeatMatch = v) } }
    Toggle("Meet in the middle", s.injektTempoRamp, enabled = on, hint = "Ease the current track toward the next one's tempo before the blend.") { v -> update { it.copy(injektTempoRamp = v) } }
    SettingSlider("Tempo share", s.injektTempoBlend.toFloat(), 0f..100f, { "${it.roundToInt()}%" }, enabled = on && s.injektTempoRamp, hint = "How much of the tempo change the outgoing track makes.") { v ->
        update { it.copy(injektTempoBlend = v.roundToInt().toDouble()) }
    }
    SettingSlider("Maximum tempo shift", s.injektMaxTempoShift.toFloat(), 2f..16f, { "${it.roundToInt()}%" }, steps = 13, enabled = on, hint = "Per track. Further apart than this, tracks are crossfaded instead.") { v ->
        update { it.copy(injektMaxTempoShift = v.roundToInt().toDouble()) }
    }
    Choice("Transition length", listOf(4 to "4 bars", 8 to "8 bars", 16 to "16 bars", 32 to "32 bars"), s.injektBars, hint = "Shortened automatically when two tracks clash.") { v ->
        update { it.copy(injektBars = v) }
    }
    Toggle("Bass swap", s.injektBassSwap, enabled = on, hint = "Roll the outgoing bass off before the incoming bass comes up.") { v -> update { it.copy(injektBassSwap = v) } }
    Toggle("Harmonic mixing", s.injektHarmonic, enabled = on, hint = "When keys clash, filter out of the old track instead of blending.") { v -> update { it.copy(injektHarmonic = v) } }
    Toggle("Skip long intros", s.injektSkipIntro, enabled = on, hint = "Bring the next track in at its first real downbeat.") { v -> update { it.copy(injektSkipIntro = v) } }
    Toggle("Keep playing similar music", s.injektAutoQueue, hint = "When the queue runs out, continue with tracks chosen by tempo, key and energy.") { v -> update { it.copy(injektAutoQueue = v) } }
    Toggle("Analyse ahead", s.injektAnalyseAhead, enabled = on, hint = "Measure the next track while this one plays.") { v -> update { it.copy(injektAnalyseAhead = v) } }
    Toggle("Analyse on Wi-Fi only", s.injektAnalyseOnWifiOnly, enabled = on, hint = "Never spend mobile data on analysis.") { v -> update { it.copy(injektAnalyseOnWifiOnly = v) } }
}

// ---------------------------------------------------------------- audio --

@Composable
private fun AudioSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    Choice("Volume levelling (ReplayGain)", ReplayGainMode.entries.map { it to it.label }, s.replayGainMode) { v -> update { it.copy(replayGainMode = v) } }
    SettingSlider("ReplayGain pre-amp", s.replayGainPreamp.toFloat(), -12f..12f, { "%+.0f dB".format(it) }, steps = 23, enabled = s.replayGainMode != ReplayGainMode.OFF) { v ->
        update { it.copy(replayGainPreamp = v.roundToInt().toDouble()) }
    }
    Choice("Streaming quality on Wi-Fi", BITRATES, s.preferredBitrate, hint = "Lower bitrates are transcoded by your server.") { v -> update { it.copy(preferredBitrate = v) } }
    Choice("Streaming quality on mobile data", listOf(-1 to "Same as Wi-Fi") + BITRATES, s.preferredBitrateMobile) { v -> update { it.copy(preferredBitrateMobile = v) } }
    Choice(
        "Transcode format",
        listOf("" to "Server default", "mp3" to "MP3", "opus" to "Opus", "aac" to "AAC", "ogg" to "Vorbis"),
        s.preferredFormat,
        hint = "Used when a lower bitrate is chosen.",
    ) { v -> update { it.copy(preferredFormat = v) } }
}

// ------------------------------------------------------------ equaliser --

@Composable
private fun EqualiserSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    Toggle("Enable the equaliser", s.eqEnabled) { v -> update { it.copy(eqEnabled = v) } }
    Choice("Preset", EQ_PRESETS.keys.map { it to it }, s.eqPreset) { name -> update { it.withEqPreset(name) } }
    val gains = s.eqBandGains
    EQ_BANDS.forEachIndexed { index, hz ->
        val label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
        SettingSlider(label, gains[index].toFloat(), -12f..12f, { "%+.0f dB".format(it) }, steps = 23, enabled = s.eqEnabled) { v ->
            update { st -> st.copy(eqPreset = "Custom", eqGains = st.eqBandGains.toMutableList().also { it[index] = v.roundToInt().toDouble() }) }
        }
    }
    SettingSlider("Pre-amp", s.eqPreamp.toFloat(), -12f..6f, { "%+.0f dB".format(it) }, steps = 17, enabled = s.eqEnabled, hint = "Pull this down if the equaliser makes things clip.") { v ->
        update { it.copy(eqPreamp = v.roundToInt().toDouble()) }
    }
}

// -------------------------------------------------------------- offline --

@Composable
private fun OfflineSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val graph = LocalActions.current.graph
    SettingSlider("Parallel downloads", s.offlineConcurrency.toFloat(), 1f..8f, { "${it.roundToInt()}" }, steps = 6) { v ->
        update { it.copy(offlineConcurrency = v.roundToInt()) }
    }
    Choice("Download quality", BITRATES, s.offlineBitrate, hint = "Original keeps the file exactly as it is on the server.") { v -> update { it.copy(offlineBitrate = v) } }
    Toggle("Download on Wi-Fi only", s.offlineWifiOnly) { v -> update { it.copy(offlineWifiOnly = v) } }
    Toggle("Prefer offline copies", s.offlineFirst, hint = "Play a downloaded file instead of streaming when one exists.") { v -> update { it.copy(offlineFirst = v) } }
    Choice(
        "Stream cache",
        listOf(256 to "256 MB", 1024 to "1 GB", 2048 to "2 GB", 4096 to "4 GB", 8192 to "8 GB"),
        s.streamCacheMb,
        hint = "Recently streamed audio is kept so replays do not refetch. Takes effect after a restart.",
    ) { v -> update { it.copy(streamCacheMb = v) } }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Clear stream cache", onClick = {
            graph.clearMediaCache()
            graph.messages.show("Stream cache cleared.")
        })
    }
}

// -------------------------------------------------------------- servers --

@Composable
private fun ServerSettings(onAddServer: () -> Unit, onSignIn: (ServerProfile) -> Unit) {
    val actions = LocalActions.current
    val graph = actions.graph
    val profiles by graph.auth.profiles.collectAsStateWithLifecycle()
    val active by graph.auth.active.collectAsStateWithLifecycle()
    var forget by remember { mutableStateOf<ServerProfile?>(null) }
    profiles.forEach { profile ->
        val isActive = profile.id == active?.id
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.label + if (isActive) " · in use" else "", color = if (isActive) Kultr.colors.accent else Kultr.colors.ink, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(profile.username.ifBlank { "not signed in" }, profile.serverUrl).joinToString(" · "),
                    color = Kultr.colors.ink3,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when {
                !profile.hasCredentials -> TextButton(onClick = { onSignIn(profile) }) { Text("Sign in") }
                !isActive && profile.enabled -> TextButton(onClick = { graph.auth.switchTo(profile.id) }) { Text("Use") }
            }
            Switch(checked = profile.enabled, onCheckedChange = { graph.auth.setEnabled(profile.id, it) })
            IconButton(onClick = { forget = profile }) { Icon(Icons.Rounded.Close, contentDescription = "Forget ${profile.label}") }
        }
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Add a server", icon = Icons.Rounded.Add, onClick = onAddServer)
        if (active != null) Pill("Sign out", icon = Icons.AutoMirrored.Rounded.Logout, onClick = { graph.player.stop(); graph.auth.signOut() })
    }
    forget?.let { profile ->
        ConfirmDialog(
            title = "Forget ${profile.label}?",
            body = "Its saved password, synced library, downloads and listening history on this phone are deleted. Nothing changes on the server.",
            confirm = "Forget",
            onConfirm = {
                if (profile.id == active?.id) graph.player.stop()
                graph.auth.remove(profile.id)
                graph.deleteDataFor(profile.id)
            },
            onDismiss = { forget = null },
        )
    }
}

// --------------------------------------------------------------- backup --

@Composable
private fun BackupSettings(s: Settings) {
    val actions = LocalActions.current
    val graph = actions.graph
    val context = LocalContext.current
    var includeServers by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val servers = if (includeServers) {
            graph.auth.profiles.value.map { SettingsFile.ExportedServer(it.label, it.serverUrl, it.authMode) }
        } else {
            null
        }
        val text = SettingsFile.export(graph.settings.current, BuildConfig.VERSION_NAME, Instant.now().toString(), servers)
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
            .onSuccess { graph.messages.success("Settings exported.") }
            .onFailure { graph.messages.error("Could not write the file: ${it.message}") }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@rememberLauncherForActivityResult
            val result = SettingsFile.import(text, graph.settings.current)
            graph.settings.replace(result.settings)
            val added = graph.auth.importServers(result.servers.map { Triple(it.label, it.serverUrl, it.authMode) })
            report = buildString {
                append("Applied ${result.applied.size} settings")
                if (added > 0) append(" and added $added server${if (added == 1) "" else "s"} (sign in to them in Servers)")
                append(".")
                if (result.skipped.isNotEmpty()) {
                    append("\n\nSkipped:\n")
                    append(result.skipped.joinToString("\n") { (key, why) -> "• $key — $why" })
                }
            }
        } catch (err: Exception) {
            graph.messages.error(err.message ?: "That file could not be read.")
        }
    }

    Text(
        "Export every preference to a small file and import it on another phone, or in Kultr on the web. Passwords and usernames are never in it.",
        color = Kultr.colors.ink3,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Toggle("Include the list of servers", includeServers, hint = "Addresses only — never usernames or passwords.") { includeServers = it }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Export settings", onClick = { exporter.launch("kultr-settings-${Instant.now().toString().take(10)}.json") })
        Pill("Import settings", onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) })
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Reset settings", onClick = { confirmReset = true })
        Pill("Clear library data", onClick = { confirmWipe = true })
    }
    report?.let { text ->
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text("Settings imported") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { report = null }) { Text("OK") } },
        )
    }
    if (confirmReset) {
        ConfirmDialog("Reset every setting?", "Appearance, playback, InjeKt and everything else go back to their defaults. Servers are kept.", "Reset",
            onConfirm = { graph.settings.reset() }, onDismiss = { confirmReset = false })
    }
    if (confirmWipe) {
        ConfirmDialog(
            "Clear library data?",
            "The synced library, InjeKt analysis and listening history for this server are deleted from this phone. Downloads are kept. Sync again to rebuild the library.",
            "Clear",
            onConfirm = {
                actions.launch {
                    val db = graph.database.value ?: return@launch
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        db.library().clearLibrary()
                        db.analysis().clear()
                        db.history().clear()
                        db.meta().put(app.kultr.android.data.db.MetaEntity("syncState", "{}"))
                    }
                    graph.messages.show("Library data cleared.")
                }
            },
            onDismiss = { confirmWipe = false },
        )
    }
}

// ---------------------------------------------------------------- about --

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val open = { url: String -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    SettingRow("Version", hint = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    SettingRow("Source", hint = "github.com/evropiani/Kultr_Android — issues and pull requests welcome.", onClick = {
        open("https://github.com/evropiani/Kultr_Android")
    })
    SettingRow("Website", hint = "kultr.cc", onClick = { open(WEBSITE) })
    SettingRow("Get in touch", hint = "Questions, ideas, or something broken.", onClick = { open(DISCORD) }) {
        Pill("@evropiani", icon = BrandIcons.Discord, onClick = { open(DISCORD) })
    }
}

private const val WEBSITE = "https://kultr.cc/"
private const val DISCORD = "https://discord.com/users/319246364246540288"
