package app.kultr.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.BuildConfig
import app.kultr.android.data.MessageKind
import app.kultr.android.data.ServerProfile
import app.kultr.android.data.Translations
import app.kultr.android.data.UpdateCheck
import app.kultr.android.ui.AppIcon
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.MusicFolders
import app.kultr.android.ui.Routes
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.BrandIcons
import app.kultr.android.ui.components.ConfirmDialog
import app.kultr.android.ui.components.GlassPanel
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.Segmented
import app.kultr.android.ui.components.SettingRow
import app.kultr.android.ui.components.TextInputDialog
import app.kultr.android.ui.components.animatePlacement
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
import app.kultr.core.sync.SyncMode
import app.kultr.core.util.ArtworkColor
import app.kultr.core.util.t
import java.time.Instant
import kotlin.math.roundToInt

private val ACCENTS = listOf("#ff7a3d", "#7c8cff", "#ff6b9a", "#ffd166", "#45d67a", "#2ec4b6", "#4cc9f0", "#b388ff", "#8a7e70")

private val BITRATES: List<Pair<Int, String>> get() = listOf(0 to t("Original"), 320 to t("320 kbps"), 256 to t("256 kbps"), 192 to t("192 kbps"), 128 to t("128 kbps"), 96 to t("96 kbps"))

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
                t("Settings"),
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
        section(t("Appearance"), Icons.Rounded.Palette) { AppearanceSettings(settings, ::update) }
        section(t("Home page"), Icons.Rounded.Home) { HomeSettings(settings, ::update) }
        section(t("Playback"), Icons.Rounded.PlayCircle) { PlaybackSettings(settings, ::update) }
        section("InjeKt", Icons.Rounded.AutoAwesome) { InjektSettings(settings, ::update) }
        section(t("Audio"), Icons.Rounded.GraphicEq) { AudioSettings(settings, ::update) }
        section(t("Equaliser"), Icons.Rounded.Equalizer) { EqualiserSettings(settings, ::update) }
        section(t("Offline and cache"), Icons.Rounded.DownloadForOffline) { OfflineSettings(settings, ::update) }
        section(t("Music on this phone"), Icons.Rounded.PhoneAndroid) { LocalMusicSettings() }
        section(t("Servers"), Icons.Rounded.Dns) { ServerSettings(onAddServer, onSignIn) }
        section(t("Backup and reset"), Icons.Rounded.Backup) { BackupSettings(settings) }
        section(t("About"), Icons.Rounded.Info) { AboutSection() }
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
        Segmented(options, selected, onSelect, Modifier.horizontalScroll(rememberScrollState()))
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
    val actions = LocalActions.current
    SettingRow(
        t("Kultr Studio"),
        hint = t("Ready-made styles, the player's layout and its info line; share your look as a link."),
        onClick = { actions.navigate(Routes.STUDIO) },
    ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Kultr.colors.ink3) }
    LanguageRow()
    Choice(t("Theme"), listOf(ThemeMode.SYSTEM to t("System"), ThemeMode.LIGHT to t("Light"), ThemeMode.DARK to t("Dark")), s.theme) { v -> update { it.copy(theme = v) } }
    Toggle(
        t("Night mode"),
        s.nightMode,
        hint = t("Pure black instead of dark grey, for OLED screens: easier on the eyes in the dark, and lighter on the battery. Used whenever Kultr is dark, so with System it comes on with your phone's dark mode."),
    ) { v ->
        // Asking for black while the theme is Light means dark is wanted.
        update { it.copy(nightMode = v, theme = if (v && it.theme == ThemeMode.LIGHT) ThemeMode.DARK else it.theme) }
    }
    AppIconChoice()
    Toggle(t("Colour from artwork"), s.accentMode == AccentMode.ARTWORK, hint = t("The interface takes its colour from whatever is playing.")) { v ->
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
                    if (selected) Icon(Icons.Rounded.Check, contentDescription = t("Selected"), tint = Color.Black.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    if (s.accentMode == AccentMode.ARTWORK) {
        SettingSlider(
            t("How much of your colour"),
            s.accentBlend.toFloat(),
            0f..100f,
            { "${it.roundToInt()}%" },
            hint = t("0% is pure artwork colour; 100% ignores the artwork."),
        ) { v -> update { it.copy(accentBlend = v.roundToInt()) } }
    }
    Toggle(t("Blurred artwork background"), s.backdropArtwork) { v -> update { it.copy(backdropArtwork = v) } }
    Toggle(t("Clear glass"), s.clearGlass, hint = t("The floating bar and buttons show more of the page through them.")) { v -> update { it.copy(clearGlass = v) } }
    Choice(t("Corners"), listOf(CornerStyle.SHARP to t("Sharp"), CornerStyle.SOFT to t("Soft"), CornerStyle.ROUND to t("Round")), s.corners) { v -> update { it.copy(corners = v) } }
    Choice(t("Panel borders"), listOf(SurfaceBorder.NEUTRAL to t("Neutral"), SurfaceBorder.ACCENT to t("Accent")), s.surfaceBorder) { v -> update { it.copy(surfaceBorder = v) } }
    SettingSlider(t("Border opacity"), s.borderOpacity.toFloat(), 0f..100f, { "${it.roundToInt()}%" }) { v -> update { it.copy(borderOpacity = v.roundToInt()) } }
    SettingSlider(t("Panel opacity"), s.surfaceOpacity.toFloat(), 0f..200f, { "${it.roundToInt()}%" }) { v -> update { it.copy(surfaceOpacity = v.roundToInt()) } }
    Choice(t("Grid size"), listOf(GridSize.SMALL to t("Small"), GridSize.MEDIUM to t("Medium"), GridSize.LARGE to t("Large")), s.gridSize) { v -> update { it.copy(gridSize = v) } }
    Toggle(t("Compact track rows"), s.compactRows) { v -> update { it.copy(compactRows = v) } }
    Choice(t("Playhead"), PlayheadStyle.entries.map { it to it.label }, s.playhead, hint = s.playhead.note) { v -> update { it.copy(playhead = v) } }
    Toggle(t("Count time down"), s.timeRemaining, hint = t("Show how much of the track is left. Tapping the time in the player switches it too.")) { v ->
        update { it.copy(timeRemaining = v) }
    }
    Toggle(t("Show the lyrics tab"), s.showLyrics) { v -> update { it.copy(showLyrics = v) } }
    Toggle(t("Reduce motion"), s.reduceMotion, hint = t("Stops animated colour changes and playhead effects.")) { v -> update { it.copy(reduceMotion = v) } }
}

/**
 * The language Kultr shows, which Android keeps per app (Settings → Apps →
 * Kultr → Language, from Android 13); before that, Kultr follows the phone.
 */
@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    SettingRow(
        t("Language"),
        hint = t("Kultr speaks English and German. Android keeps the choice for each app; Kultr follows your phone until you choose."),
        onClick = {
            val perApp = Build.VERSION.SDK_INT >= 33
            val intent = if (perApp) {
                Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
            } else {
                Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS)
            }
            runCatching { context.startActivity(intent) }.onFailure { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_LOCALE_SETTINGS)) } }
        },
    ) { Text(Translations.currentName(), color = Kultr.colors.accent, style = MaterialTheme.typography.bodyMedium) }
}

