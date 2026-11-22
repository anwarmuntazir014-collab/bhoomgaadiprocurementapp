package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.example.domain.model.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsFor

private val BhoomgaadiColorScheme = lightColorScheme(
    primary = BhoomgaadiGreen,
    onPrimary = Color.White,
    primaryContainer = BhoomgaadiLightGreen,
    onPrimaryContainer = BhoomgaadiDarkGreen,
    secondary = SecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = SecondaryLightBlue,
    onSecondaryContainer = SecondaryBlue,
    tertiary = ProcurementAmber,
    onTertiary = Color.White,
    tertiaryContainer = ProcurementLightOrange,
    onTertiaryContainer = ProcurementOrange,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerLightRed,
    onErrorContainer = DangerRed,
    background = BgLight,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = TextSecondary,
    outline = BorderGray
)

@Composable
fun BhoomgaadiTheme(
    language: AppLanguage = AppLanguage.ENGLISH,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppStrings provides stringsFor(language)) {
        MaterialTheme(
            colorScheme = BhoomgaadiColorScheme,
            typography = buildFieldTypography(useHindi = language == AppLanguage.HINDI),
            content = content
        )
    }
}
