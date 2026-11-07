package com.bacuflix.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object FlixColors {
    val BgPrimary = Color(0xFF08090D)
    val BgSecondary = Color(0xFF14151B)
    val BgCard = Color(0xFF181A22)
    val BgElevated = Color(0xFF1F222D)
    val Red = Color(0xFFE50920)
    val RedBright = Color(0xFFFF1F35)
    val CyanGlow = Color(0xFF06B6D4)
    val MatchGreen = Color(0xFF10B981)
    val AmberGold = Color(0xFFF59E0B)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA1A1AA)
    val TextMuted = Color(0xFF71717A)
    val Border = Color(0x1FFFFFFF)
}

private val DarkColorScheme = darkColorScheme(
    primary = FlixColors.Red,
    onPrimary = Color.White,
    secondary = FlixColors.RedBright,
    onSecondary = Color.White,
    background = FlixColors.BgPrimary,
    onBackground = FlixColors.TextPrimary,
    surface = FlixColors.BgSecondary,
    onSurface = FlixColors.TextPrimary,
    surfaceVariant = FlixColors.BgCard,
    onSurfaceVariant = FlixColors.TextSecondary
)

private val FlixTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = FlixColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = FlixColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = FlixColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = FlixColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = FlixColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = FlixColors.TextSecondary
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = FlixColors.TextPrimary
    )
)

@Composable
fun BACUFlixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = FlixTypography,
        content = content
    )
}
