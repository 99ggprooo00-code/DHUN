# Runbook: rot-drill / extraction-health (live extraction health check)

**Why this needs a human:** the drill workflow only runs on a
daily schedule or a manual dispatch. Agent sessions get `HTTP 403` on
`workflow_dispatch` (re-verified 2026-09-17), and the daily schedule
does not prove *this* commit — so confirming stream health for a
release is an operator task. Do it once per release candidate, on
`main`, after merge.

## State (2026-09-20): drill fires daily, verdict is ENVIRONMENT_BLOCKED; S1 waits on device evidence

- **Current `main`:** `7304abb8d08439a073d50c38e78f7b0e1dfe0381` (PR #94 merge, 2026-09-20T15:14:02Z — docs-only, on top of PR #93 `fabeb5f`, PR #92 `39b8748`, PR #91 `6f7fa48`); the release baseline this runbook documents. Post-merge push CI green on that SHA: CI 35518928489 · Build APK 35518928490 · test-release 35518928487 (PR #93's SHA `fabeb5f` was likewise green: 35513643996 / 35513643853 / 35513643918). Rolling `test` release targets exactly `7304abb` (published 2026-09-20T15:19:07Z; `dhun-test.apk` 17,948,508 B, `dhun-test.msi` 112,861,184 B + both `.sha256` sidecars — sizes unchanged by docs-only merges).
- **PR #91 (Home continuation request-contract repair) is MERGED** as `6f7fa48` (2026-09-18T14:06Z); PR #92 and PR #93 are docs reconciles on top. Android and Windows/Desktop production extraction paths were preserved, not replaced.
- **Healthy drill:** `.github/workflows/extraction-health.yml`, workflow id **360655315**, is active and registered with the correct name `extraction-health`.
- **Latest scheduled evidence (re-verified 2026-09-20):** runs **35421383687** (2026-09-19 04:28:36Z) and **35489268023** (2026-09-20 04:29:25Z) both tested `main@6f7fa48` — both fired *before* PR #92 merged — and both classified **`ENVIRONMENT_BLOCKED`** (read from the check-run annotation, job 106021243260 for the 09-20 run). Offline + metadata + Home + search + related stages pass; the resolver is bot-gated on the runner's datacenter IP, so no live audio bytes are validated there. Only the intentional non-PASS gate fails (exit 2). The prior Home-driven `FAIL` no longer controls the result: `6f7fa48` (PR #91) carries the request-contract repair.
- **Request contract:** `InnerTubeClient` now matches the independent `ytmusicapi` comparison: `alt=json`, empty `context.user`, `browseId` in the body, `ctoken` and `continuation` in the query, and cached anonymous `X-Goog-Visitor-Id`. `HomeFeedParser.kt` remains unchanged.
- **Parser boundary:** a tab-only shell is still a genuine parse failure if encountered. Do not accept it as an exhausted page, follow its opaque endpoint, or add a speculative parser branch. The independent client supplied a real `continuationContents.sectionListContinuation` contract, so no parser change was needed.
- **Gate:** S1 remains open/RED pending residential/device playback evidence; S2 stays blocked. The first scheduled run on `main@7304abb` is due 2026-09-21 04:17 UTC (observed starts land ~04:28–04:30 UTC). Raw GitHub logs return `EOF` in this sandbox, the agent cannot dispatch the workflow (HTTP 403), and no local Gradle test ran because no JDK is installed.

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
2. Click **Run workflow** (right side, above the runs list). Select **`main`** —
   the scheduled drill already runs there daily, so dispatch only when a
   materially new candidate needs an immediate verdict. (Earlier guidance to
   pick `arena/01a0b224-dhun` is obsolete: that branch was PR #91, merged as
   `6f7fa48` on 2026-09-18.) Then click the green **Run workflow** button.
3. A new run appears at top within ~30 s. Click it, watch **probe** job
   (≈5–12 min; installs yt-dlp and runs offline + live probes).

**Legacy path (retired):** the former `rot-drill-daily.yml` file is deleted
and its wedged registry entry is orphaned. Ignore any historical URL or
zero-job artifact from that path; use `extraction-health` instead.

**Path B — owner-account API dispatch (one-liner; agent tokens get
403):**

```bash
# Release-baseline validation — the normal case. main is what the schedule
# already tests daily, so dispatch only for a materially new candidate:
gh workflow run extraction-health.yml --ref main --repo 99ggprooo00-code/DHUN
# By declared name or workflow id with the same --ref:
# gh workflow run extraction-health --ref main --repo 99ggprooo00-code/DHUN
# gh workflow run 360655315 --ref main --repo 99ggprooo00-code/DHUN
```

## Reading the verdict without job logs (sandbox recipe)

There is no local JDK and job/artifact blob downloads return `EOF` in the agent
sandbox, so the verdict is read from two REST endpoints — both work with the
agent token, and both are what ROADMAP evidence cites:

```bash
run=35489268023                                                    # any run id
job=$(gh api repos/99ggprooo00-code/DHUN/actions/runs/$run/jobs --jq '.jobs[0].id')
gh api "repos/99ggprooo00-code/DHUN/actions/jobs/$job" \
  --jq '.steps[] | [.number,.name,.conclusion] | @tsv'            # per-step truth
gh api "repos/99ggprooo00-code/DHUN/check-runs/$job/annotations" \
  --jq '.[] | [.annotation_level,(.title//"-"),.message] | @tsv'  # classification
```

Step shape of a correctly-blocked run (verified on 35489268023, job
106021243260): steps 1–10 `success` — the probe step is `continue-on-error`, so
a non-zero `:tools:playback-probe:run` is parsed from `PROBE|workflow-status` by
the classifier instead of showing as a red step; the two issue steps `skipped`;
the final gate step (`Keep the check non-zero when live health is unverified`)
is the only failure, exit code **2**. That pattern means *the drill works and
live health is unverified* — it is not a DHUN failure. A red step **before**
`Classify probe result` means something else broke (checkout, toolchain,
Gradle) and is a CI defect, not an extraction verdict.

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
