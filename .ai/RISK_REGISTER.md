# RISK_REGISTER

Reconciled 2026-09-16 (re-baseline, base `d555959`). Standing risks
first; retired risks at the bottom (kept, not deleted).

| Risk | Likelihood | Detects via | Pre-agreed response |
|---|---|---|---|
| Rot drill not running — schedule silent since 2026-09-07; push entries are 0-job noise | **HAPPENING NOW** | Stage S1 | Human dispatches on `main` + Settings → Actions check; restore daily cadence; fresh verdict re-baselines issue #14 |
| Own-client chain rot (visitorData/sts sourcing, identity gating, client-version drift) | High, recurring | rot drill red / residential playback failures | Client bump → wave maintenance → patch release ≤72h; ≥14d all-red → kill-switch stop-and-decide; triggers T1/T2 gate any ADR-007 work |
| NewPipe upstream recovery missed (watch line is drill-dependent) | Medium | drill watch line (once S1 restores it) | Re-enter as implementation option per ADR-001 |
| SMTC via JNA unstable on real Windows | Medium | S3 hardware round-trip | Ship documented fallback (tray + media keys); record in KNOWN_LIMITATIONS |
| Hardware gates skipped under schedule pressure (soaks, clean installs, SMTC, offline playback) | Medium | S3/S6 checklists | No v0.1.0 tag without signed checklists — the gate list in ROADMAP §6 is literal |
| Real blur unavailable (Android <31) | Certain | shipped | Dark fallback (PR #70); pre-blurred bitmap stays a v2 idea |
| Compose MP / Ktor / Coil regression | Low | CI | Pin versions; upgrade one dependency at a time |
| GPL compliance drift | Low | S5 review | THIRD_PARTY.md per release; no incompatible deps enter |

## Retired (kept for history)

| Risk | Retired | Why |
|---|---|---|
| `main` unbuildable (`073083c` compile break + wrong `:app` module) | 2026-09-10 (PR #56) | Repaired + regression-gated (`test_apk_workflow.py`); `main` green since |
| Desktop tests never execute in CI | 2026-09-16 (PR #71) | Named `:app-desktop:jvmTest` step + genuine-red proof |
| Android suite hides behind assembleDebug | 2026-09-16 (PR #69) | Named `:app-android:testDebugUnitTest` step (coupling kept for packaging jobs) |
| vision_platform-only gating misread as total outage | 2026-09-06 (ADR-003) | Staged-wave chain + per-identity evidence replaced single-client reads |
