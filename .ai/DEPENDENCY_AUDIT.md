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

## 2026-10-10 — repository cleanup audit (session `arena/29f9acf0-dhun`, base `main` `588f14f`)

Append-only entry. Evidence is from this session's sandbox; registry reads used `registry.npmjs.org` and `pypi.org`. Maven Central and `services.gradle.org` are not reachable from the sandbox, so Gradle versions were **not** resolved or audited here.

| Ecosystem | Scope | Result | Decision |
|---|---|---|---|
| npm — `website/` (build and CI only; ships no JS) | 4 devDependencies, 146-entry lock | `npm audit`: **9 advisories (5 high, 4 moderate)**, all transitive under `@11ty/eleventy` 3.1.6 (`braces`, `chokidar`, `nunjucks`, `js-yaml` 3.x, `gray-matter`, `argparse` 1.x, `sprintf-js`, `@11ty/eleventy-dev-server`). The registry lists **no patched version within the current majors** for `braces`, `nunjucks`, `chokidar`, `argparse` 1.x or `sprintf-js`; `js-yaml` 4 and `argparse` 3 are breaking. `npm audit fix --force` proposes a **downgrade** to `@11ty/eleventy` 0.6.0. | **HOLD, accepted build-only risk.** Do not run `npm audit fix --force`. Re-audit when Eleventy 4 leaves alpha (4.0.0-alpha.10 is the newest published alpha). Output is static HTML; the advisories affect the build tooling and the dev server. |
| npm — `website/` tests | `node --test` (`npm run test:rules`) | 41/41 pass | — |
| npm — `app-web/` | no runtime dependency, no lockfile | nothing to audit | keep zero-dependency (ADR-008 and `app-web/README.md`) |
| Python — `scripts/` | stdlib only; no requirements file | nothing pinned to audit | — |
| Python — CI diagnostics | `.github/workflows/extraction-health.yml` installs `yt-dlp` and `ytmusicapi` **unpinned** (`pip install --upgrade`) | diagnostic probe only, but the versions are not reproducible | **Recommend pinning** (needs a maintainer decision because a pin changes which YouTube-breakage signal the probe sees). |
| Gradle — `shared`, `app-android`, `app-desktop`, `tools/playback-probe` | 43 inline `implementation`/`api`/`testImplementation` declarations, no version catalog | static check: every artifact has an import of its package in its module, except one **redundant-looking** declaration: `NewPipeExtractor` in `tools/playback-probe/build.gradle.kts` (the probe's sources do not import it; `shared` declares and uses it). | **Candidate only.** Verify with `./gradlew :tools:playback-probe:dependencies` and a probe build in CI before removing. Not changed (no Gradle in sandbox). |
| Licences — `THIRD_PARTY.md` | `vlcj` row | Corrected. The row said `vlcj (+ libVLC, LGPL-2.1)` = LGPL-2.1. The `uk.co.caprica:vlcj:4.8.2` POM and the upstream `master` LICENSE are **GPL v3**. libVLC is a separate, user-installed component. | Documentation corrected. **Requires legal review** of the MSI's distribution obligations for a GPL-3.0 jar, although DHUN is itself GPL-3.0. |
| Secrets — `app-web/src/js/catalog.js` | one `AIza…` InnerTube web key (39 chars) | public web client key, not a project credential (per the spec) | No rotation. Documented. |

**Upgrades performed in this session:** none. The spec asks for the smallest change and the advisory fixes are unavailable within the current majors, so forced upgrades would add risk without a patched target.
