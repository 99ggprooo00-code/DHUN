# Dependency audit log (S5)

Append-only. Each entry: pinned vs latest-known at audit date, decision
(UPGRADE with the commit, or HOLD with the reason), and the post-v0.1.0
upgrade order. "Latest-known" comes from web sources available to the
auditing agent — re-verify at upgrade time, never trust blindly.

## 2026-09-16 — pre-v0.1.0 audit (agent; web-checked)

Decision for **every** entry: **HOLD**. Rationale, uniform: the pinned set
is mutually compatible and CI-green; the ecosystem has moved 1–6 minors
ahead on almost every axis, and upgrading anything in the
Kotlin → Compose-compiler → BOM/AGP → Ktor/Media3 chain pre-tag is churn
with a large blast radius (compiler + all platform builds + Robolectric).
Upgrades go one at a time, post-v0.1.0, in the order below. No upgrade was
forced by a known CVE in this audit (see the gap note at the bottom).

| Pinned | Latest-known (2026-09) | Verdict |
|---|---|---|
| Kotlin 2.1.20 (gradle plugins + compose compiler) | 2.3.x (2.3.21 in docs; Ktor 3.5.x wants 2.4 compat) | HOLD — keystone; upgrades first post-tag, alone |
| AGP 8.7.2 | 9.0.1 | HOLD — with/after Kotlin |
| Compose BOM 2024.12.01 | 2026.08.00 | HOLD — after Kotlin+AGP |
| Compose Multiplatform 1.8.2 (plugin) | not checked (paired with Kotlin 2.1.x line) | HOLD — with Kotlin |
| Ktor 3.1.3 (client-core/cio/mock) | 3.5.2 (Aug 2026) | HOLD — needs Kotlin ≥2.2 |
| kotlinx-coroutines 1.10.2 | 1.11.0+ | HOLD — with Kotlin |
| kotlinx-serialization-json 1.8.1 | 1.10.0 (needs Kotlin 2.3.0) | HOLD — correctly paired with Kotlin 2.1.20 today |
| SQLDelight 2.1.0 (plugin + drivers) | 2.3.2 | HOLD — after Kotlin |
| Koin 4.0.2 (core/android/jvm) | 4.2.2 | HOLD — low risk, good first upgrade post-tag |
| Media3 1.5.1 (exoplayer/session/datasource/database) | 1.11.0 stable (Aug 2026) | HOLD — 6 minors behind; biggest feature/risk delta; dedicated upgrade + S3 re-soak |
| Coil 3.1.0 (compose + network-ktor3) | 3.x newer (Jun 2026 releases) | HOLD — with Ktor |
| lifecycle/activity/core-ktx (2.8.7 / 1.9.3 / 1.15.0) | newer minors exist | HOLD — with BOM |
| Robolectric 4.14.1 | newer (unchecked) | HOLD — with AGP/BOM |
| vlcj 4.8.2 | 4.8.2 stable (5.0.0-M4 is a milestone, not stable) | HOLD — pinned IS latest stable ✓ |
| JNA 5.17.0 | 5.18+/6.x line (unchecked exact) | HOLD — vlcj's transitive need; revisit with vlcj 5 stable |
| NewPipeExtractor v0.26.5 | v0.26.4 seen (pinned is same-or-newer) | CURRENT ✓ (desktop fallback resolver) |
| junit 4.13.2 / androidx-test | stable, no action | CURRENT ✓ |

Post-v0.1.0 upgrade order (one at a time, CI green between each):
1. Koin 4.0.2 → 4.2.x (smallest blast radius).
2. Kotlin 2.1.20 → 2.2.x/2.3.x + compose compiler plugin in lockstep.
3. AGP → 9.x + Robolectric + BOM + lifecycle/activity.
4. Compose Multiplatform plugin to the Kotlin-matching line.
5. Ktor → 3.5.x + Coil + coroutines + serialization.
6. SQLDelight → 2.3.x (migration replay check).
7. Media3 → 1.11.x + full S3 re-soak (playback engine swap in all but name).
8. vlcj → 5.x stable only when it leaves milestone (with JNA as needed).

Gap (honest): the repo has no dependency-vulnerability scanning
(no Dependabot, no OSV-Scanner step). CI cannot catch a CVE in a pinned
artifact. Options for v2: enable Dependabot security updates, or add an
`osv-scanner` scheduled workflow. Until then, each audit entry above
should be re-checked for CVEs at upgrade time.
