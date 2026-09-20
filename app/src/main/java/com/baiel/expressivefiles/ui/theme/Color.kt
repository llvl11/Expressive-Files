package com.baiel.expressivefiles.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// File Type Colors for Badges and Cookie Icons
val FolderColor = Color(0xFF6750A4)

// All archive formats (zip/7z/tar/rar) share one color.
val ArchiveColor = Color(0xFF005FB0)
val ArchiveZipColor = ArchiveColor
val Archive7zColor = ArchiveColor
val ArchiveTarColor = ArchiveColor
val ArchiveRarColor = ArchiveColor
val ImageColor = Color(0xFFBA1A1A)
val VideoColor = Color(0xFF9C4146)
val AudioColor = Color(0xFF7D5260)
val DocumentColor = Color(0xFF006399)
val CodeColor = Color(0xFF006A63)
val ApkColor = Color(0xFF386A20)
val OtherFileColor = Color(0xFF79747E)

// Vibrant Palette Scheme (Design Theme)
val VibrantLightScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF005FB0),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E1FF),
    onSecondaryContainer = Color(0xFF001D49),
    tertiary = Color(0xFFBA1A1A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD6),
    onTertiaryContainer = Color(0xFF410002),
    background = Color(0xFFF3F4F9),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFE7E0EC)
)

val VibrantDarkScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFF9ECAFF),
    onSecondary = Color(0xFF003258),
    secondaryContainer = Color(0xFF00497D),
    onSecondaryContainer = Color(0xFFD1E1FF),
    tertiary = Color(0xFFFFB4AB),
    onTertiary = Color(0xFF690005),
    tertiaryContainer = Color(0xFF93000A),
    onTertiaryContainer = Color(0xFFFFDAD6),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1D1B20),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)

// Neon Violet Scheme
val NeonVioletLightScheme = lightColorScheme(
    primary = Color(0xFF651FFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE7F6),
    onPrimaryContainer = Color(0xFF311B92),
    secondary = Color(0xFF00B0FF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1F5FE),
    onSecondaryContainer = Color(0xFF01579B),
    tertiary = Color(0xFFFF4081),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF0F8),
    onSurface = Color(0xFF191C20),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F)
)

val NeonVioletDarkScheme = darkColorScheme(
    primary = Color(0xFFB388FF),
    onPrimary = Color(0xFF311B92),
    primaryContainer = Color(0xFF512DA8),
    onPrimaryContainer = Color(0xFFEDE7F6),
    secondary = Color(0xFF80D8FF),
    onSecondary = Color(0xFF004D40),
    secondaryContainer = Color(0xFF006064),
    onSecondaryContainer = Color(0xFFE0F7FA),
    tertiary = Color(0xFFFF80AB),
    background = Color(0xFF12131A),
    surface = Color(0xFF1A1B24),
    surfaceVariant = Color(0xFF262734),
    onSurface = Color(0xFFE2E2EC),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099)
)

// Cyber Teal Scheme
val CyberTealLightScheme = lightColorScheme(
    primary = Color(0xFF00838F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FA),
    onPrimaryContainer = Color(0xFF004D40),
    secondary = Color(0xFF00897B),
    tertiary = Color(0xFFE65100),
    background = Color(0xFFF4FAF9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE0ECEB),
    onSurface = Color(0xFF111E1D)
)

val CyberTealDarkScheme = darkColorScheme(
    primary = Color(0xFF4DD0E1),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF006064),
    onPrimaryContainer = Color(0xFFE0F7FA),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF0F1818),
    surface = Color(0xFF172424),
    surfaceVariant = Color(0xFF243636),
    onSurface = Color(0xFFE0EAEA)
)

// Sunset Coral Scheme
val SunsetCoralLightScheme = lightColorScheme(
    primary = Color(0xFFE64A19),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFBE9E7),
    secondary = Color(0xFFFF6F00),
    tertiary = Color(0xFF6200EA),
    background = Color(0xFFFCF7F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3E7E4),
    onSurface = Color(0xFF201A19)
)

val SunsetCoralDarkScheme = darkColorScheme(
    primary = Color(0xFFFF8A65),
    onPrimary = Color(0xFF4E1500),
    primaryContainer = Color(0xFF872300),
    secondary = Color(0xFFFFB74D),
    tertiary = Color(0xFFB388FF),
    background = Color(0xFF1A1312),
    surface = Color(0xFF241C1A),
    surfaceVariant = Color(0xFF382D2A),
    onSurface = Color(0xFFEDE0DE)
)

// Emerald Mint Scheme
val EmeraldMintLightScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F5E9),
    secondary = Color(0xFF00838F),
    tertiary = Color(0xFFF57F17),
    background = Color(0xFFF5F9F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2EBE2),
    onSurface = Color(0xFF161F17)
)

val EmeraldMintDarkScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF1B5E20),
    primaryContainer = Color(0xFF2E7D32),
    secondary = Color(0xFF4DD0E1),
    tertiary = Color(0xFFFFD54F),
    background = Color(0xFF101911),
    surface = Color(0xFF18241A),
    surfaceVariant = Color(0xFF253728),
    onSurface = Color(0xFFE0EAE1)
)

// Citrus Sun Scheme
val CitrusSunLightScheme = lightColorScheme(
    primary = Color(0xFFF57F17),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFFDE7),
    secondary = Color(0xFFE65100),
    tertiary = Color(0xFF00897B),
    background = Color(0xFFFCFAF4),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3EEE0),
    onSurface = Color(0xFF201D17)
)

val CitrusSunDarkScheme = darkColorScheme(
    primary = Color(0xFFFFD54F),
    onPrimary = Color(0xFF4E3400),
    primaryContainer = Color(0xFFF57F17),
    secondary = Color(0xFFFFB74D),
    tertiary = Color(0xFF80CBC4),
    background = Color(0xFF181711),
    surface = Color(0xFF252219),
    surfaceVariant = Color(0xFF383427),
    onSurface = Color(0xFFEAE7DC)
)
