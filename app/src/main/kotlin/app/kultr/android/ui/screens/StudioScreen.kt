package app.kultr.android.ui.screens

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.SectionHeader
import app.kultr.android.ui.components.Segmented
import app.kultr.android.ui.components.WideButton
import app.kultr.android.ui.theme.Kultr
import app.kultr.android.ui.theme.KultrTheme
import app.kultr.core.settings.AccentMode
import app.kultr.core.settings.CornerStyle
import app.kultr.core.settings.GridSize
import app.kultr.core.settings.PlayerBlock
import app.kultr.core.settings.PlayheadStyle
import app.kultr.core.settings.STUDIO_PRESETS
import app.kultr.core.settings.Settings
import app.kultr.core.settings.StudioStyle
import app.kultr.core.settings.TEMPLATE_FIELDS
import app.kultr.core.settings.ThemeMode
import app.kultr.core.settings.renderTemplate
import app.kultr.core.util.ArtworkColor
import app.kultr.core.util.t
import kotlinx.coroutines.launch

private val EXAMPLES = TEMPLATE_FIELDS.toMap()

/**
 * Kultr Studio: the whole look in one place. Start from a style, change the
 * colours, the shape, the glass and how the player is put together, see it
 * in the preview as you go, and share it as a link or a file.
 */
