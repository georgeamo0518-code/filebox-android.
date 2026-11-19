// Maps token roles onto Material 3's ColorScheme, plus extra roles M3 has no slot for.
package dev.yourname.filebox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class Category { Images, Video, Audio, Docs, Apk, Downloads, Archives }

@Immutable
data class CategoryColors(val icon: Color, val container: Color)

@Immutable
data class ExtendedColors(
    val textDisabled: Color,
    val success: Color,
    val warning: Color,
    val category: Map<Category, CategoryColors>,
)

private val LightScheme = with(LightTokens) {
    lightColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accentContainer, onPrimaryContainer = onAccentContainer,  // selected rows, active chips
        secondaryContainer = accentContainer, onSecondaryContainer = onAccentContainer, // nav drawer active item
        background = bg, onBackground = text,
        surface = bg, onSurface = text,
        surfaceVariant = surface, onSurfaceVariant = textMuted,
        surfaceContainerLowest = surfaceRaised,
        surfaceContainerLow = surfaceRaised,     // sheets, dialogs
        surfaceContainer = surface,              // bottom actions, category tiles
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
        outline = borderInput, outlineVariant = border,
        error = danger, onError = onDanger,
        scrim = scrim,
    )
}

private val DarkScheme = with(DarkTokens) {
    darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accentContainer, onPrimaryContainer = onAccentContainer,
        secondaryContainer = accentContainer, onSecondaryContainer = onAccentContainer,
        background = bg, onBackground = text,
        surface = bg, onSurface = text,
        surfaceVariant = surface, onSurfaceVariant = textMuted,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surfaceRaised,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        surfaceContainerHighest = surfaceRaised,
        outline = borderInput, outlineVariant = border,
        error = danger, onError = onDanger,
        scrim = scrim,
    )
}

private fun categories(vararg pairs: Pair<Color, Color>) =
    Category.entries.zip(pairs.toList()) { c, (icon, bg) -> c to CategoryColors(icon, bg) }.toMap()

private val LightExtended = with(LightTokens) {
    ExtendedColors(
        textDisabled, success, warning,
        categories(catImages, catVideo, catAudio, catDocs, catApk, catDownloads, catArchives),
    )
}

private val DarkExtended = with(DarkTokens) {
    ExtendedColors(
        textDisabled, success, warning,
        categories(catImages, catVideo, catAudio, catDocs, catApk, catDownloads, catArchives),
    )
}

private val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

enum class ThemeMode { System, Light, Dark }

@Composable
fun FileboxTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    // Dynamic (wallpaper) colour is deliberately off: it would bypass the checked palette.
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalExtendedColors provides if (dark) DarkExtended else LightExtended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = FileboxTypography,
            shapes = FileboxShapes,
            content = content,
        )
    }
}

object FileboxTheme {
    val extended: ExtendedColors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current
}
