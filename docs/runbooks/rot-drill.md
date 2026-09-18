# Runbook: rot-drill / extraction-health (live extraction health check)

**Why this needs a human:** the drill workflow only runs on a
daily schedule or a manual dispatch. Agent sessions get `HTTP 403` on
`workflow_dispatch` (re-verified 2026-09-17), and the daily schedule
does not prove *this* commit — so confirming stream health for a
release is an operator task. Do it once per release candidate, on
`main`, after merge.

## State (2026-09-18 ~04:17 UTC): owner run completed RED; S1 remains open

- **Current `main`:** `33e94b06125b8ce1eefe9aab0a2faca116ca53fe` (PR #90). CI **35246193151**, Build APK **35246193174**, and test-release **35246193097** pass; the rolling `test` release targets this SHA and has APK/MSI plus both checksum sidecars.
- **Current healthy drill:** `.github/workflows/extraction-health.yml`, workflow id **360655315**, is active and registered with the correct name `extraction-health`. Owner-dispatched run **35306224822** completed `failure` and uploaded artifact `rot-drill-35306224822` (id **10532130174**, 4,357 B).
- **Current result:** offline/metadata/search/related passed, but `home-more` failed with `Parse(detail=Home response contained no section list or Home continuation action)`. Own-client and yt-dlp returned `AuthRequired` / `LOGIN_REQUIRED` bot-gating; no audio bytes were validated. NewPipe separately returned `Parse(detail=JSON response is too short)`. This is a mixed RED, not an S1 pass.
- **Required classification:** treat the resolver `AuthRequired` as GitHub-runner network evidence requiring approved residential/device verification. Investigate the Home continuation parse failure independently; do not add cookies, sign-in, PO tokens, BotGuard, attestation, or ADR-007. The raw artifact download returned `EOF` in the sandbox; issue #14's workflow comment and artifact metadata remain the recorded evidence.
- **Parser candidate:** PR #91 now supports shelf-specific continuation wrappers/renderers and logs only response keys when no supported Home shape is found. This is a source/test candidate, not live acceptance; wait for CI and a candidate-branch probe or sanitized payload before classifying `home-more` as fixed.
- **Dispatch limitation:** the agent still receives HTTP 403 for `workflow_dispatch` and cannot write issue comments. The owner-triggered run is authoritative; no second run is needed until the Home response shape and network classification are addressed.
- **Support ticket #4765894** may be closed as resolved by the distinct workflow path; it is not a prerequisite for the mixed-RED investigation.

## Dispatch

**Path A — Actions UI (the repo owner's path):**

1. Open `https://github.com/99ggprooo00-code/DHUN/actions/workflows/extraction-health.yml`
   (repo → **Actions** → **extraction-health** in the left sidebar — the
   workflow's declared name is `extraction-health`; sidebar label should
   reflect that name when registration is healthy).
2. Click **Run workflow** (right side, above the runs list); leave
   **Branch: main** selected; click green **Run workflow**.
3. A new run appears at top within ~30 s. Click it, watch **probe** job
   (≈5–12 min; installs yt-dlp and runs offline + live probes).

**Legacy path (retired):** the former `rot-drill-daily.yml` file is deleted
and its wedged registry entry is orphaned. Ignore any historical URL or
zero-job artifact from that path; use `extraction-health` instead.

**Path B — owner-account API dispatch (one-liner; agent tokens get
403):**

```bash
gh workflow run extraction-health.yml --ref main --repo 99ggprooo00-code/DHUN
# or by declared name:
# gh workflow run extraction-health --ref main --repo 99ggprooo00-code/DHUN
# or with workflow id:
# gh workflow run 360655315 --ref main --repo 99ggprooo00-code/DHUN
```

## Reading the verdict

- **Green run** = extraction healthy on this commit. Nothing else to do;
  if a `[rot-drill]` issue was open, the workflow comments and closes it
  automatically.
- **Red run** = open run → **probe** job → expand **Run playback probes**:
  - `LOGIN_REQUIRED` / "Sign in to confirm you're not a bot" = YouTube
    gating GitHub runner datacenter IP. Not proof of user breakage: verify
    residential playback (phone on mobile data, see s3-hardware-checklist.md)
    before treating as rot. Do not introduce cookies, sign-in, PO tokens,
    BotGuard, attestation, or ADR-007 to make this runner green.
  - A separate parser error must not be folded into the bot-gating result.
    For run **35306224822**, `home-more` failed with
    `Parse(detail=Home response contained no section list or Home continuation action)`;
    capture the raw/sanitized continuation shape before changing the parser.
  - Anything else = probable extractor rot. Workflow opens/comments on
    issue `[rot-drill] Live extraction probe failed` with last 12 KB log;
    full log artifact `rot-drill-<run_id>`, 14-day retention. A blob-download
    failure in an agent sandbox does not mean the artifact is absent when the
    artifact API and issue comment confirm it exists.
- **Gray / skipped probe job** = run never started (concurrency cancel).
  Re-dispatch.
- **Red run with ZERO jobs** (failure but jobs list empty, total_count:0)
  = trigger noise, not verdict — see below.

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
