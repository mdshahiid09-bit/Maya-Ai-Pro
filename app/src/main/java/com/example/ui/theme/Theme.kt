package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val JarvisColorScheme = darkColorScheme(
    primary = JarvisNeonCyan,
    onPrimary = Color.Black,
    primaryContainer = JarvisDeepBlue,
    onPrimaryContainer = JarvisNeonCyan,
    secondary = JarvisNeonBlue,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF003366),
    onSecondaryContainer = Color.White,
    background = AmoledBlack,
    onBackground = Color.White,
    surface = DarkSurfaceJarvis,
    onSurface = Color.White,
    surfaceVariant = CardBackgroundJarvis,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorderJarvis,
    error = StatusError
)

val GirlfriendColorScheme = darkColorScheme(
    primary = GirlfriendRose,
    onPrimary = Color.White,
    primaryContainer = GirlfriendDeepRose,
    onPrimaryContainer = GirlfriendPink,
    secondary = GirlfriendPink,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4A1528),
    onSecondaryContainer = Color.White,
    background = AmoledBlack,
    onBackground = Color.White,
    surface = DarkSurfaceGirlfriend,
    onSurface = Color.White,
    surfaceVariant = CardBackgroundGirlfriend,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorderGirlfriend,
    error = StatusError
)

@Composable
fun MayaTheme(
    isGirlfriendMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isGirlfriendMode) GirlfriendColorScheme else JarvisColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
