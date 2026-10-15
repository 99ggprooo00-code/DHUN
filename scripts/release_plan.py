"""Choose the release channel before any build job starts.

A reviewed, newly added intent file is the one-shot alternative to Actions
workflow_dispatch for v1.00.002. Merely retaining or editing the file on later
main pushes must not publish another build. PRs never publish.
"""

import argparse
import subprocess
from pathlib import Path

INTENT = ".github/release-intent/v1.00.002"
MAIN = "refs/heads/main"


def newly_added_intent(repository: Path, sha: str) -> bool:
    # A merge commit's first parent is the previous main tip. Compare against
    # that parent, not against the PR branch or the entire commit history.
    result = subprocess.run(
        ["git", "diff", "--diff-filter=A", "--name-only", f"{sha}^1", sha, "--", INTENT],
        cwd=repository,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.splitlines() == [INTENT]


def plan(event: str, ref: str, manual_build_only: str, sha: str, repository: Path) -> tuple[str, str]:
    if event == "workflow_dispatch":
        if manual_build_only not in ("true", "false"):
            raise ValueError("A manual build must explicitly supply the typed build_only input")
        public = ref == MAIN and manual_build_only == "false"
        return ("public", "false") if public else ("test", "true")
    if event == "push" and ref == MAIN:
        return ("public", "false") if newly_added_intent(repository, sha) else ("test", "false")
    return "test", "true"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--event", required=True)
    parser.add_argument("--ref", required=True)
    parser.add_argument("--manual-build-only", default="")
    parser.add_argument("--sha", required=True)
    parser.add_argument("--repository", type=Path, default=Path("."))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    channel, build_only = plan(args.event, args.ref, args.manual_build_only, args.sha, args.repository)
    # This plan is a gate: build/publish jobs must depend on its success.
    with args.output.open("a") as output:
        output.write(f"channel={channel}\nbuild_only={build_only}\n")
    print(f"Release plan: channel={channel}, build_only={build_only}")


if __name__ == "__main__":
    main()
