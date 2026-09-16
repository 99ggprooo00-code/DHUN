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

## Recording the evidence

For a release candidate, append one line to
`docs/verification/14-release.md` (Current status section):

```text
- rot-drill <run_id> (<YYYY-MM-DD HH:MM UTC>, main@<sha>): GREEN — <one-line note>
```

That line is the S1 exit criterion: no tag without it.
