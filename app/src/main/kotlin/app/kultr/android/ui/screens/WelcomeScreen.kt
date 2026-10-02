package app.kultr.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.AppGraph
import app.kultr.android.R
import app.kultr.android.ui.MusicFolders
import app.kultr.android.ui.components.ArtworkBackdropPlain
import app.kultr.android.ui.components.Pill
import app.kultr.android.ui.components.glass
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.sync.SyncMode
import kotlinx.coroutines.launch

private enum class WelcomeStep { HELLO, CHOICE, SERVER, FOLDERS, TOUR }

/**
 * The first start: a welcome, then where the music is (a Navidrome server, or
 * folders on this phone), then a short tour of what Kultr does.
 *
 * Also shown, without the welcome and the tour, when every library has been
 * signed out of: [returning] starts at the choice and finishes once a library
 * is in use.
 */
@Composable
fun WelcomeFlow(graph: AppGraph, returning: Boolean, onFinished: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(if (returning) WelcomeStep.CHOICE else WelcomeStep.HELLO) }
    var local by rememberSaveable { mutableStateOf(false) }
    fun ready() {
        if (returning) onFinished() else step = WelcomeStep.TOUR
    }

    BackHandler(enabled = step == WelcomeStep.SERVER || step == WelcomeStep.FOLDERS || (step == WelcomeStep.CHOICE && !returning)) {
        step = if (step == WelcomeStep.CHOICE) WelcomeStep.HELLO else WelcomeStep.CHOICE
    }

    Box(Modifier.fillMaxSize()) {
        ArtworkBackdropPlain()
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(320)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(260)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
            },
            label = "welcome",
        ) { current ->
            when (current) {
                WelcomeStep.HELLO -> Hello(onNext = { step = WelcomeStep.CHOICE })
                WelcomeStep.CHOICE -> Choice(
                    graph,
                    returning = returning,
                    onServer = { step = WelcomeStep.SERVER },
                    onLocal = { step = WelcomeStep.FOLDERS },
                    onSaved = onFinished,
                )
                WelcomeStep.SERVER -> LoginScreen(
                    graph,
                    onCancel = { step = WelcomeStep.CHOICE },
                    onDone = {
                        local = false
                        ready()
                    },
                )
                WelcomeStep.FOLDERS -> Folders(
                    graph,
                    onBack = { step = WelcomeStep.CHOICE },
                    onDone = {
                        graph.auth.useLocalLibrary()
                        graph.sync.start(SyncMode.FULL, quiet = true)
                        local = true
                        ready()
                    },
                )
                WelcomeStep.TOUR -> Tour(local = local, onDone = onFinished)
            }
        }
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun Hello(onNext: () -> Unit) {
    val colors = Kultr.colors
    Page {
        Spacer(Modifier.height(72.dp))
        Image(painterResource(R.drawable.kultr_logo), contentDescription = null, modifier = Modifier.size(132.dp))
        Spacer(Modifier.height(28.dp))
        Text("Welcome to Kultr", style = MaterialTheme.typography.headlineLarge, color = colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Your music, mixed the way a DJ would mix it. From your own Navidrome server, or straight from this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(56.dp))
        Pill(
            "Get started",
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            accent = true,
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        )
    }
}

