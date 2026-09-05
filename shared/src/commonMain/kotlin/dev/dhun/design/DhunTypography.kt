package dev.dhun.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material3-compatible type scale. Uses the system font (no bundled font
 * yet — the scale and weights are the contract; the family can be swapped
 * without touching any screen). Every Text uses either these or a
 * MaterialTheme.typography alias — no raw `sp` outside design.
 */
object DhunTypographyTokens {
    val displayLarge = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.W400, letterSpacing = (-0.25).sp)
    val displayMedium = TextStyle(fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.W400)
    val displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.W400)
    val headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.W500)
    val headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.W500)
    val headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.W500)
    val titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.W500)
    val titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.W600, letterSpacing = 0.15.sp)
    val titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600, letterSpacing = 0.1.sp)
    val labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W600, letterSpacing = 0.1.sp)
    val labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.W600, letterSpacing = 0.5.sp)
    val labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.W600, letterSpacing = 0.5.sp)
    val bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.W400, letterSpacing = 0.5.sp)
    val bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W400, letterSpacing = 0.25.sp)
    val bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.W400, letterSpacing = 0.4.sp)

    // Semantic display sizes used by the legacy harnesses and small labels.
    // Keeping them here lets platform UI scale with the same token family.
    val compact = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.W400)
    val trackTitle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.W500)
    val playerTitle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.W500)
    val hero = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.W500)
    val bodyRelaxed = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.W400)
    val brand = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.W600, letterSpacing = 2.sp)
    val compactLetterSpacing = 1.sp
}

/** Material3 Typography wired to Dhun tokens — used by DhunTheme. */
val DhunTypography = Typography(
    displayLarge = DhunTypographyTokens.displayLarge,
    displayMedium = DhunTypographyTokens.displayMedium,
    displaySmall = DhunTypographyTokens.displaySmall,
    headlineLarge = DhunTypographyTokens.headlineLarge,
    headlineMedium = DhunTypographyTokens.headlineMedium,
    headlineSmall = DhunTypographyTokens.headlineSmall,
    titleLarge = DhunTypographyTokens.titleLarge,
    titleMedium = DhunTypographyTokens.titleMedium,
    titleSmall = DhunTypographyTokens.titleSmall,
    labelLarge = DhunTypographyTokens.labelLarge,
    labelMedium = DhunTypographyTokens.labelMedium,
    labelSmall = DhunTypographyTokens.labelSmall,
    bodyLarge = DhunTypographyTokens.bodyLarge,
    bodyMedium = DhunTypographyTokens.bodyMedium,
    bodySmall = DhunTypographyTokens.bodySmall,
)
