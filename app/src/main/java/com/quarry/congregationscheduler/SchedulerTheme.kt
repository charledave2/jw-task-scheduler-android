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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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

internal val SchedulerFontFamily = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_bold, FontWeight.Bold)
)
private fun TextStyle.withSchedulerFont() = copy(fontFamily = SchedulerFontFamily)

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
    bodySmall = BaseTypography.bodySmall.withSchedulerFont().copy(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = BaseTypography.labelLarge.withSchedulerFont(),
    labelMedium = BaseTypography.labelMedium.withSchedulerFont().copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = BaseTypography.labelSmall.withSchedulerFont().copy(fontSize = 12.sp, lineHeight = 16.sp)
)

internal data class SchedulerPaletteOption(val id: String, val label: String, val swatch: Color)
internal val SchedulerPaletteOptions = listOf(
    SchedulerPaletteOption("forest", "Forest", Color(0xFF5C745F)),
    SchedulerPaletteOption("ocean", "Ocean", Color(0xFF315F8C)),
    SchedulerPaletteOption("teal", "Teal", Color(0xFF006B5F)),
    SchedulerPaletteOption("amber", "Amber", Color(0xFF6D5300))
)

private data class PaletteAccents(
    val primary: Color, val onPrimary: Color, val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val onSecondary: Color, val secondaryContainer: Color, val onSecondaryContainer: Color,
    val tertiary: Color, val onTertiary: Color, val tertiaryContainer: Color, val onTertiaryContainer: Color
)

private fun paletteAccents(id: String, dark: Boolean) = when (id) {
    "ocean" -> if (dark) PaletteAccents(Color(0xFFA4C9F0), Color(0xFF17324B), Color(0xFF294B69), Color(0xFFD8EAFA), Color(0xFFB8CADB), Color(0xFF243746), Color(0xFF394F61), Color(0xFFD6E5F2), Color(0xFFC3C8E5), Color(0xFF2D3047), Color(0xFF41445D), Color(0xFFE1E0FA))
        else PaletteAccents(Color(0xFF315F8C), Color.White, Color(0xFFD4E6F7), Color(0xFF0D1D2F), Color(0xFF496981), Color.White, Color(0xFFD5E8F1), Color(0xFF10212B), Color(0xFF585D83), Color.White, Color(0xFFE2E0F5), Color(0xFF191A33))
    "teal" -> if (dark) PaletteAccents(Color(0xFF80D5C4), Color(0xFF00382F), Color(0xFF005144), Color(0xFF9CF2DF), Color(0xFFB0CCC4), Color(0xFF203732), Color(0xFF354C45), Color(0xFFD0E8DF), Color(0xFFAFCBCB), Color(0xFF1C3535), Color(0xFF324B4B), Color(0xFFCAE7E6))
        else PaletteAccents(Color(0xFF006B5F), Color.White, Color(0xFF9CF2DF), Color(0xFF00201B), Color(0xFF4B635C), Color.White, Color(0xFFD0E8DF), Color(0xFF0A211B), Color(0xFF456364), Color.White, Color(0xFFCAE7E6), Color(0xFF062020))
    "amber" -> if (dark) PaletteAccents(Color(0xFFE8C66D), Color(0xFF352900), Color(0xFF564310), Color(0xFFF8E6A9), Color(0xFFD1C49E), Color(0xFF352F1F), Color(0xFF4D442E), Color(0xFFEDE1C2), Color(0xFFE0C2A5), Color(0xFF3E2D1C), Color(0xFF58432E), Color(0xFFF9DEBF))
        else PaletteAccents(Color(0xFF6D5300), Color.White, Color(0xFFF2E5B7), Color(0xFF241A00), Color(0xFF625B43), Color.White, Color(0xFFEDE1C2), Color(0xFF1F1B0D), Color(0xFF76573A), Color.White, Color(0xFFF9DEBF), Color(0xFF2B1708))
    else -> if (dark) PaletteAccents(Color(0xFFA9C6A8), Color(0xFF172318), Color(0xFF34473A), Color(0xFFDCE8D7), Color(0xFFC3D0C0), Color(0xFF253328), Color(0xFF343F34), Color(0xFFDCE8D7), Color(0xFFC6D2B8), Color(0xFF263126), Color(0xFF384437), Color(0xFFDDE8D4))
        else PaletteAccents(Color(0xFF5C745F), Color.White, Color(0xFFDCE8D8), Color(0xFF1D3021), Color(0xFF627A67), Color.White, Color(0xFFEDF2EB), Color(0xFF253328), Color(0xFF77835D), Color.White, Color(0xFFE6EACF), Color(0xFF30391D))
}

@Composable
fun SchedulerTheme(dark: Boolean, palette: String = "forest", content: @Composable () -> Unit) {
    val accent = paletteAccents(palette, dark)
    val colors = if (dark) darkColorScheme(
        primary = accent.primary, onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer, onPrimaryContainer = accent.onPrimaryContainer,
        secondary = accent.secondary, onSecondary = accent.onSecondary,
        secondaryContainer = accent.secondaryContainer, onSecondaryContainer = accent.onSecondaryContainer,
        tertiary = accent.tertiary, onTertiary = accent.onTertiary,
        tertiaryContainer = accent.tertiaryContainer, onTertiaryContainer = accent.onTertiaryContainer,
        background = Color(0xFF151713), surface = Color(0xFF20231E), onSurface = Color(0xFFE8E9E1),
        surfaceDim = Color(0xFF151713), surfaceBright = Color(0xFF383C35),
        surfaceContainerLowest = Color(0xFF151713), surfaceContainerLow = Color(0xFF1B1E19),
        surfaceContainer = Color(0xFF292D27), surfaceContainerHigh = Color(0xFF32372F), surfaceContainerHighest = Color(0xFF3B4038),
        onSurfaceVariant = Color(0xFFB9BDB2), outline = Color(0xFF7A8276), outlineVariant = Color(0xFF3B4038),
        surfaceVariant = Color(0xFF292D27), surfaceTint = accent.primary,
        inverseSurface = Color(0xFFE8E9E1), inverseOnSurface = Color(0xFF20231E), inversePrimary = Color(0xFF5C745F)
    ) else lightColorScheme(
        primary = accent.primary, onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer, onPrimaryContainer = accent.onPrimaryContainer,
        secondary = accent.secondary, onSecondary = accent.onSecondary,
        secondaryContainer = accent.secondaryContainer, onSecondaryContainer = accent.onSecondaryContainer,
        tertiary = accent.tertiary, onTertiary = accent.onTertiary,
        tertiaryContainer = accent.tertiaryContainer, onTertiaryContainer = accent.onTertiaryContainer,
        background = Color(0xFFF6F6F3), surface = Color.White, onSurface = Color(0xFF252721),
        surfaceDim = Color(0xFFDADDD7), surfaceBright = Color.White,
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F9F5),
        surfaceContainer = Color(0xFFF0F2ED), surfaceContainerHigh = Color(0xFFE9ECE6), surfaceContainerHighest = Color(0xFFE1E5DE),
        outline = Color(0xFF73796F), outlineVariant = Color(0xFFE5E6DF),
        surfaceVariant = Color(0xFFF0F2ED), onSurfaceVariant = Color(0xFF686D64), surfaceTint = accent.primary,
        inverseSurface = Color(0xFF303B30), inverseOnSurface = Color(0xFFF6F6F3), inversePrimary = Color(0xFFA9C6A8)
    )
    MaterialTheme(colorScheme = colors, typography = SchedulerTypography, shapes = SchedulerShapes, content = content)
}
