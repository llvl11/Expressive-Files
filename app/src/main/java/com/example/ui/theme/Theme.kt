package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.model.AppColorPalette
import com.example.model.AppThemeMode

@Composable
fun ExpressiveFilesTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    colorPalette: AppColorPalette = AppColorPalette.VIBRANT,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme: ColorScheme = when {
        colorPalette == AppColorPalette.VIBRANT -> {
            if (isDark) VibrantDarkScheme else VibrantLightScheme
        }
        colorPalette == AppColorPalette.DYNAMIC && supportsDynamic -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        colorPalette == AppColorPalette.NEON_VIOLET -> {
            if (isDark) NeonVioletDarkScheme else NeonVioletLightScheme
        }
        colorPalette == AppColorPalette.CYBER_TEAL -> {
            if (isDark) CyberTealDarkScheme else CyberTealLightScheme
        }
        colorPalette == AppColorPalette.SUNSET_CORAL -> {
            if (isDark) SunsetCoralDarkScheme else SunsetCoralLightScheme
        }
        colorPalette == AppColorPalette.EMERALD_MINT -> {
            if (isDark) EmeraldMintDarkScheme else EmeraldMintLightScheme
        }
        colorPalette == AppColorPalette.CITRUS_SUN -> {
            if (isDark) CitrusSunDarkScheme else CitrusSunLightScheme
        }
        else -> {
            // Default to Vibrant Palette
            if (isDark) VibrantDarkScheme else VibrantLightScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = CookieShapes,
        typography = ExpressiveTypography,
        content = content
    )
}
