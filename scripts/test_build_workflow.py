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

    def test_build_jobs_do_not_get_contents_write(self):
        before_publish = self.text.split("\n  publish:\n", 1)[0]
        self.assertIn("permissions:\n  contents: read", before_publish)
        self.assertNotIn("contents: write", before_publish)
        self.assertIn("    permissions:\n      contents: write", self.publish)

    def test_branch_build_cannot_cancel_the_main_release_group(self):
        self.assertIn("github.ref == 'refs/heads/main' && 'test-release' || format('test-build-{0}', github.ref)", self.text)

    def test_msi_uses_the_existing_packaging_workflow_counter(self):
        self.assertIn("python scripts/installer_version.py $env:GITHUB_RUN_NUMBER $env:GITHUB_RUN_ATTEMPT", self.text)
        self.assertIn('"-PdhunInstallerVersion=$version"', self.text)
        self.assertIn("-ExpectedVersion $env:INSTALLER_VERSION", self.text)


if __name__ == "__main__":
    unittest.main()
