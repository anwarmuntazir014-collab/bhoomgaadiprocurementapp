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

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val FieldTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    ),
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        color = TextSecondary
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
)
