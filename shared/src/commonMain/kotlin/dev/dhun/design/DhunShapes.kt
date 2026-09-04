package dev.dhun.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object DhunShapes {
    val extraSmall = RoundedCornerShape(4.dp)
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val extraLarge = RoundedCornerShape(24.dp)
    val extraExtraLarge = RoundedCornerShape(28.dp)
    val full = CircleShape

    // Semantic aliases
    val card = medium
    val cardLarge = large
    val chip = full
    val button = full
    val artwork = medium
    val artworkCircle = full
    val bottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val glass = large
}
