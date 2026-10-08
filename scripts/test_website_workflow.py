"""Contract for `.github/workflows/website.yml` (no third-party YAML parser).

The site's browser evidence only exists if the workflow actually runs it. This
file asserts the wiring the way the rest of the repository asserts its
workflows: by reading the YAML as text and naming the steps that must exist and
the steps that must have been retired.

Three things it is here to catch, each of which was a real defect or a real
risk in this workstream:

1. **A measurement job that never measures.** The a11y job used to shell out to
   `@axe-core/cli`, which exited 1 without writing a report on all three routes
   — the workflow reported a *warning* about a tool that did not run, and axe
   evidence did not exist. axe-core now runs through `@axe-core/playwright`
   inside `website/tests/browser.mjs`, and this file fails if the CLI invocation
   comes back or if the browser script stops being invoked.
2. **Browser binaries in the wrong job.** `@playwright/test` is a dev dependency
   of `website/`, so a plain `npm ci` downloads ~150 MB of browser binaries.
   Only the browser job needs them; the build job must skip the download.
3. **A smoke check that cannot fail usefully.** The served-site job may only run
   after a real deploy (while Pages is `legacy`, the deploy job skips and there
   is nothing new to fetch), and it must call the script that re-runs the
   honesty contract against the published bytes.
"""

import pathlib
import re
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
WORKFLOW = REPO_ROOT / ".github" / "workflows" / "website.yml"


class WorkflowText(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding="utf-8")

    def job(self, name: str) -> str:
        """The text of one job, from its `name:` key to the next job."""
        match = re.search(rf"^  {re.escape(name)}:\n(.*?)(?=^  [a-z][a-z0-9-]*:\n|\Z)", self.text, re.M | re.S)
        self.assertIsNotNone(match, f"job {name} is missing from {WORKFLOW.name}")
        return match.group(1)


def block_scalar_regions(lines: list[str]) -> set[int]:
    """Indices of lines that are the *content* of a `|` or `>` block scalar.

    Content inside `run: |` is shell, not YAML, so a colon there is fine and
    must not be reported. A block scalar starts on a `key: |` line and ends
    when indentation drops back to the key's own level.
    """
    inside: set[int] = set()
    key_indent: int | None = None
    for number, line in enumerate(lines):
        if key_indent is not None:
            if line.strip() and (len(line) - len(line.lstrip())) <= key_indent:
                key_indent = None
            else:
                inside.add(number)
                continue
        stripped = line.rstrip("\n")
        if re.search(r":\s*[|>][-+]?\s*$", stripped):
            key_indent = len(line) - len(line.lstrip())
    return inside


def yaml_hygiene_violations(text: str) -> list[str]:
    """Unquoted `: ` inside a plain scalar — invalid YAML, invisible as text.

    GitHub Actions says nothing until the run is rejected, and the repository
    deliberately carries no YAML parser (no Python one is available in the
    environment where these checks run). This is the one YAML mistake a text
    edit actually makes — a job or step name like
    `name: Measurements (Playwright: viewports)` — so it is caught here by
    rule instead of by a red run on main.
    """
    lines = text.splitlines()
    skipped = block_scalar_regions(lines)
    violations: list[str] = []
    for number, line in enumerate(lines, 1):
        if number - 1 in skipped:
            continue
        match = re.match(r"^(\s*)(-\s+)?([A-Za-z_][A-Za-z0-9_-]*):\s+(\S.*)$", line)
        if not match:
            continue
        value = match.group(4).strip()
        if value[:1] in ("\"", "'", "|", ">", "{", "["):
            continue
        if ": " in value or value.endswith(":"):
            violations.append(
                f"line {number}: {match.group(3)}: {value!r} — an unquoted ': ' in a YAML "
                f"scalar is a parse error; quote the value"
            )
    return violations


class Triggers(WorkflowText):
    def test_site_scripts_trigger_the_workflow(self):
        for path in (
            "website/**",
            "scripts/website_claims.py",
            "scripts/website_quality.py",
            "scripts/website_smoke.py",
            "scripts/test_website_claims.py",
            "scripts/test_website_quality.py",
            "scripts/test_website_smoke.py",
            "scripts/report_lighthouse.py",
            "scripts/test_report_lighthouse.py",
        ):
            self.assertIn(f'"{path}"', self.text, f"{path} does not trigger the site workflow")

    def test_every_script_the_workflow_runs_also_triggers_it(self):
        """Derived, not listed: a script the workflow *runs* but does not watch
        is a check that can be changed without ever running.

        `scripts/report_lighthouse.py` was exactly that — the Lighthouse job
        calls it on every run, and the `paths:` filters did not name it, so an
        edit to the only reader of the Lighthouse reports could land without the
        workflow that uses it ever starting. Each path must appear at least
        twice (the `push` and `pull_request` filters), which is asserted rather
        than assumed.
        """
        used = sorted(set(re.findall(r"scripts/[a-z_]+\.py", self.text)))
        self.assertTrue(used, "the workflow references no scripts at all")
        for path in used:
            self.assertGreaterEqual(
                self.text.count(f'"{path}"'),
                2,
                f"{path} is used by {WORKFLOW.name} but does not trigger it on both "
                f"push and pull_request",
            )

    def test_app_workflows_are_not_touched_by_this_file(self):
        """The site workflow must stay the only site-owned workflow."""
        for other in ("ci.yml", "build-apk.yml", "test-release.yml", "extraction-health.yml"):
            path = REPO_ROOT / ".github" / "workflows" / other
            self.assertTrue(path.is_file(), f"{other} disappeared")