@Composable
private fun Choice(graph: AppGraph, returning: Boolean, onServer: () -> Unit, onLocal: () -> Unit, onSaved: () -> Unit) {
    val colors = Kultr.colors
    val profiles by graph.auth.profiles.collectAsStateWithLifecycle()
    Page {
        Spacer(Modifier.height(48.dp))
        Text("Where is your music?", style = MaterialTheme.typography.headlineMedium, color = colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "You can add the other one later, in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink3,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        ChoiceCard(
            icon = Icons.Rounded.Dns,
            title = "On my Navidrome server",
            body = "Sign in, and Kultr keeps a copy of your library on this phone, so browsing is instant and works offline. Plays are counted on your server.",
            onClick = onServer,
        )
        Spacer(Modifier.height(14.dp))
        ChoiceCard(
            icon = Icons.Rounded.PhoneAndroid,
            title = "On this phone",
            body = "Choose the folders your music is in. No server, no account: Kultr plays your files, with their covers, albums and artists.",
            onClick = onLocal,
        )
        // Signed out of everything: the libraries already set up are one tap away.
        val saved = profiles.filter { it.enabled && it.usable }
        if (returning && saved.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text("Or go back to", color = colors.ink3, style = MaterialTheme.typography.labelLarge)
            saved.forEach { profile ->
                Pill(
                    profile.label,
                    icon = if (profile.local) Icons.Rounded.PhoneAndroid else Icons.Rounded.Dns,
                    onClick = { if (graph.auth.switchTo(profile.id)) onSaved() },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ChoiceCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    val colors = Kultr.colors
    val shape = RoundedCornerShape(Kultr.radii.lg)
    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .glass(shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.ink2)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = colors.ink3)
    }
}

@Composable
private fun Folders(graph: AppGraph, onBack: () -> Unit, onDone: () -> Unit) {
    val colors = Kultr.colors
    val folders by graph.local.folders.collectAsStateWithLifecycle()
    Page {
        Spacer(Modifier.height(48.dp))
        Text("Choose your music folders", style = MaterialTheme.typography.headlineMedium, color = colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Pick the folders your music is in: Music, a memory card, a folder of downloads. Kultr gets access to those folders and nothing else, and finds new music in them on its own.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 440.dp),
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .glass(RoundedCornerShape(Kultr.radii.lg))
                .padding(16.dp),
        ) { MusicFolders() }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth().widthIn(max = 480.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Back", onClick = onBack)
            Pill(
                "Continue",
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                accent = true,
                enabled = folders.isNotEmpty(),
                onClick = onDone,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private class Feature(val icon: ImageVector, val title: String, val body: String)

@Composable
private fun Tour(local: Boolean, onDone: () -> Unit) {
    val colors = Kultr.colors
    val features = listOf(
        Feature(
            Icons.Rounded.AutoAwesome,
            "Mixes like a DJ",
            "InjeKt measures each track's tempo, key and structure, then blends one into the next on the beat. " +
                "Prefer it simple? Crossfade, gapless or a clean cut, in Settings.",
        ),
        Feature(
            Icons.Rounded.TouchApp,
            "Glass you can slide",
            "Touch the tab bar and slide your finger: the lens follows it. Swipe sideways through your library, " +
                "and pull the player down to close it.",
        ),
        Feature(
            Icons.Rounded.PanTool,
            "Drag it where it goes",
            "Long-press a track, an album or an artist and drop it on Play next, Add to queue or Favourite.",
        ),
        if (local) {
            Feature(
                Icons.Rounded.Widgets,
                "On your home screen",
                "Add the Kultr widget, as large as you like, with the artwork if you want it. Headphone buttons, " +
                    "the lock screen and Android Auto work too.",
            )
        } else {
            Feature(
                Icons.Rounded.DownloadForOffline,
                "Take it offline",
                "Download albums, playlists or your whole library, and they play without a connection. " +
                    "Plays made offline reach your server later.",
            )
        },
        Feature(
            Icons.Rounded.Palette,
            "Make it yours",
            "Choose the shelves on your home page, colours taken from your artwork, light or dark. It's all in Settings.",
        ),
    )
    val pager = rememberPagerState { features.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == features.lastIndex

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = onDone) { Text("Skip", color = colors.ink2) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            FeaturePage(features[index])
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            features.indices.forEach { i ->
                val width by animateDpAsState(if (i == pager.currentPage) 22.dp else 8.dp, label = "dot")
                Box(
                    Modifier
                        .height(8.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) colors.accent else colors.ink4),
                )
            }
        }
        Pill(
            if (last) "Start listening" else "Next",
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            accent = true,
            onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun FeaturePage(feature: Feature) {
    val colors = Kultr.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(176.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(colors.accent.copy(alpha = 0.45f), colors.accent.copy(alpha = 0.04f))))
                // Just the lit rim of glass over the glow.
                .glass(CircleShape, tint = Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(feature.icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(76.dp))
        }
        Spacer(Modifier.height(36.dp))
        Text(feature.title, style = MaterialTheme.typography.headlineSmall, color = colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            feature.body,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}
