"""Pins the DHUN glass design system against default-Material-3 drift.

Why this file exists: the app's visual identity is frosted glass
(`docs/design/glass-system.md`), and the recurring failure is *not* a missing
token — it is a new or edited screen shipping a component's default Material
appearance: a raw `AlertDialog`, a Material `Card`/`Surface` panel, an
`OutlinedTextField` inside a glass dialog, a bare `Switch`/`Slider`. Each one
looks "done" in review, compiles, passes tests, and quietly makes the app look
like stock Material 3 — which is exactly the drift the owner has reported
repeatedly.

A static contract catches it without a device or a screenshot: the banned
composables may only appear in the design system (where they are customised and
wrapped), or in a file listed in `scripts/design_system_allowlist.txt` with a
reason. The allowlist is a *debt register*: shrinking it is the point.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHARED_UI = ROOT / "shared/src/commonMain/kotlin/dev/dhun"
DESIGN_DIR = SHARED_UI / "design"
ALLOWLIST = Path(__file__).with_name("design_system_allowlist.txt")
# The native shells render the same product on Android and Windows; both were
# shipping default Material chrome (an AlertDialog battery rationale, two flat
# Surface strips) until 2026-10-30, so they are scanned too.
PLATFORM_UI = [
    ROOT / "app-android/src/main",
    ROOT / "app-desktop/src/jvmMain",
]

# Composables whose DEFAULT appearance is Material 3, not DHUN glass. Each may
# still be used inside the design system (wrapped and fully restyled) — but not
# called from a screen.
BANNED = {
    "AlertDialog": "use GlassDialog (frosted card, gradient body, glass edge)",
    "BasicAlertDialog": "use GlassDialog",
    "OutlinedTextField": "use DhunTextField",
    "TextField": "use DhunTextField",
    "Card": "use GlassCard",
    "Surface": "use GlassCard (or a glass modifier); see glass-system.md §2",
    "ListItem": "compose the row with dhunContentRowSurface()",
    "TopAppBar": "the shell builds its own chrome",
    "Snackbar": "use ActionErrorBanner / the dialog contract",
    "ModalBottomSheet": "use LyricsArtworkSheet",
    "Checkbox": "use DhunCheckbox (glass square, accent fill, 48dp target)",
    "Switch": "use DhunSwitch (glass off-track, accent on-track)",
    "Slider": "use DhunSlider (accent groove over translucent glass)",
    "RadioButton": "use DhunCheckbox with role semantics, or a filter chip",
    "DropdownMenuItem": "use MenuActionRow inside TrackMenuSurface (glass rows)",
    "TextButton": "use DhunTextButton (accent label, pill shape, DHUN ripple)",
    "Button": "use DhunButton / DhunTonalButton / DhunOutlinedButton",
    "OutlinedButton": "use DhunOutlinedButton",
    "FilledTonalButton": "use DhunTonalButton",
    "ElevatedButton": "use DhunButton",
    "IconButton": "use DhunIconButton (glass circle, not a bare Material icon button)",
    "LinearProgressIndicator": "use DhunProgressBar / the shimmer loading states",
    "CircularProgressIndicator": "use DhunSpinner (thin accent arc in a glass track)",
    "NavigationBar": "use DhunNavigationBar (DHUN glass band + accent pill, not Material's pill)",
    "NavigationBarItem": "use DhunNavigationBarItem",
    "NavigationRail": "use DhunNavigationRail (same material as the dock, width from the policy)",
    "NavigationRailItem": "use DhunNavigationRailItem",
    "HorizontalDivider": "use DhunDivider (tapered glass hairline)",
    "Divider": "use DhunDivider (tapered glass hairline)",
    "PullToRefreshBox": "use DhunPullToRefresh (glass disc + accent arc, not Material's ring)",
}

# Layout containers Material renders *around* content. They may be used, but only
# with a transparent container so the DHUN glass behind them is what you see —
# a default `containerColor` is a flat Material plate inside the glass shell.
TRANSPARENT_ONLY = ("DropdownMenu",)


def call_span(text, name, start_line):
    """The full call text from `name(` to its matching `)` (multi-line aware)."""
    lines = text.splitlines()
    pattern = re.compile(r"(?<![.\w])" + re.escape(name) + r"\s*\(")
    index = start_line - 1
    match = pattern.search(lines[index])
    if not match:
        return ""
    depth = 0
    out = []
    for number in range(index, len(lines)):
        chunk = lines[number]
        if number == index:
            chunk = chunk[match.start():]
        for char in chunk:
            out.append(char)
            if char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
                if depth == 0:
                    return "".join(out)
    return "".join(out)


def allowlisted():
    if not ALLOWLIST.exists():
        return {}
    entries = {}
    for raw in ALLOWLIST.read_text().splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        path, _, reason = line.partition(":")
        entries[path.strip()] = reason.strip()
    return entries


def kotlin_files():
    shared = [p for p in SHARED_UI.rglob("*.kt") if DESIGN_DIR not in p.parents]
    platform = [p for root in PLATFORM_UI if root.exists() for p in root.rglob("*.kt")]
    return sorted(shared + platform)


def call_sites(text, name):
    """Yield (line_number, code) for `Name(` calls that are not package imports."""
    pattern = re.compile(r"(?<![.\w])" + re.escape(name) + r"\s*\(")
    for number, line in enumerate(text.splitlines(), start=1):
        code = line.strip()
        if code.startswith("import ") or code.startswith("//"):
            continue
        if pattern.search(code):
            yield number, code


class GlassDesignSystemTest(unittest.TestCase):
    def test_no_default_material_component_reaches_a_screen(self):
        allowed = allowlisted()
        failures = []
        for path in kotlin_files():
            text = path.read_text()
            rel = path.relative_to(ROOT).as_posix()
            for name, remedy in BANNED.items():
                # Imports alone are not the problem if nothing is called, but an
                # import of a banned component is the warning sign — check calls.
                for number, code in call_sites(text, name):
                    if rel in allowed:
                        break
                    failures.append(f"{rel}:{number}: `{name}(` — {remedy}  [{code}]")
        self.assertEqual(
            [],
            failures,
            "default-Material UI in a DHUN glass app (see docs/design/glass-system.md):\n"
            + "\n".join(failures),
        )

    def test_layout_containers_stay_transparent_over_the_glass(self):
        failures = []
        for path in kotlin_files():
            text = path.read_text()
            rel = path.relative_to(ROOT).as_posix()
            for name in TRANSPARENT_ONLY:
                for number, _ in call_sites(text, name):
                    span = call_span(text, name, number)
                    if "Color.Transparent" not in span:
                        failures.append(
                            f"{rel}:{number}: `{name}(` must pass containerColor = Color.Transparent "
                            "and paint the DHUN glass itself (GlassDock / dhunGlassBody)"
                        )
        self.assertEqual([], failures, "\n".join(failures))

    def test_allowlist_entries_point_at_real_files_and_explain_themselves(self):
        for rel, reason in allowlisted().items():
            self.assertTrue((ROOT / rel).exists(), f"allowlist entry for a missing file: {rel}")
            self.assertGreater(len(reason), 8, f"allowlist entry needs a reason: {rel}")

    def test_the_design_system_documents_every_component_it_asks_screens_to_use(self):
        doc = (ROOT / "docs/design/glass-system.md").read_text()
        for component in (
            "GlassCard",
            "GlassDialog",
            "dhunContentRowSurface",
            "DhunTextField",
            "DhunSwitch",
            "DhunSlider",
            "DhunCheckbox",
            "DhunSpinner",
            "DhunDivider",
            "DhunPullToRefresh",
            "DhunBackgrounds",
            "BackgroundSection",
            "BackgroundPalette",
            "DhunNavigationBar",
            "DhunNavigationRail",
            "AmbientBackdrop",
            "dhunGlassBody",
            "TrackMenuSurface",
            "ActionErrorBanner",
        ):
            self.assertIn(component, doc, f"glass-system.md must name {component}")

    def test_glass_material_is_defined_once(self):
        """The recipe (gradient, sheen, edge, dialog floor) lives in one object."""
        definition = (DESIGN_DIR / "components/GlassCard.kt").read_text()
        for constant in (
            "BODY_TOP_LIFT",
            "BODY_BOTTOM_DEEPEN",
            "SHEEN_TOP",
            "SHEEN_EXTENT",
            "EDGE_TOP_ALPHA",
            "EDGE_BOTTOM_ALPHA",
            "DIALOG_BODY_ALPHA_DARK",
            "DIALOG_BODY_ALPHA_LIGHT",
        ):
            self.assertIn(constant, definition, f"GlassMaterialPolicy must define {constant}")
        # And no screen re-invents the material with inline magic numbers.
        for path in kotlin_files():
            text = path.read_text()
            rel = path.relative_to(ROOT).as_posix()
            self.assertNotIn(
                "Brush.verticalGradient(listOf(0f to",
                text,
                f"{rel} builds its own glass gradient — use GlassMaterialPolicy",
            )

    def test_blur_and_colour_stay_separate_features(self):
        """The background feature's two switches must not be wired together.

        Dynamic blur may not consult the colour switch and vice versa: they are
        independent by requirement, and "one call that does both" is exactly how
        that independence rots.
        """
        policy = (ROOT / "shared/src/commonMain/kotlin/dev/dhun/design/background/BackgroundPalette.kt").read_text()
        blur = policy[policy.index("object BackgroundBlurPolicy"):]
        self.assertNotIn(
            "dynamicColors",
            blur,
            "BackgroundBlurPolicy must not read the colour switch",
        )
        self.assertNotIn(
            "blurEnabled",
            policy[policy.index("fun resolve("):policy.index("fun source(")],
            "palette resolution must not read any blur switch",
        )
        # One extraction pipeline: the backgrounds derive from the existing
        # extractor, and never decode an image of their own.
        backgrounds = (ROOT / "shared/src/commonMain/kotlin/dev/dhun/design/background").rglob("*.kt")
        for path in backgrounds:
            text = path.read_text()
            for forbidden in ("AsyncImage", "readPixels", "ImageBitmap", "BitmapFactory"):
                self.assertNotIn(
                    forbidden,
                    text,
                    f"{path.name} starts its own image pipeline ({forbidden}); reuse ArtworkColorExtractor",
                )

    def test_every_frosted_surface_asks_its_section_for_the_radius(self):
        """One blur rule for the whole app.

        Every place that frosts artwork has to resolve its radius through
        `DhunBackgrounds.blurPercentFor(section)`: the master switch, the
        section switch and static-vs-dynamic blur all live in that one call.
        A surface that reads the raw slider (`DhunAppearance.backdropBlurPercent`)
        directly silently ignores the user's switches — which is exactly the bug
        the feature exists to fix, so it is a gate now.
        """
        sources = [
            ROOT / "shared/src/commonMain/kotlin/dev/dhun/design/components/NowPlayingBackdrop.kt",
            ROOT / "shared/src/commonMain/kotlin/dev/dhun/design/components/LyricsMaterial.kt",
            ROOT / "shared/src/commonMain/kotlin/dev/dhun/design/components/GlassCard.kt",
            ROOT / "shared/src/commonMain/kotlin/dev/dhun/ui/player/FullPlayer.kt",
        ]
        for path in sources:
            text = path.read_text()
            for line in text.splitlines():
                if "Modifier.blur(" not in line and ".blur(" not in line:
                    continue
                self.assertNotIn(
                    "DhunAppearance.backdropBlurPercent",
                    line,
                    f"{path.name} builds a blur from the raw slider; use DhunBackgrounds.blurPercentFor(section)",
                )
        # The surfaces that paint their own artwork layer must hand it a section
        # (MiniPlayer reaches it through AcrylicSurface, which passes its own).
        mini = (ROOT / "shared/src/commonMain/kotlin/dev/dhun/ui/player/MiniPlayer.kt").read_text()
        self.assertTrue(
            re.search(r"AcrylicSurface\([\s\S]{0,200}?section\s*=\s*BackgroundSection\.MINI_PLAYER", mini),
            "MiniPlayer must paint the miniplayer section",
        )
        lyrics = (ROOT / "shared/src/commonMain/kotlin/dev/dhun/design/components/LyricsMaterial.kt").read_text()
        self.assertIn("section: BackgroundSection", lyrics)
        for caller in (
            ROOT / "shared/src/commonMain/kotlin/dev/dhun/design/components/GlassCard.kt",
        ):
            self.assertTrue(
                re.search(r"LyricsArtworkLayer\([\s\S]{0,240}?section\s*=", caller.read_text()),
                f"{caller.name} must tell the artwork layer which section it belongs to",
            )

    def test_every_background_section_is_persisted_and_wired(self):
        sections = (ROOT / "shared/src/commonMain/kotlin/dev/dhun/design/background/BackgroundPalette.kt").read_text()
        for section in ("HOME", "SEARCH", "LIBRARY", "MINI_PLAYER", "FULL_PLAYER"):
            self.assertIn(f'{section}("', sections, f"BackgroundSection.{section} must exist")
        # The mini player and the full player must actually paint their own
        # section, not inherit the page's.
        for path, section in (
            ("shared/src/commonMain/kotlin/dev/dhun/ui/player/MiniPlayer.kt", "MINI_PLAYER"),
            ("shared/src/commonMain/kotlin/dev/dhun/ui/player/FullPlayer.kt", "FULL_PLAYER"),
            ("shared/src/commonMain/kotlin/dev/dhun/ui/shell/DhunAppShell.kt", "MINI_PLAYER"),
        ):
            text = (ROOT / path).read_text()
            self.assertIn(
                f"BackgroundSection.{section}",
                text,
                f"{path} must wire its own background section",
            )

    def test_the_glass_docs_are_reachable_from_the_agent_instructions(self):
        for doc in (".ai/MASTER_PROMPT.md", "docs/ui-ux-design.md"):
            text = (ROOT / doc).read_text()
            self.assertIn(
                "glass-system.md",
                text,
                f"{doc} must point at docs/design/glass-system.md so agents cannot miss it",
            )


if __name__ == "__main__":
    unittest.main()
