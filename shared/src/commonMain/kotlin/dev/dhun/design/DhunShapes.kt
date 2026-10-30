package dev.dhun.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * DHUN's corner scale — extra-large on primary media cards, pill chips, one
 * `navIndicator` for the tab pill. Material 3's shape *theme* is never what
 * these are read from: the values live here so every glass panel, chip and
 * button rounds the same way (`docs/design/glass-system.md`).
 */
object DhunShapes {
    val extraSmall = RoundedCornerShape(4.dp)
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val extraLarge = RoundedCornerShape(28.dp)
    val extraExtraLarge = RoundedCornerShape(32.dp)
    val full = CircleShape

    // Semantic aliases — named by what they are used for, not by a Material role.
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
