"""Safety contracts for the artifact-only dispatch path (no third-party YAML parser)."""

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
            ("refs/heads/main", "push", False, True),
            ("refs/heads/main", "schedule", False, False),
        )
        code = compile(tree, str(WORKFLOW), "eval")
        for ref, event, build_only, expected in cases:
            with self.subTest(ref=ref, event=event, build_only=build_only):
                result = eval(code, {"__builtins__": {}}, {"ref": ref, "event": event, "build_only": build_only})
                self.assertIs(result, expected)

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

    def test_publish_reasserts_the_rolling_release_is_readable(self):
        # GitHub lists draft releases only to callers with push access. The msi
        # job runs on contents:read and the public Releases page has no push
        # token either, so publishing is not done until isDraft is provably false.
        self.assertIn("gh release edit test --draft=false --prerelease", self.publish)
        self.assertIn("\"$(gh release view test --json isDraft --jq '.isDraft')\" != \"false\"", self.publish)
        self.assertLess(
            self.publish.index("gh release create test"),
            self.publish.index("gh release edit test --draft=false"),
        )

    def test_unreadable_baseline_skips_the_install_over_check_instead_of_wedging(self):
        smoke = (WORKFLOW.parents[2] / "scripts/check_msi_upgrade.ps1").read_text()
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

    def test_msi_uses_the_existing_packaging_workflow_counter(self):
        self.assertIn("python scripts/installer_version.py $env:GITHUB_RUN_NUMBER $env:GITHUB_RUN_ATTEMPT", self.text)
        self.assertIn('"-PdhunInstallerVersion=$version"', self.text)
        self.assertIn("-ExpectedVersion $env:INSTALLER_VERSION", self.text)


if __name__ == "__main__":
    unittest.main()
