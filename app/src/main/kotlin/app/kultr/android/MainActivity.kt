package app.kultr.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kultr.android.ui.KultrAppUi
import app.kultr.android.ui.rememberAccent
import app.kultr.android.ui.theme.KultrTheme
import app.kultr.android.ui.theme.isDark

class MainActivity : ComponentActivity() {
    /** Bumped whenever something (the media notification) asks for the full player. */
    private var openPlayerRequest by mutableIntStateOf(0)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = KultrApp.graph
        if (savedInstanceState == null) handleIntent(intent)
        askForNotifications()

        setContent {
            val settings by graph.settings.settings.collectAsStateWithLifecycle()
            val player by graph.player.state.collectAsStateWithLifecycle()
            val accent = rememberAccent(player.current?.artworkId, settings)
            val dark = isDark(settings)
            LaunchedEffect(dark) {
                val transparent = Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            KultrTheme(settings, accent) {
                KultrAppUi(graph, openPlayerRequest)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        KultrApp.graph.player.connect()
    }

    override fun onStop() {
        super.onStop()
        KultrApp.graph.player.disconnect()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) openPlayerRequest++
    }

    /** Download progress needs notifications; ask once, on first launch. */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("kultr.ui", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return
        prefs.edit { putBoolean(KEY_ASKED_NOTIFICATIONS, true) }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "kultr.open_player"
        private const val KEY_ASKED_NOTIFICATIONS = "askedNotifications"
    }
}