class BuildJob(WorkflowText):
    def test_build_does_not_download_browsers(self):
        job = self.job("build")
        self.assertIn("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", job)

    def test_build_runs_every_local_gate(self):
        job = self.job("build")
        for command in (
            "npm run build",
            "node tools/verify-minify.mjs",
            "scripts/website_claims.py",
            "scripts/website_quality.py",
            "html-validate",
        ):
            self.assertIn(command, job, f"the build job no longer runs {command}")

    def test_drift_check_still_present(self):
        self.assertRegex(self.job("build"), r"git diff --exit-code[^\n]*website/dist")


class BrowserJob(WorkflowText):
    def test_pinned_chromium_is_installed(self):
        self.assertRegex(self.job("browser"), r"playwright install --with-deps chromium")

    def test_browser_measurements_run(self):
        job = self.job("browser")
        self.assertIn("node tests/browser.mjs", job)
        self.assertIn("SITE_BASE", job)

    def test_browser_evidence_does_not_depend_on_the_lighthouse_job(self):
        """A noisy score must never hide the browser measurements."""
        self.assertRegex(self.job("browser"), r"needs:\s*\[?build\]?")
        self.assertNotRegex(self.job("browser"), r"needs:\s*\[?lighthouse")

    def test_axe_cli_is_retired(self):
        """`@axe-core/cli` never produced a report; axe runs inside the browser job.

        The comment above the browser step still names it — that is the record of
        why it went — so this asserts on the invocation, not on the string.
        """
        self.assertNotRegex(self.text, r"npx[^\n]*@axe-core/cli")

    def test_screenshots_are_uploaded_as_artifacts(self):
        job = self.job("browser")
        self.assertIn("browser-evidence", job)
        self.assertIn("website/tests/screenshots", job)

class LighthouseJob(WorkflowText):
    def test_lighthouse_takes_three_samples_and_reports_each(self):
        job = self.job("lighthouse")
        self.assertIn("for sample in 1 2 3", job)
        self.assertIn("scripts/report_lighthouse.py", job)

    def test_lighthouse_is_its_own_job(self):
        """Separate runners: neither measurement can starve the other."""
        self.assertRegex(self.job("lighthouse"), r"needs:\s*\[?build\]?")
        self.assertNotIn("browser-evidence", self.job("lighthouse"))


class ServedJob(WorkflowText):
    def test_served_site_is_smoke_checked_after_a_deploy(self):
        job = self.job("served")
        self.assertIn("scripts/website_smoke.py", job)
        self.assertRegex(job, r"needs:\s*\[?deploy\]?")

    def test_served_job_only_runs_on_main(self):
        self.assertRegex(self.job("served"), r"github\.ref == 'refs/heads/main'")


class DeployJob(WorkflowText):
    def test_deploy_still_skips_with_a_warning_when_pages_is_legacy(self):
        job = self.job("deploy")
        self.assertIn("build_type", job)
        self.assertRegex(job, r"::warning title=Pages source not switched")

    def test_deploy_still_depends_on_the_checked_build(self):
        self.assertRegex(self.job("deploy"), r"needs:\s*\[?build\]?")


class SiteOwnedScriptsExist(unittest.TestCase):
    def test_every_referenced_site_script_exists(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        for script in set(re.findall(r"scripts/(website_[a-z_]+\.py)", text)):
            self.assertTrue(
                (REPO_ROOT / "scripts" / script).is_file(), f"{script} is referenced but missing"
            )

    def test_browser_script_exists(self):
        self.assertTrue((REPO_ROOT / "website" / "tests" / "browser.mjs").is_file())


if __name__ == "__main__":
    unittest.main()


class YamlHygiene(WorkflowText):
    def test_the_committed_workflow_is_valid_yaml_by_this_rule(self):
        self.assertEqual(yaml_hygiene_violations(self.text), [])

    def test_an_unquoted_colon_in_a_scalar_is_caught(self):
        broken = self.text.replace(
            'name: Browser measurements',
            'name: Measurements (Playwright: viewports)',
            1,
        )
        self.assertTrue(yaml_hygiene_violations(broken), "the rule missed an unquoted ': '")

    def test_shell_inside_run_blocks_is_not_treated_as_yaml(self):
        text = 'jobs:\n  a:\n    steps:\n      - run: |\n          echo "x: y"\n'
        self.assertEqual(yaml_hygiene_violations(text), [])
