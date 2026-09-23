package app.kultr.android.ui.player

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.cast.MediaRouteButton
import app.kultr.android.ui.theme.Kultr

/**
 * "Play on another device": pick a Chromecast or speaker group. It only
 * appears once Cast is available on this phone; while connected it lights
 * up, and tapping it again offers to stop casting.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier, tint: Color = Kultr.colors.ink2) {
    CompositionLocalProvider(LocalContentColor provides tint) {
        MediaRouteButton(modifier)
    }
}
