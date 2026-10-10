"""Safety contracts for the release workflow without a third-party YAML parser."""

import ast
import re
import unittest
from pathlib import Path

WORKFLOW = Path(__file__).resolve().parents[1] / ".github/workflows/test-release.yml"


class BuildWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.text = WORKFLOW.read_text()
        self.publish = self.text.split("\n  publish:\n", 1)[1]

    def test_typed_manual_input_defaults_to_build_only(self):
        inputs = self.text.split("  workflow_dispatch:\n", 1)[1].split("  push:\n", 1)[0]
        self.assertIn("      build_only:", inputs)
        self.assertIn("        type: boolean", inputs)
        self.assertIn("        default: true", inputs)

    def test_actual_publish_expression_denies_every_branch_build(self):
        expression = re.search(r"^    if: \$\{\{ (.+) \}\}$", self.publish, re.MULTILINE).group(1)
        expression = expression.replace("github.ref", "ref").replace("github.event_name", "event").replace("inputs.build_only", "build_only")
        expression = expression.replace("&&", "and").replace("||", "or")
        expression = re.sub(r"\bfalse\b", "False", expression)
        tree = ast.parse(expression, mode="eval")
        # Evaluate only the boolean subset this guard actually uses, and only
        # typed boolean inputs (the workflow schema above guarantees that type).
        allowed = (ast.Expression, ast.BoolOp, ast.And, ast.Or, ast.Compare, ast.Eq, ast.Name, ast.Load, ast.Constant)
        self.assertTrue(all(isinstance(node, allowed) for node in ast.walk(tree)))
        self.assertTrue(all(node.id in {"ref", "event", "build_only"} for node in ast.walk(tree) if isinstance(node, ast.Name)))
        cases = (
            ("refs/heads/arena/example", "workflow_dispatch", True, False),
            ("refs/heads/arena/example", "workflow_dispatch", False, False),
            ("refs/heads/arena/example", "push", False, False),
            ("refs/pull/30/merge", "pull_request", True, False),
            ("refs/pull/30/merge", "pull_request", False, False),
            ("refs/heads/main", "workflow_dispatch", True, False),
            ("refs/heads/main", "workflow_dispatch", False, True),
            ("refs/heads/main", "push", False, False),
            ("refs/heads/main", "push", True, False),
            ("refs/heads/main", "schedule", False, False),
        )
        code = compile(tree, str(WORKFLOW), "eval")
        for ref, event, build_only, expected in cases:
            with self.subTest(ref=ref, event=event, build_only=build_only):
                result = eval(code, {"__builtins__": {}}, {"ref": ref, "event": event, "build_only": build_only})
                self.assertIs(result, expected)

    def test_merge_to_main_cannot_mutate_releases(self):
        # A merge pushes to main; that must build/test only, never publish.
        self.assertIn("  push:\n    branches: [main]", self.text)
        self.assertNotIn("github.event_name == 'push'", self.publish)
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.build_only == false", self.publish)

    def test_installer_is_a_pull_request_check_before_merge(self):
        self.assertIn("  pull_request:\n    branches: [main]", self.text)
        self.assertIn("- name: Check install-over and userdata on disposable Windows", self.text)
        self.assertIn("    needs: [apk, msi]", self.publish)

    def test_unsigned_msi_is_finalized_before_checksum_and_upload(self):
        scripts = WORKFLOW.parents[2] / "scripts"
        stage = (scripts / "stage_msi.ps1").read_text()
        self.assertLess(stage.index("patch_msi_upgrade.ps1"), stage.index("stage_artifact.py"))
        smoke = (scripts / "check_msi_upgrade.ps1").read_text()
        self.assertIn("UPGRADINGPRODUCTCODE=", smoke)
        self.assertIn("candidate-uninstall.log", smoke)
        patch = (scripts / "patch_msi_upgrade.ps1").read_text()
        self.assertIn("Get-AuthenticodeSignature", patch)
        self.assertIn("DHUN_UPGRADE_DATA_POLICY", patch)
        self.assertIn("'UPGRADINGPRODUCTCODE'", patch)

    def test_build_jobs_do_not_get_contents_write(self):
        before_publish = self.text.split("\n  publish:\n", 1)[0]
        self.assertIn("permissions:\n  contents: read", before_publish)
        self.assertNotIn("contents: write", before_publish)
        self.assertIn("    permissions:\n      contents: write", self.publish)

    def test_rolling_test_release_stays_a_private_draft(self):
        self.assertIn("gh release create test", self.publish)
        self.assertIn("--draft --prerelease", self.publish)
        self.assertIn("--notes-file docs/releases/rolling-test.md", self.publish)
        self.assertIn("\"$(gh release view test --json isDraft --jq '.isDraft')\" != \"true\"", self.publish)
        self.assertNotIn("gh release edit test --draft=false", self.publish)

    def test_fixed_version_is_created_once_as_public_prerelease(self):
        self.assertIn("Create the fixed v1.00.001 public prerelease once", self.publish)
        self.assertIn("python scripts/stage_versioned_release.py", self.publish)
        self.assertIn("--notes-file docs/releases/v1.00.001.md", self.publish)
        for asset in (
            "dhun-v1.00.001.apk",
            "dhun-v1.00.001-arm64-v8a.apk",
            "dhun-v1.00.001-armeabi-v7a.apk",
            "dhun-v1.00.001.msi",
        ):
            self.assertIn(f"dist/$version/{asset}", self.publish)
            self.assertIn(f"dist/$version/{asset}.sha256", self.publish)
        self.assertIn("--prerelease", self.publish)
        self.assertIn(".isDraft')\" != \"false\"", self.publish)
        self.assertIn(".isPrerelease')\" != \"true\"", self.publish)
        self.assertIn("already exists; leaving its assets and tag unchanged", self.publish)
        self.assertIn("already exists but is still a draft; refusing to silently skip it", self.publish)
        self.assertIn("already exists but is not marked as a prerelease", self.publish)

    def test_unreadable_baseline_skips_the_install_over_check_instead_of_wedging(self):
        smoke = (WORKFLOW.parents[2] / "scripts/check_msi_upgrade.ps1").read_text()
        self.assertIn("'dhun-v1.00.001.msi'", smoke)
        self.assertIn("'dhun-test.msi'", smoke)
        self.assertIn('gh release download $tag --repo $env:GITHUB_REPOSITORY --pattern $assetName', smoke)
        # The skip is announced once, as a warning, and never as a pass.
        self.assertEqual(1, smoke.count("MSI install-over SKIPPED"))
        self.assertIn("::warning title=MSI install-over SKIPPED::", smoke)
        self.assertIn('installOver = "skipped: $baselineReason"', smoke)
        self.assertIn("exit 0", smoke)
        self.assertNotIn("Could not download the published MSI baseline/checksum", smoke)
        # A fetch that succeeds and then contradicts itself must still fail the
        # run: skipping covers unreadable release state, not bad baselines.
        self.assertIn("throw 'Published baseline MSI checksum did not match'", smoke)
        self.assertIn("throw 'Candidate and baseline do not share an upgrade identity'", smoke)
        self.assertIn("do not attempt a downgrade", smoke)

    def test_branch_build_cannot_cancel_the_main_release_group(self):
        self.assertIn("github.ref == 'refs/heads/main' && 'test-release' || format('test-build-{0}', github.ref)", self.text)

    def test_rolling_release_publishes_universal_and_both_abi_apks(self):
        apk = self.text.split("\n  apk:\n", 1)[1].split("\n  msi:\n", 1)[0]
        self.assertIn("python scripts/stage_android_apks.py", apk)
        self.assertIn("app-android/build/outputs/apk/debug", apk)
        self.assertNotIn("cp app-android/build/outputs/apk/debug/app-android-debug.apk", apk)
        create = self.publish.split("gh release create test", 1)[1].split("--target", 1)[0]
        for name in ("dhun-test.apk", "dhun-test-arm64-v8a.apk", "dhun-test-armeabi-v7a.apk"):
            self.assertIn(f"python scripts/stage_artifact.py out/{name}", apk)
            self.assertIn(name, create)
            self.assertIn(f"{name}.sha256", create)

    def test_msi_uses_the_existing_packaging_workflow_counter(self):
        self.assertIn("python scripts/installer_version.py $env:GITHUB_RUN_NUMBER $env:GITHUB_RUN_ATTEMPT", self.text)
        self.assertIn('"-PdhunInstallerVersion=$version"', self.text)
        self.assertIn("-ExpectedVersion $env:INSTALLER_VERSION", self.text)


    def test_future_release_assets_contain_no_json(self):
        # Release-facing assets: no .json may be uploaded by the publish job.
        # (The published v1.00.001 .build-info.json files are historical and untouched.)
        self.assertNotIn(".json", self.publish)
        self.assertIn("dist/$version/dhun-v1.00.001.msi.provenance.txt", self.publish)
        self.assertIn("dist/dhun-test.msi.provenance.txt", self.publish)
        for line in self.text.splitlines():
            if "upload" in line or "gh release create" in line:
                self.assertNotIn(".json", line)
        for block in self.text.split("uses: actions/upload-artifact@v6")[1:]:
            self.assertNotIn(".json", block.split("\n      - ", 1)[0])

    def test_install_over_is_required_for_main_and_a_skip_is_not_green_there(self):
        msi = self.text.split("\n  msi:\n", 1)[1].split("\n  aab:\n", 1)[0]
        self.assertIn("REQUIRE_INSTALL_OVER: ${{ github.ref == 'refs/heads/main' && 'true' || 'false' }}", msi)
        self.assertIn("-RequireInstallOver:$require", msi)
        smoke = (WORKFLOW.parents[2] / "scripts/check_msi_upgrade.ps1").read_text()
        self.assertIn("[switch]$RequireInstallOver", smoke)
        self.assertIn("MSI install-over is REQUIRED for this build but was SKIPPED", smoke)
        # The machine-readable outcome is text, and distinguishes skipped from passed.
        self.assertIn("status = 'skipped'", smoke)
        self.assertIn("status = 'passed'", smoke)
        self.assertIn("'result.txt'", smoke)
        self.assertNotIn("result.json", smoke)
        self.assertNotIn("ConvertTo-Json", smoke)


if __name__ == "__main__":
    unittest.main()
