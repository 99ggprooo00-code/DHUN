# 06 — Repository-wide multi-source claims audit

**Audit date:** 2026-10-28 (UTC). **Starting revision:** `12498caa02607edd74504161f388fc2e11d34a4c`.
**Working branch:** `arena/5ced2dbe-dhun`; initially clean. The original alignment was not authorised for commit/push/merge. The owner's explicit follow-up now authorises a verified PR/merge; see §12. No public app-release publication is authorised.

This extends, rather than replaces, [02 — the 2026-10-20 baseline](02-baseline-audit-2026-10-20.md) and the [2026-10-10 legal audit](../legal/AUDIT-2026-10-10-in-app-legal-pages.md). Those are dated evidence, not current product definitions. This audit is an engineering/documentation correction, **not legal review or provider approval**.

## 1. Initial discrepancies (before editing)

DHUN's intended identity is multi-source; the implementation still has one music catalogue family per client. Native Android/desktop use YouTube Music; the separate browser client uses JioSaavn. Three JioSaavn wrapper hosts are failover instances, **not three music providers**. No Spotify adapter, OAuth flow, SDK or playback implementation was found. Existing Kotlin/Compose native and dependency-free JavaScript browser architecture must remain intact.

The initial tracked-file search included hidden directories, source, tests, JSON fixtures, generated bundles, templates, workflows and release history: **749 tracked files; 257 matching text files; 1,914 matching lines** for provider names, provider boundaries, affiliation and multi/single-source language. Generated/minified payloads and fixture data were classified separately from editorial claims. The final coverage inventory and re-scan are recorded below after corrections.

### Findings and required actions

“Current statement” below describes the **starting revision**, not the corrected claim. Priority describes the impact of leaving it misleading. A code-level follow-up is not marked fixed by changing prose.

| File or location | Current statement / issue | Classification | Required action | Dependency | Priority | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| `README.md` introduction / status / web paragraph | Defines the project by native YouTube Music; says browser talks to YouTube, has equaliser and offline capability without shell qualification | Contradictory | Revise identity; name native vs browser providers and missing browser features | PRD §1, `api.js`, browser status | Critical | Product alignment checks; web tests |
| `docs/PRD.md` §§1,4 | Single-source definition, contradictory web scope, code-over-plan hierarchy conflates intent and behaviour | Outdated | Establish authoritative direction, status vocabulary and platform distinctions | ADR-010, current code | High | Cross-surface contracts and links |
| `.ai/MASTER_PROMPT.md` §§1,3,4,9 | YouTube defines the whole product; B2 still undecided; CIO “everywhere” despite Android OkHttp | Contradictory | Revise current instructions; preserve dated build history | PRD, ADR-008 amendments, DI | High | Agent-instruction checks |
| `.ai/README.md`, `.ai/AUTONOMY_PROMPT.md` | Old no-hand-rolled-extraction / exact browser parity directives; unsolicited push instructions | Outdated | Read current vision first; evidence gates; respect session authorisation | Master, PRD, roadmap | High | Instruction and link checks |
| `.ai/ROADMAP.md` current task | D2 intent ambiguity and Watch mode next-step compete with current owner direction | Outdated | Add current task and evidence; keep old sessions historical | ADR-010 amendment | High | Historical traceability checks |
| `.ai/PROMPT_SEQUENCE.md` | 2026-09-02 phase prompts still framed as operational | Historical | Mark historical and point to current instructions, not rebuild phases | Master / PRD | Medium | History preserved; links |
| `.ai/HANDOFF_NEXT_SESSION.md` | Headline is old B1/B2 state | Historical | New current handoff above intact snapshots | Roadmap / audit | Medium | Current pointer check |
| `.ai/WEBSITE_PLAN.md` | Old scope and provider positioning coexist with appended amendments | Incomplete | Add current identity/authority and generation map; preserve Part A/B evidence | Site data/templates | High | Fresh build / claims |
| `.ai/KNOWN_LIMITATIONS.md`, `.ai/DEBUG_LOG.md`, `.ai/RISK_REGISTER.md` | No current consolidated provider/legal blockers; old “current” incidents can be mistaken for live state | Incomplete | Add dated reconciliation, policy and architecture gaps | Research 03–05 / ADR-010 | High | Links; no fabricated runtime evidence |
| `legal/about.md` → native About | Defines native client as entire product; dated health failure presented as build's live release verdict | Outdated | Multi-source intent + native-only implementation + planned sources; runtime build tokens retained; remove undated live verdict | Generator → `LegalContent.kt` → `AboutLegalScreen.kt` | High | Regenerate; hash / token / rendering tests |
| `legal/terms.md` → native Terms | YouTube-only legal scope; no software/content licence distinction; stale health verdict | Legally sensitive | Keep DRAFT; retain native restrictions; distinguish browser, planned Spotify and lack of approval | Generator / research / ADR-010 D1 | Critical | Draft/status, restriction and content-rights checks |
| `legal/privacy.md` §8 | Browser privacy names YouTube and wrong CSP | Contradictory | Name JioSaavn wrapper failover and CDN, localStorage and shell-only cache | `api.js`, CSP, `library.js`, `sw.js` | Critical | Host parity / privacy checks |
| `legal/privacy.md` §§1,3,4,7 | “Nothing leaves” local metadata; desktop cache missing; logs unaudited; unconditional uninstall/delete assurances | Legally sensitive | Describe actual requests, visitor id, cache/log paths, conditional deletion and user submissions; no hypothetical Spotify traffic | DI, SQLDelight, caches, logs, installer | High | Source trace; no packet/hardware claim |
| `legal/support.md` | Says audio/lyrics cache clear UI absent; all clears supposedly keep dialog open on failure | Contradictory | List real controls and limitations without inventing robust completion semantics | Settings UI/viewmodel + shell/platform callbacks | High | Replace obsolete legal assertions |
| `legal/third-party-notices.md`, `legal/open-source-licenses.md`, `THIRD_PARTY.md` | Service access confused with library licences; KuGou / web wrapper notice incomplete; referenced licence texts not bundled | Incomplete / legally sensitive | Distinguish software vs content, add service attribution and unresolved distribution-notice follow-up; preserve verbatim licences | `{{include: …}}`, build dependencies | High | Generated inclusion / full text tests |
| `docs/legal/TERMS-DRAFT.md`, `docs/legal/PRIVACY-DRAFT.md` | Earlier unlinked drafts contain stale facts/placeholders | Historical | Mark archived predecessors and link actual native/browser draft sources; preserve body | Legal generator / web corpus | Medium | Traceable history + link checks |
| `shared/src/commonMain/kotlin/dev/dhun/ui/legal/AboutLegalScreen.kt` | Draft shown as “effective”; bundled pages/token substitution otherwise correct | Legally sensitive | Small label correction only; no navigation/player rewrite | Legal front matter / `LegalTokens` | Medium | Source contract; Kotlin compilation if available |
| `scripts/gen_legal_content.py` | Claims browser renders native legal corpus although build explicitly excludes it | Outdated | Correct pipeline documentation and generated banners, not merge distinct policies | `app-web/tools/build.mjs` | High | Fresh generation / exclusion test |
| `shared/src/commonMain/kotlin/dev/dhun/legal/LegalContent.kt`, `app-web/src/js/data/legal-content.js` | Generated native corpus includes old claims | Outdated | Regenerate from legal Markdown and includes; JS copy is parity fixture, not shipped web policy | Generator | High | `--check`; hash parity |
| `app-web/src/js/data/web-legal.js` About | “Same player”, unofficial wrappers called public API, no multi-source priority/status | Incomplete | Browser-specific About; no YouTube/Spotify playback claim; named non-affiliation | `legal.js` → About route | High | Rendered legal tests |
| `app-web/src/js/data/web-legal.js` Privacy / Terms | Says catalogue content not stored although snapshots persist; erase inaccurately described; API hosts/hosting/cookie distinctions incomplete | Legally sensitive | Keep DRAFT; disclose local snapshots, settings-preserving erase, API `omit` vs browser-managed media, host retention unknown | Library / transfer / CSP / audio element | Critical | Source and rendered text tests |
| `app-web/src/index.html`, `app-web/src/manifest.webmanifest`, `app-web/package.json` | Generic/player marketing without product/provider status; “public API” ambiguity | Incomplete | Metadata describes browser client of multi-source product; current unofficial JioSaavn only | Build copies src → dist | Medium | Metadata / source checks |
| `app-web/README.md`, `docs/web-player/README.md` | Mostly correct browser implementation; one-host CSP example; product vision absent | Incomplete | Link authoritative vision, correct origin list; retain real limitations | Browser code / documentation gate | Medium | `web_player_docs.py` |
| `docs/web-player/PRD.md`, `PROVIDER-INTEGRATION.md` | Spotify “native design only”; owner intent “open”; JioSaavn asserted only feasible provider globally | Outdated | Project-level planned source, per-platform gates; remove universal impossibility claim | PRD / ADR-010 / research | High | Docs + provider contracts |
| `docs/web-player/ARCHITECTURE.md` | Wrong `SourceError` kinds; claims everything pure; singular CSP origin; no real second-provider gap | Incomplete | Correct seam facts; identify design-only capability/identity work | `api.js`, library / player | Medium | Docs checks; source inspection |
| `docs/web-player/SECURITY-PRIVACY.md`, `USER-GUIDE.md` | Hosting requests omitted; no-added-identifier too broad; no-upload wording hides queries; source plan absent | Incomplete | Scope library locally vs actual network; future Spotify not active | Web policy source | High | Host parity; docs / links |
| `docs/web-player/KNOWN-LIMITATIONS.md`, `IMPLEMENTATION-STATUS.md`, `IMPLEMENTATION-PLAN.md`, `RESEARCH-REGISTER.md`, `DECISIONS-AND-CHANGES.md` | Correct unverified audible playback gate; stale next Watch task / contradictory intent | Incomplete / historical | Retain evidence, add current status/gates and dated reconciliation | Research / ADR amendments | High | Unit counts, paths, no evidence promotion |
| `website/src/index.njk`, `website/src/_data/site.js` | Title, description and lead say “a YouTube Music client”; only Google non-affiliation | Outdated | Multi-source identity, YouTube first, Spotify planned/deferred, native/web distinctions | Eleventy front matter / data | Critical | Built HTML / metadata claims |
| `website/src/features.njk` FAQ | Offline answer not platform-scoped; “nothing else cached” false; browser missing from platforms answer | Contradictory | Native features scoped; browser shell-only; real third-party requests; provider roadmap | Site data / caches / browser status | High | Build / claims / FAQ checks |
| `website/src/_includes/base.njk` footer / structured data | No legal source links; JSON-LD generic platforms; no broader identity | Incomplete | Shared visible provider summary, draft legal links, metadata description | Site data, sources | High | JSON-LD / links / HTML validation |
| `website/dist/{index.html,features/index.html,ui/index.html,404.html}` | Committed generated pages repeat stale claims | Outdated | Rebuild from sources, not edit output alone | Eleventy + pruning / minification | High | Deterministic rebuild / drift checks |
| `docs/TRD.md` | Web still described as YouTube-dependent; claims provider seam already source-neutral | Contradictory | Preserve architecture but document actual coupling and planned boundary | Kotlin `SearchFilter`, ids, DI; JS seam | High | Source inspection / links |
| `shared/src/commonMain/kotlin/dev/dhun/provider/MusicProvider.kt` KDoc | Says YouTube types never cross boundary, yet imports InnerTube `SearchFilter` and uses `videoId` | Contradictory | Correct comment; record implementation follow-up, no API/data migration now | Entities / use cases / storage | High | Existing signatures unchanged |
| `docs/research/03-youtube-youtube-music.md` | Resolver priority stale; old “no doc checks policies”; definitive web/legal impossibility statements | Incomplete / legally sensitive | Dated primary-source recheck and code mapping; distinguish engineering incompatibility from legal conclusion | Current resolver / official policies | High | Source links/date/evidence labels |
| `docs/research/04-spotify.md` | ADR undecided, native SDK vaguely implies in-app PCM; July limits secondary/unverified | Outdated | Current primary research, App Remote vs in-app SDK; all feasibility gates retained | Spotify policy / terms / SDK / quota docs | High | Primary source recheck; no adapter claimed |
| `docs/research/05-source-capability-matrix.md` | “Supported” conflates upstream APIs and DHUN; native audio caches omitted; Spotify offline terms unread | Contradictory | Separate product implementation vs upstream possibility; correct data/cost/permissions | Research / source evidence | High | Vocabulary and matrix contracts |
| `docs/research/README.md`, `docs/decisions/README.md` | Research index calls accepted ADR-010 PROPOSED; decision index misses amendments | Outdated | Correct current indexes and cross-links | ADR-008 / ADR-010 | Medium | Link / historical checks |
| `docs/decisions/ADR-010-multi-source-provider-direction.md` | Accepted decisions plus later proposals/ambiguous interpretation; scope identity underspecified | Historical / incomplete | Append explicit owner-directed amendment; preserve D1–D6 and earlier analysis | Binding 2026-10-28 direction | Critical | Amendment + original decisions retained |
| `docs/decisions/ADR-001-extraction-engine.md`, `ADR-006-offline-music-downloads-architecture.md`, `ADR-008-browser-web-player-target.md`, `ADR-009-marketing-site.md` | Accepted source-specific decisions and older superseded web framing | Historical / legally sensitive | Keep text; add current applicability pointers where needed; architecture acceptance is not content permission | ADR-010 amendment / PRD | Medium | Historical traceability |
| `docs/research/02-baseline-audit-2026-10-20.md`, `CHANGELOG.md`, `docs/releases/v1.00.001.md`, dated verification / `.ai/` sessions | Old observations / test counts / source claims | Historical | Preserve observations; dated pointer on baseline, not rewrite tests or outcomes | New audit / current status | Low | No fabricated replacement evidence |
| `docs/README.md`, `docs/implementation-plan.md`, `docs/INSTALL.md`, `docs/releases/rolling-test.md` | Onboarding/release entry points lack current cross-platform provider distinctions | Incomplete | Link vision/status, retain fixed release history | PRD / matrix / web docs | Medium | Links and release identity tests |
| `scripts/website_claims.py`, `scripts/test_website_claims.py` | Any non-negated Spotify occurrence forbidden, including “planned”; no multi-source requirement | Outdated | Allow explicit plans/research, reject implemented support claims; add direction and platform safeguards | Built HTML + served smoke uses same rules | High | Positive and mutation fixtures |
| `scripts/test_legal_content.py` | Explicitly forbids real native cache controls; tests native bundle as web policy | Contradictory | Assert current controls, draft/legal distinctions, real host split, runtime tokens | Generator + UI + browser corpus | High | Full Python suite |
| `app-web/tests/legal.test.mjs`, `scripts/test_app_web.py`, `scripts/test_repo_health.py`, `.github/workflows/{ci,website}.yml` | Existing generation/CSP/link gates mostly correct; no product identity guard across surfaces | Incomplete | Extend auto-discovered checks; no unnecessary workflow rewrite | Current CI discovery and website smoke | High | Baseline and final test commands |

