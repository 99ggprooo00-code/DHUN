# Runbook: rot-drill (live extraction health check)

**Why this needs a human:** the `rot-drill` workflow only runs on a
daily schedule or a manual dispatch. Agent sessions get `HTTP 403` on
`workflow_dispatch` (re-verified 2026-09-17), and the daily schedule
does not prove *this* commit — so confirming stream health for a
release is an operator task. Do it once per release candidate, on
`main`, after merge.

## State (2026-09-17): the workflow is MISSING from the Actions UI

The Actions UI shows no `rot-drill` entry at all (no list entry, no
Run-workflow button), while the REST registry still reports it
`state: active` (workflow id **348098190**, name stuck at the file
path). Until the re-registration fix (comment-only edit to the
workflow file, user-approved 2026-09-17) restores the UI entry, the
UI path below does not exist — use the API path (any account with
owner rights + a `workflow`-scoped PAT) or wait for re-registration.

## Dispatch

**Path A — Actions UI (the repo owner's path; needs re-registration
first):**

1. Open `https://github.com/99ggprooo00-code/DHUN/actions/workflows/rot-drill.yml`
   (repo → **Actions** → **rot-drill** in the left sidebar).
2. Click **Run workflow** (right side, above the runs list); leave
   **Branch: main** selected (there are no input fields); click the
   green **Run workflow** button in the dropdown.
3. A new run appears at the top within ~30 s. Click it, then watch the
   **probe** job (≈5–12 min; it installs `yt-dlp` and runs the offline +
   live playback probes).

**Path B — owner-account API dispatch (one-liner; agent tokens get
403):**

```bash
gh workflow run rot-drill.yml --ref main --repo 99ggprooo00-code/DHUN
# or, with a workflow-scoped PAT:
# curl -X POST -H "Authorization: Bearer <PAT>" \
#   -H "Accept: application/vnd.github+json" \
#   https://api.github.com/repos/99ggprooo00-code/DHUN/actions/workflows/348098190/dispatches \
#   -d '{"ref":"main"}'    # success = HTTP 204, empty body
```

## Reading the verdict

- **Green run** = extraction healthy on this commit. Nothing else to do;
  if a `[rot-drill]` issue was open, the workflow comments and closes it
  automatically.
- **Red run** = open the run → **probe** job → expand **Run playback
  probes (offline + live)**:
  - `LOGIN_REQUIRED` / *"Sign in to confirm you're not a bot"* =
    YouTube is gating the GitHub runner's datacenter IP. This does **not**
    prove user breakage: verify playback on a residential connection
    (your phone on mobile data, see `s3-hardware-checklist.md`) before
    treating it as rot.
  - Anything else (parse errors, 400s, `sts` failures across all client
    profiles) = probable extractor rot. The workflow opens (or comments
    on) an issue titled `[rot-drill] Live extraction probe failed` with
    the last 12 KB of log; the full `rot-drill.log` is on the run under
    **Artifacts** (`rot-drill-<run_id>`, 14-day retention). Paste the
    issue number back to the agent — that is the S1 handoff.
- **Gray / skipped `probe` job** = the run never started (concurrency
  cancel or runner outage). Re-dispatch; if it repeats, report it like
  any CI outage, not like rot.
- **Red run with ZERO jobs** (run shows `failure` but the jobs list is
  empty, `total_count: 0`) = trigger noise, not a verdict — see below.

## Observed anomalies (reconciled 2026-09-16 — read before dispatching)

Verified against the full 189-run Actions history (GitHub API, session
`arena/01a0ac91-dhun`); UI-absence evidence added 2026-09-17:

- **The workflow is absent from the Actions UI entirely (2026-09-17,
  user report):** no list entry and no Run-workflow button, while the
  REST registry reports `state: active` (id 348098190) and
  `gh workflow list` shows the registry name as the file path — the
  same registration decay that killed the schedule also dropped the
  UI entry. Planned fix: comment-only edit to this workflow file on
  `main` to force re-registration (user-approved 2026-09-17);
  fallback = GitHub support ticket (workflow id 348098190, last
  scheduled run 34083253658, missed windows 09-08 → 09-16, absent
  from UI but active in registry).
- **0-job push runs are noise, not verdicts.** Every push since
  2026-09-07 05:56 UTC created a `rot-drill` run with `event: push`,
  **0 jobs**, `conclusion: failure`, `failure_reason: null` — although
  the workflow file has **no `push:` trigger in any version** (verified
  at SHAs from 2026-09-01 through `c5b1793`). Such runs carry no probe
  output, open or comment on no issue, and must never be cited as
  extraction evidence (e.g. 35135427771 on the 2026-09-16 merge push).
- **The schedule has been silent since 2026-09-07 04:28 UTC** (run
  34083253658). It fired every day 09-02 → 09-07 (4 green, 2 red — both
  red pre-#57 chain); 9 further 04:17 UTC windows (09-08 → 09-16) never
  fired despite daily pushes to `main`. Workflow registry `state:
  active` (id **348098190**); file unchanged since `da9d779` (09-07
  06:17). Working diagnosis: GitHub-side trigger-registration anomaly
  around that edit (the registry name still shows the file path, not
  `rot-drill`).
- **Before and after dispatching, check the schedule too:** once the
  UI entry is restored, confirm the workflow is enabled on that page.
  If the next 04:17 UTC window is missed after the manual run, the
  schedule needs restoring: force re-registration (trivial commit to
  `main` + one manual run — user-approved 2026-09-17) or a GitHub
  support ticket citing workflow id 348098190, last scheduled run
  34083253658, and the missed windows.
- **Agent dispatch is 403** (`gh workflow run` → `HTTP 403: Resource
  not accessible by integration`), re-verified 2026-09-17. The *Run
  workflow* click stays an operator task — and per handoff v2
  (2026-09-17) the user can only click UI buttons (merge, Run
  workflow), so Path B above is for a future operator with a PAT, not
  the current user. An agent token also cannot comment on issue #14
  (issue-comment writes 403), but the workflow itself updates the
  issue on the next live run.

## Recording the evidence

For a release candidate, append one line to
`docs/verification/14-release.md` ("Live evidence log" → "Rot-drill"
section):

```text
- rot-drill <run_id> (<YYYY-MM-DD HH:MM UTC>, main@<sha>): GREEN — <one-line note>
```

That line is the S1 exit criterion: no tag without it.
