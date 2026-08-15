package com.chayu.volumecontrol

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF415F91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF565F71),
    surface = Color(0xFFF9F9FF),
    surfaceContainerHighest = Color(0xFFE0E2EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF284777),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC7DC),
    surface = Color(0xFF111318),
    surfaceContainerHighest = Color(0xFF44474F),
)

// Bundled Google Sans Flex keeps Material You-style Latin characters consistent
// on every device; Android supplies the fallback glyphs for Chinese.
private val GoogleSans = FontFamily(Font(R.font.google_sans_flex))
private val GoogleSansText = GoogleSans

private val VolumeTypography = androidx.compose.material3.Typography(
    displayLarge = androidx.compose.material3.Typography().displayLarge.copy(fontFamily = GoogleSans),
    titleLarge = androidx.compose.material3.Typography().titleLarge.copy(fontFamily = GoogleSans),
    titleMedium = androidx.compose.material3.Typography().titleMedium.copy(fontFamily = GoogleSans),
    bodyLarge = androidx.compose.material3.Typography().bodyLarge.copy(fontFamily = GoogleSansText),
    bodyMedium = androidx.compose.material3.Typography().bodyMedium.copy(fontFamily = GoogleSansText),
    labelLarge = androidx.compose.material3.Typography().labelLarge.copy(fontFamily = GoogleSansText),
    labelMedium = androidx.compose.material3.Typography().labelMedium.copy(fontFamily = GoogleSansText),
    labelSmall = androidx.compose.material3.Typography().labelSmall.copy(fontFamily = GoogleSansText),
)

@Composable
fun VolumeControlTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, typography = VolumeTypography, content = content)
}
