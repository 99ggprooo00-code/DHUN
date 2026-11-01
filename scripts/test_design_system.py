"""Source guardrails for DHUN's native visual system.

The raw-control ban covers every Kotlin source in the native UI roots; design
wrappers are deliberately outside that scope. No assertion here claims a
rendered screenshot or Kotlin execution.
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
SHARED = ROOT / "shared/src/commonMain/kotlin/dev/dhun"
UI_SOURCE_ROOTS = (
    SHARED / "ui",
    ROOT / "app-android",
    ROOT / "app-desktop",
)
RAW_STOCK_CALLS = (
    "AlertDialog",
    "BasicAlertDialog",
    "BasicTextField",
    "Button",
    "Card",
    "CenterAlignedTopAppBar",
    "Checkbox",
    "CircularProgressIndicator",
    "DropdownMenuItem",
    "ExposedDropdownMenuBox",
    "FilledTonalButton",
    "IconButton",
    "LinearProgressIndicator",
    "ListItem",
    "ModalBottomSheet",
    "NavigationBar",
    "NavigationBarItem",
    "NavigationRail",
    "NavigationRailItem",
    "OutlinedButton",
    "OutlinedTextField",
    "PullToRefreshBox",
    "RadioButton",
    "Slider",
    "Snackbar",
    "Surface",
    "Switch",
    "TextButton",
    "TopAppBar",
)


def source(relative: str) -> str:
    return (SHARED / relative).read_text(encoding="utf-8")


def native_ui_sources():
    for root in UI_SOURCE_ROOTS:
        yield from sorted(root.rglob("*.kt"))


class DesignSystemSourceTest(unittest.TestCase):
    def test_native_ui_does_not_call_raw_stock_visuals(self):
        # The negative lookbehind is important: DhunTextButton is a DHUN wrapper,
        # not a raw TextButton call.
        patterns = {
            name: re.compile(rf"(?<![A-Za-z0-9_]){re.escape(name)}\s*\(")
            for name in RAW_STOCK_CALLS
        }
        violations = []
        for path in native_ui_sources():
            relative = path.relative_to(ROOT)
            text = path.read_text(encoding="utf-8")
            for name, pattern in patterns.items():
                for match in pattern.finditer(text):
                    line = text.count("\n", 0, match.start()) + 1
                    violations.append(f"{relative}:{line}: raw {name}()")
        self.assertEqual([], violations, "use the DHUN component wrappers")

    def test_menu_anchors_leave_all_visible_paint_to_dhun(self):
        pattern = re.compile(r"(?<![A-Za-z0-9_])DropdownMenu\s*\((.*?)\)\s*\{", re.DOTALL)
        violations = []
        menu_count = 0
        for path in native_ui_sources():
            relative = path.relative_to(ROOT)
            text = path.read_text(encoding="utf-8")
            for match in pattern.finditer(text):
                menu_count += 1
                arguments = match.group(1)
                line = text.count("\n", 0, match.start()) + 1
                for required in (
                    "containerColor = Color.Transparent",
                    "tonalElevation = DhunSpacing.zero",
                    "shadowElevation = DhunSpacing.zero",
                ):
                    if required not in arguments:
                        violations.append(f"{relative}:{line}: missing {required}")
        self.assertGreater(menu_count, 0)
        self.assertEqual([], violations)

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
        self.assertIn("label = value.label", romanization)

        tabs = source("ui/player/PlayerTabs.kt")
        self.assertIn("if (id != selectedProvider) onSelect(id)", tabs)
        self.assertIn("label = label", tabs)
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

    def test_settings_shell_and_battery_actions_keep_callbacks(self):
        settings = source("ui/settings/SettingsScreen.kt")
        self.assertIn("viewModel.setBackdropBlurPercent(percent)", settings)
        self.assertIn("DhunAppearance.setBackdropPrefs(blurPercent = percent)", settings)
        self.assertIn("viewModel.setBackdropBrightnessPercent(percent)", settings)
        self.assertIn("DhunAppearance.setBackdropPrefs(brightnessPercent = percent)", settings)
        self.assertIn("onCheckedChange = session::setEnabled", settings)
        self.assertIn("onChange = session::setPreamp", settings)
        self.assertIn("session.setBandGain(band.index, it)", settings)
        self.assertIn("viewModel.setCountryCode(region.code)", settings)

        shell = source("ui/shell/DhunAppShell.kt")
        self.assertIn("DhunNavigationBarItem(", shell)
        self.assertIn("DhunNavigationRailItem(", shell)
        self.assertIn("nav.selectTab(tab, keepDetailOnTabChange = false)", shell)
        self.assertIn("nav.selectTab(tab, keepDetailOnTabChange = layout.showsDetailPane)", shell)

        activity = (ROOT / "app-android/src/main/kotlin/dev/dhun/android/MainActivity.kt").read_text(
            encoding="utf-8"
        )
        self.assertIn("GlassDialog(onDismissRequest", activity)
        self.assertIn("openBatteryOptimizationSettings()", activity)

    def test_active_guidance_matches_the_simple_background_model(self):
        active_documents = (
            ROOT / "docs/design/glass-system.md",
            ROOT / "docs/ui-ux-design.md",
            ROOT / ".ai/MASTER_PROMPT.md",
        )
        rejected_or_unimplemented = (
            "DhunColourSlider",
            "AmbientBackdrop",
            "BackgroundComponent",
            "two independent switches",
            "Dynamic colours",
        )
        for path in active_documents:
            text = path.read_text(encoding="utf-8")
            self.assertIn("Background blur", text, path)
            self.assertIn("Background brightness", text, path)
            for phrase in rejected_or_unimplemented:
                self.assertNotIn(phrase, text, f"{path}: stale background guidance: {phrase}")

        spec = active_documents[0].read_text(encoding="utf-8")
        self.assertIn("sharp, dimmed artwork", spec)
        self.assertIn("DhunNavigationBar", spec)
        self.assertIn("GlassMaterialPolicy", spec)
        self.assertNotIn("Material 3", spec)
        self.assertNotIn("Material-3", spec)
        self.assertNotIn("Material3", spec)

        roadmap_head = (ROOT / ".ai/ROADMAP.md").read_text(encoding="utf-8").split("\n---\n", 1)[0]
        self.assertIn("arena/8a70a040-dhun", roadmap_head)
        self.assertIn("Background blur + Background brightness", roadmap_head)
        self.assertNotIn("two orthogonal switches", roadmap_head)
        self.assertNotIn("DhunColourSlider", roadmap_head)

    def test_product_identity_is_not_assigned_to_the_toolkit(self):
        home = source("ui/home/HomeScreen.kt")
        shapes = source("design/DhunShapes.kt")
        self.assertNotIn("Home — Material 3", home)
        self.assertNotIn("Material 3 shape scale", shapes)
        self.assertIn("Home — DHUN glass", home)
        self.assertIn("DHUN shape scale", shapes)


if __name__ == "__main__":
    unittest.main()
