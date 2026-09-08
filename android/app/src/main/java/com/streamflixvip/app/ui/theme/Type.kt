package com.streamflixvip.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografia Cinema Noir.
 * Sans do sistema — evita Google Fonts (R.array) que quebrava o APK.
 */
val StreamFlixFontFamily = FontFamily.SansSerif

val StreamFlixTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = StreamFlixFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        color = StreamFlixColors.Text,
    ),
    headlineLarge = TextStyle(
        fontFamily = StreamFlixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        color = StreamFlixColors.Text,
    ),
    titleLarge = TextStyle(
        fontFamily = StreamFlixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        color = StreamFlixColors.Text,
    ),
    titleMedium = TextStyle(
        fontFamily = StreamFlixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = StreamFlixColors.Text,
    ),
    bodyLarge = TextStyle(
        fontFamily = StreamFlixFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        color = StreamFlixColors.Text,
    ),
    bodyMedium = TextStyle(
        fontFamily = StreamFlixFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = StreamFlixColors.TextMuted,
    ),
    labelLarge = TextStyle(
        fontFamily = StreamFlixFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = StreamFlixColors.Text,
    ),
    labelMedium = TextStyle(
        fontFamily = StreamFlixFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = StreamFlixColors.TextMuted,
    ),
)
