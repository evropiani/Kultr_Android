package app.kultr.android.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kultr.core.settings.CornerStyle
import app.kultr.core.settings.Settings
import app.kultr.core.settings.SurfaceBorder
import app.kultr.core.settings.ThemeMode

/**
 * Kultr's palette is deliberately almost colourless. Colour arrives at
 * runtime: the accent is taken from the artwork of whatever is playing, and
 * every glass surface and border picks it up, so the interface takes on the
 * mood of the music.
 */
@Immutable
data class KultrColors(
    val dark: Boolean,
    val accent: Color,
    val onAccent: Color,
    val background: Color,
    val elevated: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val glass: Color,
    val glassStrong: Color,
    val edge: Color,
    val line: Color,
    val danger: Color = Color(0xFFFF5F57),
    val warning: Color = Color(0xFFFFBD2E),
    val success: Color = Color(0xFF45D67A),
) {
    val accentSoft: Color get() = accent.copy(alpha = 0.18f)
    val accentGlow: Color get() = accent.copy(alpha = 0.35f)
}

@Immutable
data class KultrRadii(val xs: Dp, val sm: Dp, val md: Dp, val lg: Dp, val xl: Dp)

val LocalKultrColors = staticCompositionLocalOf {
    kultrColors(dark = true, accent = DEFAULT_ACCENT, settings = Settings())
}
val LocalRadii = staticCompositionLocalOf { radiiFor(CornerStyle.SOFT) }

/** The Reduce motion setting: sliding highlights and reordering snap instead of moving. */
val LocalReduceMotion = staticCompositionLocalOf { false }

val DEFAULT_ACCENT = Color(0xFF7C8CFF)

fun radiiFor(style: CornerStyle): KultrRadii = when (style) {
    CornerStyle.SHARP -> KultrRadii(3.dp, 4.dp, 6.dp, 8.dp, 10.dp)
    CornerStyle.SOFT -> KultrRadii(8.dp, 12.dp, 18.dp, 26.dp, 34.dp)
    CornerStyle.ROUND -> KultrRadii(12.dp, 18.dp, 26.dp, 34.dp, 44.dp)
}

fun kultrColors(dark: Boolean, accent: Color, settings: Settings): KultrColors {
    val scale = settings.surfaceOpacity.coerceIn(0, 200) / 100f
    val edgeBase = if (settings.surfaceBorder == SurfaceBorder.ACCENT) accent else if (dark) Color.White else Color(0xFF0A0A10)
    val edgeAlpha = (settings.borderOpacity.coerceIn(0, 100) / 100f) * if (dark) 0.8f else 0.6f
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF0A0A10) else Color.White
    return if (dark) {
        KultrColors(
            dark = true,
            accent = accent,
            onAccent = onAccent,
            background = Color(0xFF08080C),
            elevated = Color(0xFF101018),
            ink = Color.White.copy(alpha = 0.96f),
            ink2 = Color.White.copy(alpha = 0.66f),
            ink3 = Color.White.copy(alpha = 0.42f),
            ink4 = Color.White.copy(alpha = 0.22f),
            glass = Color.White.copy(alpha = (0.07f * scale).coerceAtMost(1f)),
            glassStrong = Color.White.copy(alpha = (0.12f * scale).coerceAtMost(1f)),
            edge = edgeBase.copy(alpha = edgeAlpha),
            line = Color.White.copy(alpha = 0.09f),
        )
    } else {
        KultrColors(
            dark = false,
            accent = accent,
            onAccent = onAccent,
            background = Color(0xFFECEEF4),
            elevated = Color(0xFFF7F8FC),
            ink = Color(0xFF0A0A10).copy(alpha = 0.94f),
            ink2 = Color(0xFF0A0A10).copy(alpha = 0.62f),
            ink3 = Color(0xFF0A0A10).copy(alpha = 0.42f),
            ink4 = Color(0xFF0A0A10).copy(alpha = 0.2f),
            glass = Color.White.copy(alpha = (0.55f * scale).coerceAtMost(1f)),
            glassStrong = Color.White.copy(alpha = (0.72f * scale).coerceAtMost(1f)),
            edge = edgeBase.copy(alpha = edgeAlpha),
            line = Color(0xFF0A0A10).copy(alpha = 0.08f),
        )
    }
}

private val KultrTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Small caps-ish label used above headings ("ALBUM", "PLAYLIST"). */
val EyebrowStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)

@Composable
fun isDark(settings: Settings): Boolean = when (settings.theme) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun KultrTheme(settings: Settings, accent: Color, content: @Composable () -> Unit) {
    val dark = isDark(settings)
    val animatedAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(if (settings.reduceMotion) 0 else 900),
        label = "accent",
    )
    val colors = kultrColors(dark, animatedAccent, settings)
    val radii = radiiFor(settings.corners)
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accent.copy(alpha = 0.25f),
            onPrimaryContainer = colors.ink,
            secondary = colors.accent,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.accent.copy(alpha = 0.22f),
            onSecondaryContainer = colors.ink,
            tertiary = colors.accent,
            background = colors.background,
            onBackground = colors.ink,
            surface = colors.background,
            onSurface = colors.ink,
            surfaceVariant = colors.glassStrong,
            onSurfaceVariant = colors.ink2,
            surfaceContainer = colors.elevated,
            surfaceContainerHigh = Color(0xFF16161F),
            surfaceContainerHighest = Color(0xFF1C1C26),
            surfaceContainerLow = Color(0xFF0C0C12),
            surfaceContainerLowest = colors.background,
            outline = colors.edge,
            outlineVariant = colors.line,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accent.copy(alpha = 0.2f),
            onPrimaryContainer = colors.ink,
            secondary = colors.accent,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.accent.copy(alpha = 0.18f),
            onSecondaryContainer = colors.ink,
            tertiary = colors.accent,
            background = colors.background,
            onBackground = colors.ink,
            surface = colors.background,
            onSurface = colors.ink,
            surfaceVariant = Color.White,
            onSurfaceVariant = colors.ink2,
            surfaceContainer = colors.elevated,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color.White,
            surfaceContainerLow = colors.elevated,
            surfaceContainerLowest = Color.White,
            outline = colors.edge,
            outlineVariant = colors.line,
            error = colors.danger,
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = KultrTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(radii.xs),
            small = RoundedCornerShape(radii.sm),
            medium = RoundedCornerShape(radii.md),
            large = RoundedCornerShape(radii.lg),
            extraLarge = RoundedCornerShape(radii.xl),
        ),
    ) {
        CompositionLocalProvider(
            LocalKultrColors provides colors,
            LocalRadii provides radii,
            LocalReduceMotion provides settings.reduceMotion,
            content = content,
        )
    }
}

object Kultr {
    val colors: KultrColors
        @Composable get() = LocalKultrColors.current
    val radii: KultrRadii
        @Composable get() = LocalRadii.current
}
