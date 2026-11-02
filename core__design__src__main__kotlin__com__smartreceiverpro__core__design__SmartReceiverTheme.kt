package com.smartreceiverpro.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

// Premium Dark Palette per Section 17:
// Charcoal Black background, Dark Slate surfaces, Electric Blue primary accent
val CharcoalBlack = Color(0xFF0A0D12)
val DarkSurface = Color(0xFF131822)
val ElevatedDarkSurface = Color(0xFF1C2433)
val ElectricBlue = Color(0xFF00B8FF)
val DeepElectricBlue = Color(0xFF0077FF)
val EmeraldOnline = Color(0xFF00E676)
val AmberWarning = Color(0xFFFFB300)
val CrimsonDanger = Color(0xFFFF3D57)
val TextPrimaryDark = Color(0xFFF3F6FA)
val TextSecondaryDark = Color(0xFF9BA7B8)

private val PremiumDarkScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color(0xFF001E2F),
    primaryContainer = Color(0xFF003550),
    onPrimaryContainer = Color(0xFFB8EAFF),
    secondary = DeepElectricBlue,
    onSecondary = Color.White,
    background = CharcoalBlack,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ElevatedDarkSurface,
    onSurfaceVariant = TextSecondaryDark,
    error = CrimsonDanger,
    onError = Color.White
)

private val OptionalLightScheme = lightColorScheme(
    primary = Color(0xFF006699),
    onPrimary = Color.White,
    background = Color(0xFFF6F8FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EEF5)
)

val SmartReceiverTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )
)

@Composable
fun SmartReceiverProTheme(
    darkTheme: Boolean = true,
    languageCode: String = "ar",
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PremiumDarkScheme else OptionalLightScheme
    val layoutDirection = if (languageCode.lowercase().startsWith("ar")) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SmartReceiverTypography,
            content = content
        )
    }
}
