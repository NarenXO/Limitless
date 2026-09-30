package com.teamdexters.limitless.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.R

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

val LimitlessTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        color = PureWhite
    ),
    headlineSmall = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = PureWhite
    ),
    bodyLarge = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = PureWhite
    ),
    bodyMedium = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = SoftWhite
    ),
    labelMedium = TextStyle(
        fontFamily = manropeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = NeutralGray
    ),
    
    // Fallbacks for standard Material 3 types used elsewhere
    titleMedium = TextStyle(fontFamily = manropeFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = PureWhite),
    titleSmall = TextStyle(fontFamily = manropeFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PureWhite),
    labelLarge = TextStyle(fontFamily = manropeFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = SoftWhite),
    bodySmall = TextStyle(fontFamily = manropeFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = NeutralGray)
)
