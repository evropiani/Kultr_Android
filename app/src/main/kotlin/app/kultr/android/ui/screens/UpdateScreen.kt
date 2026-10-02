package app.kultr.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.BuildConfig
import app.kultr.android.data.AppRelease
import app.kultr.android.data.UpdateCheck
import app.kultr.android.data.UpdateDownload
import app.kultr.android.ui.LocalActions
import app.kultr.android.ui.chromePadding
import app.kultr.android.ui.components.AccentWash
import app.kultr.android.ui.components.EmptyState
import app.kultr.android.ui.components.Eyebrow
import app.kultr.android.ui.components.GlassPanel
import app.kultr.android.ui.components.Loading
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.glass
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.util.ReleaseNotes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** What a new version brings, and the button that installs it. */
@Composable
fun UpdateScreen() {
    val actions = LocalActions.current
    val graph = actions.graph
    val colors = Kultr.colors
    val release by graph.updates.available.collectAsStateWithLifecycle()
    val check by graph.updates.check.collectAsStateWithLifecycle()
    val download by graph.updates.download.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        AccentWash()
        Column(Modifier.fillMaxSize()) {
            BackBar("Update")
            val r = release
            if (r == null) {
                // Nothing newer: say so, and offer to look again.
                Column(
                    Modifier.fillMaxSize().padding(bottom = chromePadding(16.dp)),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (check is UpdateCheck.Checking) {
                        Loading()
                    } else {
                        val failed = check as? UpdateCheck.Failed
                        EmptyState(
                            Icons.Rounded.Verified,
                            if (failed != null) "Could not check for updates" else "Kultr is up to date",
                            body = failed?.message ?: "You have ${BuildConfig.VERSION_NAME}, the latest version.",
                            action = {
                                Pill("Check again", icon = Icons.Rounded.Refresh, onClick = { actions.launch { graph.updates.checkNow() } })
                            },
                        )
                    }
                }
                return@Column
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = chromePadding(16.dp)),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow("New version")
                    Text(r.title, style = MaterialTheme.typography.headlineMedium, color = colors.ink)
                    Text(
                        listOfNotNull("You have ${BuildConfig.VERSION_NAME}", releasedOn(r.publishedAt)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink3,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (val d = download) {
                        is UpdateDownload.Downloading -> {
                            val fraction = d.fraction
                            if (fraction == null) {
                                LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent)
                            } else {
                                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = colors.accent)
                            }
                            Text(
                                if (fraction == null) "Downloading…" else "Downloading… ${(fraction * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ink2,
                            )
                        }
                        UpdateDownload.Installing -> Text("Opening Android's installer…", color = colors.ink2)
                        else -> {
                            Pill(
                                "Update to ${r.version}",
                                icon = Icons.Rounded.SystemUpdate,
                                accent = true,
                                onClick = { graph.updates.install(r) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (d is UpdateDownload.Failed) {
                                Text(d.message, style = MaterialTheme.typography.bodySmall, color = colors.danger)
                            }
                        }
                    }
                    Text(
                        "Android asks you to confirm. Your servers, music folders, library and downloads stay as they are.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink3,
                    )
                }

                GlassPanel(Modifier.fillMaxWidth()) { Notes(r.notes) }

                Pill(
                    "Release page",
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = { graph.updates.openPage(r) },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * The note at the top of the app that a new version is out: tapping it opens
 * [UpdateScreen], the cross hides it until the next version.
 */
@Composable
fun UpdateBanner(release: AppRelease, onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Kultr.colors
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .fillMaxWidth()
            .glass(shape)
            .clip(shape)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(colors.accent),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Kultr ${release.version} is available",
                style = MaterialTheme.typography.titleSmall,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "See what's new and update",
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = colors.ink3)
        }
    }
}

/** The release notes, laid out from their Markdown. */
@Composable
private fun Notes(markdown: String) {
    val colors = Kultr.colors
    val blocks = remember(markdown) { ReleaseNotes.parse(markdown) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (blocks.isEmpty()) {
            Text("No notes came with this version.", color = colors.ink3)
        }
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ReleaseNotes.Block.Heading -> Text(
                    styled(block.text),
                    style = if (block.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    color = colors.ink,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp),
                )
                is ReleaseNotes.Block.Bullet -> Row {
                    Text("•", color = colors.accent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(16.dp))
                    Text(styled(block.text), style = MaterialTheme.typography.bodyMedium, color = colors.ink2)
                }
                is ReleaseNotes.Block.Paragraph -> Text(styled(block.text), style = MaterialTheme.typography.bodyMedium, color = colors.ink2)
            }
        }
    }
}

@Composable
private fun styled(spans: List<ReleaseNotes.Span>): AnnotatedString {
    val colors = Kultr.colors
    return remember(spans, colors) {
        buildAnnotatedString {
            spans.forEach { span ->
                val style = SpanStyle(
                    color = if (span.bold) colors.ink else Color.Unspecified,
                    fontWeight = if (span.bold) FontWeight.SemiBold else null,
                    fontFamily = if (span.code) FontFamily.Monospace else null,
                    background = if (span.code) colors.glass else Color.Unspecified,
                )
                val link = span.link
                if (link == null) {
                    withStyle(style) { append(span.text) }
                } else {
                    val linkStyle = TextLinkStyles(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline))
                    withLink(LinkAnnotation.Url(link, linkStyle)) { withStyle(style) { append(span.text) } }
                }
            }
        }
    }
}

private fun releasedOn(iso: String?): String? = iso?.let {
    runCatching {
        "released " + Instant.parse(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    }.getOrNull()
}
