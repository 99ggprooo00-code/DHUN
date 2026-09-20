# RISK_REGISTER

Reconciled 2026-09-16 (re-baseline, base `d555959`). Standing risks
first; retired risks at the bottom (kept, not deleted).

| Risk | Likelihood | Detects via | Pre-agreed response |
|---|---|---|---|
| Own-client chain rot (visitorData/sts sourcing, identity gating, client-version drift) | High, recurring | **Residential** playback failures are the real detector; the runner drill only detects `FAIL`-class breakage (it is permanently `ENVIRONMENT_BLOCKED` by datacenter gating) | Client bump → wave maintenance → patch release ≤72h; ≥14d all-red → kill-switch stop-and-decide; triggers T1/T2 gate any ADR-007 work. Baseline: **2026-09-20 residential PASS on `main@d99060e`** (4 songs, Android + Windows) — compare future reports against that. |
| Drill verdict misread as product breakage (`ENVIRONMENT_BLOCKED` ≠ rot) | Medium — it nearly happened | Compare the probed SHA with the SHA that played on hardware (`gh api compare/<a>...<b>`); a docs-only diff means the network is the variable | Never start extraction/ADR-007 work off a runner verdict alone. Require a residential failure. Recorded in DEBUG_LOG 2026-09-20 |
| Residential evidence goes stale (it is a point-in-time sample, and extraction rots) | Medium | Any extraction-code change after 2026-09-20, or a user report of silence/stalls | Re-run `docs/runbooks/s1-residential-evidence.md` on the new build before the v0.1.0 tag; docs-only merges do **not** invalidate the existing evidence |
| NewPipe upstream recovery missed (watch line is drill-dependent) | Medium | drill watch line (once S1 restores it) | Re-enter as implementation option per ADR-001 |
| SMTC via JNA unstable on real Windows | Medium | S3 hardware round-trip | Ship documented fallback (tray + media keys); record in KNOWN_LIMITATIONS |
| Hardware gates skipped under schedule pressure (soaks, clean installs, SMTC, offline playback) | Medium | S3/S6 checklists | No v0.1.0 tag without signed checklists — the gate list in ROADMAP §6 is literal |
| Real blur unavailable (Android <31) | Certain | shipped | Dark fallback (PR #70); pre-blurred bitmap stays a v2 idea |
| Compose MP / Ktor / Coil regression | Low | CI | Pin versions; upgrade one dependency at a time |
| GPL compliance drift | Low | S5 review | THIRD_PARTY.md per release; no incompatible deps enter |

## Retired (kept for history)

| Risk | Retired | Why |
|---|---|---|
| Rot drill not running — schedule silent since 2026-09-07; push entries were 0-job noise | 2026-09-20 (S1 GREEN) | Daily `extraction-health` (id 360655315, cron `17 4 * * *`) has fired on schedule since PR #88; runs 35421383687 / 35489268023 classify honestly. The remaining half — proof outside the runner — landed as the user's residential test on `main@d99060e`. |
| `main` unbuildable (`073083c` compile break + wrong `:app` module) | 2026-09-10 (PR #56) | Repaired + regression-gated (`test_apk_workflow.py`); `main` green since |
| Desktop tests never execute in CI | 2026-09-16 (PR #71) | Named `:app-desktop:jvmTest` step + genuine-red proof |
| Android suite hides behind assembleDebug | 2026-09-16 (PR #69) | Named `:app-android:testDebugUnitTest` step (coupling kept for packaging jobs) |
| vision_platform-only gating misread as total outage | 2026-09-06 (ADR-003) | Staged-wave chain + per-identity evidence replaced single-client reads |