## 2. Authority and display pipelines

- **Product direction:** [PRD §1](../PRD.md#1-product-definition-and-source-of-truth); accepted decisions and dated amendments are in ADR-010. Implementation evidence does not redefine intent. The [capability matrix](05-source-capability-matrix.md) distinguishes code/test/hardware evidence.
- **Native legal:** `legal/*.md` front matter + `THIRD_PARTY.md`, `LICENSE`, `LICENSES/*`, `SECURITY.md` includes → `scripts/gen_legal_content.py` → compiled `LegalContent.kt` → shared `AboutLegalScreen` / `LegalDocumentScreen` via Settings and `DhunAppShell`. Android and desktop supply `DhunAppInfo`; only version-name/version-code/channel/platform tokens are substituted at runtime. These pages work offline. `effectiveDate` is an existing schema name; a draft is not in force.
- **Browser legal:** hand-authored `app-web/src/js/data/web-legal.js` → `webLegalById` / `views.js` → `legal.js` + `markdown.js` → `#/about/{about,privacy,terms,licences,support}`. The generator's `data/legal-content.js` is a native-corpus parity fixture explicitly excluded by `tools/build.mjs`; it is **not** the browser policy. Separate texts are necessary because data flows differ.
- **Public site:** `website/src/_data/site.js` copy plus front matter in `index.njk`, `features.njk`, `ui.njk` / shared `base.njk` → Eleventy → CSS pruning/minification → committed `website/dist/`. No standalone About/Terms/Privacy/download/source-code page exists: About is hero/FAQ/footer, native downloads and code are GitHub links; legal draft sources and browser About routes are linked, not invented as adopted policies. Title, description, Open Graph, Twitter and JSON-LD come from those sources. Social card is `summary`; no provider art or new social image is needed.
- **Deployment:** `.github/workflows/website.yml` builds root-hosted test output separately from `/DHUN/`-prefixed Pages output, copies `app-web/dist/` to `app/`, tests claims/links, then deploys only on the authorised workflow event. Local validation is not deployed-origin evidence.
- **Checks:** `.github/workflows/ci.yml` auto-discovers Python `test_*.py` before Kotlin builds; website workflow builds and tests the site and app, checks committed-output drift, HTML, browser and served-byte rules. No new backend, runtime dependency, credential or provider is needed for this audit.

## 3. Completed corrections and additional findings

**Outcome: alignment edits and local safeguards completed, not multi-provider runtime readiness.**
DHUN has **always been intended as a free, open-source, cross-platform multi-source music client**.
This is a correction to inconsistent descriptions, not a new single-source-to-multi-source pivot.
YouTube / YouTube Music is first integration/development priority; Spotify remains **Planned** with execution
**Deferred** pending all technical, account, policy, legal, playback, privacy, licence and cost gates.
Additional providers need evidence. Implementation/provider/capability differences per build remain explicit.

- **Authority/decisions:** PRD §1 governs intent/status; code and dated tests govern implemented behaviour.
  ADR-010 has a dated amendment, not a silent replacement of D1–D6. D5 stays design-only;
  ADR-009 stays Proposed. Old research, decision text, changelogs, release and hardware observations remain historical.
- **Native legal:** all seven Markdown documents/includes regenerated into both native bundle targets.
  About and Terms now separate direction/current native features/planned sources/rights; Terms/Privacy/Support
  remain drafts. The UI says draft/not in force and last updated, retaining the `effectiveDate` field for compatibility.
  Runtime version-name, version-code, channel and platform substitution is unchanged. No invented legal adoption/contact.
- **Browser legal:** **five** browser-owned documents (`about`, `privacy`, `terms`, `licences`, `support`),
  not the seven-document native policy or a new six-document schema. Service notices are within Licences.
  The existing exact five-ID JS test remains valid; new semantic/rendered tests add privacy/status/rights evidence.
- **Privacy/control accuracy:** no “nothing leaves”, “no token therefore anonymous”, “no metadata stored”,
  “uninstall erases everything” or guaranteed deletion assertion. Real cache/history controls are listed.
  Erase UI/confirmation now names its actual library-only scope. Browser handler/schema behaviour is unchanged,
  and a real local Chromium test confirms settings and shell-cache retention using synthetic data.
- **Public inputs/outputs:** website data/front matter/layout and all four generated public pages agree.
  Metadata/JSON-LD, FAQ, source/release/legal links and native/browser screenshot comparisons are scoped.
  README and Interface gallery no longer link native-only lyrics/download/EQ/Windows tools as browser equivalents.
  No standalone adopted policy, About or download page was invented; current native source links/browser routes are explicit.
- **Instructions/research/onboarding:** current `.ai`, specs, browser docs, indexes and release entry points point to PRD/status;
  dated applicability notes resolve old extraction, source-neutrality and parity assertions without deleting history.
- **Safeguards:** source + built + served positive contracts, present-support vs planned-language semantics, actual
  browser-provider checks, native legal hashes/includes/tokens and current control assertions, fixed exact CSP origin
  checks, real legal/erase browser tests and non-ignored-worktree hygiene. Existing CI discovers them; workflows unchanged.
- **Small justified runtime/UI changes only:** legal draft/date labels; browser Erase wording; one notice wrapping rule;
  browser local/cache patch `3.0.1`; follow-up Home sample-badge disclosure fix (§12). No provider/API/player/queue/auth/storage migration, new backend/runtime dependency,
  paid service, Spotify implementation or native-stack rewrite. Cache patch is **not a published release**.

### Further discrepancies found during the final trace/rescan

| Exact location | Starting statement / defect | Classification | Action / current result | Dependency | Priority | Verification |
| --- | --- | --- | --- | --- | --- | --- |
| `README.md` native screenshot tables | “Open Synced Lyrics/Downloads/Desktop view” in browser, despite missing browser features | Contradictory | Removed invalid native-only links; current comparisons labelled; historical captures retained | Browser route/feature code | High | Positive gallery regression test; local browser |
| `website/src/ui.njk` surfaces intro; `site.js` screenshot labels/provenance | Every native capture allegedly opens that exact browser screen; blurring supposedly proves no unpermitted copyrighted art | Legally sensitive / contradictory | Explicit historical native vs browser comparison; no legal-clearance claim; no asset change | Provenance/source/rights | High | Four-page build, HTML, browser, gallery safeguard |
| `docs/verification/SCREENSHOT_PROVENANCE.md` old deep-link table | Old native/browser mappings could be taken as current | Historical | Dated applicability note; original dates/files/dimensions/observations retained | Current screenshot copy | Medium | No WebP changes; link/static/browser checks |
| `docs/app-flow.md` surface map | Same browser/native settings/navigation and unqualified offline | Contradictory | Separate client/provider/features; offline shell only | PRD/browser implementation | High | Full Python/docs links |
| `docs/backend-schema.md` conceptual entities | Provider-scoped/namespaced fields risk being read as actual migration | Incomplete | Clearly proposed; current source-shaped native and separate browser stores described | Matrix §3 / D5 | High | No SQL/schema/signature changes |
| `docs/decisions/ADR-002-fullscreen-player-design.md` context / implementation order | Source-neutral boundary already achieved, UI sees no InnerTube | Contradictory historical assertion | Dated applicability amendment, original visual decision retained | `MusicProvider`/`SearchFilter` and callers | High | KDoc/source inspection; no neutrality claim |
| `.ai/PROBLEMS_AND_FIXES.md`, `DEPENDENCY_AUDIT.md`, `ui-research-vivi-music.md` | Old extractor/fallback/copying directives look standing/current | Historical | Dated scope notes above intact originals | Current MASTER/PRD/ADR | Medium | Current-pointer / file/link inspection |
| `app-web/src/js/api.js` opening/instance comments | Public catalogue; sends no identifier; every host the same MIT project/free/CORS-open | Unverified / incomplete | Comments scope actual requests and unknown per-host provenance/rights; logic unchanged | Actual fetch and primary terms | High | Request/CSP tests; no invented host audit |
| `site.js` extraction/offline/closing/source citations | No token, every-client failure, guaranteed check coverage, impossible browser storage, two nonexistent abbreviated ADR paths | Incomplete / unverified | Native/visitor-ID/attempt/evidence scopes; valid long ADR paths; no universal guarantee | Source/research | High | Evidence-path regression test, built/served checks |
| `DhunAppInfo.kt` KDoc | Stale hard-coded Android/MSI versions presented as current | Outdated | Explain metadata roles, preserve runtime values/tokens | Platform build info | Medium | KDoc-only diff; legal token tests |
| `AboutLegalScreen.kt` draft date | Draft appears legally “effective” | Legally sensitive | Not-in-force / Last updated labels only | Legal status/schema | High | Source assertion; native compilation not run |
| `app-web/src/js/version.js`, `src/sw.js` | Changed shell/legal files would retain old cache namespace | Incomplete | Local browser `3.0.1`; unchanged storage key/schema and channel | Future authorised deployment | Medium | Build/SW/version tests; local offline + erase check |
| Site footer report link; browser sample notice | 42.5 px standalone “Issues” target; sample disclosure spilled at phone width | UI verification finding | Longer report-link label; one `overflow-wrap` property; thresholds not weakened | Real local Chromium | Medium | First full browser 20 findings → final 144 measurements / zero failures |
| `website/budget-baseline.json` | Necessary new disclosures exceeded old 5% ratchet on two routes | Intended documented growth | Regenerate weights, retain 5% and 60 KiB limits | Final built pages | Medium | 32 quality checks, HTML, lossless/deterministic build |
| `scripts/website_smoke.py` | Only three routes and negative claims; positive direction could disappear after publication | Incomplete | Four actual public routes including 404; shared positive contract on served bytes | Product contract | High | 26 served-rule tests; root + prefixed local HTTP smoke |

## 4. Platform/provider and architecture findings

| Surface | Current implementation / evidence | Not established / not implemented |
| --- | --- | --- |
| Native Android | Kotlin Multiplatform / Compose; `YouTubeMusicProvider`, InnerTube metadata, own-client stream resolver, Media3; local library/history/lyrics/download/cache and device controls | No provider OAuth, Spotify/JioSaavn music adapter or capability registry. No new native compile/device/audio acceptance in this audit. Permissions remain unresolved. |
| Native desktop JVM | Same native YouTube path; own-client primary, optional **user-installed** yt-dlp fallback; VLCJ/system libVLC; Windows primary target | NewPipe is a recovery watch, not production primary/fallback. Linux/macOS hardware acceptance unverified. Hosted/static tests are not Windows/VLC sound proof. |
| Browser | Separate dependency-free ES modules; JioSaavn through three wrapper hosts, HTML audio; local library/export/import; installed offline shell; labelled silent samples | **Partial** integration, not native parity or catalogue federation. No YouTube, Spotify, audio downloads, EQ or lyric provider. Local render/erase verified, deployed-origin live catalogue and audible sound unverified. |
| Spotify, all builds | Product **Planned**, implementation **Deferred**, research updated | No adapter, SDK, OAuth, account dashboard verification, tokens, playback or active traffic; no platform route or public-scale promise approved. |
| Further music providers | Evidence-based intake direction | No implementation or selection inferred from lyrics providers, three wrapper mirrors, upstream API features or a Watch/Jamendo proposal. |

**Architecture is not yet provider-neutral.** `MusicProvider` imports InnerTube `SearchFilter` and uses
video/browse IDs; entities, use cases, DB/queue/download/lyrics keys and callers inherit those assumptions.
Browser `api.js` normalises a single catalogue behind one contract; three hosts do not create three adapters.
No provider-scoped identity/capability/account/player registry or non-destructive multi-source migration was found.
Only the misleading KDoc was changed, not signatures, IDs, tables or user data.

A bounded D5 design must cover: stable provider-scoped IDs and collision/migration fixtures; capability/error/status
contracts; platform-specific authenticated account/storage/revocation; player ownership and queue/offline/DSP eligibility;
source/attribution display; local retention/deletion; least-privilege scopes; tests for unavailable/denied/Premium/403/429
and missing capabilities. Do not build a universal PCM player assumption around Spotify App Remote/SDK, or replace KMP/Compose
with Tauri/React. These are **follow-up recommendations, not completed architecture or new implementation orders**.

## 5. Legal and privacy: factual corrections vs unresolved review

| Factual corrections made from source/tests | What still needs permission, review or runtime evidence |
| --- | --- |
| Native queries/IDs/client/country/language and returned visitor identifiers go to YouTube/Google; media/art requests disclose network information; guest/no login is not anonymous | Applicability/permission for unofficial extraction, audio separation/background, downloads, metadata/art/audio/lyric storage; local law; no final violation/compliance ruling made |
| LRCLIB receives native track metadata/manual queries/version User-Agent; KuGou receives queries/candidates/access keys; configured order, local cache and saved choices exist | Current lyric-content rights/retention/operator obligations, packet-level completeness and exact provider-side logs |
| Native SQLDelight library/history/search/settings/lyrics/matches, downloads and artwork/segment/whole-track/prebuffer caches exist | Reliable end-to-end deletion/error completion, rights/retention lifecycle, Android/Windows failure and uninstall tests |
| Packaged desktop uses launcher-adjacent `userdata`; unpackaged OS directories and temporary startup logs differ. Local diagnostics may contain OS/JVM/path/stack/IDs/URLs; no automatic uploader found; OS notifications/widgets/media controls display metadata | Packet/log/redaction audit and safe diagnostic UI/export; no blanket “no personal information” or universal uninstall erasure guarantee |
| Browser wrapper failover can disclose a query/ID to several operators; CDN sees media/art requests; API uses `credentials: omit`, `cache: no-store`, unlike browser-managed media/image requests | Per-host operator/provenance/permission/retention/deletion, actual deployed CORS/media/region/autoplay outcomes |
| Browser `dhun.web.v3` stores likes/playlists/history/searches/settings and referenced metadata/art-URL snapshots; persisted stream URLs blanked; snapshots pruned; SW caches shell, not API/audio | Normal browser media cache and third-party logging are outside DHUN's erase guarantee; exports/voluntary public issues need redaction |
| Browser Erase library clears actual local library/snapshots, retains settings + shell; actual handler checked with disposable synthetic data; browser origin-site controls can clear the rest | Neither action deletes provider/CDN/GitHub logs. No native erase-all/export control was invented; no account exists to disconnect today |
| Software GPL/other bundled grants are distinct from music/artwork/lyrics/streams/APIs/trademarks; non-affiliation names YouTube, Google, JioSaavn, Spotify; licence texts/asset provenance retained | Complete resolved/transitive release notices/SBOM, SDK/GPL compatibility and system libVLC boundary; blurring is not copyright clearance |
| Planned Spotify has no active DHUN data flow; only conditional future account/token/privacy design is described | Account/scopes/secure storage/expiry/refresh/revocation/disconnect/deletion and policy clarification before any implementation |

Terms/Privacy (and support disclaimers) are **drafts, not yet in force**. No legal approval, controller identity,
privacy/security inbox, trademark licence, provider agreement, funded counsel or adopted policy was fabricated.
No disclaimer or owner acceptance cures provider restrictions. Native current-path maintenance is retained under D1,
not expanded or declared compliant. Unresolved review remains a **release gate**, not “fixed by documentation.”

## 6. Dated external evidence and unknowns

Evidence labels used here and the matrix: **[R]** repository/source inspection; **[T]** named local execution below;
**[P]** targeted primary provider text read using the retrieval tool on **2026-10-28**; **[H]** earlier dated hardware/CI
record, not re-run; **[served-text]** current public page extraction, not raw-header/byte/audio verification; **[U]** unknown.
No full-policy legal review or immutable external snapshot is claimed. Historical 2026-10-20 research remains labelled below
the new rechecks in research 03/04, and the original matrix remains in matrix §6.

| Primary link / scope read [P] | Finding relevant to this correction | Unknown / limit |
| --- | --- | --- |
| [YouTube Terms](https://www.youtube.com/static?template=terms), permissions/restrictions | Material download/reproduction/automation/circumvention restrictions; GPL/personal/noncommercial is not a blanket waiver | Specific permissions, unofficial-path applicability and jurisdictional legal assessment |
| [YouTube API policies](https://developers.google.com/youtube/terms/developer-policies), III.I.7/9/14/1/13 and III.E.4 | Audio/background/non-API retrieval/substitute UX/geo restrictions; most metadata refresh/delete within 30 days subject to exceptions | Do not pronounce all local data forbidden or all caching permitted; mapping unofficial datum/purpose needs review |
| [Quota calculator](https://developers.google.com/youtube/data/determine_quota_cost), updated 2026-10-15 | Default 100 search calls/day, 100 insert calls/day, 10,000 other units/day with separate search/upload buckets | No DHUN official API integration, project quota, price or extension approval exists |
| [IFrame API](https://developers.google.com/youtube/iframe_api_reference), queue/load/control sections; [minimum functionality](https://developers.google.com/youtube/terms/required-minimum-functionality) | Visible official player can load/queue/seek; identity/Referer, minimum 200×200, visibility/autoplay/branding/overlay requirements | No raw audio/background/offline parity or current DHUN implementation; D2/CSP/runtime spike needs prior approval |
| [Google Privacy](https://policies.google.com/privacy), collection sections | No sign-in does not eliminate IP/request/identifier/activity disclosure | Do not assert every generic policy field is sent by DHUN; no packet audit |
| [Spotify Developer Policy](https://developer.spotify.com/policy), effective 2025-05-15 | III.5 multi-service integration restriction, III.7 mixing, III.9 narrow transfer exception, III.11 core UX, III.13/14 analysis/ML; Premium music streaming and commercial/attribution limits | Premium, source-separated UI/queues or metadata-only use are not automatic permission; provider clarification needed |
| [Spotify Developer Terms](https://developer.spotify.com/terms), streaming/storage/deletion sections | Control of background playback can be Streaming; no general ripping/permanent/indefinite storage permission; security/disconnect/delete duties | Narrow temporary/conditional exceptions do not authorise DHUN downloads; SDK/software/content compatibility review open |
| [Quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes), [February migration](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide), [July changelog](https://developer.spotify.com/documentation/web-api/references/changes/july-2026) | Premium development owner; 5 allowlisted users; login can succeed then API 403; changed endpoints/fields/search limits; July **25 client IDs** and shared developer quotas | Owner account/entitlement/dashboard/actual rates unverified; no Nepal price or subscription/access approval invented. Extended access organisations/250k MAU criteria are not hobby-app permission or guaranteed approval |
| [Web API migration](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide), [Web Playback SDK](https://developer.spotify.com/documentation/web-playback-sdk) | Metadata/library/control is not an audio URL; browser Connect device needs eligible Premium, DRM/browser/autoplay/runtime review; commercial written approval | No in-DHUN playback, SDK/CSP change, general catalogue sync or all-browser support implemented |
| [Android SDK](https://developer.spotify.com/documentation/android) | Beta App Remote controls installed Spotify app; Spotify owns playback/cache/network, not Media3 PCM | Not a native in-DHUN audio route or permission to copy offline music; exact platform/account eligibility needs tests |
| [PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow), [redirect rules](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri) | Public clients can use PKCE without a client secret; exact HTTPS registration except specified literal-IP loopback HTTP; no `localhost` | No auth/secure token store/redirect has been implemented or verified across DHUN platforms; no backend automatically required or authorised |
| [JioSaavn Terms](https://www.jiosaavn.com/corporate/terms/), “Your Use” clauses, updated 2026-10-07; [Privacy](https://www.jiosaavn.com/corporate/privacy/) | Provided/authorised-interface and unofficial/automated-access restrictions are material to wrappers; service IP/log/device practices differ from DHUN local storage | No wrapper/provider permission, independent-operator retention or content licence established; not a final legal verdict |
| [Wrapper project](https://github.com/sumitkolhe/jiosaavn-api), repository overview [R-external] | Identifies unofficial API; observed revision `fc6bd132193d1fc71c2ab3db373e78b92884f67c` (2026-04-16) | Full LICENSE not fetched in this audit; does not prove each configured host's operator, code version, availability, CORS or music rights |

### Published pages actually read — not silently updated

On 2026-10-28 the retrieval tool read **all returned chunks** of
[Home](https://99ggprooo00-code.github.io/DHUN/),
[Features](https://99ggprooo00-code.github.io/DHUN/features/),
[Interface](https://99ggprooo00-code.github.io/DHUN/ui/) and
[404](https://99ggprooo00-code.github.io/DHUN/404.html) **[served-text]**.
The published Home title still described “a YouTube Music client with no sign-in, for Android and Windows”;
Features still said “nothing else is cached” and framed every client's playback as YouTube-dependent;
Interface still offered an “exact screen” browser comparison and a blurring-as-copyright-clearance assertion.
The 404 content was a generic recovery page, not a provider integration. These are published older copies,
not this corrected local artifact. Page extraction does not expose every head/footer/header field or prove audio;
no raw deployed digest/CSP test, canonical-origin playback, deployment or adopted policy is claimed.
**No commit, push, merge or publication was performed; live copy requires a later authorised deployment.**

## 7. Validation ledger — 2026-10-28

This section records the initial local validation. The separately authorised PR/remote-CI follow-up is in §12.
Environment: Python **3.11.2**, Node **22.22.3**, npm **10.9.8**. No local Java/JDK available.
Website dev dependencies installed from the pinned existing lockfile (145 packages); no dependency/lockfile changed.
For local headless rendering only, Chromium **153.0.8010.0** came from `@sparticuz/chromium@153.0.0` via npm into
an external ignored cache, with its shared libraries/fonts. It is **not** an app/runtime dependency or standard-CI-browser
substitution claim. Tests used normal Playwright launch/CSP, not disabled web security. All audio/catalogue/device/legal
limitations below remain independent of browser geometry or handler evidence.

| Command / check actually executed | Exact result / meaning |
| --- | --- |
| Baseline `python3 -m unittest discover -s scripts -p 'test_*.py'` | **466 tests, OK**, 7.862 s before edits |
| Baseline `npm test` (repository root) | **160 pass, 0 fail/skip**, 9,153.71168 ms before edits |
| Final Python discovery (same command) | **493 tests, OK**, 7.213 s, no failures; includes the new 18 positive/mutation/evidence/gallery tests, corrected legal and served-page checks |
| `python3 scripts/gen_legal_content.py` then `--check` | **7 pages / 2 targets current**; native/JS parity hashes, include expansion, licence texts and allowed runtime tokens checked in Python suite; generated Kotlin lexical checks pass, not a JVM compile |
| `npm run check` (root) | **PASS**: icon parity, browser build, **165 pass / 0 fail / 0 skip**, docs gate; no app dependency install needed |
| Browser build within that command | **29 files, 20 reachable modules, 29 shell entries**; native `legal-content.js` excluded, all reachable/cached entries checked |
| `python3 scripts/web_player_docs.py docs/web-player` | **20 documents pass** module map, evidence paths, local links and current 165-test size/status claims |
| `npm --prefix website run build` | **PASS**, four public HTML pages plus sitemap/assets; all **16 artifact files** byte-identical on a fresh root rebuild |
| `npm --prefix website run verify:minify` | **PASS**, fresh unminified output equivalent ignoring whitespace; no generated-only edits |
| `npm --prefix website run test:rules` | **52 pass, 0 fail/skip** (browser decision, pruning and annotation predicates) |
| `./node_modules/.bin/html-validate 'dist/**/*.html'` (cwd `website`) | **PASS, exit 0**, all four generated pages |
| `python3 scripts/website_claims.py website/dist` | **4 pages pass**, 11 forbidden-claim rules, 3 caveats, 5 digest rules, 12 telemetry rules plus shared positive direction contract |
| `python3 scripts/product_claims.py` | **PASS**, authority/current native/browser/legal/source + every built page's direction/status/metadata/draft-link anchors |
| `python3 scripts/website_quality.py website/dist` | **32 checks pass**; unchanged weight/zero-JS/accessibility/static-link/design/asset thresholds |
| `python3 scripts/website_smoke.py http://127.0.0.1:8080` | **PASS**, 4 actual public routes + app shell/assets; product/provider/canonical/caveat/CSP/link checks against locally served bytes |
| `DHUN_SITE_PATH_PREFIX=/DHUN npm --prefix website run build`, assemble local `/DHUN/`, same smoke with `/DHUN` base | **PASS**, all 4 prefixed pages and **14 distinct** root-relative targets remain under the project base and resolve. Tracked root artifact restored and proven byte-identical |
| `LD_LIBRARY_PATH=/tmp/al2023/lib FONTCONFIG_PATH=/tmp/fonts DHUN_CHROMIUM=/tmp/chromium SITE_BASE=http://127.0.0.1:8080 node website/tests/browser.mjs` | **144 measurements, 0 notes, 0 failures, exit 0**. Full script, not narrowed. Includes responsive/contrast/focus/axe/offline/sample/library checks plus all 5 legal pages, rendered version/tokens/no external legal-page requests and actual synthetic erase scope |
| Additional local Markdown fragment audit of changed documents | **51 local fragment links checked, 0 broken anchors**; fenced examples/external URLs excluded; file/evidence paths also covered by the repository/docs tests |
| `git diff --check` | **PASS** after EOF whitespace correction; no hidden failed result counted as final success |
| Full expanded worktree rescan / companion inventory | **753 paths** (749 tracked + 4 non-ignored untracked); **727 text**, 26 binary/NUL excluded as text; final counts and exact line dispositions in companion inventory |

The existing workflows already discover Python tests and run website/build/served checks. No workflow change or new remote CI
run is asserted. Static “tests exist” claims were not treated as executed tests; JVM/Android sources remain unexecuted here.

**Failures found and corrected, not waived:** planned-provider parsing initially mishandled coordinated negation/soft wraps;
legal tests encoded false missing controls; a policy hyperlink was incorrectly treated as a runtime network destination;
added disclosures exceeded the old two-route weight ratchet; full browser measured the short footer link/sample-text overflow.
The new erase test's initial fixture used a mutation result wrapper and omitted normalised settings; corrected the fixture,
not the handler or retention expectations. Wrong-cwd HTML invocation found no files; the final right-cwd invocation above passed.
No browser, budget, CSP, legal/draft or product gate was disabled to obtain the final pass.

### Deliberate weight-baseline refresh

The required platform/status/legal/privacy disclosures add text/links, not runtime JS or unlicensed assets.
`website/budget-baseline.json` is regenerated intentionally; **5% growth ratchet** and **61,440 B (60 KiB)**
HTML+CSS absolute ceiling remain unchanged. Inlined CSS size is unchanged.

| Route | Previous baseline | Final HTML+CSS |
| --- | --- | --- |
| `/` | 50,380 B | **54,385 B** |
| `/features/` | 48,143 B | **51,401 B** |
| `/ui/` | 58,069 B | **59,646 B** |

**Not tested / not approved:** native Gradle/JVM/Android compilation or tests (no Java), Android device/Windows VLC sound,
install/upgrade/uninstall/deletion failures, Linux/macOS hardware, Firefox/Safari, live provider API/audio from the deployed
origin, production OAuth/Premium/Spotify access, packet captures/provider logs/retention, complete licence/SBOM/legal review,
Lighthouse, raw canonical-origin HTML/CSP digests or new GitHub Actions checks. Local samples and offline shell are not audio
proof. Public page text was read, but the live site was not rebuilt/published by this work.

## 8. Search coverage, dispositions and retained matches

Initial inspection preceded edits: branch/status/tree/build/workflows/ADRs/native and browser source, then 749 tracked-file
search (257 matching files / 1,914 matching lines). The expanded final query includes provider aliases, boundary/identity,
non-affiliation/trademark/copyright language, hidden `.ai`/`.github`, tests, fixtures, source, build/tool/workflow inputs,
canonical legal/includes, all tracked generated outputs and current specs/research. Package/storage/player/UI wiring and
metadata with no query hits were additionally inspected, not treated as nonexistent.

The [complete matching-file and line inventory](06-multi-source-match-inventory.md) lists **every baseline or final matching
file**, line ranges and its current/history/decision/research/implementation/test/generated/licence disposition, plus the
ignored browser artifact. Report self-references are inspected separately rather than recursively counted. `.git` internals,
external `node_modules`, caches/logs/screenshots and build caches are excluded from project-claim totals; actual dependency
manifests/graph and fresh browser output are separately checked. No signed URL/payload dump/new data corpus is committed.

Legitimate retained matches include: YouTube-specific native classes/IDs/queries and sanitised provider fixtures;
JioSaavn wrapper/CDN/CSP/error/source badges; Spotify planned/blocked-policy research and non-affiliation; negative
support fixtures; source-neutrality goals vs measured gaps; historical YouTube-only/extraction/browser/CI assertions under
dated supersession notices; original changelogs/phase evidence; and verbatim licence language. “Single source of copy”
means a template-data authority, not a single music provider. Multiple lyric providers do not prove multiple music adapters.
No word-only ban, wholesale identifier rename, fabricated new test result, erased release history or silent data migration.

## 9. Ordered next work — recommendations, not completed implementation

1. **Resolve permissions/legal/notice release gates:** existing native unofficial extraction/background/download/cache/metadata
   and browser JioSaavn/wrapper/content permissions; lyric rights; controller/contact/applicable law/adoption; resolved/transitive
   dependency notices. Disclosures/owner acceptance/green CI do not substitute for permission. No expansion under this audit.
2. **Obtain current runtime evidence:** a Java-enabled native build/test run, Android and Windows/VLC acceptance with dated
   build/network/error evidence; actual deployed-origin live catalogue and audible playback; other-browser/platform evidence
   where available. This can run alongside legal research but cannot satisfy its independent gate. Redact diagnostics/URLs.
3. **Bounded maintenance tasks:** fix Settings clear pending/error/retry semantics and test file/DB/race/failure/persistence;
   inspect/redact diagnostics and verify request/deletion boundaries. No user-data loss, migration or global player rewrite.
4. **D5 design before another source:** provider-scoped identity/capabilities/errors/auth/player/queue/retention model and
   backwards-compatible migration/tests. Preserve native KMP/Compose and the separate browser; planned design is not implemented support.
5. **Spotify feasibility before code:** re-read current policy/API/SDK/endpoint/redirect/quotas; establish a permitted scope,
   platform route, eligible account/access and explicit cost authority. Current §III.5/Premium/dev-mode/public-scale/privacy/licence
   questions remain; execution stays Deferred, no purchase/credentials/backend/SDK authorised.
6. **Other providers / optional Watch:** value, content/terms/maintenance/platform/privacy/licence/cost evidence; an actual owner
   decision before a third-party script/CSP/live runtime spike. No universal browser/native parity promise.
7. **Publication follow-up, now explicitly authorised:** review the working tree, run applicable PR checks, commit/push/PR
   on the assigned session branch, fix root causes and merge only after this work is complete. The original alignment was
   unpublished; §12 records the follow-up. No public app release/tag or new provider is authorised by this instruction.

## 10. Genuine owner decisions (none needed to reaffirm the identity)

The multi-source direction, YouTube priority and Spotify plan are **resolved**, not questions to ask again.
Future gated decisions are: the legal/controller/private-contact/adoption approach and permitted service scopes;
whether any Spotify account/recurring-cost/access constraint may change (not assumed); whether a proposed visible Watch mode
is wanted enough to authorise its runtime/privacy/CSP work; and when, if ever, a public app release is authorised after
independent technical/legal gates. Commit/push/conditional merge of this alignment is now explicitly authorised (§12). No technical workaround, separation of queues, Premium purchase or missing contact is invented here.

## 11. Every changed/created/deleted file and why

**110 files: 104 modified, 6 created, 0 deleted**, relative to starting revision `12498caa02607edd74504161f388fc2e11d34a4c`. All work remains on `arena/5ced2dbe-dhun`. The original alignment snapshot was unstaged/uncommitted; §12 tracks the later authorised PR/CI follow-up. Canonical licence texts, dependency lockfiles, screenshot assets, `.github/workflows`, provider signatures/SQL schema and user data unchanged. Ignored browser builds/test screenshots and external tool/cache/log files are not Git additions.

| Action | Exact file | Reason |
| --- | --- | --- |
| Modified | `.ai/AUTONOMY_PROMPT.md` | Current authority/session permissions above preserved historical autonomy and extraction orders; no automatic publication. |
| Modified | `.ai/DEBUG_LOG.md` | Dated causes/fixes for claims/privacy drift and actual PR native/CORS/sample-label failures; failed attempts, final evidence and old incidents retained. |
| Modified | `.ai/DEPENDENCY_AUDIT.md` | Dated applicability note: old fallback/version/licence observations are historical, not current resolved review. |
| Modified | `.ai/HANDOFF_NEXT_SESSION.md` | Current direction, evidence and remaining gates above intact archived B1/B2/branch snapshots. |
| Modified | `.ai/KNOWN_LIMITATIONS.md` | Current provider permission, storage/deletion, architecture and verification gaps; old incidents dated. |
| Modified | `.ai/MASTER_PROMPT.md` | Multi-source authority, KMP/browser/provider/transport split and history; record explicit follow-up PR/merge authority without permitting public app releases or new services/providers. |
| Modified | `.ai/PROBLEMS_AND_FIXES.md` | Label 2026-09-01 plan audit/old extractor and browser impossibility orders historical; retain original analysis. |
| Modified | `.ai/PROMPT_SEQUENCE.md` | Archive phase prompts; point to current PRD/instructions rather than re-executing historic build tasks. |
| Modified | `.ai/README.md` | Instruction reading order and intent-vs-behaviour/permissions hierarchy. |
| Modified | `.ai/RISK_REGISTER.md` | Dated current policy, privacy, access/cost, identity and evidence risk reconciliation. |
| Modified | `.ai/ROADMAP.md` | Current alignment/completion and remaining dependency order; earlier active tasks explicitly historical. |
| Modified | `.ai/WEBSITE_PLAN.md` | Current identity, source/generation/status contract above preserved design and deployment history. |
| Modified | `.ai/ui-research-vivi-music.md` | Historical attribution/copying note is not renewed rewrite authority or complete licence/content permission. |
| Modified | `.github/ISSUE_TEMPLATE/playback_failure.md` | Ask for actual client/provider/error; silent samples and redaction guidance; no browser-YouTube assumption. |
| Modified | `README.md` | Correct product and platform/provider identity, privacy/offline scope, current pre-release pointer and misleading native-gallery browser links. |
| Modified | `SECURITY.md` | Scope browser data flow to JioSaavn rather than YouTube; preserve interim private-contact procedure. |
| Modified | `THIRD_PARTY.md` | Separate software grants from services/content; disclose actual services, independence and unresolved distribution notices/permissions. |
| Modified | `app-desktop/build.gradle.kts` | Installer description identifies the multi-source product and this native YouTube Music build; packaging logic unchanged. |
| Modified | `app-web/README.md` | Real browser/source/feature/privacy/CSP boundaries, product direction, five legal pages and fresh 165-test count. |
| Modified | `app-web/package.json` | Package description scopes browser JioSaavn within DHUN multi-source direction; no dependency or licence-grant change. |
| Modified | `app-web/src/css/app.css` | One overflow-wrap rule keeps sample/error disclosure readable on narrow screens; no new control or design-system change. |
| Modified | `app-web/src/index.html` | Browser title/description scopes identity and current provider; strict CSP unchanged. |
| Modified | `app-web/src/js/api.js` | Comments only: unofficial access, actual query/ID disclosures and unverified per-host provenance/rights; requests/failover logic unchanged. |
| Modified | `app-web/src/js/data/legal-content.js` | Regenerated native-corpus parity fixture from canonical legal inputs; still excluded from shipped browser app. |
| Modified | `app-web/src/js/data/web-legal.js` | Rewrite independent five-page browser corpus with true providers/storage/erase/rights, draft status and build tokens. |
| Modified | `app-web/src/js/legal.js` | Correct renderer/pipeline comment: native corpus is not browser policy; renderer behaviour unchanged. |
| Modified | `app-web/src/js/main.js` | Truthful library-only Erase confirmation plus always label fictional Home fallback after HTTP/shape as well as network errors; detail/search HTTP-error semantics preserved. |
| Modified | `app-web/src/js/version.js` | Local browser patch/cache identity 3.0.1 lets a future authorised deployment retire stale legal/copy assets; no publication. |
| Modified | `app-web/src/js/views.js` | Erase library label replaces misleading erase-browser wording; actual scope unchanged. |
| Modified | `app-web/src/manifest.webmanifest` | Browser install description scopes product/current JioSaavn client; no new platform feature. |
| Modified | `app-web/src/sw.js` | Default shell-cache namespace follows browser 3.0.1; cache strategy, storage schema and audio/API exclusions unchanged. |
| Modified | `app-web/tests/legal.test.mjs` | Five new semantic/renderer privacy, direction, rights, draft and token tests; existing exact five-ID corpus contract retained. |
| Modified | `app-web/tests/views.test.mjs` | Assert truthful Erase library control label without changing storage behaviour. |
| Modified | `docs/INSTALL.md` | Current provider/platform/release/rights caveats and Linux/macOS verification limits; no release artifacts changed. |
| Modified | `docs/PRD.md` | Authoritative multi-source identity, YouTube-first/Spotify-planned priority, status vocabulary and per-platform implementation evidence. |
| Modified | `docs/README.md` | Onboarding authority, intent-vs-code and separate browser feature/provider boundaries; current audit links. |
| Modified | `docs/TRD.md` | Real architecture/providers, incomplete neutrality and non-destructive migration follow-up; preserve native/browser stacks. |
| Modified | `docs/app-flow.md` | Remove exact native/browser settings/navigation parity and unqualified offline implication; distinguish current sources. |
| Modified | `docs/backend-schema.md` | Namespaced provider entities are proposed, not already implemented; source-shaped/native-vs-browser storage and permission caveats. |
| Modified | `docs/decisions/ADR-001-extraction-engine.md` | Dated applicability note; preserve source-specific accepted decision without claiming global identity or content permission. |
| Modified | `docs/decisions/ADR-002-fullscreen-player-design.md` | Dated applicability amendment corrects old source-neutrality claims; visual decisions and original body preserved. |
| Modified | `docs/decisions/ADR-006-offline-music-downloads-architecture.md` | Dated scope/permission note on native download decision; browser absence is not a universal technical impossibility. |
| Modified | `docs/decisions/ADR-008-browser-web-player-target.md` | Current product/provider/approval applicability above preserved browser-target history and amendments. |
| Modified | `docs/decisions/ADR-009-marketing-site.md` | Current product/metadata applicability note; original Proposed status retained, not silently accepted. |
| Modified | `docs/decisions/ADR-010-multi-source-provider-direction.md` | Dated 2026-10-28 amendment clarifies D1–D6, Watch proposal, all Spotify gates, current split and design-only D5; original decisions retained. |
| Modified | `docs/decisions/README.md` | Index reflects accepted/amended ADR-010, proposed ADR-009 and current dependencies. |
| Modified | `docs/implementation-plan.md` | Current authority/provider/evidence/scope overlay; no new implementation permission or stack migration. |
| Modified | `docs/legal/PRIVACY-DRAFT.md` | Archive earlier draft as predecessor; link actual native/browser sources; body preserved. |
| Modified | `docs/legal/TERMS-DRAFT.md` | Archive earlier draft as predecessor, not adopted policy; body preserved. |
| Modified | `docs/releases/rolling-test.md` | Current platform/provider boundary pointer; preserve rolling/private-draft and fixed-release history. |
| Modified | `docs/research/02-baseline-audit-2026-10-20.md` | Dated current-direction pointer above preserved baseline observations/results. |
| Modified | `docs/research/03-youtube-youtube-music.md` | Dated primary-source recheck: real resolver split, API/IFrame capabilities/quotas and extraction/background/download/storage risks; historical research retained. |
| Modified | `docs/research/04-spotify.md` | Dated primary recheck: metadata/control/SDK/App Remote, quotas/Premium/PKCE/policy/privacy/cost gates; no implementation; original research retained. |
| Modified | `docs/research/05-source-capability-matrix.md` | Current implementation-vs-upstream evidence/cost/privacy/architecture/R1–R12 matrix; original historical matrix preserved in §6. |
| Created | `docs/research/06-multi-source-claims-audit.md` | New discrepancy table, completed corrections, primary evidence, legal/architecture/open gates, validation and every-file change manifest. |
| Created | `docs/research/06-multi-source-match-inventory.md` | New complete baseline/final matching-file/line classifications, scope/query and generated-artifact coverage; no payload datasets copied. |
| Modified | `docs/research/README.md` | Current research index/authority/evidence keys, accepted ADR status and audit/inventory links. |
| Modified | `docs/verification/SCREENSHOT_PROVENANCE.md` | Dated applicability note: preserve captures/dimensions/old links; no current browser-parity, hardware or copyright-clearance inference. |
| Modified | `docs/web-player/ARCHITECTURE.md` | Correct purity/error taxonomy/CSP/module/legal boundary; describe missing provider identity/capability/auth/player design. |
| Modified | `docs/web-player/DECISIONS-AND-CHANGES.md` | Dated current multi-source/Spotify/Watch/permission overlay; earlier owner quotations remain historical. |
| Modified | `docs/web-player/IMPLEMENTATION-PLAN.md` | Order permission/evidence/bounded design dependencies; no implied new runtime/provider authorisation. |
| Modified | `docs/web-player/IMPLEMENTATION-STATUS.md` | Partial/source-test evidence vs release readiness; fresh counts/current scope; old CI is not a new deployment/audio/legal pass. |
| Modified | `docs/web-player/KNOWN-LIMITATIONS.md` | Current JioSaavn/access/erase/feature limits and project-level Spotify plan; no universal impossible-provider assertion. |
| Modified | `docs/web-player/PRD.md` | Browser PRD inherits project intent but states its actual single-catalogue feature/status constraints. |
| Modified | `docs/web-player/PROVIDER-INTEGRATION.md` | Three failover wrappers are one catalogue; real boundaries/permissions, Spotify planned and all policy/access gates. |
| Modified | `docs/web-player/README.md` | Index/onboarding distinguishes authoritative direction, actual browser provider and independent drafts. |
| Modified | `docs/web-player/RELEASE-DEPLOYMENT.md` | Record local browser/cache patch 3.0.1 and publication prerequisites, not an already published release. |
| Modified | `docs/web-player/RESEARCH-REGISTER.md` | Current primary permission/technical recheck links, evidence and scoped future work. |
| Modified | `docs/web-player/RUNBOOK.md` | Requests for planned sources are not permanently excluded; current D2/approval and Spotify feasibility gates remain. |
| Modified | `docs/web-player/SECURITY-PRIVACY.md` | Actual hosts/headers/credentials-vs-media/hosting/snapshots/erase scope and retention unknowns. |
| Modified | `docs/web-player/TESTING.md` | 165-test size and new real-browser legal/erase contract; distinguish unit/local render from live/native/legal acceptance. |
| Modified | `docs/web-player/TRD.md` | Current shell-cache patch reference; no architecture rewrite. |
| Modified | `docs/web-player/USER-GUIDE.md` | Current browser provider/features/privacy/erase labels and multi-source plan without native parity. |
| Modified | `legal/about.md` | Multi-source identity, current native YouTube implementation, Spotify plan, platform/draft/rights limits and live build tokens. |
| Modified | `legal/open-source-licenses.md` | Software grants are not content/service permission; outstanding notice review; verbatim includes unchanged. |
| Modified | `legal/privacy.md` | Actual native and browser requests, visitor IDs/lyrics/caches/storage/logs/deletion controls and limits; no invented Spotify flows. |
| Modified | `legal/security-reporting.md` | Update source reconciliation date because included SECURITY.md changed; procedure/status preserved. |
| Modified | `legal/support.md` | Real controls and their completion/error limitations, current providers, privacy/reporting redaction and no invented contacts. |
| Modified | `legal/terms.md` | Draft multi-source scope, native/browser/Spotify distinction and software/content/provider rights restrictions, not legal approval. |
| Modified | `legal/third-party-notices.md` | Service-vs-software/rights and unresolved review clarification; existing THIRD_PARTY inclusion preserved. |
| Modified | `scripts/gen_legal_content.py` | Correct pipeline comments/generated banners: native bundle plus excluded JS parity fixture; generation semantics maintained. |
| Created | `scripts/product_claims.py` | New positive cross-surface direction/status/platform/privacy/public-page contracts, not a blanket provider-word ban. |
| Modified | `scripts/test_app_web.py` | Exempt exact legal policy-link targets, not network origins; pin runtime connect-src to actual wrappers/CDN. |
| Modified | `scripts/test_legal_content.py` | Replace false absent-cache-control/compliance word bans; assert real wiring/hosts/drafts and native/parity scope; existing hashes/full texts/tokens retained. |
| Created | `scripts/test_product_claims.py` | New 18 positive/mutation/source-evidence/gallery/SDK-boundary tests auto-discovered by existing CI. |
| Modified | `scripts/test_repo_health.py` | Include non-ignored untracked audit/new files in hygiene/link/secret checks; correct unofficial-wrapper comment. |
| Modified | `scripts/test_web_player_docs.py` | Read actual JS test count in mutation fixture rather than stale hard-coded 160. |
| Modified | `scripts/test_website_claims.py` | Planning-vs-present support, negation, real browser provider and non-affiliation fixtures; legitimate provider names retained. |
| Modified | `scripts/test_website_smoke.py` | Four-route/current-direction/404 stale-status mutations plus existing HTTP/canonical/link rules. |
| Modified | `scripts/test_website_workflow.py` | Guard controlled catalogue fixtures for all app measurement contexts and retain exact unexpected-console/page-error failure predicates. |
| Modified | `scripts/website_claims.py` | Allow legitimate explicit plans/research, reject current Spotify/additional-provider support and wrong browser-YouTube claims; call positive built-page contract. |
| Modified | `scripts/website_smoke.py` | Apply shared positive product/status/legal-link/metadata contract to all four served public routes including 404. |
| Modified | `shared/src/commonMain/kotlin/dev/dhun/legal/DhunAppInfo.kt` | KDoc only: remove stale hard-coded Android/MSI version comparison; runtime metadata and four-token substitution unchanged. |
| Modified | `shared/src/commonMain/kotlin/dev/dhun/legal/LegalContent.kt` | Regenerated compiled native legal documents/includes/digests from authoritative Markdown; no hand-edit-only output fix. |
| Modified | `shared/src/commonMain/kotlin/dev/dhun/provider/MusicProvider.kt` | KDoc only: acknowledge InnerTube filters/IDs and unfinished neutrality; signatures/functionality unchanged. |
| Modified | `shared/src/commonMain/kotlin/dev/dhun/ui/legal/AboutLegalScreen.kt` | Display draft/not-in-force and Last updated labels, not legal effectiveness; existing navigation/schema preserved. |
| Modified | `shared/src/jvmTest/kotlin/dev/dhun/legal/MarkdownParserTest.kt` | Replace obsolete three-row Support expectation with exact nine real controls, complete columns and failure/retention semantics; parser gate retained. |
| Modified | `website/budget-baseline.json` | Deliberately record added product/provider/legal disclosures; 5% ratchet and 60 KiB absolute budget unchanged. |
| Modified | `website/dist/404.html` | Fresh generated public recovery page with shared product/provider/draft footer and metadata. |
| Modified | `website/dist/features/index.html` | Fresh generated scoped features/FAQ/rights/provider/privacy/evidence copy. |
| Modified | `website/dist/index.html` | Fresh generated multi-source lead/metadata/platform/history/privacy and scoped native/browser comparisons. |
| Modified | `website/dist/ui/index.html` | Fresh generated historical-native vs browser comparisons; no exact-feature or copyright-clearance implication. |
| Modified | `website/src/_data/site.js` | Canonical identity/metadata/platform/privacy/rights/status/scoped gallery copy, non-affiliation and accurate source paths; retained PR/screenshot history. |
| Modified | `website/src/_includes/base.njk` | Shared visible platform/provider/plan block, draft legal links, truthful JSON-LD; accessible footer report-link label. |
| Modified | `website/src/features.njk` | Scoped native/browser current features, corrected FAQ/data/cache/account/cost risks and legal drafts. |
| Modified | `website/src/index.njk` | Multi-source front matter/description and native-vs-browser/offline/evidence caveats. |
| Modified | `website/src/ui.njk` | Historical native captures are not identical browser screens; scope metadata and comparison links. |
| Modified | `website/tests/browser.mjs` | Five legal/version/erase contracts plus controlled catalogue UI contexts and real HTTP-outage Home/detail assertions; console/page-error and geometry gates unchanged. |
| Created | `website/tests/catalogue-fixture.mjs` | Test-only interception of configured wrapper API paths for explicit network refusal/HTTP 503; no live-service success or security bypass. |
| Created | `website/tests/catalogue-fixture.test.mjs` | Seven mutation/scope/failure/context/error-propagation tests for controlled outages; local assets, unrelated origins and service-worker behaviour remain real. |

The generated bundles/pages above were rebuilt from their authoritative inputs. This manifest includes this audit, its inventory, every small code/comment/metadata change and every changed test/document; no omitted deleted file or unreported implementation is implied.

## 12. Authorised verification / PR / merge follow-up — 2026-10-28

After the initial unpublished alignment, the owner explicitly authorised continued
verification/fixes, commits and pushes **only to `arena/5ced2dbe-dhun`**, a PR,
and autonomous merge only after the requested work and applicable PR checks are
complete. Failures must be investigated and corrected, not hidden by changing
thresholds, exclusions, provider semantics or legal/hardware claims. Initial
“not committed/published” statements above describe that earlier snapshot.

At follow-up inspection, GitHub and `git fetch origin main` confirm the starting
`main` is still `12498ca` and no assigned-branch PR exists. Its app/build/site
checks passed. The separately scheduled extraction-health run
[40483966781](https://github.com/99ggprooo00-code/DHUN/actions/runs/40483966781)
reports **ENVIRONMENT_BLOCKED**; that historical/live-service probe is not a new
PR regression or evidence of successful playback. GitHub denies branch-protection
inspection to this integration (HTTP 403); normal merge enforcement is not bypassed.

**Pre-push rerun:** root `npm run check` passes (165 tests, 0 failures/skips,
8,918.493227 ms; build/icons and 20 docs pass); Python discovery passes **493 tests
in 6.958 s**. Website rebuild, 52 rule tests, HTML validation, legal generator
(7 pages / 2 targets), claims (4 pages), quality (32), product contract, 51 local
fragment links and `git diff --check` pass. Minification preserves fresh output
(87,370 bytes saved). Updated scan: 753 paths / 727 text / 26 binary exclusions,
269 matching non-report files / 2,462 lines / 286 baseline-or-final files.

At that pre-push snapshot commit/push/PR/new CI were pending. The following
ledger records the actual later runs; no remote success or merge is inferred
from the original local tests.
Existing main workflows can deploy the site and refresh the private test draft
after merge; no public app-release intent file, manual release, new provider,
paid service or legal approval is added. Provider permissions/legal adoption,
current hardware and deployed-origin live audible playback remain open even if
all applicable PR checks pass.

### PR #197 — first remote results and root-cause fixes

Pushed **`7ca24a075fd850b2c41a6cc2694f565a0bc604fa`** to the assigned branch and
opened [PR #197](https://github.com/99ggprooo00-code/DHUN/pull/197). Actual first runs:

| Run | Actual result on initial PR head | Root cause / disposition |
| --- | --- | --- |
| [CI PR 40635056354](https://github.com/99ggprooo00-code/DHUN/actions/runs/40635056354), push 40635014929 | Failed in shared JVM tests | `MarkdownParserTest`: expected 3 Support rows, got 9. Generated Kotlin compiled; stale assertion failed. Verify exact implemented labels/order, full cells and retention/failure statements, not merely a new numeric count. |
| [Build APK 40635056383](https://github.com/99ggprooo00-code/DHUN/actions/runs/40635056383) | **Success** | Android debug build/unit coupling passed on GitHub; not device/audio evidence. |
| [test-release 40635056412](https://github.com/99ggprooo00-code/DHUN/actions/runs/40635056412) | Failed | APK passed; Windows shared JVM theme/regression step hit the same stale Support-table assertion before MSI packaging. AAB/publication jobs correctly skipped for PR. |
| [website 40635056345](https://github.com/99ggprooo00-code/DHUN/actions/runs/40635056345) | Build and Lighthouse **passed**, browser **failed** | 144 measurements / three console failures from live `saavn.sumit.co` CORS refusals. The app handled failover; the UI harness depended on live community services. Deployment/served checks correctly skipped for PR. |

GitHub check annotations and the browser step log posted by the existing workflow
were read. Raw run-log archives are unreachable from this environment; no hidden
log contents or unexecuted native pass is invented.

**Proper corrections, not waived checks:**

- Native parser test pins all nine existing controls, four nonempty columns and
  relevant retention/failure cells. No control removed or legal disclosure weakened.
- Browser UI contexts use narrow test-only configured-wrapper `/api/` interception:
  explicit network refusal for layout/keyboard/sample flows, and HTTP 503 for a
  separate negative case. Local assets, service workers, unrelated origins, CSP,
  geometry/accessibility and unexpected console/page-error predicates stay intact.
  Seven fixture tests and two Python harness guards verify scope/wiring/failures.
  No successful catalogue/audio/CORS support is inferred from these fixtures.
- The HTTP fixture exposed a genuine Home disclosure bug: fictional rows after
  HTTP/shape failures left the source badge idle because only network errors set
  sample mode. A bounded `noteFailure` call labels every Home fallback. The HTTP
  browser case asserts the no-audio sample notice **and** that an HTTP detail
  refusal stays HTTP 503, even for a demo id; no fake successful album is inserted.
- First HTTP-only layout fixture was the wrong scenario for the intended
  network-only entity fallback: local run 138 measurements / 7 failures, not a
  pass. Correct scenario separation fixed the harness. The HTTP check then hit
  a network-idle wait while failover cancels in-flight losers; explicit visible
  badge/error-state waits replaced global network quiescence, not a longer timeout.

**Local corrected executions:** root check **165/0/0** (8,989.506076 ms, build/icons/
20 docs pass); site rules **59/0/0**; full unrestricted Chromium **146 measurements,
0 notes, 0 failures**, exit 0 (83.259 s). Narrowed diagnostic runs are not counted
as the full pass. Python discovery already passes **495 tests / 7.559 s** including
the two new harness guards. After final ledger/inventory edits, full Python
was rerun: **495 tests / 7.708 s, OK**. Final scan: **755 paths** (753 tracked +
2 untracked), **729 text / 26 binary exclusions**, **270 matching files / 2,471
lines / 287 baseline-or-final files**; audit self-references excluded.
The original 106-file manifest now includes four justified test/fixture paths:
**110 files = 104 modified + 6 created, 0 deleted**, always compared to the task's
starting revision, not to a later clean commit.

**Current state:** corrected source/tests and evidence ready for validation/push;
fresh final-head remote CI/package/site confirmation is still required. No merge
has happened. Independent legal/hardware/deployed-audio gates above remain open.
