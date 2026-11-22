package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val NotoDevanagari = FontFamily(
    Font(R.font.noto_sans_devanagari, FontWeight.Normal),
    Font(R.font.noto_sans_devanagari, FontWeight.Medium),
    Font(R.font.noto_sans_devanagari, FontWeight.SemiBold),
    Font(R.font.noto_sans_devanagari, FontWeight.Bold)
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

fun buildFieldTypography(useHindi: Boolean): Typography {
    val primaryFamily = if (useHindi) NotoDevanagari else PlusJakartaSans
    return Typography(
        displaySmall = TextStyle(
            fontFamily = primaryFamily,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        ),
        headlineSmall = TextStyle(
            fontFamily = primaryFamily,
            fontSize = 21.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        ),
        titleLarge = TextStyle(
            fontFamily = primaryFamily,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        ),
        titleMedium = TextStyle(
            fontFamily = primaryFamily,
            fontSize = 15.5.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        ),
        bodyLarge = TextStyle(
            fontFamily = primaryFamily,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Normal,
            color = TextPrimary
        ),
        bodyMedium = TextStyle(
            fontFamily = primaryFamily,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Normal,
            color = TextSecondary
        ),
        labelLarge = TextStyle(
            fontFamily = primaryFamily,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold
        ),
        labelMedium = TextStyle(
            fontFamily = primaryFamily,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    )
}