/** The icon on the home screen: Cream, or one of three others. */
@Composable
private fun AppIconChoice() {
    val context = LocalContext.current
    val messages = LocalActions.current.graph.messages
    val colors = Kultr.colors
    var chosen by remember { mutableStateOf(AppIcon.current(context)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(t("App icon"), style = MaterialTheme.typography.bodyLarge, color = colors.ink)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon.entries.forEach { icon ->
                val selected = icon == chosen
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (icon != chosen) {
                                AppIcon.use(context, icon)
                                chosen = icon
                                messages.show(t("App icon: %@. The home screen shows it in a moment.", icon.label))
                            }
                        }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(shape)
                            .background(Color(icon.background))
                            .border(if (selected) 3.dp else 1.dp, if (selected) colors.accent else colors.line, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The launcher shows the middle two thirds of the icon's layer.
                        Image(painterResource(icon.foreground), contentDescription = null, modifier = Modifier.requiredSize(84.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(icon.label, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.accent else colors.ink2)
                }
            }
        }
    }
}

// ------------------------------------------------------------ home page --

@Composable
private fun HomeSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val on = resolveHomeTiles(s.homeTiles)
    val off = availableHomeTiles(s.homeTiles)
    var adding by remember { mutableStateOf(false) }
    HomeWidgetSettings()
    Spacer(Modifier.height(8.dp))
    Text(
        t("Shelves are shown in this order. One with nothing in it is skipped."),
        color = Kultr.colors.ink3,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    on.forEachIndexed { index, tile ->
        // Keyed, so moving a shelf slides both rows past each other.
        key(tile.id) {
            Row(Modifier.animatePlacement().fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(tile.title, color = Kultr.colors.ink)
                    Text(tile.note, color = Kultr.colors.ink3, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(enabled = index > 0, onClick = {
                    update { st -> st.copy(homeTiles = on.map { it.id }.toMutableList().apply { add(index - 1, removeAt(index)) }) }
                }) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = t("Move up")) }
                IconButton(enabled = index < on.size - 1, onClick = {
                    update { st -> st.copy(homeTiles = on.map { it.id }.toMutableList().apply { add(index + 1, removeAt(index)) }) }
                }) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = t("Move down")) }
                IconButton(onClick = { update { st -> st.copy(homeTiles = on.map { it.id } - tile.id) } }) {
                    Icon(Icons.Rounded.Close, contentDescription = t("Remove from the home page"))
                }
            }
        }
    }
    if (off.isNotEmpty()) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Pill(t("Add a shelf"), icon = Icons.Rounded.Add, onClick = { adding = true })
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
    Toggle(t("Crossfade between tracks"), s.crossfadeEnabled, hint = t("Two decks play at once for the length of the fade.")) { v -> update { it.copy(crossfadeEnabled = v) } }
    SettingSlider(t("Crossfade length"), s.crossfadeSeconds.toFloat(), 1f..20f, { "%.0f s".format(it) }, steps = 18, enabled = s.crossfadeEnabled) { v ->
        update { it.copy(crossfadeSeconds = v.roundToInt().toDouble()) }
    }
    Choice(t("Fade shape"), CrossfadeCurve.entries.map { it to it.label }, s.crossfadeCurve) { v -> update { it.copy(crossfadeCurve = v) } }
    Toggle(t("Also fade when you skip"), s.crossfadeOnSkip, hint = t("A short fade instead of a hard cut on next and previous.")) { v -> update { it.copy(crossfadeOnSkip = v) } }
    Toggle(t("Gapless playback"), s.gapless, hint = t("With crossfade off, the next track starts the instant this one ends.")) { v -> update { it.copy(gapless = v) } }
    Toggle(
        t("Karousel"),
        s.karousel,
        hint = t("When the queue runs out, music like what's been playing keeps coming: similar songs, artists like these and your own favourites. Its songs show under their own heading in Up next. Tapping the shuffle button twice turns it on too."),
    ) { v -> update { it.copy(karousel = v) } }
    Toggle(
        t("Wind down before the sleep timer"),
        s.sleepWindDown,
        hint = t("In a sleep timer's last 15 minutes, Karousel plays its calmest picks first."),
    ) { v -> update { it.copy(sleepWindDown = v) } }
    Toggle(t("Resume where you left off"), s.resumeOnStart, hint = t("Restores the queue and position when Kultr opens.")) { v -> update { it.copy(resumeOnStart = v) } }
    Toggle(
        t("Send plays to Navidrome"),
        s.scrobble,
        hint = t("Counts each play on your server, so recently and most played are the same on every device and survive reinstalling. Plays made offline are sent, with their real time, once you are back."),
    ) { v -> update { it.copy(scrobble = v) } }
}

