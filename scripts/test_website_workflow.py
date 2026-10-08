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

    def test_build_proves_the_browser_rules_without_a_browser(self):
        """The browser job's decision logic must be mutation-proven in the job
        that has Node and no browser: a rule that stops firing has to fail
        somewhere cheap, not only on a runner that installs Chromium."""
        job = self.job("build")
        self.assertIn("npm run test:rules", job)

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


class CanonicalUrlIsReported(WorkflowText):
    """The URL a reader visits first is not the site yet, and every run says so.

    The deploy job has always skipped its publish steps with a warning while
    Pages' source is the branch (`build_type: legacy`), which is right — but it
    only runs on a push to main, so a pull request, a visitor and the run summary
    all saw nothing. Meanwhile the canonical URL renders the repository README
    through legacy Jekyll, and README.md advertised it as the marketing site: the
    defect was not the behaviour, it was the silence.

    These tests pin the three things that make the fact survive: it is reported
    in the *build* job (which runs on every trigger and fails nothing), it names
    the exact setting and the runbook, and it stays a warning rather than an
    error — a misconfiguration an agent cannot fix must never redden a build that
    is otherwise green.
    """

    def test_build_job_reports_the_pages_source_on_every_run(self):
        job = self.job("build")
        self.assertIn("build_type", job)
        self.assertIn("GITHUB_STEP_SUMMARY", job)
        self.assertRegex(job, r"::warning title=The canonical URL is not this site yet")

    def test_the_report_names_the_setting_and_the_runbook(self):
        job = self.job("build")
        self.assertIn("Settings -> Pages", job)
        self.assertIn("GitHub Actions", job)
        self.assertIn("docs/runbooks/publishing-the-site.md", job)

    def test_the_report_is_a_warning_not_an_error(self):
        self.assertNotIn("::error title=The canonical URL", self.job("build"))

    def test_deploy_job_still_owns_the_publish_gate(self):
        job = self.job("deploy")
        self.assertIn("steps.pages_source.outputs.build_type == 'workflow'", job)


class PublishingRunbook(unittest.TestCase):
    """The runbook a reader needs when the warning tells them to go read it."""

    def test_the_runbook_exists_and_names_the_exact_setting(self):
        runbook = REPO_ROOT / "docs" / "runbooks" / "publishing-the-site.md"
        self.assertTrue(runbook.is_file(), "the Pages warning points at a missing runbook")
        text = runbook.read_text(encoding="utf-8")
        for phrase in ("Build and deployment", "GitHub Actions", "build_type", "website/dist"):
            self.assertIn(phrase, text)

    def test_every_doc_that_promises_the_url_is_the_site_says_when_it_is_not(self):
        """A doc may call the URL the site only if it also says what gates that."""
        readme = (REPO_ROOT / "README.md").read_text(encoding="utf-8")
        section = readme.split("## Website", 1)[1].split("\n## ", 1)[0]
        if "99ggprooo00-code.github.io/DHUN" in section:
            self.assertIn("legacy", section.lower())


class BrowserHarnessSurvivesAndDiagnoses(unittest.TestCase):
    """The browser harness must never die silently in CI.

    On 2026-10-08 the first run of the new browser checks (website run
    37814413312) failed with nothing in the readable channel but "Process
    completed with exit code 1": `forcedColorsReport`, a function serialized
    *into the page* by `page.evaluate`, called `forcedColorsBoundaryMissing`,
    which is imported from `./rules.mjs` in Node scope and therefore undefined
    inside the page — a ReferenceError in the page rejected the evaluate, the
    script threw before it annotated anything, and no screenshots were written
    either. Two rules prevent the repeat, and both are asserted here.
    """

    @classmethod
    def setUpClass(cls):
        cls.src = (REPO_ROOT / "website" / "tests" / "browser.mjs").read_text(encoding="utf-8")

    # -- helpers -----------------------------------------------------------

    @staticmethod
    def imported_rules(src: str) -> set[str]:
        """Names imported from ./rules.mjs — Node scope, not page scope."""
        match = re.search(r"import\s*\{([^}]*)\}\s*from\s*[\"']\./rules\.mjs[\"']", src)
        assert match, "browser.mjs no longer imports ./rules.mjs — update this guard"
        return {name.strip() for name in match.group(1).split(",") if name.strip()}

    @staticmethod
    def evaluate_by_name(src: str) -> set[str]:
        return set(re.findall(r"page\.evaluate\(\s*(\w+)\s*\)", src))

    @staticmethod
    def body_of(src: str, name: str) -> str:
        """A top-level function body, by its closing brace at column zero."""
        start = src.index(f"function {name}(")
        end = src.index("\n}", start)
        return src[start:end]

    @staticmethod
    def strip_comments(text: str) -> str:
        text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        return re.sub(r"//[^\n]*", "", text)

    # -- the two rules -----------------------------------------------------

    def test_functions_serialised_into_the_page_are_self_contained(self):
        """A page function may only use page scope: no imports, no closures."""
        imported = self.imported_rules(self.src)
        checked = 0
        for name in sorted(self.evaluate_by_name(self.src)):
            body = self.strip_comments(self.body_of(self.src, name))
            for symbol in sorted(imported):
                self.assertNotIn(
                    symbol,
                    body,
                    f"{name} is passed to page.evaluate by name and references {symbol!r}, "
                    f"which exists only in Node scope: the page throws ReferenceError and the "
                    f"whole run dies before any annotation. Return the raw values and apply "
                    f"the rule in Node.",
                )
            checked += 1
        self.assertGreaterEqual(checked, 5, "no page functions found — this guard went stale")

    def test_every_check_runs_behind_the_crash_guard(self):
        checks = re.findall(r"^async function (check\w+)\(", self.src, re.M)
        self.assertGreaterEqual(len(checks), 5, "no check functions found — this guard went stale")
        block = self.src[self.src.index("const CHECKS = [") : self.src.index("let browser;")]
        for name in checks:
            self.assertIn(
                f"() => {name}(browser)",
                block,
                f"{name} is defined but never runs: a check that is not in CHECKS is not a check",
            )

    def test_the_guard_records_and_the_annotations_always_run(self):
        guard = self.body_of(self.src, "guard")
        self.assertRegex(guard, r"catch\s*\(error\)")
        self.assertIn("fail(", guard, "a crash must be recorded as a failure, not thrown away")
        finally_block = self.src[self.src.index("} finally {") :]
        self.assertIn(
            "emitAnnotations();",
            finally_block,
            "the annotations must be emitted from the finally block: Actions log archives are "
            "unreadable from this environment, so dropping them drops the evidence",
        )

    def test_the_page_gathers_and_rules_decide(self):
        """The architecture the crash broke: gather in the page, decide in Node."""
        rules = (REPO_ROOT / "website" / "tests" / "rules.mjs").read_text(encoding="utf-8")
        for symbol in self.imported_rules(self.src):
            self.assertIn(f"export function {symbol}", rules, f"{symbol} is imported but not exported")


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
