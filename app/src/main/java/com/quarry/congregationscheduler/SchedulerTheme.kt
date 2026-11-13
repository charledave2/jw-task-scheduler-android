package com.quarry.congregationscheduler

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Shared visual tokens for the app's forest-green Material theme. */
object SchedulerSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xLarge = 20.dp
    val xxLarge = 24.dp
    val cardPadding = 16.dp
    val rowMinimumHeight = 56.dp
    val controlHeight = 48.dp
    val iconSize = 20.dp
    val avatarSize = 40.dp
}

private val SchedulerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private fun TextStyle.withSchedulerFont() = copy(fontFamily = FontFamily.SansSerif)

private val BaseTypography = Typography()
private val SchedulerTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.withSchedulerFont(),
    displayMedium = BaseTypography.displayMedium.withSchedulerFont(),
    displaySmall = BaseTypography.displaySmall.withSchedulerFont(),
    headlineLarge = BaseTypography.headlineLarge.withSchedulerFont(),
    headlineMedium = BaseTypography.headlineMedium.withSchedulerFont(),
    headlineSmall = BaseTypography.headlineSmall.withSchedulerFont(),
    titleLarge = BaseTypography.titleLarge.withSchedulerFont(),
    titleMedium = BaseTypography.titleMedium.withSchedulerFont(),
    titleSmall = BaseTypography.titleSmall.withSchedulerFont(),
    bodyLarge = BaseTypography.bodyLarge.withSchedulerFont(),
    bodyMedium = BaseTypography.bodyMedium.withSchedulerFont(),
    bodySmall = BaseTypography.bodySmall.withSchedulerFont(),
    labelLarge = BaseTypography.labelLarge.withSchedulerFont(),
    labelMedium = BaseTypography.labelMedium.withSchedulerFont(),
    labelSmall = BaseTypography.labelSmall.withSchedulerFont()
)

@Composable
fun SchedulerTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFA9C6A8), onPrimary = Color(0xFF172318),
        primaryContainer = Color(0xFF34473A), onPrimaryContainer = Color(0xFFDCE8D7),
        secondary = Color(0xFFC3D0C0), onSecondary = Color(0xFF253328),
        background = Color(0xFF151713), surface = Color(0xFF20231E), onSurface = Color(0xFFE8E9E1),
        surfaceDim = Color(0xFF151713), surfaceBright = Color(0xFF383C35),
        surfaceContainerLowest = Color(0xFF151713), surfaceContainerLow = Color(0xFF1B1E19),
        surfaceContainer = Color(0xFF292D27), surfaceContainerHigh = Color(0xFF32372F), surfaceContainerHighest = Color(0xFF3B4038),
        onSurfaceVariant = Color(0xFFB9BDB2), outline = Color(0xFF7A8276), outlineVariant = Color(0xFF3B4038),
        secondaryContainer = Color(0xFF343F34), onSecondaryContainer = Color(0xFFDCE8D7),
        tertiary = Color(0xFFC6D2B8), onTertiary = Color(0xFF263126),
        tertiaryContainer = Color(0xFF384437), onTertiaryContainer = Color(0xFFDDE8D4),
        surfaceVariant = Color(0xFF292D27), surfaceTint = Color(0xFFA9C6A8),
        inverseSurface = Color(0xFFE8E9E1), inverseOnSurface = Color(0xFF20231E), inversePrimary = Color(0xFF5C745F)
    ) else lightColorScheme(
        primary = Color(0xFF5C745F), onPrimary = Color.White, background = Color(0xFFF6F6F3),
        primaryContainer = Color(0xFFDCE8D8), onPrimaryContainer = Color(0xFF1D3021),
        secondary = Color(0xFF627A67), onSecondary = Color.White,
        surface = Color.White, onSurface = Color(0xFF252721),
        surfaceDim = Color(0xFFDADDD7), surfaceBright = Color.White,
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F9F5),
        surfaceContainer = Color(0xFFF0F2ED), surfaceContainerHigh = Color(0xFFE9ECE6), surfaceContainerHighest = Color(0xFFE1E5DE),
        outline = Color(0xFF73796F), outlineVariant = Color(0xFFE5E6DF), secondaryContainer = Color(0xFFEDF2EB),
        onSecondaryContainer = Color(0xFF253328), tertiary = Color(0xFF77835D), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE6EACF), onTertiaryContainer = Color(0xFF30391D),
        surfaceVariant = Color(0xFFF0F2ED), onSurfaceVariant = Color(0xFF686D64), surfaceTint = Color(0xFF5C745F),
        inverseSurface = Color(0xFF303B30), inverseOnSurface = Color(0xFFF6F6F3), inversePrimary = Color(0xFFA9C6A8)
    )

    MaterialTheme(
        colorScheme = colors,
        typography = SchedulerTypography,
        shapes = SchedulerShapes,
        content = content
    )
}