// --------------------------------------------------------------- injekt --

@Composable
private fun InjektSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    Toggle(t("Enable InjeKt"), s.injektEnabled, hint = t("Beat-matched, key-aware transitions planned from each track's tempo, key and structure.")) { v ->
        update { it.copy(injektEnabled = v) }
    }
    val on = s.injektEnabled
    Toggle("Beat-match", s.injektBeatMatch, enabled = on, hint = t("Bring both tracks to a shared tempo and line up their bars.")) { v -> update { it.copy(injektBeatMatch = v) } }
    Toggle(t("Meet in the middle"), s.injektTempoRamp, enabled = on, hint = t("Ease the current track toward the next one's tempo before the blend.")) { v -> update { it.copy(injektTempoRamp = v) } }
    SettingSlider(t("Tempo share"), s.injektTempoBlend.toFloat(), 0f..100f, { "${it.roundToInt()}%" }, enabled = on && s.injektTempoRamp, hint = t("How much of the tempo change the outgoing track makes.")) { v ->
        update { it.copy(injektTempoBlend = v.roundToInt().toDouble()) }
    }
    SettingSlider(t("Maximum tempo shift"), s.injektMaxTempoShift.toFloat(), 2f..16f, { "${it.roundToInt()}%" }, steps = 13, enabled = on, hint = t("Per track. Further apart than this, tracks are crossfaded instead.")) { v ->
        update { it.copy(injektMaxTempoShift = v.roundToInt().toDouble()) }
    }
    Choice(t("Transition length"), listOf(4 to t("4 bars"), 8 to t("8 bars"), 16 to t("16 bars"), 32 to t("32 bars")), s.injektBars, hint = t("Shortened automatically when two tracks clash.")) { v ->
        update { it.copy(injektBars = v) }
    }
    Toggle(t("Bass swap"), s.injektBassSwap, enabled = on, hint = t("Roll the outgoing bass off before the incoming bass comes up.")) { v -> update { it.copy(injektBassSwap = v) } }
    Toggle(t("Harmonic mixing"), s.injektHarmonic, enabled = on, hint = t("When keys clash, filter out of the old track instead of blending.")) { v -> update { it.copy(injektHarmonic = v) } }
    Toggle(t("Skip long intros"), s.injektSkipIntro, enabled = on, hint = t("Bring the next track in at its first real downbeat.")) { v -> update { it.copy(injektSkipIntro = v) } }
    Toggle(
        t("Analyse ahead"),
        s.injektAnalyseAhead,
        enabled = on,
        hint = t("The current and next track are always analysed when a track starts. This also measures the one after, so skipping ahead lands on a transition that is ready too."),
    ) { v -> update { it.copy(injektAnalyseAhead = v) } }
    Toggle(t("Analyse on Wi-Fi only"), s.injektAnalyseOnWifiOnly, enabled = on, hint = t("Never spend mobile data on analysis.")) { v -> update { it.copy(injektAnalyseOnWifiOnly = v) } }
}

