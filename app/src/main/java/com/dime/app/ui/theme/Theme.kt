
package com.dime.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Semantic palette constants ────────────────────────────────────────────────
// Used as fallback / reference; screens define their own local copies.
val IncomeGreen  = Color(0xFF34D399)
val ExpenseRed   = Color(0xFFFF5C5C)
val AccentPurple = Color(0xFF9B6FFF)

// ── Dark (OLED-first, premium fintech) ───────────────────────────────────────
private val DarkColorPalette = darkColorScheme(
    primary          = Color(0xFFF0F0F5),   // Near-white — primary text, icons, FAB
    onPrimary        = Color(0xFF0A0A0F),   // Ink on primary surfaces
    primaryContainer = Color(0xFF1C1C26),   // Elevated card BG
    onPrimaryContainer = Color(0xFFD0D0E0),

    secondary        = Color(0xFF7A7A8C),   // Muted subtitle / secondary text
    onSecondary      = Color(0xFF0A0A0F),
    secondaryContainer = Color(0xFF17171E),
    onSecondaryContainer = Color(0xFFB0B0C0),

    tertiary         = AccentPurple,
    onTertiary       = Color(0xFFF0F0F5),

    background       = Color(0xFF000000),   // True OLED black
    onBackground     = Color(0xFFF0F0F5),

    surface          = Color(0xFF0F0F14),   // Card / bottom-sheet surface
    onSurface        = Color(0xFFE8E8F0),
    surfaceVariant   = Color(0xFF1A1A24),   // Chip / toggle BG
    onSurfaceVariant = Color(0xFF7A7A8C),

    outline          = Color(0xFF2A2A38),   // Dividers, borders
    outlineVariant   = Color(0xFF1E1E2A),

    error            = ExpenseRed,
    onError          = Color(0xFFF0F0F5),
    errorContainer   = ExpenseRed.copy(alpha = 0.15f),
    onErrorContainer = ExpenseRed,

    scrim            = Color(0x99000000),
    inverseSurface   = Color(0xFFF0F0F5),
    inverseOnSurface = Color(0xFF0A0A0F),
    inversePrimary   = Color(0xFF0A0A0F),
)

// ── Light (clean, minimal, high-contrast) ────────────────────────────────────
private val LightColorPalette = lightColorScheme(
    primary          = Color(0xFF0A0A0F),   // Near-black
    onPrimary        = Color(0xFFF8F8FC),
    primaryContainer = Color(0xFFEEEEF6),
    onPrimaryContainer = Color(0xFF1A1A26),

    secondary        = Color(0xFF60607A),
    onSecondary      = Color(0xFFF8F8FC),
    secondaryContainer = Color(0xFFF0F0F8),
    onSecondaryContainer = Color(0xFF40405A),

    tertiary         = Color(0xFF7060CC),
    onTertiary       = Color(0xFFF8F8FC),

    background       = Color(0xFFF5F5FA),
    onBackground     = Color(0xFF0A0A0F),

    surface          = Color(0xFFFFFFFF),
    onSurface        = Color(0xFF0A0A0F),
    surfaceVariant   = Color(0xFFEAEAF2),
    onSurfaceVariant = Color(0xFF60607A),

    outline          = Color(0xFFD0D0DC),
    outlineVariant   = Color(0xFFE0E0EC),

    error            = Color(0xFFD32F2F),
    onError          = Color(0xFFF8F8FC),
    errorContainer   = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF800000),

    scrim            = Color(0x66000000),
    inverseSurface   = Color(0xFF1A1A26),
    inverseOnSurface = Color(0xFFF0F0F8),
    inversePrimary   = Color(0xFFF0F0F5),
)

@Composable
fun DimeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColorPalette else LightColorPalette
    MaterialTheme(
        colorScheme = colors,
        content     = content
    )
}
