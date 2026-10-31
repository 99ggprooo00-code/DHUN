package dev.dhun.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Material 3 shape scale — extra-large on primary media cards, pill chips.
 * Keeps the product feeling rounded and premium without Liquid Glass.
 */
object DhunShapes {
    val extraSmall = RoundedCornerShape(4.dp)
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val extraLarge = RoundedCornerShape(28.dp)
    val extraExtraLarge = RoundedCornerShape(32.dp)
    val full = CircleShape

    // Semantic aliases (M3 roles)
    val card = large
    val cardLarge = extraLarge
    val chip = full // pill filter / assist chips
    val button = full
    val artwork = large
    val artworkHero = extraLarge
    val artworkCircle = full
    val bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val glass = large
    val navIndicator = RoundedCornerShape(16.dp)
}
