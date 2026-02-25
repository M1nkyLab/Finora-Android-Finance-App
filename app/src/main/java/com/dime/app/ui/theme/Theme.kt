
package com.dime.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColorScheme(
    primary = Color(0xFF9B6FFF),
    background = Color(0xFF0D0D0F),
    surface = Color(0xFF19191E),
    onSurface = Color.White,
    onBackground = Color.White,
    secondary = Color(0xFF7A7A8C)
)

private val LightColorPalette = lightColorScheme(
    primary = Color(0xFF9B6FFF),
    background = Color(0xFFFDFDFD),
    surface = Color(0xFFF0F0F0),
    onSurface = Color.Black,
    onBackground = Color.Black,
    secondary = Color(0xFF5A5A6C)
)

@Composable
fun DimeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