// ---------------------------------------------------------------- audio --

@Composable
private fun AudioSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    Choice(t("Volume levelling (ReplayGain)"), ReplayGainMode.entries.map { it to it.label }, s.replayGainMode) { v -> update { it.copy(replayGainMode = v) } }
    SettingSlider(t("ReplayGain pre-amp"), s.replayGainPreamp.toFloat(), -12f..12f, { "%+.0f dB".format(it) }, steps = 23, enabled = s.replayGainMode != ReplayGainMode.OFF) { v ->
        update { it.copy(replayGainPreamp = v.roundToInt().toDouble()) }
    }
    Choice(t("Streaming quality on Wi-Fi"), BITRATES, s.preferredBitrate, hint = t("Lower bitrates are transcoded by your server.")) { v -> update { it.copy(preferredBitrate = v) } }
    Choice(t("Streaming quality on mobile data"), listOf(-1 to t("Same as Wi-Fi")) + BITRATES, s.preferredBitrateMobile) { v -> update { it.copy(preferredBitrateMobile = v) } }
    Choice(
        t("Transcode format"),
        listOf("" to t("Server default"), "mp3" to "MP3", "opus" to t("Opus"), "aac" to "AAC", "ogg" to t("Vorbis")),
        s.preferredFormat,
        hint = t("Used when a lower bitrate is chosen."),
    ) { v -> update { it.copy(preferredFormat = v) } }
}

