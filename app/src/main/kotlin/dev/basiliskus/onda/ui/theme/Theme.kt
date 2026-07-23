package dev.basiliskus.onda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = CatppuccinLatte.blue, onPrimary = CatppuccinLatte.base,
    primaryContainer = CatppuccinLatte.surface1, onPrimaryContainer = CatppuccinLatte.blue,
    secondary = CatppuccinLatte.mauve, onSecondary = CatppuccinLatte.base,
    secondaryContainer = CatppuccinLatte.surface1, onSecondaryContainer = CatppuccinLatte.blue,
    tertiary = CatppuccinLatte.teal, onTertiary = CatppuccinLatte.base,
    tertiaryContainer = CatppuccinLatte.surface1, onTertiaryContainer = CatppuccinLatte.teal,
    error = CatppuccinLatte.red, onError = CatppuccinLatte.base,
    errorContainer = CatppuccinLatte.surface1, onErrorContainer = CatppuccinLatte.red,
    background = CatppuccinLatte.base, onBackground = CatppuccinLatte.text,
    surface = CatppuccinLatte.base, onSurface = CatppuccinLatte.text,
    surfaceVariant = CatppuccinLatte.surface0, onSurfaceVariant = CatppuccinLatte.subtext0,
    surfaceContainerLowest = CatppuccinLatte.crust, surfaceContainerLow = CatppuccinLatte.mantle,
    surfaceContainer = CatppuccinLatte.mantle, surfaceContainerHigh = CatppuccinLatte.surface0,
    surfaceContainerHighest = CatppuccinLatte.surface1,
    outline = CatppuccinLatte.overlay0, outlineVariant = CatppuccinLatte.surface1,
    inverseSurface = CatppuccinLatte.text, inverseOnSurface = CatppuccinLatte.base,
    inversePrimary = CatppuccinFrappe.blue,
    surfaceTint = CatppuccinLatte.blue,
)

private val DarkScheme = darkColorScheme(
    primary = CatppuccinFrappe.blue, onPrimary = CatppuccinFrappe.crust,
    primaryContainer = CatppuccinFrappe.surface1, onPrimaryContainer = CatppuccinFrappe.blue,
    secondary = CatppuccinFrappe.mauve, onSecondary = CatppuccinFrappe.crust,
    secondaryContainer = CatppuccinFrappe.surface1, onSecondaryContainer = CatppuccinFrappe.blue,
    tertiary = CatppuccinFrappe.teal, onTertiary = CatppuccinFrappe.crust,
    tertiaryContainer = CatppuccinFrappe.surface1, onTertiaryContainer = CatppuccinFrappe.teal,
    error = CatppuccinFrappe.red, onError = CatppuccinFrappe.crust,
    errorContainer = CatppuccinFrappe.surface1, onErrorContainer = CatppuccinFrappe.red,
    background = CatppuccinFrappe.base, onBackground = CatppuccinFrappe.text,
    surface = CatppuccinFrappe.base, onSurface = CatppuccinFrappe.text,
    surfaceVariant = CatppuccinFrappe.surface0, onSurfaceVariant = CatppuccinFrappe.subtext0,
    surfaceContainerLowest = CatppuccinFrappe.crust, surfaceContainerLow = CatppuccinFrappe.mantle,
    surfaceContainer = CatppuccinFrappe.mantle, surfaceContainerHigh = CatppuccinFrappe.surface0,
    surfaceContainerHighest = CatppuccinFrappe.surface1,
    outline = CatppuccinFrappe.overlay0, outlineVariant = CatppuccinFrappe.surface1,
    inverseSurface = CatppuccinFrappe.text, inverseOnSurface = CatppuccinFrappe.base,
    inversePrimary = CatppuccinLatte.blue,
    surfaceTint = CatppuccinFrappe.blue,
)

@Composable
fun OndaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}

@Composable
fun accentFor(name: String): Color {
    val dark = isSystemInDarkTheme()
    val accents = if (dark) listOf(
        CatppuccinFrappe.blue, CatppuccinFrappe.mauve, CatppuccinFrappe.teal, CatppuccinFrappe.peach,
        CatppuccinFrappe.green, CatppuccinFrappe.red, CatppuccinFrappe.yellow, CatppuccinFrappe.pink,
        CatppuccinFrappe.sapphire, CatppuccinFrappe.lavender,
    ) else listOf(
        CatppuccinLatte.blue, CatppuccinLatte.mauve, CatppuccinLatte.teal, CatppuccinLatte.peach,
        CatppuccinLatte.green, CatppuccinLatte.red, CatppuccinLatte.yellow, CatppuccinLatte.pink,
        CatppuccinLatte.sapphire, CatppuccinLatte.lavender,
    )
    return accents[Math.floorMod(name.hashCode(), accents.size)]
}
