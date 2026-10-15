"""Exercise the one-time publication trigger against real Git history."""

import subprocess
import tempfile
import unittest
from pathlib import Path

from release_plan import INTENT, MAIN, newly_added_intent, plan


class ReleasePlanTest(unittest.TestCase):
    def test_dispatch_and_nonmain_can_never_become_public_accidentally(self):
        repository = Path(".")
        sha = "unused"
        self.assertEqual(("public", "false"), plan("workflow_dispatch", MAIN, "false", sha, repository))
        self.assertEqual(("test", "true"), plan("workflow_dispatch", MAIN, "true", sha, repository))
        self.assertEqual(("test", "true"), plan("workflow_dispatch", "refs/heads/arena/a18537b2-dhun", "false", sha, repository))
        self.assertEqual(("test", "true"), plan("pull_request", MAIN, "false", sha, repository))
        self.assertEqual(("test", "true"), plan("pull_request", "refs/pull/25/merge", "", sha, repository))
        self.assertEqual(("test", "true"), plan("push", "refs/heads/feature", "", sha, repository))
        with self.assertRaises(ValueError):
            plan("workflow_dispatch", MAIN, "", sha, repository)
        with self.assertRaises(ValueError):
            plan("workflow_dispatch", MAIN, "FALSE", sha, repository)

    def test_intent_only_on_commit_that_adds_exact_file(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)

            def git(*args):
                return subprocess.check_output(["git", *args], cwd=repo, text=True).strip()

            git("init", "-q")
            git("config", "user.name", "Release plan test")
            git("config", "user.email", "release-plan@example.invalid")
            (repo / "README.md").write_text("baseline\n")
            git("add", ".")
            git("commit", "-qm", "baseline")
            base = git("rev-parse", "HEAD")
            (repo / "README.md").write_text("ordinary main push\n")
            git("add", ".")
            git("commit", "-qm", "ordinary")
            ordinary = git("rev-parse", "HEAD")
            self.assertEqual(("test", "false"), plan("push", MAIN, "", ordinary, repo))
            intent = repo / INTENT
            intent.parent.mkdir(parents=True)
            intent.write_text("Publish v1.00.002 once after review and merge.\n")
            git("add", ".")
            git("commit", "-qm", "add reviewed intent")
            added = git("rev-parse", "HEAD")
            self.assertTrue(newly_added_intent(repo, added))
            self.assertEqual(("public", "false"), plan("push", MAIN, "", added, repo))
            self.assertEqual(("test", "true"), plan("pull_request", MAIN, "", added, repo))
            intent.write_text("change an existing intent\n")
            git("add", ".")
            git("commit", "-qm", "modify")
            modified = git("rev-parse", "HEAD")
            self.assertEqual(("test", "false"), plan("push", MAIN, "", modified, repo))
            (repo / "README.md").write_text("another ordinary main push\n")
            git("add", ".")
            git("commit", "-qm", "later")
            self.assertEqual(("test", "false"), plan("push", MAIN, "", git("rev-parse", "HEAD"), repo))
            self.assertNotEqual(base, added)


if __name__ == "__main__":
    unittest.main()