// ------------------------------------------------------------ equaliser --

@Composable
private fun EqualiserSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    Toggle(t("Enable the equaliser"), s.eqEnabled) { v -> update { it.copy(eqEnabled = v) } }
    Choice(t("Preset"), EQ_PRESETS.keys.map { it to t(it) }, s.eqPreset) { name -> update { it.withEqPreset(name) } }
    val gains = s.eqBandGains
    EQ_BANDS.forEachIndexed { index, hz ->
        val label = if (hz >= 1000) "${hz / 1000} kHz" else t("%@ Hz", hz)
        SettingSlider(label, gains[index].toFloat(), -12f..12f, { "%+.0f dB".format(it) }, steps = 23, enabled = s.eqEnabled) { v ->
            update { st -> st.copy(eqPreset = "Custom", eqGains = st.eqBandGains.toMutableList().also { it[index] = v.roundToInt().toDouble() }) }
        }
    }
    SettingSlider(t("Pre-amp"), s.eqPreamp.toFloat(), -12f..6f, { "%+.0f dB".format(it) }, steps = 17, enabled = s.eqEnabled, hint = t("Pull this down if the equaliser makes things clip.")) { v ->
        update { it.copy(eqPreamp = v.roundToInt().toDouble()) }
    }
}

// -------------------------------------------------------------- offline --

@Composable
private fun OfflineSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    val graph = LocalActions.current.graph
    SettingSlider(t("Parallel downloads"), s.offlineConcurrency.toFloat(), 1f..8f, { "${it.roundToInt()}" }, steps = 6) { v ->
        update { it.copy(offlineConcurrency = v.roundToInt()) }
    }
    Choice(t("Download quality"), BITRATES, s.offlineBitrate, hint = t("Original keeps the file exactly as it is on the server.")) { v -> update { it.copy(offlineBitrate = v) } }
    Toggle(t("Download on Wi-Fi only"), s.offlineWifiOnly) { v -> update { it.copy(offlineWifiOnly = v) } }
    Toggle(t("Prefer offline copies"), s.offlineFirst, hint = t("Play a downloaded file instead of streaming when one exists.")) { v -> update { it.copy(offlineFirst = v) } }
    Choice(
        t("Stream cache"),
        listOf(256 to t("256 MB"), 1024 to t("1 GB"), 2048 to t("2 GB"), 4096 to t("4 GB"), 8192 to t("8 GB")),
        s.streamCacheMb,
        hint = t("Recently streamed audio is kept so replays do not refetch. Takes effect after a restart."),
    ) { v -> update { it.copy(streamCacheMb = v) } }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(t("Clear stream cache"), onClick = {
            graph.clearMediaCache()
            graph.messages.show(t("Stream cache cleared."))
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
    var renaming by remember { mutableStateOf<ServerProfile?>(null) }
    profiles.forEach { profile ->
        val isActive = profile.id == active?.id
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.label + if (isActive) t(" · in use") else "", color = if (isActive) Kultr.colors.accent else Kultr.colors.ink, fontWeight = FontWeight.SemiBold)
                Text(
                    if (profile.local) {
                        t("Folders on this phone, no server")
                    } else {
                        listOf(profile.username.ifBlank { t("not signed in") }, profile.serverUrl).joinToString(" · ")
                    },
                    color = Kultr.colors.ink3,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when {
                !profile.usable -> TextButton(onClick = { onSignIn(profile) }) { Text(t("Sign in")) }
                !isActive && profile.enabled -> TextButton(onClick = { graph.auth.switchTo(profile.id) }) { Text(t("Use")) }
            }
            Switch(checked = profile.enabled, onCheckedChange = { graph.auth.setEnabled(profile.id, it) })
            ServerMenu(
                profile,
                onRename = { renaming = profile },
                // Signing in again to the same address and user keeps the server and its name.
                onChangePassword = { onSignIn(profile) },
                onForget = { forget = profile },
            )
        }
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(t("Add a server"), icon = Icons.Rounded.Add, onClick = onAddServer)
        if (active != null) Pill(t("Sign out"), icon = Icons.AutoMirrored.Rounded.Logout, onClick = { graph.player.stop(); graph.auth.signOut() })
    }
    renaming?.let { profile ->
        TextInputDialog(
            title = t("Name this server"),
            initial = profile.label,
            confirm = t("Rename"),
            onConfirm = { graph.auth.rename(profile.id, it) },
            onDismiss = { renaming = null },
        )
    }
    forget?.let { profile ->
        ConfirmDialog(
            title = t("Forget %@?", profile.label),
            body = t("Its saved password, synced library, downloads and listening history on this phone are deleted. Nothing changes on the server."),
            confirm = t("Forget"),
            onConfirm = {
                if (profile.id == active?.id) graph.player.stop()
                graph.auth.remove(profile.id)
                graph.deleteDataFor(profile.id)
            },
            onDismiss = { forget = null },
        )
    }
}

