# Runbook: rot-drill (live extraction health check)

**Why this needs a human:** the `rot-drill` workflow only runs on a daily
schedule or a manual *Run workflow* click. Agent sessions get `HTTP 403`
on `workflow_dispatch`, and the daily schedule does not prove *this*
commit — so confirming stream health for a release is a 3-minute operator
task. Do it once per release candidate, on `main`, after merge.

## Dispatch (exact clicks)

1. Open `https://github.com/99ggprooo00-code/DHUN/actions/workflows/rot-drill.yml`
   (repo → **Actions** → **rot-drill** in the left sidebar).
2. Click **Run workflow** (right side, above the runs list).
3. Leave **Branch: main** selected. There are no input fields.
4. Click the green **Run workflow** button in the dropdown.
5. A new run appears at the top within ~30 s. Click it, then watch the
   **probe** job (≈5–12 min; it installs `yt-dlp` and runs the offline +
   live playback probes).

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
`arena/01a0ac91-dhun`):

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
- **Before and after dispatching, check the schedule too:** on the same
  Actions page confirm the workflow is enabled. If the next 04:17 UTC
  window is missed after your manual run, the schedule needs
  restoring: force re-registration (trivial commit to `main` + one
  manual run — a workflow change, needs the user's OK per S1) or a
  GitHub support ticket citing workflow id 348098190, last scheduled
  run 34083253658, and the missed windows.
- **Agent dispatch is 403** (`gh workflow run` → `HTTP 403: Resource
  not accessible by integration`), re-verified 2026-09-16. The *Run
  workflow* click stays an operator task; an agent token also cannot
  comment on issue #14 (issue-comment writes 403), but the workflow
  itself updates the issue on the next live run.

## Recording the evidence

For a release candidate, append one line to
`docs/verification/14-release.md` (Current status section):

```text
- rot-drill <run_id> (<YYYY-MM-DD HH:MM UTC>, main@<sha>): GREEN — <one-line note>
```

That line is the S1 exit criterion: no tag without it.