@Composable
fun StudioScreen() {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val s by graph.settings.settings.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf(t("My style")) }
    val current = StudioStyle.of(s, name)
    fun update(transform: (Settings) -> Settings) = graph.settings.update(transform)

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(StudioStyle.json(current).toByteArray()) } }
            .onSuccess { graph.messages.show(t("Style saved")) }
            .onFailure { graph.messages.error(t("The style could not be saved.")) }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        val style = text?.let(StudioStyle::read)
        if (style != null) graph.incomingStyle.value = style else graph.messages.error(t("That file isn't a Kultr style."))
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = chromePadding(32.dp))) {
        item {
            BackBar()
            Text(t("Kultr Studio"), style = MaterialTheme.typography.headlineMedium, color = colors.ink, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
            StudioPreview(s, Modifier.padding(horizontal = 16.dp))
        }
        item {
            SectionHeader(t("Start from a style"), modifier = Modifier.padding(top = 16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                STUDIO_PRESETS.forEach { preset ->
                    PresetCard(preset, chosen = preset.matches(s)) { graph.settings.replace(preset.appliedTo(graph.settings.current)) }
                }
            }
            Note(t("A style is only the look: your library, servers and other settings stay as they are."))
        }
        item {
            SectionHeader(t("Colours"))
            Picker(t("Theme"), listOf(ThemeMode.SYSTEM to t("System"), ThemeMode.LIGHT to t("Light"), ThemeMode.DARK to t("Dark")), s.theme) { v -> update { it.copy(theme = v) } }
            SwitchRow(t("Night mode (pure black)"), s.nightMode) { v -> update { it.copy(nightMode = v) } }
            SwitchRow(t("Colour from the artwork"), s.accentMode == AccentMode.ARTWORK) { v ->
                update { it.copy(accentMode = if (v) AccentMode.ARTWORK else AccentMode.FIXED) }
            }
            SwitchRow(t("Blurred cover behind the player"), s.backdropArtwork) { v -> update { it.copy(backdropArtwork = v) } }
            Note(t("The accent colours and the rest are in Settings → Appearance."))
        }
        item {
            SectionHeader(t("Shape and glass"))
            Picker(t("Corners"), listOf(CornerStyle.SHARP to t("Sharp"), CornerStyle.SOFT to t("Soft"), CornerStyle.ROUND to t("Round")), s.corners) { v -> update { it.copy(corners = v) } }
            Picker(t("Grid"), listOf(GridSize.SMALL to t("Small covers"), GridSize.MEDIUM to t("Medium"), GridSize.LARGE to t("Large")), s.gridSize) { v -> update { it.copy(gridSize = v) } }
            SwitchRow(t("Compact rows"), s.compactRows) { v -> update { it.copy(compactRows = v) } }
            SwitchRow(t("Clear glass"), s.clearGlass) { v -> update { it.copy(clearGlass = v) } }
            Picker(t("Playhead"), PlayheadStyle.entries.map { it to it.label }, s.playhead) { v -> update { it.copy(playhead = v) } }
        }
        item {
            SectionHeader(t("The player, top to bottom"))
            val ordered = s.playerBlocks + PlayerBlock.entries.filter { it !in s.playerBlocks }
            ordered.forEachIndexed { index, block ->
                val on = block in s.playerBlocks
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(block.label, style = MaterialTheme.typography.bodyLarge, color = if (on) colors.ink else colors.ink3, modifier = Modifier.weight(1f))
                    IconButton(enabled = on && index > 0, onClick = { update { it.copy(playerBlocks = it.playerBlocks.moved(block, -1)) } }) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = t("Move %@ up", block.label))
                    }
                    IconButton(enabled = on && index < s.playerBlocks.size - 1, onClick = { update { it.copy(playerBlocks = it.playerBlocks.moved(block, 1)) } }) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = t("Move %@ down", block.label))
                    }
                    Switch(checked = on, enabled = !on || s.playerBlocks.size > 1, onCheckedChange = { show ->
                        update { it.copy(playerBlocks = if (show) it.playerBlocks + block else it.playerBlocks - block) }
                    })
                }
            }
            Note(t("Move a part up or down, or switch it off to hide it."))
        }
        item {
            SectionHeader(t("The info line"))
            OutlinedTextField(
                s.factsTemplate,
                { v -> update { it.copy(factsTemplate = v) } },
                placeholder = { Text(t("Kultr's own line")) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TEMPLATE_FIELDS.forEach { (field, _) ->
                    Box(
                        Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(50))
                            .background(colors.sand)
                            .clickable {
                                update {
                                    val joiner = if (it.factsTemplate.isEmpty()) "" else " · "
                                    it.copy(factsTemplate = it.factsTemplate + "{?$field:$joiner{$field}}")
                                }
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(field, style = MaterialTheme.typography.labelLarge, color = colors.accent) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Looks like"), color = colors.ink3, modifier = Modifier.weight(1f))
                Text(if (s.factsTemplate.isEmpty()) t("2019 · Electronic · FLAC") else renderTemplate(s.factsTemplate, EXAMPLES), color = colors.ink, maxLines = 1)
            }
            if (s.factsTemplate.isNotEmpty()) {
                TextButton(onClick = { update { it.copy(factsTemplate = "") } }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(t("Back to Kultr's own line")) }
            }
            Note("{bpm} puts the tempo in. {?genre: · {genre}} shows the genre, and its dot, only when a track has one. Tap a name to add it.")
        }
        item {
            SectionHeader(t("Share"))
            OutlinedTextField(name, { name = it }, label = { Text(t("Style name")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            ActionRow(t("Share as a link"), Icons.Rounded.Link) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, name).putExtra(Intent.EXTRA_TEXT, "A Kultr style: $name\n${current.link}")
                context.startActivity(Intent.createChooser(send, "Share “$name”"))
            }
            ActionRow(t("Save as a file"), Icons.AutoMirrored.Rounded.InsertDriveFile) { saveFile.launch("$name.kultrstyle.json") }
            ActionRow(t("Copy the link"), Icons.Rounded.ContentCopy) {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(t("Kultr style"), current.link))) }
                graph.messages.show(t("Style link copied"))
            }
            ActionRow(t("Use a link from the clipboard"), Icons.Rounded.ContentPaste) {
                scope.launch {
                    val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    val style = text?.let(StudioStyle::read)
                    if (style != null) graph.incomingStyle.value = style else graph.messages.show(t("There's no Kultr style link on the clipboard."))
                }
            }
            ActionRow(t("Open a style file"), Icons.Rounded.FolderOpen) { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            Note(t("Someone opening your link sees the style first, then chooses whether to use it."))
        }
    }
}

private fun List<PlayerBlock>.moved(block: PlayerBlock, by: Int): List<PlayerBlock> {
    val from = indexOf(block)
    val to = from + by
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Kultr.colors.ink3, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Kultr.colors.ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> Picker(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Kultr.colors.ink)
        Spacer(Modifier.height(6.dp))
        Segmented(options, selected, onSelect, Modifier.horizontalScroll(rememberScrollState()))
    }
}

@Composable
private fun ActionRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Kultr.colors.accent)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Kultr.colors.accent)
    }
}

