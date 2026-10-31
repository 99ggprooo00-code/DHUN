"""Safety contracts for the release workflow without a third-party YAML parser."""

import shutil
import subprocess
import unittest
from pathlib import Path

WORKFLOW = Path(__file__).resolve().parents[1] / ".github/workflows/test-release.yml"


class BuildWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.text = WORKFLOW.read_text()
        self.publish_test = self.text.split("\n  publish_test:\n", 1)[1].split("\n  publish:\n", 1)[0]
        self.publish = self.text.split("\n  publish:\n", 1)[1]

    def test_typed_manual_input_defaults_to_build_only(self):
        inputs = self.text.split("  workflow_dispatch:\n", 1)[1].split("  push:\n", 1)[0]
        self.assertIn("      build_only:", inputs)
        self.assertIn("        type: boolean", inputs)
        self.assertIn("        default: true", inputs)

    def test_publish_requires_public_plan_and_green_package_jobs(self):
        # ReleasePlanTest exercises the real git-history channel decision. The
        # workflow must consume it both when building and when publishing.
        self.assertIn("needs: [release_plan, apk, msi]", self.publish)
        self.assertIn("github.ref == 'refs/heads/main'", self.publish)
        self.assertIn("needs.release_plan.outputs.channel == 'public'", self.publish)
        self.assertIn("needs.release_plan.outputs.build_only == 'false'", self.publish)
        self.assertIn("github.event_name == 'push'", self.publish)
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.build_only == false", self.publish)
        self.assertIn("if: ${{ github.event_name == 'workflow_dispatch' && inputs.purge_legacy_dev_releases }}", self.publish)
        self.assertIn("fetch-depth: 2", self.text)
        self.assertIn("python scripts/release_plan.py", self.text)
        self.assertIn("- '.github/release-intent/v1.00.002'", self.text)

    def test_ordinary_app_code_merge_updates_only_the_private_rolling_test_release(self):
        push = self.text.split("  push:\n", 1)[1].split("\n\npermissions:", 1)[0]
        self.assertIn("branches: [main]", push)
        for path in ("shared/**", "app-android/**", "app-desktop/**", "gradle/**"):
            self.assertIn(f"- '{path}'", push)
        self.assertIn("github.event_name == 'push'", self.publish_test)
        self.assertIn("github.ref == 'refs/heads/main'", self.publish_test)
        self.assertIn("needs: [release_plan, apk, msi]", self.publish_test)
        self.assertIn("needs.release_plan.outputs.channel == 'test'", self.publish_test)
        self.assertIn("needs.release_plan.outputs.build_only == 'false'", self.publish_test)
        self.assertIn("gh release create test", self.publish_test)
        self.assertNotIn("Stage, verify and publish the fixed v1.00.002", self.publish_test)
        # Only a newly added marker can select public on a main push.
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.build_only == false", self.publish)

    def test_test_catalogue_uses_explicit_build_channel_and_public_does_not(self):
        self.assertIn("DHUN_RELEASE_CHANNEL:", self.text)
        self.assertEqual(3, self.text.count("DHUN_RELEASE_CHANNEL: ${{ needs.release_plan.outputs.channel }}"))
        self.assertEqual(3, self.text.count("BUILD_ONLY: ${{ needs.release_plan.outputs.build_only }}"))
        self.assertIn("if: ${{ env.DHUN_RELEASE_CHANNEL == 'test' }}", self.text)
        self.assertIn('"-PdhunReleaseChannel=$DHUN_RELEASE_CHANNEL"', self.text)
        self.assertIn('"-PdhunReleaseChannel=$env:DHUN_RELEASE_CHANNEL"', self.text)
        android = (WORKFLOW.parents[2] / "app-android/build.gradle.kts").read_text()
        desktop = (WORKFLOW.parents[2] / "app-desktop/build.gradle.kts").read_text()
        self.assertIn('buildConfigField("String", "DHUN_RELEASE_CHANNEL"', android)
        self.assertIn('jvmArgs += "-Ddhun.release.channel=$dhunReleaseChannel"', desktop)
        activity = (WORKFLOW.parents[2] / "app-android/src/main/kotlin/dev/dhun/android/MainActivity.kt").read_text()
        desktop_main = (WORKFLOW.parents[2] / "app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/Main.kt").read_text()
        self.assertIn('"test" -> DhunAppInfo.TEST_RELEASE_CHANNEL', activity)
        self.assertIn('"test" -> DhunAppInfo.TEST_RELEASE_CHANNEL', desktop_main)
        shell = (WORKFLOW.parents[2] / "shared/src/commonMain/kotlin/dev/dhun/ui/shell/DhunAppShell.kt").read_text()
        settings = (WORKFLOW.parents[2] / "shared/src/commonMain/kotlin/dev/dhun/ui/settings/SettingsScreen.kt").read_text()
        hub = (WORKFLOW.parents[2] / "shared/src/commonMain/kotlin/dev/dhun/ui/settings/SettingsHub.kt").read_text()
        persistence = (WORKFLOW.parents[2] / "app-android/src/main/kotlin/dev/dhun/android/ui/NavStatePersistence.kt").read_text()
        self.assertIn("showThemeCatalogue = appInfo.isTestRelease", shell)
        self.assertIn("showThemeCatalogue: Boolean = false", settings)
        self.assertIn("showThemeCatalogue = showThemeCatalogue", settings)
        self.assertIn("if (showThemeCatalogue)", hub)
        self.assertIn('"Theme catalogue"', hub)
        # Visual tuning is an experimental companion to the test-only catalogue.
        self.assertIn('if (category == SettingsCategory.APPEARANCE && showThemeCatalogue)', settings)
        self.assertIn('VisualTuningControls(viewModel)', settings)
        self.assertIn('DhunVisualTuning.restore(settingsVm.visualTuning.value, appInfo.isTestRelease)', shell)
        self.assertIn("allowThemeCatalogue = BuildConfig.DHUN_RELEASE_CHANNEL == \"test\"", activity)
        self.assertIn(".filter { allowThemeCatalogue || it != DetailRoute.ThemeCatalogPage }", persistence)
        self.assertIn("viewModel.setThemeId(mode.id)", shell)
        self.assertIn("viewModel.setAccentId(accent.id)", shell)

    def test_automatic_test_release_guard_is_main_push_only(self):
        self.assertIn("github.ref == 'refs/heads/main'", self.publish_test)
        self.assertIn("github.event_name == 'push'", self.publish_test)
        self.assertIn("needs.release_plan.outputs.channel == 'test'", self.publish_test)

    def test_installer_is_a_pull_request_check_before_merge(self):
        self.assertIn("  pull_request:\n    branches: [main]", self.text)
        self.assertIn("- name: Check install-over and userdata on disposable Windows", self.text)
        self.assertIn("    needs: [release_plan, apk, msi]", self.publish)

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
        before_publish = self.text.split("\n  publish_test:\n", 1)[0]
        self.assertIn("permissions:\n  contents: read", before_publish)
        self.assertNotIn("contents: write", before_publish)
        self.assertIn("    permissions:\n      contents: write", self.publish_test)
        self.assertIn("    permissions:\n      contents: write", self.publish)

    def test_rolling_test_release_stays_a_private_draft(self):
        self.assertIn("gh release create test", self.publish_test)
        self.assertIn("--draft --prerelease", self.publish_test)
        self.assertIn("--notes-file docs/releases/rolling-test.md", self.publish_test)
        self.assertIn("\"$(gh release view test --json isDraft --jq '.isDraft')\" != \"true\"", self.publish_test)
        self.assertIn("targetCommitish", self.publish_test)
        self.assertNotIn("gh release edit test --draft=false", self.publish_test)

    def test_legacy_release_purge_is_an_explicit_opt_in(self):
        # The legacy dev releases are irreversible to delete. They may be removed
        # only when the maintainer ticks a typed, default-off input on a manual
        # public release. Automatic rolling-test updates never purge legacy tags.
        inputs = self.text.split("  workflow_dispatch:\n", 1)[1].split("  pull_request:\n", 1)[0]
        self.assertIn("      purge_legacy_dev_releases:", inputs)
        block = inputs.split("      purge_legacy_dev_releases:", 1)[1]
        self.assertIn("        type: boolean", block)
        self.assertIn("        default: false", block)
        purge = self.publish.split("      - name: Delete legacy dev releases", 1)[1].split("\n      - name:", 1)[0]
        self.assertIn("if: ${{ github.event_name == 'workflow_dispatch' && inputs.purge_legacy_dev_releases }}", purge)
        for tag in ("dev-v0.1.0", "dev-v0.1.1", "dev"):
            self.assertIn(f"gh release delete {tag} --yes --cleanup-tag", purge)
        self.assertIn("git push origin :refs/tags/dev", purge)
        self.assertNotIn("gh release delete test", self.publish)
        self.assertIn("gh release delete test --yes --cleanup-tag", self.publish_test)
        for tag in ("dev-v0.1.0", "dev-v0.1.1", "dev"):
            self.assertNotIn(f"gh release delete {tag} ", self.publish_test)

    @unittest.skipUnless(shutil.which("jq"), "jq is available on the Ubuntu release runner")
    def test_draft_asset_comparison_normalizes_json_presentation(self):
        # GitHub CLI --jq prints compact JSON while jq -s prints multi-line
        # JSON. The old byte-for-byte comparison falsely rejected a complete
        # twelve-asset draft. Normalize both before comparing.
        self.assertIn("| jq -R . | jq -sc 'sort')", self.publish)
        self.assertIn("--jq '[.assets[] | select(.size > 0) | .name] | sort' | jq -c .)", self.publish)
        names = "z.apk\na.apk\n"
        quoted = subprocess.check_output(["jq", "-R", "."], input=names.encode())
        expected = subprocess.check_output(["jq", "-sc", "sort"], input=quoted).decode().strip()
        actual = subprocess.check_output(["jq", "-c", "."], input=b'["a.apk","z.apk"]').decode().strip()
        self.assertEqual(expected, actual)

    def test_fixed_version_is_created_once_as_public_prerelease(self):
        self.assertIn("Stage, verify and publish the fixed v1.00.002 prerelease once", self.publish)
        self.assertIn("python scripts/stage_versioned_release.py", self.publish)
        self.assertIn("--notes-file docs/releases/v1.00.002.md", self.publish)
        for asset in (
            "dhun-v1.00.002.apk",
            "dhun-v1.00.002-arm64-v8a.apk",
            "dhun-v1.00.002-armeabi-v7a.apk",
            "dhun-v1.00.002.msi",
        ):
            self.assertIn(f"dist/$version/{asset}", self.publish)
            self.assertIn(f"dist/$version/{asset}.sha256", self.publish)
        self.assertIn("--draft --prerelease", self.publish)
        self.assertIn('gh release edit "$version" --repo "$GITHUB_REPOSITORY" --draft=false --prerelease', self.publish)
        self.assertIn('actual_assets=$(gh release view', self.publish)
        self.assertIn('if [ "$actual_assets" != "$expected_assets" ]; then', self.publish)
        self.assertIn('python scripts/check_android_identity.py "$aapt" out \'1.00.002\' 7', self.text)
        self.assertIn(".isDraft')\" != \"false\"", self.publish)
        self.assertIn(".isPrerelease')\" != \"true\"", self.publish)
        self.assertIn("already exists at $existing_target; leaving its assets and tag unchanged", self.publish)
        self.assertIn("already exists but is still a draft; refusing to silently skip it", self.publish)
        self.assertIn("already exists but is not marked as a prerelease", self.publish)

    def test_unreadable_baseline_skips_the_install_over_check_instead_of_wedging(self):
        smoke = (WORKFLOW.parents[2] / "scripts/check_msi_upgrade.ps1").read_text()
        # Before v1.00.002 is published, v1.00.001 names its verified MSI
        # `dhun.msi`, not the versioned name used by the future public path.
        self.assertIn("@{ Tag = 'v1.00.002'; Name = 'dhun-v1.00.002.msi' }", smoke)
        self.assertIn("@{ Tag = 'v1.00.001'; Name = 'dhun.msi' }", smoke)
        self.assertIn("@{ Tag = 'v1.00.001'; Name = 'dhun-v1.00.001.msi' }", smoke)
        self.assertIn("@{ Tag = 'test'; Name = 'dhun-test.msi' }", smoke)
        self.assertLess(smoke.index("Name = 'dhun-v1.00.002.msi'"), smoke.index("Name = 'dhun.msi'"))
        self.assertLess(smoke.index("Name = 'dhun.msi'"), smoke.index("Name = 'dhun-test.msi'"))
        self.assertIn("Published baseline MSI checksum did not match", smoke)
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
        create = self.publish_test.split("gh release create test", 1)[1].split("--target", 1)[0]
        for name in ("dhun-test.apk", "dhun-test-arm64-v8a.apk", "dhun-test-armeabi-v7a.apk"):
            self.assertIn(f"python scripts/stage_artifact.py out/{name}", apk)
            self.assertIn(name, create)
        self.assertIn("dist/dhun-test.msi", create)
        # Internal checksums/provenance still protect build promotion, but the
        # rolling beta exposes exactly its four installers and no text sidecars.
        self.assertNotIn(".sha256", create)
        self.assertNotIn(".provenance.txt", create)
        self.assertIn("expected_test_assets=", self.publish_test)
        self.assertIn("actual_test_assets=", self.publish_test)
        self.assertIn("The rolling test release must contain only the four installers", self.publish_test)

    def test_msi_uses_the_existing_packaging_workflow_counter(self):
        self.assertIn("python scripts/installer_version.py $env:GITHUB_RUN_NUMBER $env:GITHUB_RUN_ATTEMPT", self.text)
        self.assertIn('"-PdhunInstallerVersion=$version"', self.text)
        self.assertIn("-ExpectedVersion $env:INSTALLER_VERSION", self.text)


    def test_future_release_assets_contain_no_json(self):
        # Release-facing assets: no .json may be uploaded by the publish job.
        # (The published v1.00.001 .build-info.json files are historical and untouched.)
        self.assertNotIn(".json", self.publish)
        self.assertIn("dist/$version/dhun-v1.00.002.msi.provenance.txt", self.publish)
        create_test = self.publish_test.split("gh release create test", 1)[1].split("--target", 1)[0]
        self.assertNotIn(".provenance.txt", create_test)
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
