package dev.basiliskus.onda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = CatppuccinLatte.blue, onPrimary = CatppuccinLatte.base,
    secondary = CatppuccinLatte.mauve, onSecondary = CatppuccinLatte.base,
    tertiary = CatppuccinLatte.teal, onTertiary = CatppuccinLatte.base,
    error = CatppuccinLatte.red, onError = CatppuccinLatte.base,
    background = CatppuccinLatte.base, onBackground = CatppuccinLatte.text,
    surface = CatppuccinLatte.base, onSurface = CatppuccinLatte.text,
    surfaceVariant = CatppuccinLatte.surface0, onSurfaceVariant = CatppuccinLatte.subtext0,
    surfaceContainer = CatppuccinLatte.mantle, surfaceContainerHigh = CatppuccinLatte.surface0,
    outline = CatppuccinLatte.overlay0, outlineVariant = CatppuccinLatte.surface1,
)

private val DarkScheme = darkColorScheme(
    primary = CatppuccinFrappe.blue, onPrimary = CatppuccinFrappe.crust,
    secondary = CatppuccinFrappe.mauve, onSecondary = CatppuccinFrappe.crust,
    tertiary = CatppuccinFrappe.teal, onTertiary = CatppuccinFrappe.crust,
    error = CatppuccinFrappe.red, onError = CatppuccinFrappe.crust,
    background = CatppuccinFrappe.base, onBackground = CatppuccinFrappe.text,
    surface = CatppuccinFrappe.base, onSurface = CatppuccinFrappe.text,
    surfaceVariant = CatppuccinFrappe.surface0, onSurfaceVariant = CatppuccinFrappe.subtext0,
    surfaceContainer = CatppuccinFrappe.mantle, surfaceContainerHigh = CatppuccinFrappe.surface0,
    outline = CatppuccinFrappe.overlay0, outlineVariant = CatppuccinFrappe.surface1,
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
