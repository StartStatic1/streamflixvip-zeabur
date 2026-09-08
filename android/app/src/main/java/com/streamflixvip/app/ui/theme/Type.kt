package com.streamflixvip.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.googlefonts.R as GoogleFontsR

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = GoogleFontsR.array.com_google_android_gms_fonts_certs,
)

private val manrope = GoogleFont("Manrope")

val StreamFlixFontFamily = FontFamily(
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.ExtraBold),
)

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
