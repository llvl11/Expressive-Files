package com.baiel.expressivefiles.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.baiel.expressivefiles.model.AppColorPalette
import com.baiel.expressivefiles.model.AppThemeMode

@Composable
fun ExpressiveFilesTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    colorPalette: AppColorPalette = AppColorPalette.DYNAMIC,
    pitchBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val scheme: ColorScheme = when (colorPalette) {
        AppColorPalette.DYNAMIC ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        AppColorPalette.NEON_VIOLET ->
            if (isDark) NeonVioletDarkScheme else NeonVioletLightScheme
        AppColorPalette.CYBER_TEAL ->
            if (isDark) CyberTealDarkScheme else CyberTealLightScheme
        AppColorPalette.SUNSET_CORAL ->
            if (isDark) SunsetCoralDarkScheme else SunsetCoralLightScheme
        AppColorPalette.EMERALD_MINT ->
            if (isDark) EmeraldMintDarkScheme else EmeraldMintLightScheme
        AppColorPalette.CITRUS_SUN ->
            if (isDark) CitrusSunDarkScheme else CitrusSunLightScheme
        AppColorPalette.VIBRANT ->
            if (isDark) VibrantDarkScheme else VibrantLightScheme
    }.let { base ->
        // AMOLED variant: only the page background goes true black; cards,
        // sheets and strips keep their dark grays so depth survives.
        if (isDark && pitchBlack) base.copy(background = Color.Black) else base
    }

    MaterialTheme(
        colorScheme = scheme,
        shapes = CookieShapes,
        typography = ExpressiveTypography,
        content = content
    )
}

