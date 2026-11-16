package com.neighborly.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Brand {
    val Green = Color(0xFF22774A)
    val GreenDark = Color(0xFF1E4A33)
    val GreenContainer = Color(0xFFE3F1E8)
    val Background = Color(0xFFF0F2F5)
    val Card = Color(0xFFFFFFFF)
    val Text = Color(0xFF050505)
    val TextSecondary = Color(0xFF65676B)
    val Error = Color(0xFFD93025)
    val Accent = Color(0xFFE0663A)
    val Blue = Color(0xFF2F6DB5)
    val Amber = Color(0xFFB7791F)
    val Purple = Color(0xFF6B4FA0)
}

private val LightColors = lightColorScheme(
    primary = Brand.Green,
    onPrimary = Color.White,
    primaryContainer = Brand.GreenContainer,
    onPrimaryContainer = Brand.GreenDark,
    secondary = Brand.GreenDark,
    onSecondary = Color.White,
    secondaryContainer = Brand.GreenContainer,
    onSecondaryContainer = Brand.GreenDark,
    tertiary = Brand.Accent,
    background = Brand.Background,
    onBackground = Brand.Text,
    surface = Brand.Card,
    onSurface = Brand.Text,
    surfaceVariant = Brand.Background,
    onSurfaceVariant = Brand.TextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF7F8FA),
    surfaceContainerHighest = Brand.Background,
    outline = Color(0xFFCED0D4),
    outlineVariant = Color(0xFFE4E6EB),
    error = Brand.Error,
)

/** Dark scheme derived from the same greens. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FCB9E),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Brand.GreenDark,
    onPrimaryContainer = Color(0xFFBDEBCF),
    secondary = Color(0xFFA6D3B6),
    onSecondary = Color(0xFF0E3721),
    secondaryContainer = Color(0xFF254E37),
    onSecondaryContainer = Color(0xFFC2EFD2),
    tertiary = Color(0xFFFFB59A),
    background = Color(0xFF18191A),
    onBackground = Color(0xFFE4E6EB),
    surface = Color(0xFF242526),
    onSurface = Color(0xFFE4E6EB),
    surfaceVariant = Color(0xFF3A3B3C),
    onSurfaceVariant = Color(0xFFB0B3B8),
    surfaceContainerLowest = Color(0xFF242526),
    surfaceContainerLow = Color(0xFF242526),
    surfaceContainer = Color(0xFF242526),
    surfaceContainerHigh = Color(0xFF2E2F30),
    surfaceContainerHighest = Color(0xFF3A3B3C),
    outline = Color(0xFF5A5C5E),
    outlineVariant = Color(0xFF3E4042),
    error = Color(0xFFF28B82),
)

private val base = Typography()
private val Roboto = FontFamily.Default

val NeighborlyTypography = Typography(
    titleLarge = base.titleLarge.copy(fontFamily = Roboto, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = base.titleMedium.copy(fontFamily = Roboto, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = base.titleSmall.copy(fontFamily = Roboto, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = Roboto, fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = Roboto, fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = base.bodySmall.copy(fontFamily = Roboto, fontSize = 13.sp, lineHeight = 17.sp),
    labelLarge = base.labelLarge.copy(fontFamily = Roboto, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = base.labelMedium.copy(fontFamily = Roboto, fontSize = 13.sp),
    labelSmall = base.labelSmall.copy(fontFamily = Roboto, fontSize = 12.sp),
)

val NeighborlyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Immutable
data class ExtraColors(
    val bubble: Color,
    val accent: Color,
    val secondaryText: Color,
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(bubble = Brand.Background, accent = Brand.Accent, secondaryText = Brand.TextSecondary)
}

object NbTextStyles {
    val name = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val meta = TextStyle(fontSize = 13.sp)
    val post = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
}

@Composable
fun NeighborlyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val extra = if (darkTheme) {
        ExtraColors(bubble = Color(0xFF3A3B3C), accent = Color(0xFFFFB59A), secondaryText = Color(0xFFB0B3B8))
    } else {
        ExtraColors(bubble = Brand.Background, accent = Brand.Accent, secondaryText = Brand.TextSecondary)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = NeighborlyTypography,
            shapes = NeighborlyShapes,
            content = content,
        )
    }
}