/** A style card: its page colour, its accent and its corners, and its name. */
@Composable
private fun PresetCard(style: StudioStyle, chosen: Boolean, onClick: () -> Unit) {
    val colors = Kultr.colors
    val dark = style.theme == ThemeMode.DARK || (style.theme == ThemeMode.SYSTEM && colors.dark)
    val page = when {
        dark && style.nightMode -> Color.Black
        dark -> Color(0xFF1C1A17)
        else -> Color(0xFFF6F0E5)
    }
    val ink = if (dark) Color.White else Color(0xFF1F1A14)
    val accent = Color(ArtworkColor.parseHex(style.accent) ?: 0xFFFF7A3D.toInt())
    val radius = when (style.corners) {
        CornerStyle.SHARP -> 4.dp
        CornerStyle.SOFT -> 10.dp
        CornerStyle.ROUND -> 18.dp
    }
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(104.dp, 96.dp)
                .clip(RoundedCornerShape(radius))
                .background(page)
                .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.accent else colors.ink4, RoundedCornerShape(radius))
                .padding(10.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(radius / 2)).background(ink.copy(alpha = 0.15f)))
                Box(Modifier.size(52.dp, 5.dp).clip(RoundedCornerShape(50)).background(ink.copy(alpha = 0.8f)))
                Box(Modifier.size(34.dp, 5.dp).clip(RoundedCornerShape(50)).background(accent))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t(style.name), style = MaterialTheme.typography.labelLarge, color = colors.ink)
            if (chosen) Icon(Icons.Rounded.Check, contentDescription = t("Chosen"), tint = colors.accent, modifier = Modifier.padding(start = 4.dp).size(14.dp))
        }
    }
}

/** The player in miniature, built from the blocks in their order, in the look as it is now. */
@Composable
private fun StudioPreview(s: Settings, modifier: Modifier = Modifier) {
    val colors = Kultr.colors
    val radii = Kultr.radii
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radii.lg))
            .background(colors.background)
            .border(1.dp, colors.ink4, RoundedCornerShape(radii.lg))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        s.playerBlocks.forEach { block ->
            when (block) {
                PlayerBlock.ARTWORK -> Box(
                    Modifier.size(120.dp).clip(RoundedCornerShape(radii.lg)).background(colors.accent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = colors.accent, modifier = Modifier.size(40.dp)) }
                PlayerBlock.TITLE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Paper Moons", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.ink)
                    Text("Ama Okoro — Night Ferries", style = MaterialTheme.typography.bodyMedium, color = colors.ink2)
                }
                PlayerBlock.FACTS -> Text(
                    if (s.factsTemplate.isEmpty()) t("2019 · Electronic · FLAC") else renderTemplate(s.factsTemplate, EXAMPLES),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.ink3,
                    textAlign = TextAlign.Center,
                )
                PlayerBlock.PROGRESS -> Box(Modifier.width(220.dp).height(4.dp).clip(RoundedCornerShape(50)).background(colors.ink4)) {
                    Box(Modifier.width(90.dp).height(4.dp).clip(RoundedCornerShape(50)).background(colors.accent))
                }
                PlayerBlock.TRANSPORT -> Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = null, tint = colors.ink)
                    Icon(Icons.Rounded.Pause, contentDescription = null, tint = colors.ink, modifier = Modifier.size(36.dp))
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = colors.ink)
                }
            }
        }
    }
}

/** A style someone shared: what it looks like, and the choice to use it. */
@Composable
fun StylePreviewDialog(style: StudioStyle, onDone: () -> Unit) {
    val graph = LocalActions.current.graph
    val colors = Kultr.colors
    val look = style.appliedTo(graph.settings.current)
    val accent = Color(ArtworkColor.parseHex(look.accent) ?: 0xFFFF7A3D.toInt())
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(colors.background).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Rounded.Palette, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
            Text("“${t(style.name)}”", style = MaterialTheme.typography.headlineSmall, color = colors.ink)
            Text(t("A Kultr style someone shared: the look only, nothing else."), style = MaterialTheme.typography.bodyMedium, color = colors.ink2, textAlign = TextAlign.Center)
            // The preview in the style's own colours, whatever the app looks like now.
            KultrTheme(look, accent) { StudioPreview(look) }
            Spacer(Modifier.weight(1f))
            WideButton(t("Use this style"), Icons.Rounded.Palette, onClick = {
                graph.settings.replace(style.appliedTo(graph.settings.current))
                graph.messages.show(t("Now using “%@”", t(style.name)))
                onDone()
            }, modifier = Modifier.fillMaxWidth(), accent = true)
            TextButton(onClick = onDone) { Text(t("Not now"), color = colors.ink2) }
        }
    }
}
