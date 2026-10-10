"""LaunchActivity must not mention Compose / MainActivity in its bytecode inputs.

ART may verify every method of the launcher activity before onCreate. If
LaunchActivity.kt references Compose, ComponentActivity, or the MainActivity
class literal, API 29 can die before a frame. The v31 alias still points at
MainActivity; this file only gates the API < 31 trampoline.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LAUNCH = ROOT / "app-android" / "src" / "main" / "kotlin" / "dev" / "dhun" / "android" / "LaunchActivity.kt"
MANIFEST = ROOT / "app-android" / "src" / "main" / "AndroidManifest.xml"
BOOLS = ROOT / "app-android" / "src" / "main" / "res" / "values" / "bools.xml"
BOOLS_V31 = ROOT / "app-android" / "src" / "main" / "res" / "values-v31" / "bools.xml"


def _code_only(src: str) -> str:
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return re.sub(r"//.*?$", "", src, flags=re.M)


class LaunchActivityIsolationTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(LAUNCH.exists(), "LaunchActivity.kt is the API < 31 launcher")
        self.src = LAUNCH.read_text()
        self.code = _code_only(self.src)

    def test_is_a_raw_framework_activity(self):
        self.assertIn("class LaunchActivity : Activity()", self.code)
        self.assertNotIn("ComponentActivity", self.code)

    def test_source_has_no_androidx_or_compose(self):
        self.assertNotIn("androidx", self.code)
        self.assertNotIn("compose", self.code.lower())

    def test_does_not_mention_main_activity_as_a_class_literal(self):
        # Class-name *string* is how we start MainActivity without putting it
        # in this class's constant pool. A `MainActivity::class` / import
        # would re-introduce the ART verification edge.
        self.assertNotIn("import dev.dhun.android.MainActivity", self.code)
        self.assertNotIn("MainActivity::class", self.code)
        self.assertIn("setClassName(packageName, MAIN_ACTIVITY)", self.code)
        self.assertIn('"dev.dhun.android.MainActivity"', self.code)

    def test_manifest_splits_launcher_on_api_31(self):
        manifest = MANIFEST.read_text()
        self.assertIn("android:name=\".LaunchActivity\"", manifest)
        self.assertIn('android:targetActivity=".LaunchActivity"', manifest)
        self.assertIn('android:targetActivity=".MainActivity"', manifest)
        self.assertIn("@bool/dhun_view_launcher", manifest)
        self.assertIn("@bool/dhun_compose_launcher", manifest)
        # MAIN/LAUNCHER must live on the aliases, not on MainActivity itself,
        # or API 29 would still class-load Compose as the launcher.
        # The activity is self-closing (`/>`) — it has no intent-filter child.
        main_block = re.search(
            r'<activity\s+android:name="\.MainActivity"[^>]*/>',
            manifest,
        )
        self.assertIsNotNone(main_block, "MainActivity activity block must be self-closing")
        self.assertNotIn("android.intent.action.MAIN", main_block.group(0))
        self.assertIn('android.intent.action.MAIN', manifest)
        self.assertGreaterEqual(manifest.count("android.intent.action.MAIN"), 2)

    def test_api29_uses_view_launcher_api31_uses_compose(self):
        bools = BOOLS.read_text()
        v31 = BOOLS_V31.read_text()
        self.assertIn(">false</bool>", bools.split("dhun_compose_launcher")[1][:80])
        self.assertIn(">true</bool>", bools.split("dhun_view_launcher")[1][:80])
        self.assertIn(">true</bool>", v31.split("dhun_compose_launcher")[1][:80])
        self.assertIn(">false</bool>", v31.split("dhun_view_launcher")[1][:80])


if __name__ == "__main__":
    unittest.main()
