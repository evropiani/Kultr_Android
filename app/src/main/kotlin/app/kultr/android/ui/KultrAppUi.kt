package app.kultr.android.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.kultr.android.AppGraph
import app.kultr.android.data.MessageKind
import app.kultr.android.data.ServerProfile
import app.kultr.android.ui.components.AddToPlaylistDialog
import app.kultr.android.ui.components.ArtworkBackdropPlain
import app.kultr.android.ui.components.DragDropState
import app.kultr.android.ui.components.DropZoneOverlay
import app.kultr.android.ui.components.LocalDragDrop
import app.kultr.android.ui.components.LocalGlassBackdrop
import app.kultr.android.ui.components.RatingDialog
import app.kultr.android.ui.components.SleepTimerDialog
import app.kultr.android.ui.components.glassSource
import app.kultr.android.ui.components.rememberArtworkUrl
import app.kultr.android.ui.components.rememberGlassBackdrop
import app.kultr.android.ui.player.ArtworkBackdrop
import app.kultr.android.ui.player.MiniPlayer
import app.kultr.android.ui.player.NowPlayingScreen
import app.kultr.android.ui.screens.AlbumScreen
import app.kultr.android.ui.screens.ArtistScreen
import app.kultr.android.ui.screens.DownloadIndicator
import app.kultr.android.ui.screens.DownloadsScreen
import app.kultr.android.ui.screens.GenreScreen
import app.kultr.android.ui.screens.HomeScreen
import app.kultr.android.ui.screens.LibraryScreen
import app.kultr.android.ui.screens.LoginScreen
import app.kultr.android.ui.screens.PlaylistScreen
import app.kultr.android.ui.screens.SearchScreen
import app.kultr.android.ui.screens.SettingsScreen
import app.kultr.android.ui.screens.StatsScreen
import app.kultr.android.ui.screens.SyncScreen
import app.kultr.android.ui.theme.DEFAULT_ACCENT
import app.kultr.android.ui.theme.Kultr
import app.kultr.core.settings.AccentMode
import app.kultr.core.settings.Settings
import app.kultr.core.util.ArtworkColor
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME(Routes.HOME, "Home", Icons.Rounded.Home),
    LIBRARY(Routes.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
    SEARCH(Routes.SEARCH, "Search", Icons.Rounded.Search),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
}

/** The tabs in the floating bar; Search has its own round button beside it. */
private val BAR_TABS = listOf(Tab.HOME, Tab.LIBRARY, Tab.SETTINGS)
private val GLASS_TABS = BAR_TABS.map { GlassTab(it.label, it.icon) }

/** A [UiMessage][app.kultr.android.data.UiMessage] on its way to the snackbar. */
private class Message(override val message: String, val kind: MessageKind, long: Boolean) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = if (long) SnackbarDuration.Long else SnackbarDuration.Short
}

/** Where the sign-in screen should start when it is opened over the app. */
private data class LoginRequest(val url: String = "", val user: String = "")

/**
 * The accent the whole interface is tinted with: taken from the artwork of
 * what is playing (optionally blended with the chosen colour), or fixed.
 */
@Composable
fun rememberAccent(coverId: String?, settings: Settings): Color {
    val chosen = ArtworkColor.parseHex(settings.accent)?.let { Color(it) } ?: DEFAULT_ACCENT
    val context = LocalContext.current
    val url = rememberArtworkUrl(if (settings.accentMode == AccentMode.ARTWORK) coverId else null, 96)
    // produceState keeps the last colour while the next one is worked out, so it never flashes.
    val sampled by produceState<Int?>(null, url) {
        value = if (url == null) null else withContext(Dispatchers.Default) { sampleArtwork(context, url) }
    }
    val fromArt = sampled
    if (settings.accentMode == AccentMode.FIXED || fromArt == null) return chosen
    val blend = settings.accentBlend.coerceIn(0, 100)
    return if (blend == 0) Color(fromArt) else Color(ArtworkColor.mix(fromArt, chosen.toArgbInt(), blend / 100.0))
}

