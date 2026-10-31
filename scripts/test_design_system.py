"""Source guardrails for DHUN's native visual system.

These tests deliberately cover only the screen surfaces already migrated.
Settings and remaining shell controls join the banned-call scope in their own
stages; an untested global ban today would only encode an allowlist of known
defects. No assertion here claims a rendered screenshot or Kotlin execution.
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "shared/src/commonMain/kotlin/dev/dhun"
SCREEN_SURFACES = (
    "ui/home/HomeScreen.kt",
    "ui/search/SearchScreen.kt",
    "ui/browse/ArtistScreen.kt",
    "ui/browse/AlbumScreen.kt",
    "ui/browse/PlaylistScreen.kt",
    "ui/library/LibraryScreen.kt",
    "ui/player/FullPlayer.kt",
    "ui/player/MiniPlayer.kt",
    "ui/player/PlayerSeekBar.kt",
    "ui/player/PlayerTabs.kt",
    "ui/player/SyncedLyrics.kt",
    "ui/player/TransportControls.kt",
    "ui/player/LyricsControlsDialog.kt",
    "ui/player/LyricsMatchPicker.kt",
    "ui/player/LyricsRomanizationControls.kt",
    "ui/player/PlaybackErrorDialog.kt",
)
RAW_STOCK_CALLS = (
    "AlertDialog",
    "BasicAlertDialog",
    "Button",
    "Card",
    "Checkbox",
    "CircularProgressIndicator",
    "DropdownMenuItem",
    "IconButton",
    "LinearProgressIndicator",
    "NavigationBar",
    "NavigationBarItem",
    "NavigationRail",
    "NavigationRailItem",
    "OutlinedTextField",
    "PullToRefreshBox",
    "Slider",
    "Surface",
    "Switch",
    "TextButton",
)


def source(relative: str) -> str:
    return (SHARED / relative).read_text(encoding="utf-8")


class DesignSystemSourceTest(unittest.TestCase):
    def test_migrated_screens_do_not_call_raw_stock_visuals(self):
        # The negative lookbehind is important: DhunTextButton is a DHUN wrapper,
        # not a raw TextButton call.
        patterns = {
            name: re.compile(rf"(?<![A-Za-z0-9_]){re.escape(name)}\s*\(")
            for name in RAW_STOCK_CALLS
        }
        violations = []
        for relative in SCREEN_SURFACES:
            text = source(relative)
            for name, pattern in patterns.items():
                for match in pattern.finditer(text):
                    line = text.count("\n", 0, match.start()) + 1
                    violations.append(f"{relative}:{line}: raw {name}()")
        self.assertEqual([], violations, "use the DHUN component wrappers")

    def test_search_keeps_ime_and_physical_enter_submission(self):
        screen = source("ui/search/SearchScreen.kt")
        field = source("design/components/DhunTextField.kt")
        self.assertIn("onSubmit = onSearchSubmit", screen)
        self.assertIn("imeAction = ImeAction.Search", screen)
        self.assertIn("SearchInputPolicy.shouldSubmitOnKeyEvent(event)", screen)
        self.assertIn("onSearch = { if (imeAction == ImeAction.Search) onSubmit?.invoke() }", field)
        self.assertIn("Modifier.onKeyEvent { event -> keyHandler(event) }", field)

    def test_home_keeps_all_refresh_entry_points(self):
        screen = source("ui/home/HomeScreen.kt")
        self.assertIn("event.key == Key.F5", screen)
        self.assertIn('CustomAccessibilityAction("Refresh music")', screen)
        self.assertIn("DhunPullToRefresh(", screen)
        self.assertIn("onRefresh = viewModel::refresh", screen)
        self.assertIn("DhunTextButton(onClick = onRefresh", screen)

    def test_library_progress_retains_the_indeterminate_path(self):
        screen = source("ui/library/LibraryScreen.kt")
        controls = source("design/components/DhunControls.kt")
        self.assertIn("progress = fraction", screen)
        self.assertIn("val value = DhunControlPolicy.progressValue(progress)", controls)
        self.assertIn("if (value == null)", controls)

    def test_player_visual_adoption_preserves_operation_hooks(self):
        player = source("ui/player/FullPlayer.kt")
        self.assertIn("DhunSlider(", player)
        self.assertIn("onValueChange = viewModel::setVolume", player)
        self.assertIn("valueRange = 0f..1f", player)

        romanization = source("ui/player/LyricsRomanizationControls.kt")
        self.assertIn("onCheckedChange = controller::setEnabled", romanization)
        self.assertIn("controller.setMode(value)", romanization)

        tabs = source("ui/player/PlayerTabs.kt")
        self.assertIn("if (id != selectedProvider) onSelect(id)", tabs)
        self.assertIn("MenuActionRow(", tabs)

        picker = source("ui/player/LyricsMatchPicker.kt")
        self.assertIn("singleLine = false", picker)
        self.assertIn("minLines = 3", picker)
        self.assertIn("maxLines = 5", picker)
        self.assertIn("TtmlParser.MAX_INPUT_CHARS + 1", picker)
        self.assertIn("controller.previewTtml(track.id, ttml)", picker)
        self.assertIn("controller.choose(track.id, match)", picker)

        error = source("ui/player/PlaybackErrorDialog.kt")
        self.assertIn("GlassDialog(onDismissRequest = onDismiss)", error)
        self.assertIn("diagnosticText(it, 4_000)", error)
        self.assertIn("onDismiss(); onRetry()", error)

    def test_product_identity_is_not_assigned_to_the_toolkit(self):
        home = source("ui/home/HomeScreen.kt")
        shapes = source("design/DhunShapes.kt")
        self.assertNotIn("Home — Material 3", home)
        self.assertNotIn("Material 3 shape scale", shapes)
        self.assertIn("Home — DHUN glass", home)
        self.assertIn("DHUN shape scale", shapes)


if __name__ == "__main__":
    unittest.main()
