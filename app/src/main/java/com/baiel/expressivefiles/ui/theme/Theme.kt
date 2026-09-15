package com.baiel.expressivefiles.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.baiel.expressivefiles.model.AppColorPalette
import com.baiel.expressivefiles.model.AppThemeMode
import com.baiel.expressivefiles.model.OsType

@Composable
fun ExpressiveFilesTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    colorPalette: AppColorPalette = AppColorPalette.DYNAMIC,
    osType: OsType = OsType.PIXEL,
    pitchBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    // Palette first, always: MagicOS only restyles the surfaces around the
    // accent, so every palette (including dynamic) works under both OS skins.
    val scheme: ColorScheme = when {
        // MagicOS + Dynamic: no wallpaper-derived Material You - the dynamic
        // palette IS MagicOS blue (Honor-style accent, MagicOS surfaces).
        osType == OsType.MAGIC_OS && colorPalette == AppColorPalette.DYNAMIC ->
            if (isDark) MagicOsDarkScheme else MagicOsLightScheme
        colorPalette == AppColorPalette.DYNAMIC ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        colorPalette == AppColorPalette.NEON_VIOLET ->
            if (isDark) NeonVioletDarkScheme else NeonVioletLightScheme
        colorPalette == AppColorPalette.CYBER_TEAL ->
            if (isDark) CyberTealDarkScheme else CyberTealLightScheme
        colorPalette == AppColorPalette.SUNSET_CORAL ->
            if (isDark) SunsetCoralDarkScheme else SunsetCoralLightScheme
        colorPalette == AppColorPalette.EMERALD_MINT ->
            if (isDark) EmeraldMintDarkScheme else EmeraldMintLightScheme
        colorPalette == AppColorPalette.CITRUS_SUN ->
            if (isDark) CitrusSunDarkScheme else CitrusSunLightScheme
        else ->
            if (isDark) VibrantDarkScheme else VibrantLightScheme
    }.let { base ->
        // MagicOS skin: Honor-style white/black surfaces under the palette's
        // own accent colors (blue is just the default Vibrant pairing).
        val withOs = if (osType == OsType.MAGIC_OS) base.withMagicOsSurfaces(isDark) else base
        // AMOLED variant: only the page background goes true black; cards,
        // sheets and strips keep their dark grays so depth survives.
        if (isDark && pitchBlack) {
            withOs.copy(background = Color.Black)
        } else withOs
    }

    // Shape getters must see the selected skin too, not the default local.
    CompositionLocalProvider(LocalOsType provides osType) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = CookieShapes,
            typography = ExpressiveTypography,
            content = content
        )
    }
}

/**
 * MagicOS surface treatment applied over any palette scheme: Honor-style
 * white (light) / black (dark) backgrounds and surfaces, while primary,
 * secondary, tertiary and error accents stay the palette's own.
 */
private fun ColorScheme.withMagicOsSurfaces(isDark: Boolean): ColorScheme {
    val magic = if (isDark) MagicOsDarkScheme else MagicOsLightScheme
    return copy(
        background = magic.background,
        onBackground = magic.onBackground,
        surface = magic.surface,
        onSurface = magic.onSurface,
        surfaceVariant = magic.surfaceVariant,
        onSurfaceVariant = magic.onSurfaceVariant,
        surfaceContainerLowest = magic.surfaceContainerLowest,
        surfaceContainerLow = magic.surfaceContainerLow,
        surfaceContainer = magic.surfaceContainer,
        surfaceContainerHigh = magic.surfaceContainerHigh,
        surfaceContainerHighest = magic.surfaceContainerHighest,
        outline = magic.outline,
        outlineVariant = magic.outlineVariant,
        inverseSurface = magic.inverseSurface,
        inverseOnSurface = magic.inverseOnSurface,
        scrim = magic.scrim
    )
}

