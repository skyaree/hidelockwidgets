package com.squeeare.hidelockwidgets.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    background = Color(0xFF121010),
    surface = Color(0xFF1D1A1B),
    surfaceContainer = Color(0xFF211E20),
    surfaceContainerHigh = Color(0xFF2A2527),
    surfaceContainerHighest = Color(0xFF332D30),
    primary = Color(0xFFF0D0D7),
    onPrimary = Color(0xFF40272E),
    primaryContainer = Color(0xFF5A3B44),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondaryContainer = Color(0xFF574A4E),
    onSecondaryContainer = Color(0xFFF4DEE4),
    outlineVariant = Color(0xFF5B5356)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5A4050),
    primaryContainer = Color(0xFFFFD8E5),
    secondaryContainer = Color(0xFFE8DDE2)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(42.dp)
)

private val AppTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun HideLockWidgetsTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx).copy(
            background = Color(0xFF121010),
            surface = Color(0xFF1D1A1B),
            surfaceContainer = Color(0xFF211E20),
            surfaceContainerHigh = Color(0xFF2A2527),
            surfaceContainerHighest = Color(0xFF332D30)
        )
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, typography = AppTypography, content = content)
}
