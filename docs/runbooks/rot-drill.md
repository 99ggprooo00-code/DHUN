# Runbook: rot-drill / extraction-health (live extraction health check)

**Why this needs a human:** the drill workflow only runs on a
daily schedule or a manual dispatch. Agent sessions get `HTTP 403` on
`workflow_dispatch` (re-verified 2026-09-17), and the daily schedule
does not prove *this* commit — so confirming stream health for a
release is an operator task. Do it once per release candidate, on
`main`, after merge.

## State (2026-09-18): Home transport repaired; resolver gate remains

- **Current `main`:** `33e94b06125b8ce1eefe9aab0a2faca116ca53fe` (PR #90). Its baseline CI/test-release evidence remains separate from this unmerged candidate.
- **Current candidate:** PR **#91**, branch `arena/01a0b224-dhun`, head **`c71d1bb`**, is OPEN and unmerged. Android and Windows/Desktop production paths remain preserved and were not replaced.
- **Healthy drill:** `.github/workflows/extraction-health.yml`, workflow id **360655315**, is active and registered with the correct name `extraction-health`.
- **Latest candidate evidence:** push run **35325690972** tested `c71d1bb`. Its classifier step passed, the rot-drill issue step was skipped, and the final result was `ENVIRONMENT_BLOCKED`; only the intentional non-PASS gate failed. This means the prior Home-driven `FAIL` no longer controls the result. The resolver remains blocked by the GitHub runner's YouTube bot gate, so no live audio bytes were validated.
- **Request contract:** `InnerTubeClient` now matches the independent `ytmusicapi` comparison: `alt=json`, empty `context.user`, `browseId` in the body, `ctoken` and `continuation` in the query, and cached anonymous `X-Goog-Visitor-Id`. `HomeFeedParser.kt` remains unchanged.
- **Parser boundary:** a tab-only shell is still a genuine parse failure if encountered. Do not accept it as an exhausted page, follow its opaque endpoint, or add a speculative parser branch. The independent client supplied a real `continuationContents.sectionListContinuation` contract, so no parser change was needed.
- **Gate:** S1 remains open/RED pending approved residential/device playback evidence; S2 is blocked and PR #91 remains open/unmerged. Raw GitHub logs return `EOF` in this sandbox, the agent cannot dispatch the workflow (HTTP 403), and no local Gradle test ran because no JDK is installed.

## Dispatch

The procedures below are retained for a materially new candidate or an approved
residential/device validation. Do not repeat the same owner-triggered run merely
to loop on the current result; this investigation already has the distinct
request-contract comparison and run **35325690972**.

**Path A — Actions UI (the repo owner's path):**

1. Open `https://github.com/99ggprooo00-code/DHUN/actions/workflows/extraction-health.yml`
   (repo → **Actions** → **extraction-health** in the left sidebar — the
   workflow's declared name is `extraction-health`; sidebar label should
   reflect that name when registration is healthy).
2. Click **Run workflow** (right side, above the runs list). For the current
   PR candidate validation select **`arena/01a0b224-dhun`** (not `main`),
   then click the green **Run workflow** button.
3. A new run appears at top within ~30 s. Click it, watch **probe** job
   (≈5–12 min; installs yt-dlp and runs offline + live probes).

**Legacy path (retired):** the former `rot-drill-daily.yml` file is deleted
and its wedged registry entry is orphaned. Ignore any historical URL or
zero-job artifact from that path; use `extraction-health` instead.

**Path B — owner-account API dispatch (one-liner; agent tokens get
403):**

```bash
# Current PR candidate validation:
gh workflow run extraction-health.yml --ref arena/01a0b224-dhun --repo 99ggprooo00-code/DHUN
# After the candidate is merged, release-baseline validation uses main:
# gh workflow run extraction-health.yml --ref main --repo 99ggprooo00-code/DHUN
# or by declared name / workflow id with the same --ref:
# gh workflow run extraction-health --ref arena/01a0b224-dhun --repo 99ggprooo00-code/DHUN
# gh workflow run 360655315 --ref arena/01a0b224-dhun --repo 99ggprooo00-code/DHUN
```

## Reading the verdict

- **`PROBE|verdict|PASS`** and a successful workflow = all required live and offline checks passed on that runner. Only this is a health pass; the parser candidate still needs this evidence before S1 closes.
- **`PROBE|verdict|ENVIRONMENT_BLOCKED`** = explicit YouTube runner bot-gating/authentication evidence. The workflow remains non-zero and does not close a rot issue, because live stream health was not established. Verify residential/device playback; do not introduce cookies, sign-in, PO tokens, BotGuard, attestation, or ADR-007.
- **`PROBE|verdict|UNAVAILABLE`** = external live service/network did not provide a health result. It is not a DHUN parser verdict, but it is also not a pass.
- **`PROBE|verdict|FAIL`** = a production-path/probe check failed. Home feed or continuation parser errors are always in this category. The workflow opens/comments on issue `[rot-drill] Live extraction probe failed` with the last 12 KB and retains the full 14-day artifact.
- A separate `WATCH|newpipe-stream|BROKEN|Parse(JSON response is too short)` line is diagnostic only. NewPipe is not in either production resolver chain and must not be folded into Home or own-client/yt-dlp conclusions.
- Historical run **35321898985** exposed the tab-only shell; candidate run **35325690972** then classified `ENVIRONMENT_BLOCKED` after the request-contract repair. The independent client supplied a real `continuationContents.sectionListContinuation` contract, so no parser branch was needed. A tab-only shell remains invalid, and raw logs for the latest job still return `EOF` in this sandbox.
- **Gray / skipped probe job** = run never started (concurrency cancel). Re-dispatch.
- **Red run with ZERO jobs** (failure but jobs list empty, total_count:0) = trigger noise, not verdict — see below.

## Observed anomalies (history)

- **The workflow is absent from the Actions UI entirely (2026-09-17,
  user report for rot-drill):** no list entry and no Run-workflow button,
  while REST reports `state: active` (id 348098190) and
  `gh workflow list` shows registry name as file path — same decay that killed schedule.

  **Fix attempts:**
  1. Comment-only edit (PR #77 = `3ff3a55`, merged 2026-09-17 01:03 UTC) — FAILED.
     Registry entry 348098190 untouched: name still file path, updated_at frozen,
     phantom 0-job push run 35171317970, button absent.
  2. Delete + verbatim re-add (PR #78 = `df6a0be`, then PR #79 = `56324f5`,
     merged ~02:21 UTC) — FAILED. Delete dropped entry ~90 s, but re-add
     reattached SAME id 348098190 by file path. Name stayed file path,
     phantom push 35174080320.
  3. Support ticket SUBMITTED (~03:15 UTC). Diagnostic page confirmed
     "stale/corrupted workflow registration". Request originally targeted
     348098190; after PR #84 new wedged id 360227450 (path rot-drill-daily.yml)
     is active target. Both failing to read name: field and firing phantom pushes.
  3b. Rename to new file path (PR #84, merged ~05:40 UTC) — FAILED. New id
      360227450 created but ALSO wedged (path-as-name, phantom 35186690348).
  4. **New distinct file extraction-health.yml (PR #88, merged `3c593fb`
     ~15:40 UTC) — SUCCESS.** Id 360655315, name correctly extraction-health,
     no phantom push on merge. Bypassed path-specific corruption.

- **0-job push runs are noise, not verdicts.** Every push since 2026-09-07
  05:56 UTC created a rot-drill run with event: push, 0 jobs, conclusion: failure,
  failure_reason: null — although file has no push: trigger in any version.
  Such runs carry no probe output and must never be cited as extraction evidence.

- **Schedule silent since 2026-09-07 04:28 UTC** (run 34083253658). Fired daily
  09-02 → 09-07 (4 green, 2 red — both pre-#57 chain); ≥10 missed windows
  09-08 → 09-17 passed without scheduled run despite daily pushes.

- **Agent dispatch is 403** (gh workflow run → HTTP 403), re-verified 2026-09-17.
  Run-workflow click stays operator task. Agent token also cannot comment on
  issue #14 (403), but workflow itself updates issue on next live run.

## Recording the evidence

For a release candidate, append one line to
`docs/verification/14-release.md` ("Live evidence log" → "Rot-drill"
section):

```
- extraction-health <run_id> (<YYYY-MM-DD HH:MM UTC>, main@<sha>): GREEN — <note>
- extraction-health 35306224822 (2026-09-18 04:17 UTC, main@33e94b0): RED / mixed — Home continuation parse failure plus runner bot-gating; artifact rot-drill-35306224822; S1 remains open.
```

A recorded RED is evidence, not the S1 exit criterion. S1 exit requires the
mixed findings to be reconciled and a later accepted verdict; no tag without
that evidence.