private fun Color.toArgbInt(): Int = ArtworkColor.rgb((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

private suspend fun sampleArtwork(context: Context, url: String): Int? = runCatching {
    val request = ImageRequest.Builder(context).data(url).size(48).allowHardware(false).build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    val bitmap = result.image.toBitmap()
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    ArtworkColor.dominant(pixels)
}.getOrNull()

/**
 * The root of the interface. Without a server it is the sign-in screen;
 * otherwise the tabs, the mini player and the full-screen player on top.
 */
@Composable
fun KultrAppUi(graph: AppGraph, openPlayerRequest: Int, openDownloadsRequest: Int = 0) {
    val active by graph.auth.active.collectAsStateWithLifecycle()
    val client by graph.auth.client.collectAsStateWithLifecycle()
    var login by remember { mutableStateOf<LoginRequest?>(null) }
    val profile = active

    when {
        profile == null -> LoginScreen(graph)
        client == null -> LoginScreen(
            graph,
            prefillUrl = profile.serverUrl,
            prefillUser = profile.username,
            onCancel = { graph.auth.signOut() },
        )
        else -> Box(Modifier.fillMaxSize()) {
            MainUi(
                graph = graph,
                openPlayerRequest = openPlayerRequest,
                openDownloadsRequest = openDownloadsRequest,
                onAddServer = { login = LoginRequest() },
                onSignIn = { p: ServerProfile -> login = LoginRequest(p.serverUrl, p.username) },
            )
            val request = login
            if (request != null) {
                BackHandler { login = null }
                LoginScreen(
                    graph,
                    prefillUrl = request.url,
                    prefillUser = request.user,
                    onCancel = { login = null },
                    onDone = { login = null },
                )
            }
        }
    }
}

@Composable
private fun MainUi(
    graph: AppGraph,
    openPlayerRequest: Int,
    openDownloadsRequest: Int,
    onAddServer: () -> Unit,
    onSignIn: (ServerProfile) -> Unit,
) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val dialogs = remember { Dialogs() }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val player by graph.player.state.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val actions = remember(nav) {
        AppActions(
            graph = graph,
            scope = scope,
            navigate = { route ->
                playerOpen = false
                nav.navigate(route) { launchSingleTop = true }
            },
            back = { nav.popBackStack() },
            openPlayer = { playerOpen = true },
            dialogs = dialogs,
        )
    }

    val dragDrop = remember(actions) { DragDropState { payload, action -> actions.drop(payload, action) } }

    LaunchedEffect(openPlayerRequest) {
        if (openPlayerRequest > 0) playerOpen = true
    }
    LaunchedEffect(openDownloadsRequest) {
        if (openDownloadsRequest > 0) actions.openDownloads()
    }
    LaunchedEffect(Unit) {
        graph.messages.messages.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(Message(message.text, message.kind, message.long))
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    var selectedTab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val currentTab = Tab.entries.firstOrNull { it.route == backStack?.destination?.route }
    LaunchedEffect(currentTab) { if (currentTab != null) selectedTab = currentTab }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val glass = rememberGlassBackdrop()
    var chromeHeight by remember { mutableIntStateOf(0) }
    val chromeInset = if (keyboardOpen) 0.dp else with(LocalDensity.current) { chromeHeight.toDp() }

    fun openTab(tab: Tab) {
        selectedTab = tab
        // A tab always opens at its own start page, dropping any album,
        // artist or other page opened inside it. Already there: nothing to do.
        if (backStack?.destination?.route == tab.route) return
        if (tab == Tab.HOME) {
            nav.popBackStack(Routes.HOME, inclusive = false)
        } else {
            nav.navigate(if (tab == Tab.LIBRARY) Routes.library() else tab.route) {
                popUpTo(nav.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    CompositionLocalProvider(
        LocalActions provides actions,
        LocalDragDrop provides dragDrop,
        LocalGlassBackdrop provides glass,
        LocalChromeInset provides chromeInset,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Everything the floating controls float over, recorded for their glass.
            Box(Modifier.fillMaxSize().glassSource(glass)) {
                if (settings.backdropArtwork && player.current != null) {
                    ArtworkBackdrop(player.current?.artworkId)
                } else {
                    ArtworkBackdropPlain()
                }
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxSize().imePadding(),
                    // Pages rise into place; the one left behind fades.
                    enterTransition = { fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 24 } },
                    exitTransition = { fadeOut(tween(120)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(140)) + slideOutVertically(tween(200)) { it / 24 } },
                ) {
                    composable(Routes.HOME) { HomeScreen() }
                    composable(
                        Routes.LIBRARY,
                        arguments = listOf(navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null }),
                    ) { entry ->
                        val tab = entry.arguments?.getString("tab")
                            ?.let { name -> LibraryTab.entries.firstOrNull { it.name == name } }
                            ?: LibraryTab.ALBUMS
                        LibraryScreen(tab)
                    }
                    composable(Routes.SEARCH) { SearchScreen() }
                    composable(Routes.SETTINGS) { SettingsScreen(onAddServer = onAddServer, onSignIn = onSignIn) }
                    composable(Routes.ALBUM) { AlbumScreen(it.arguments?.getString("id").orEmpty()) }
                    composable(Routes.ARTIST) { ArtistScreen(it.arguments?.getString("id").orEmpty()) }
                    composable(Routes.PLAYLIST) { PlaylistScreen(it.arguments?.getString("id").orEmpty()) }
                    composable(Routes.GENRE) { GenreScreen(it.arguments?.getString("name").orEmpty()) }
                    composable(Routes.SYNC) { SyncScreen() }
                    composable(Routes.STATS) { StatsScreen() }
                    composable(
                        Routes.DOWNLOADS,
                        arguments = listOf(navArgument("page") { type = NavType.StringType; nullable = true; defaultValue = null }),
                    ) { entry ->
                        val page = entry.arguments?.getString("page")
                            ?.let { name -> DownloadsPage.entries.firstOrNull { it.name == name } }
                            ?: DownloadsPage.NOW
                        DownloadsScreen(page)
                    }
                }
            }

            if (!keyboardOpen) {
                // The floating controls: downloads, the mini player and the tab bar,
                // each a piece of glass over the page.
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { chromeHeight = it.height }
                        .navigationBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // The Downloads page shows all of this already.
                    if (backStack?.destination?.route != Routes.DOWNLOADS) {
                        DownloadIndicator(onOpen = { actions.openDownloads() })
                    }
                    MiniPlayer(player)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassTabBar(
                            tabs = GLASS_TABS,
                            selected = BAR_TABS.indexOf(selectedTab),
                            onSelect = { index -> openTab(BAR_TABS[index]) },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        GlassRoundButton(
                            icon = Tab.SEARCH.icon,
                            label = Tab.SEARCH.label,
                            selected = selectedTab == Tab.SEARCH,
                            onClick = { openTab(Tab.SEARCH) },
                        )
                    }
                }
            }

            SnackbarHost(
                snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .padding(bottom = if (keyboardOpen) 8.dp else chromeInset),
            ) { data ->
                val colors = Kultr.colors
                val kind = (data.visuals as? Message)?.kind ?: MessageKind.INFO
                Snackbar(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    containerColor = colors.elevated,
                    contentColor = when (kind) {
                        MessageKind.ERROR -> colors.danger
                        MessageKind.WARNING -> colors.warning
                        MessageKind.SUCCESS -> colors.ink
                        MessageKind.INFO -> colors.ink
                    },
                ) { Text(data.visuals.message) }
            }

            AnimatedVisibility(
                visible = playerOpen && player.current != null,
                enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
            ) {
                NowPlayingScreen(player, onClose = { playerOpen = false })
            }
            BackHandler(enabled = playerOpen && player.current != null) { playerOpen = false }

            DropZoneOverlay(dragDrop)
        }

        dialogs.addToPlaylist?.let { songs -> AddToPlaylistDialog(songs, onDismiss = { dialogs.addToPlaylist = null }) }
        dialogs.rate?.let { song -> RatingDialog(song, onDismiss = { dialogs.rate = null }) }
        if (dialogs.sleepTimer) SleepTimerDialog(onDismiss = { dialogs.sleepTimer = false })
    }
}
