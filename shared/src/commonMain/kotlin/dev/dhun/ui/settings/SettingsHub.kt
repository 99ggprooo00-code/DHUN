package dev.dhun.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunIcon
import dev.dhun.design.DhunIconView
import dev.dhun.design.DhunSpacing
import dev.dhun.design.DhunThemeMode
import dev.dhun.design.components.GlassCard
import dev.dhun.design.components.SectionHeader
import dev.dhun.presentation.settings.SettingsCategory

@Composable
internal fun SettingsHub(
    isDesktop: Boolean,
    themeId: String,
    accentMode: String,
    onOpenCategory: (SettingsCategory) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query, isDesktop) { SettingsCategory.search(query, isDesktop) }
    Text("Make DHUN yours", style = MaterialTheme.typography.titleLarge, color = DhunColors.textPrimary)
    Text("Appearance, playback and your data — organized in one place.",
        style = MaterialTheme.typography.bodyMedium, color = DhunColors.textSecondary)
    OutlinedTextField(value = query, onValueChange = { query = it.take(120) },
        label = { Text("Search settings categories") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    if (matches.isEmpty()) Text("No settings categories match. Try theme, lyrics, history or storage.",
        style = MaterialTheme.typography.bodyMedium, color = DhunColors.textSecondary)
    matches.groupBy { it.group }.forEach { (group, categories) ->
        GlassCard(modifier = Modifier.fillMaxWidth(), elevated = false) {
            Column(Modifier.fillMaxWidth().padding(vertical = DhunSpacing.sm)) {
                SectionHeader(title = group)
                categories.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable(role = Role.Button, onClickLabel = "Open ${category.title}") { onOpenCategory(category) }
                            .heightIn(min = DhunSpacing.touchTarget)
                            .padding(horizontal = DhunSpacing.lg, vertical = DhunSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DhunSpacing.sm),
                    ) {
                        DhunIconView(icon = category.icon(), contentDescription = null,
                            modifier = Modifier.size(DhunSpacing.iconSize), tint = DhunColors.accent)
                        Column(Modifier.weight(1f)) {
                            Text(category.title, style = MaterialTheme.typography.titleMedium, color = DhunColors.textPrimary)
                            val summary = if (category == SettingsCategory.APPEARANCE) {
                                val theme = if (themeId == "system") "Follow system" else DhunThemeMode.fromId(themeId)?.label ?: "Dark"
                                "$theme · ${if (accentMode == "artwork") "Dynamic artwork accents" else "Static accent"}"
                            } else category.summary
                            Text(summary, style = MaterialTheme.typography.bodySmall, color = DhunColors.textSecondary)
                        }
                        Spacer(Modifier.width(DhunSpacing.xs))
                        Text("›", style = MaterialTheme.typography.titleLarge, color = DhunColors.textSecondary)
                    }
                }
            }
        }
    }
}

private fun SettingsCategory.icon(): DhunIcon = when (this) {
    SettingsCategory.APPEARANCE -> DhunIcon.Palette
    SettingsCategory.PLAYBACK -> DhunIcon.Play
    SettingsCategory.LYRICS -> DhunIcon.Lyrics
    SettingsCategory.CONTENT -> DhunIcon.LibraryMusic
    SettingsCategory.PRIVACY -> DhunIcon.History
    SettingsCategory.STORAGE -> DhunIcon.Download
    SettingsCategory.DESKTOP -> DhunIcon.Home
    SettingsCategory.ABOUT -> DhunIcon.MoreVert
}
