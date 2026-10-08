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
        ):
            self.assertIn(f'"{path}"', self.text, f"{path} does not trigger the site workflow")

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
        self.assertRegex(self.job("a11y"), r"playwright install --with-deps chromium")

    def test_browser_measurements_run(self):
        job = self.job("a11y")
        self.assertIn("node tests/browser.mjs", job)
        self.assertIn("SITE_BASE", job)

    def test_axe_cli_is_retired(self):
        """`@axe-core/cli` never produced a report; axe runs inside the browser job.

        The comment above the browser step still names it — that is the record of
        why it went — so this asserts on the invocation, not on the string.
        """
        self.assertNotRegex(self.text, r"npx[^\n]*@axe-core/cli")

    def test_screenshots_are_uploaded_as_artifacts(self):
        job = self.job("a11y")
        self.assertIn("browser-evidence", job)
        self.assertIn("website/tests/screenshots", job)

    def test_lighthouse_still_reports(self):
        self.assertIn("scripts/report_lighthouse.py", self.job("a11y"))


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
