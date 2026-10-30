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
            "BackgroundComponent",
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

    def test_every_frosted_surface_asks_its_component_for_the_radius(self):
        """One blur rule for the whole app.

        Every place that frosts artwork has to resolve its radius through
        `DhunBackgrounds.blurPercentFor(component)`: the global switch, the
        surface's own switch and adaptive-vs-steady all live in that one call.
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
                    f"{path.name} builds a blur from the raw slider; use DhunBackgrounds.blurPercentFor(component)",
                )
        # The surfaces that paint their own artwork layer must hand it a component
        # (MiniPlayer reaches it through AcrylicSurface, which passes its own).
        mini = (ROOT / "shared/src/commonMain/kotlin/dev/dhun/ui/player/MiniPlayer.kt").read_text()
        self.assertTrue(
            re.search(r"AcrylicSurface\([\s\S]{0,200}?component\s*=\s*BackgroundComponent\.MINI_PLAYER", mini),
            "MiniPlayer must paint the miniplayer surface",
        )
        lyrics = (ROOT / "shared/src/commonMain/kotlin/dev/dhun/design/components/LyricsMaterial.kt").read_text()
        self.assertIn("component: BackgroundComponent", lyrics)
        for caller in (
            ROOT / "shared/src/commonMain/kotlin/dev/dhun/design/components/GlassCard.kt",
        ):
            self.assertTrue(
                re.search(r"LyricsArtworkLayer\([\s\S]{0,240}?component\s*=", caller.read_text()),
                f"{caller.name} must tell the artwork layer which surface it belongs to",
            )

    def test_blur_off_means_no_artwork_is_painted(self):
        """The switch removes the thumbnail; it does not swap it for a dimmed one.

        "Background blur off" used to keep drawing the cover sharp with a veil,
        which is precisely the setting the user could not tell apart from a
        weaker blur. Every layer that draws artwork must therefore ask
        `BackgroundBlurPolicy.paintsArtwork(...)` (or `DhunBackgrounds.showsArtwork`,
        which is the same answer per surface) and return before painting.
        """
        painters = {
            "shared/src/commonMain/kotlin/dev/dhun/design/components/NowPlayingBackdrop.kt": "showsArtwork",
            "shared/src/commonMain/kotlin/dev/dhun/design/components/LyricsMaterial.kt": "paintsArtwork",
            "shared/src/commonMain/kotlin/dev/dhun/design/components/GlassCard.kt": "showsArtwork",
            "shared/src/commonMain/kotlin/dev/dhun/ui/player/FullPlayer.kt": "paintsArtwork",
        }
        for rel, marker in painters.items():
            text = (ROOT / rel).read_text()
            self.assertIn(marker, text, f"{rel} must gate its artwork layer on the blur switch")
        # The page backdrop must return *before* it builds the image request, or
        # "off" still costs a decode and a look at a sharp cover.
        backdrop = (
            ROOT / "shared/src/commonMain/kotlin/dev/dhun/design/components/NowPlayingBackdrop.kt"
        ).read_text()
        self.assertRegex(
            backdrop,
            r"if \(!BackgroundBlurPolicy\.paintsArtwork\(blurPercent\)\) return",
            "NowPlayingBackdrop must draw nothing at all when this surface has no blur",
        )

    def test_every_background_component_is_persisted_and_wired(self):
        """Four surfaces, each with a saved colour and its own blur switch.

        The customisation unit is the *surface the user recognises* — pages,
        navigation bar, miniplayer, full player — not an implementation palette.
        Renaming one, or wiring a surface to another's colour, has to fail here.
        """
        policy = (ROOT / "shared/src/commonMain/kotlin/dev/dhun/design/background/BackgroundPalette.kt").read_text()
        for component, wid in (
            ("PAGES", "page"),
            ("NAVIGATION", "nav"),
            ("MINI_PLAYER", "mini"),
            ("FULL_PLAYER", "player"),
        ):
            self.assertIn(f'{component}("{wid}"', policy, f"BackgroundComponent.{component} must exist")
        keys = (ROOT / "shared/src/commonMain/kotlin/dev/dhun/data/SettingsKeys.kt").read_text()
        self.assertIn("background_component_", keys)
        self.assertIn("BACKGROUND_FIELD_COLOR", keys)
        self.assertIn("BACKGROUND_FIELD_FOLLOW_ARTWORK", keys)
        # The mini player, the full player and the navigation chrome must each
        # paint their own surface, not inherit the page's.
        for path, component in (
            ("shared/src/commonMain/kotlin/dev/dhun/ui/player/MiniPlayer.kt", "MINI_PLAYER"),
            ("shared/src/commonMain/kotlin/dev/dhun/ui/player/FullPlayer.kt", "FULL_PLAYER"),
            ("shared/src/commonMain/kotlin/dev/dhun/ui/shell/DhunAppShell.kt", "MINI_PLAYER"),
            ("shared/src/commonMain/kotlin/dev/dhun/ui/shell/DhunAppShell.kt", "NAVIGATION"),
        ):
            text = (ROOT / path).read_text()
            self.assertIn(
                f"BackgroundComponent.{component}",
                text,
                f"{path} must wire its own background surface ({component})",
            )
        # The settings screen must offer the four surfaces and their colours, and
        # must not fall back to asking the user for palette *layers*.
        settings = (ROOT / "shared/src/commonMain/kotlin/dev/dhun/ui/settings/BackgroundSettings.kt").read_text()
        self.assertIn("BackgroundComponent.entries", settings)
        for word in ("PaletteLayer", "Primary (base wash)", "Secondary (main glow)", "Companion glow"):
            self.assertNotIn(word, settings, "the surface picker must not expose palette layers")

    def test_the_two_global_switches_are_the_whole_global_model(self):
        """Background blur and Dynamic colours are the only global decisions.

        The previous pass had three globals in one block, which read as "blur,
        something, colour". Adaptive blur is a *detail of an enabled blur*, so it
        belongs with the strength slider, not between the two switches; and every
        surface's colour has to reach the veil over its own artwork, or picking a
        colour for the player silently does nothing while blur is on.
        """
        settings = (
            ROOT / "shared/src/commonMain/kotlin/dev/dhun/ui/settings/BackgroundSettings.kt"
        ).read_text()
        self.assertNotIn(
            "Adaptive blur",
            settings,
            "the two-switch block must not grow a third global; keep blur detail in Backdrop detail",
        )
        for global_switch in ("Background blur", "Dynamic colours"):
            self.assertIn(global_switch, settings, f"{global_switch} must stay a global switch")
        detail = (ROOT / "shared/src/commonMain/kotlin/dev/dhun/ui/settings/SettingsScreen.kt").read_text()
        self.assertIn("Adaptive blur", detail, "adaptive blur belongs to the Backdrop detail group")

        # Every artwork layer's veil carries its own surface's colour.
        for rel in (
            "shared/src/commonMain/kotlin/dev/dhun/design/components/NowPlayingBackdrop.kt",
            "shared/src/commonMain/kotlin/dev/dhun/ui/player/FullPlayer.kt",
        ):
            text = (ROOT / rel).read_text()
            self.assertIn(
                "veilTintFor(",
                text,
                f"{rel} paints a theme-grey veil, so a picked colour cannot tint its own artwork",
            )
        player = (ROOT / "shared/src/commonMain/kotlin/dev/dhun/ui/player/FullPlayer.kt").read_text()
        self.assertRegex(
            player,
            r"veilTintFor\(\s*palette = DhunBackgrounds\.paletteFor\(BackgroundComponent\.FULL_PLAYER\)",
            "the full player's veil must use the full player's own palette",
        )

    def test_page_backdrops_follow_the_surface_they_are_composed_on(self):
        """A backdrop inherits the component of the surface it is composed on.

        Album, artist and playlist pages paint their own artwork backdrop, and
        they are reachable from Home, Search *and* Library. Pinning those
        backdrops to one hardcoded section meant a page kept another surface's
        switches. The component is therefore a nullable parameter resolved from
        `LocalBackgroundComponent`, never a hardcoded literal.
        """
        text = (
            ROOT / "shared/src/commonMain/kotlin/dev/dhun/design/components/NowPlayingBackdrop.kt"
        ).read_text()
        for name in ("NowPlayingBackdrop", "AmbientBackdrop", "PageArtworkBackdrop"):
            self.assertTrue(
                re.search(
                    rf"fun {name}\([\s\S]{{0,240}}?component: BackgroundComponent\? = null",
                    text,
                ),
                f"{name} must take an optional component instead of defaulting to one surface",
            )
        self.assertGreaterEqual(
            text.count("LocalBackgroundComponent.current"),
            3,
            "every backdrop must fall back to the surface it is composed on",
        )
        for screen in ("AlbumScreen", "ArtistScreen", "PlaylistScreen"):
            body = (
                ROOT / f"shared/src/commonMain/kotlin/dev/dhun/ui/browse/{screen}.kt"
            ).read_text()
            self.assertNotIn(
                "component = BackgroundComponent.PAGES",
                body,
                f"{screen} must not pin its backdrop to one surface",
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
