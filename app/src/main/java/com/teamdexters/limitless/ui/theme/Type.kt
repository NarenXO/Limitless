package com.teamdexters.limitless.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp

import com.teamdexters.limitless.R

// Manrope font configuration
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val manropeFont = GoogleFont("Manrope")

val manropeFontFamily = FontFamily(
    Font(googleFont = manropeFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = manropeFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = manropeFont, fontProvider = provider, weight = FontWeight.Bold)
)

// Typography Styles using TextPrimary (0xFF1F1F1F) as default text color
val LimitlessTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        color = TextPrimary
    ),
    headlineSmall = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    titleSmall = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    bodySmall = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextPrimary
    ),
    labelLarge = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = TextPrimary
    )
)
