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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kultr.core.settings.CornerStyle
import app.kultr.core.settings.Settings
import app.kultr.core.settings.SurfaceBorder
import app.kultr.core.settings.ThemeMode
import app.kultr.core.util.ArtworkColor

/**
 * Kultr's palette is deliberately almost colourless. Colour arrives at
 * runtime: the accent is taken from the artwork of whatever is playing, and
 * every glass surface and border picks it up, so the interface takes on the
 * mood of the music.
 */
@Immutable
data class KultrColors(
    val dark: Boolean,
    /** Night mode: dark, with pure black behind everything for OLED screens. */
    val night: Boolean = false,
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

    /** The fill of a button inside a page: sand, with an accent label. */
    val sand: Color get() = if (dark) ink.copy(alpha = 0.1f) else Color(0xFFEADFCC)
}

@Immutable
data class KultrRadii(val xs: Dp, val sm: Dp, val md: Dp, val lg: Dp, val xl: Dp)

val LocalKultrColors = staticCompositionLocalOf {
    kultrColors(dark = true, accent = DEFAULT_ACCENT, settings = Settings())
}
val LocalRadii = staticCompositionLocalOf { radiiFor(CornerStyle.SOFT) }

/** The Reduce motion setting: sliding highlights and reordering snap instead of moving. */
val LocalReduceMotion = staticCompositionLocalOf { false }

val DEFAULT_ACCENT = Color(0xFFFF7A3D)

/**
 * Material's outline: the rim and knob of a switch that is off, the edge of a
 * text field. It stays visible whatever Border opacity is set to, which is
 * about the glass panels; at 0% an off switch would otherwise be a blank pill.
 */
private val CONTROL_OUTLINE_DARK = Color(0xFFF3EDE2).copy(alpha = 0.55f)
private val CONTROL_OUTLINE_LIGHT = Color(0xFF1F1A14).copy(alpha = 0.5f)

/** A pale colour from the artwork washes out on the light background; deepen it just enough to read. */
private fun readableOnLight(color: Color): Color {
    val argb = color.toArgb()
    val readable = ArtworkColor.readableOnLight(argb)
    return if (readable == argb) color else Color(readable)
}

fun radiiFor(style: CornerStyle): KultrRadii = when (style) {
    CornerStyle.SHARP -> KultrRadii(3.dp, 4.dp, 6.dp, 8.dp, 10.dp)
    CornerStyle.SOFT -> KultrRadii(8.dp, 12.dp, 18.dp, 26.dp, 34.dp)
    CornerStyle.ROUND -> KultrRadii(12.dp, 18.dp, 26.dp, 34.dp, 44.dp)
}

/**
 * Kultr's palette: cream pages and warm black text in light mode, warm
 * charcoal in dark, and one orange for whatever you can tap. Colour is flat
 * everywhere; the album cover is the only colourful thing on screen. With
 * "Colour from artwork" on, the accent comes from what is playing instead.
 */
fun kultrColors(dark: Boolean, accent: Color, settings: Settings): KultrColors {
    val scale = settings.surfaceOpacity.coerceIn(0, 200) / 100f
    val ink = if (dark) Color(0xFFF3EDE2) else Color(0xFF1F1A14)
    val edgeBase = if (settings.surfaceBorder == SurfaceBorder.ACCENT) accent else ink
    val edgeAlpha = (settings.borderOpacity.coerceIn(0, 100) / 100f) * if (dark) 0.5f else 0.35f
    // Whichever of white and ink reads better on the accent.
    val onAccent = if (accent.luminance() > 0.2f) Color(0xFF1F1A14) else Color.White
    return if (dark) {
        // Pure black lets an OLED screen switch those pixels off altogether.
        val night = settings.nightMode
        KultrColors(
            dark = true,
            night = night,
            accent = accent,
            onAccent = onAccent,
            background = if (night) Color.Black else Color(0xFF1C1A17),
            elevated = if (night) Color(0xFF0E0D0B) else Color(0xFF26231F),
            ink = ink,
            ink2 = ink.copy(alpha = 0.7f),
            ink3 = ink.copy(alpha = 0.5f),
            ink4 = ink.copy(alpha = 0.26f),
            glass = ink.copy(alpha = (0.07f * scale).coerceAtMost(1f)),
            glassStrong = ink.copy(alpha = (0.12f * scale).coerceAtMost(1f)),
            edge = edgeBase.copy(alpha = edgeAlpha),
            line = ink.copy(alpha = 0.1f),
        )
    } else {
        val paper = Color(0xFFFFFCF6)
        KultrColors(
            dark = false,
            accent = accent,
            onAccent = onAccent,
            background = Color(0xFFF6F0E5),
            elevated = paper,
            ink = ink,
            ink2 = Color(0xFF5A5046),
            ink3 = Color(0xFF6E6255),
            ink4 = Color(0xFFA89C8D),
            glass = paper.copy(alpha = (0.8f * scale).coerceAtMost(1f)),
            glassStrong = paper,
            edge = edgeBase.copy(alpha = edgeAlpha),
            line = ink.copy(alpha = 0.12f),
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
    val colors = kultrColors(dark, if (dark) animatedAccent else readableOnLight(animatedAccent), settings)
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
            surfaceContainerHigh = if (colors.night) Color(0xFF15130F) else Color(0xFF2E2A25),
            surfaceContainerHighest = if (colors.night) Color(0xFF1C1A16) else Color(0xFF35302A),
            surfaceContainerLow = if (colors.night) Color(0xFF070605) else Color(0xFF201E1A),
            surfaceContainerLowest = colors.background,
            outline = CONTROL_OUTLINE_DARK,
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
            surfaceVariant = colors.elevated,
            onSurfaceVariant = colors.ink2,
            surfaceContainer = colors.elevated,
            surfaceContainerHigh = colors.elevated,
            surfaceContainerHighest = colors.elevated,
            surfaceContainerLow = colors.elevated,
            surfaceContainerLowest = colors.elevated,
            outline = CONTROL_OUTLINE_LIGHT,
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