@Composable
private fun ServerMenu(profile: ServerProfile, onRename: () -> Unit, onChangePassword: () -> Unit, onForget: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = t("More for %@", profile.label), tint = Kultr.colors.ink3)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(t("Rename…")) },
                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { open = false; onRename() },
            )
            if (profile.hasCredentials) {
                DropdownMenuItem(
                    text = { Text(t("Change password…")) },
                    leadingIcon = { Icon(Icons.Rounded.Key, null) },
                    onClick = { open = false; onChangePassword() },
                )
            }
            DropdownMenuItem(
                text = { Text(t("Forget"), color = Kultr.colors.danger) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Kultr.colors.danger) },
                onClick = { open = false; onForget() },
            )
        }
    }
}

// ------------------------------------------------------- music on phone --

/**
 * The library on the phone: the folders it is read from, and scanning them.
 * Before there is one, a way to start it.
 */
@Composable
private fun LocalMusicSettings() {
    val graph = LocalActions.current.graph
    val colors = Kultr.colors
    val profiles by graph.auth.profiles.collectAsStateWithLifecycle()
    val active by graph.auth.active.collectAsStateWithLifecycle()
    val scanning by graph.sync.running.collectAsStateWithLifecycle()
    val localProfile = profiles.firstOrNull { it.local }
    val inUse = active?.local == true

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (localProfile == null) {
                t("Play the music stored on this phone, without a server. Choose the folders it is in; Kultr gets access to those and nothing else.")
            } else {
                t("Kultr reads these folders. New and changed files are picked up when it starts, or when you scan.")
            },
            color = colors.ink3,
            style = MaterialTheme.typography.bodySmall,
        )
        MusicFolders(onChanged = {
            // The first folder starts the library on the phone; while it is in use, folders are read in at once.
            graph.auth.ensureLocalLibrary()
            if (graph.isLocal) graph.sync.start(SyncMode.CHECK, quiet = true)
        })
        if (localProfile != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (inUse) {
                    Pill(if (scanning) t("Scanning…") else t("Scan now"), icon = Icons.Rounded.Sync, enabled = !scanning, onClick = { graph.sync.start(SyncMode.CHECK) })
                    Pill(t("Read all tags again"), enabled = !scanning, onClick = { graph.sync.start(SyncMode.FULL) })
                } else {
                    Pill(t("Play music on this phone"), icon = Icons.Rounded.PhoneAndroid, accent = true, onClick = {
                        graph.player.stop()
                        graph.auth.switchTo(localProfile.id)
                        graph.sync.startFirstSyncIfNeeded()
                    })
                }
            }
        }
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
            .onSuccess { graph.messages.success(t("Settings exported.")) }
            .onFailure { graph.messages.error(t("Could not write the file: %@", it.message)) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@rememberLauncherForActivityResult
            val result = SettingsFile.import(text, graph.settings.current)
            graph.settings.replace(result.settings)
            val added = graph.auth.importServers(result.servers.map { Triple(it.label, it.serverUrl, it.authMode) })
            report = buildString {
                append(t("Applied %@ settings", result.applied.size))
                if (added > 0) append(t(if (added == 1) " and added %@ server (sign in to it in Servers)" else " and added %@ servers (sign in to them in Servers)", added))
                append(".")
                if (result.skipped.isNotEmpty()) {
                    append("\n\nSkipped:\n")
                    append(result.skipped.joinToString("\n") { (key, why) -> "• $key — $why" })
                }
            }
        } catch (err: Exception) {
            graph.messages.error(err.message ?: t("That file could not be read."))
        }
    }

    Text(
        t("Export every preference to a small file and import it on another phone, or in Kultr on the web. Passwords and usernames are never in it."),
        color = Kultr.colors.ink3,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Toggle(t("Include the list of servers"), includeServers, hint = t("Addresses only — never usernames or passwords.")) { includeServers = it }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(t("Export settings"), onClick = { exporter.launch("kultr-settings-${Instant.now().toString().take(10)}.json") })
        Pill(t("Import settings"), onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) })
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(t("Reset settings"), onClick = { confirmReset = true })
        Pill(t("Clear library data"), onClick = { confirmWipe = true })
    }
    report?.let { text ->
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text(t("Settings imported")) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { report = null }) { Text("OK") } },
        )
    }
    if (confirmReset) {
        ConfirmDialog(t("Reset every setting?"), t("Appearance, playback, InjeKt and everything else go back to their defaults. Servers are kept."), t("Reset"),
            onConfirm = { graph.settings.reset() }, onDismiss = { confirmReset = false })
    }
    if (confirmWipe) {
        ConfirmDialog(
            t("Clear library data?"),
            t("The synced library, InjeKt analysis and listening history for this server are deleted from this phone. Downloads are kept. Sync again to rebuild the library."),
            t("Clear"),
            onConfirm = {
                actions.launch {
                    val db = graph.database.value ?: return@launch
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        db.library().clearLibrary()
                        db.analysis().clear()
                        db.history().clear()
                        db.meta().put(app.kultr.android.data.db.MetaEntity("syncState", "{}"))
                    }
                    graph.messages.show(t("Library data cleared."))
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
    val actions = LocalActions.current
    val graph = actions.graph
    val check by graph.updates.check.collectAsStateWithLifecycle()
    val available by graph.updates.available.collectAsStateWithLifecycle()
    val open = { url: String -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    SettingRow(
        t("Version"),
        hint = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" +
            (available?.let { t(" · %@ is available", it.version) } ?: ""),
    ) {
        if (check is UpdateCheck.Checking) {
            CircularProgressIndicator(color = Kultr.colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Pill(t("Check for updates"), icon = Icons.Rounded.SystemUpdate, accent = available != null, onClick = {
                actions.launch {
                    when (val result = graph.updates.checkNow()) {
                        is UpdateCheck.Available -> actions.openUpdate()
                        is UpdateCheck.UpToDate -> graph.messages.show(t("You have the latest version."), MessageKind.SUCCESS)
                        is UpdateCheck.Failed -> graph.messages.error(t("Could not check for updates. %@", result.message))
                        else -> Unit
                    }
                }
            })
        }
    }
    SettingRow(t("Update log"), hint = t("What changed in every version, from 1.0.0 to now."), onClick = { actions.navigate(Routes.UPDATE_LOG) }) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Kultr.colors.ink3)
    }
    SettingRow(t("Source"), hint = "github.com/evropiani/Kultr_Android — issues and pull requests welcome.", onClick = {
        open("https://github.com/evropiani/Kultr_Android")
    })
    SettingRow(t("Website"), hint = "kultr.cc", onClick = { open(WEBSITE) })
    SettingRow(t("Get in touch"), hint = t("Questions, ideas, or something broken."), onClick = { open(DISCORD) }) {
        Pill("@evropiani", icon = BrandIcons.Discord, onClick = { open(DISCORD) })
    }
}

private const val WEBSITE = "https://kultr.cc/"
private const val DISCORD = "https://discord.com/users/319246364246540288"
