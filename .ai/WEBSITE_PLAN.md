# DHUN web presence — research and gated implementation plan

> **Status (2026-10-08): RESEARCH / PLAN ONLY. Nothing here authorizes a
> website implementation or a DHUN web player.** The first gate is a product
> decision by the user. Until that decision, there is no `website/` directory,
> no site dependency, no Pages workflow change, and no web-target application
> code.
>
> Standing instruction: research first, compare several approaches, preserve
> the analysis in a `.md` file, and plan when to implement rather than rushing
> into a copy of the sample site.

## 1. The decision that comes before all implementation

The reference URL hides two different products:

- <https://volta-music.com/en> is Volta's **marketing/download website**.
- <https://app.volta-music.com/> is the **Flutter web application**. Re-fetched
  on 2026-10-08, its title is `Volta` and the extracted body is only
  `flutter typography measurement`, which is Flutter's bootstrap surface, not
  the marketing page.

DHUN therefore has two materially different options:

| Option | Meaning | Architectural effect | Status |
|---|---|---|---|
| **A — marketing/download site** | A static public site describing the existing Android and Desktop applications, linking the rolling release and source | New deployment workstream, but not a new application target; does not contradict the accepted Android/Desktop architecture | **Awaiting user choice** |
| **B — DHUN web player** | A browser-playable third application target | Contradicts `.ai/MASTER_PROMPT.md` §1 (`Web is deferred … likely "no"`), §3 (`Web: cut`) and §7 (Web/PWA explicitly outside S1–S6); requires a new ADR and explicit user approval before any scaffold | **Awaiting user choice; blocked on ADR if selected** |

This plan recommends **Option A** because it addresses the requested public
presence without silently reopening a rejected platform target. That is a
recommendation, not a decision. If the user chooses B, stop after writing and
approving a new ADR; do not reinterpret this file as approval.

## 2. Verified baseline — repository, Pages and the advertised URL

Verified on 2026-10-08 against GitHub and the checked-out repository:

### 2.1 The advertised URL is wrong, while Pages itself is working

`README.md` line 2 advertises:

- <https://99ggprooo00.github.io/DHUN/> — **404**, “There isn't a GitHub Pages
  site here.”

The repository owner is `99ggprooo00-code`, not `99ggprooo00`. The Pages API
reports:

```text
build_type = legacy
source.branch = main
source.path = /
html_url = https://99ggprooo00-code.github.io/DHUN/
status = built
```

The canonical URL is therefore:

- <https://99ggprooo00-code.github.io/DHUN/> — **live**, currently rendered by
  GitHub Pages/Jekyll from the root `README.md`.

The post-PR-#135 Pages run **37772062338** on `main@8a8d6c5` succeeded, and the
Pages build API records build **1269157028** as `built` with no error. The
pipeline is not publishing into a black hole. The earlier hypothesis “Pages
source is misconfigured or the artifact is empty” is disproven. The concrete
fault is a missing `-code` in the README hostname. There is still a product
problem: the canonical site is a rendered engineering README, not the planned
public experience.

**Pending user decision:** correct line 2 to the canonical URL now, remove it
until W4, or leave it untouched until the replacement site is ready. Do not
silently choose.

### 2.2 Asset inventory

A repository-wide search (excluding `.git`) finds **zero** `png`, `jpg`,
`jpeg`, `webp`, `svg` or `gif` files. Android launcher art is XML. No product
screenshots are available.

A Volta-like page is image-led, so this is a content blocker rather than an HTML
problem. The preferred capture opportunity is the same device work already
required by S3 rounds 4 and 5. Asset alternatives are:

1. user captures Android landscape/portrait and Windows fullscreen during the
   S3 sessions;
2. an emulator screenshot pipeline is researched and proved separately (not
   available in this sandbox and not assumed viable);
3. CSS-only device frames and schematic UI mockups are used, with no claim that
   they are literal screenshots.

No option is selected yet.

### 2.3 Toolchain measured in this sandbox

| Tool | Measured result |
|---|---|
| Node | `v22.22.3` |
| npm / npx | `10.9.8` / `10.9.8` |
| Python | `3.11.2` |
| Go | not installed |
| Ruby / Bundler | not installed |
| Hugo | not installed |
| npm registry | allowed by the sandbox network policy |

Consequence: Astro, Eleventy, or a no-build static site can be built and checked
locally. Jekyll and Hugo would be CI-blind in this environment. Do not choose
those generators merely because GitHub Pages has historical defaults for them.

## 3. Volta — verified pattern, not content to copy

Sources re-fetched 2026-10-08:

- <https://volta-music.com/en>
- <https://volta-music.com/en/download>
- <https://volta-music.com/en/features>
- <https://app.volta-music.com/>

### 3.1 Information architecture

The marketing homepage follows this sequence:

1. skip link;
2. eyebrow badge row (`FREE · FLAC 24/192* · NO ADS`);
3. four-word H1 (`Your music. Amplified.`);
4. one-sentence value proposition;
5. exactly two CTAs (`Download for free`, `Open in browser`);
6. platform strip;
7. three real product images;
8. four compact proof/stat blocks;
9. an honesty footnote immediately under those claims;
10. three feature sections, each a kicker, one promise, and a UI demonstration;
11. `AND MORE`, feature cards, and `/features` link;
12. closing CTA that repeats the same two actions.

It has three marketing routes under a locale prefix: `/en`, `/en/download`,
`/en/features`. Download and browser app are separate destinations. The
screenshots do most of the persuasion.

### 3.2 What transfers to DHUN

- one promise per viewport;
- two primary actions, not a wall of badges;
- a separate download page and feature page;
- a real demonstration or an adjacent footnote for every product claim;
- a closing CTA that repeats rather than inventing a third conversion path;
- visible support/platform qualifiers close to the claim.

### 3.3 What must not transfer

Volta claims six platforms, cross-device sync, import, accounts, and up to FLAC
24/192. DHUN has no evidence for those claims. DHUN currently has Android and a
Desktop JVM client (Windows first); Linux/macOS are not hardware-verified. It
has no iOS app, web app, account sync, import, or verified audio-quality number.

Copying Volta's words, visual assets, logo, fonts, or measurements is out of
scope. The reference is for page structure only.

## 4. Comparable open-source music projects — W2 research

Research rule: prefer each project's controlled domain or canonical GitHub
repository. Third-party APK/SEO sites are not treated as product evidence.
“Not found” or “archived” is itself a finding.

### 4.1 Spotube

**Sources:** <https://spotube.cc/>,
<https://github.com/team-spotube/spotube> (the old `KRTirtho/spotube` URL now
resolves to this repository), <https://spotube.cc/downloads>.

- **Above the fold:** `Spotube`, then “A cross-platform extensible open-source
  music streaming platform,” a plugin-oriented value proposition, and two CTAs:
  `Downloads` and `Learn More`.
- **Visual proof:** one large real desktop screenshot and a mobile screenshot
  carousel; images appear before the long feature inventory.
- **Risk framing:** the captured homepage does not foreground “unofficial” or
  “may break.” It frames service dependency indirectly as bring-your-own
  metadata/audio plugins. A release note acknowledges a prior hiatus from
  legal/structural complications, but that warning is not above the fold.
- **Distribution:** a dedicated downloads route plus GitHub Releases, F-Droid,
  Flathub, package managers and platform installers.
- **Lesson for DHUN:** strong separation of landing content and download detail;
  real screenshots and two CTAs work. Do **not** copy its broad platform claim
  or hide DHUN's extraction risk behind architecture language.

### 4.2 RiMusic

**Sources:** <https://github.com/fast4x/RiMusic>, historical domain
<https://rimusic.xyz/>, and repository `docs/index.html`.

- **Current state first:** the repository was archived on 2025-07-30 and the
  README says “This project, is closed.” Its checked-in site intentionally
  renders `Website not available.` The historical domain no longer provides a
  usable product site.
- **Above the fold (canonical README):** logo, multilingual/multiplatform
  sentence, ViMusic lineage, customization and “does not collect any data,”
  immediately followed by the closed-project notice.
- **Visual proof:** six phone screenshots.
- **Risk framing:** closure is prominent; the affiliation disclaimer is much
  lower. It does not frame extraction as a service that may break.
- **Distribution:** GitHub, OpenAPK, Accrescent, Obtainium, IzzyOnDroid and
  F-Droid badges remain in the archived README.
- **Lesson for DHUN:** lifecycle status must outrank marketing. Do not leave a
  polished download page pretending that an unverified or retired build is
  current.

### 4.3 InnerTune

**Source:** <https://github.com/z-huang/InnerTune>. No separate homepage is
listed by the canonical repository.

- **Above the fold:** app icon, `A Material 3 YouTube Music client for Android`,
  release/license/download badges, then GitHub, F-Droid and IzzyOnDroid install
  badges.
- **Visual proof:** five phone screenshots after the feature list.
- **Risk framing:** an explicit warning says unsupported YouTube Music regions
  need a proxy/VPN; a bottom disclaimer says the project is not affiliated with
  YouTube/Google. It does not say extraction may break.
- **Distribution:** GitHub Releases, F-Droid and IzzyOnDroid are first-class.
- **Lesson for DHUN:** trusted distribution choices can be presented cleanly,
  but DHUN currently has only test-grade GitHub artifacts and must not imply an
  F-Droid/store channel that does not exist.

### 4.4 ViMusic

**Sources:** original project <https://github.com/vfsfitvnm/ViMusic> and the
separate site <https://vimusic.vercel.app/>.

- **Identity warning:** the original `vfsfitvnm/ViMusic` repository is archived
  (2026-03-15) and has no homepage. `vimusic.vercel.app` belongs to the separate
  `ab007shetty/ViMusic` project: a React/Vite/Supabase web version with Google
  sign-in/cloud sync. It is not the original Android project's marketing site.
- **Above the fold (original README):** icon, one-line Android/YouTube Music
  description, then six screenshots before features.
- **Above the fold (separate web app):** a live library/player (`Master's Mix`),
  not a conventional marketing page.
- **Risk framing:** original README has a non-affiliation disclaimer at the
  bottom, not a may-break notice. Archive state is now supplied by GitHub.
- **Distribution:** original README links GitHub Releases, IzzyOnDroid and
  F-Droid.
- **Lesson for DHUN:** verify ownership before using a domain as inspiration;
  a familiar product name can point at a different architecture and operator.

### 4.5 OuterTune

**Source:** <https://github.com/OuterTune/OuterTune>. The domain
`outertune.app` fetched in research is an unrelated SEO/APK site and is **not**
used as canonical evidence.

- **Above the fold:** icon, one-line Material 3 description, then an unusually
  honest stop notice: the app is no longer in active development and suggests
  replacements.
- **Visual proof:** the collapsed historical README contains three large app
  screenshots plus a gallery.
- **Risk framing:** inactive status is the first substantive content. The old
  README also has a YouTube Music region warning and non-affiliation disclaimer.
- **Distribution:** historical GitHub, IzzyOnDroid and Obtainium links remain
  inside the collapsed section.
- **Lesson for DHUN:** this is the strongest comparable for status honesty. It
  also demonstrates why official links should be allow-listed: a polished
  third-party download site can look more “official” than the repository.

### 4.6 Harmony Music

**Source:** <https://github.com/anandnet/Harmony-Music>. No separate homepage is
listed by the canonical repository.

- **Above the fold:** `This repository is no longer maintained`, then a wide
  cover image, product name, cross-platform statement and feature list.
- **Visual proof:** a cover/banner exists, but no app screenshot gallery is
  presented in the README.
- **Risk framing:** maintenance status is prominent; a long generic
  non-affiliation/no-warranty disclaimer is below download and licence content.
- **Distribution:** GitHub Releases and F-Droid.
- **Lesson for DHUN:** a banner is not product proof. Screenshots are more useful
  than a decorative hero when the promise is an interface.

### 4.7 Moosync

**Sources:** <https://moosync.app/>,
<https://github.com/Moosync/Moosync>, and the public website source
<https://github.com/Moosync/Moosync.github.io>.

- **Above the fold:** `An Open Source Music Player`, H1 `A music player / For
  the community`, an OS-sensitive download control, `Download for other
  platforms`, and a decorative listening illustration.
- **Visual proof:** the page later uses a laptop screenshot among six feature
  callouts. The current app repository's screenshot section is still `TODO`, so
  the site and README are not equally complete.
- **Risk framing:** the marketing page does not foreground “unofficial” or
  “may break.” YouTube integration restrictions and the need for a user API key
  for private content are explained in the wiki instead.
- **Distribution:** direct OS download, other-platform route, GitHub Releases,
  and package channels.
- **Lesson for DHUN:** one detected-platform CTA reduces choice overload, but
  DHUN should not auto-label an OS build “supported” when its hardware gate is
  still open.

### 4.8 Echo Music

**Sources:** <https://echomusic.fun/> and
<https://github.com/EchoMusicApp/Echo-Music>. The GitHub organization is
verified for `echomusic.fun`.

- **Above the fold:** `Music that moves you. No ads. No limits.` with an Android
  APK action; the site presents itself as an Android product.
- **Visual proof:** six named screenshots (Home, Search, Material player, Apple
  style player, Lyrics, Library), repeated in a horizontal showcase.
- **Risk framing:** an FAQ says it is a third-party YouTube Music client and does
  not host songs; the repository carries a very long legal disclaimer. Neither
  presents service breakage as a primary message.
- **Distribution:** the site sends downloads to GitHub Releases; its download
  interstitial contains ads, while the app is described as ad-free.
- **Lesson for DHUN:** named screenshots help users understand what they are
  seeing. Avoid ambiguous “no ads” copy if the download path itself uses ads;
  DHUN can truthfully keep both site and app free of ad/analytics code.

### 4.9 Canonical distribution-link snapshot

These are the destinations exposed by the canonical site/README on 2026-10-08,
not a recommendation that DHUN copy every channel. “None found” means no such
link was found in the canonical material reviewed; it is not proof that a
third-party package does not exist.

| Project | GitHub release/download | F-Droid-family link exposed by project | Constraint |
|---|---|---|---|
| Spotube | <https://github.com/team-spotube/spotube/releases/latest>, <https://spotube.cc/downloads> | <https://f-droid.org/packages/oss.krtirtho.spotube> | broad multi-platform matrix is Spotube evidence only |
| RiMusic | <https://github.com/fast4x/RiMusic/releases/latest> | <https://f-droid.org/packages/it.fast4x.rimusic/>, <https://apt.izzysoft.de/fdroid/index/apk/it.fast4x.rimusic/> | repository is archived/closed; links are historical |
| InnerTune | <https://github.com/z-huang/InnerTune/releases/latest> | <https://f-droid.org/packages/com.zionhuang.music>, <https://apt.izzysoft.de/fdroid/index/apk/com.zionhuang.music> | Android-only channels |
| ViMusic (original) | <https://github.com/vfsfitvnm/ViMusic/releases/latest> | <https://f-droid.org/packages/it.vfsfitvnm.vimusic/>, <https://apt.izzysoft.de/fdroid/index/apk/it.vfsfitvnm.vimusic> | original repository is archived |
| OuterTune | <https://github.com/OuterTune/OuterTune/releases/latest> | IzzyOnDroid: <https://apt.izzysoft.de/fdroid/index/apk/com.dd3boh.outertune> | maintenance-stop notice outranks old download badges |
| Harmony Music | <https://github.com/anandnet/Harmony-Music/releases/latest> | <https://f-droid.org/packages/com.anandnet.harmonymusic> | README says no longer maintained |
| Moosync | <https://github.com/Moosync/moosync-tauri/releases>, <https://moosync.app/> | none found | desktop packaging, not Android/F-Droid |
| Echo Music | <https://github.com/EchoMusicApp/Echo-Music/releases/latest> | none found | site routes Android downloads to GitHub Releases |

DHUN currently has only its own GitHub rolling release. It must not display
F-Droid, store or “stable release” badges based on another project's pattern.

### 4.10 Repository licence snapshot

Verified from each canonical repository on 2026-10-08. These are **code
repository licences**, not permission to reuse site copy, branding, screenshots,
album art, fonts or other third-party assets.

| Project | Canonical repository licence | Evidence boundary |
|---|---|---|
| Spotube | BSD-4-Clause | root `LICENSE` self-identifies as BSD-4-Clause; GitHub's API currently returns `NOASSERTION`, so preserve the actual notice rather than relying on API labelling |
| RiMusic | GPL-3.0 | canonical repository licence metadata/file; archived repository |
| InnerTune | GPL-3.0 | canonical repository licence metadata/file |
| ViMusic (original) | GPL-3.0 | canonical `vfsfitvnm/ViMusic` repository; archived |
| OuterTune | GPL-3.0 | canonical `OuterTune/OuterTune` repository |
| Harmony Music | GPL-3.0 | canonical repository; README says it is no longer maintained |
| Moosync | GPL-3.0 | canonical `Moosync/Moosync` repository |
| Echo Music | GPL-3.0 | canonical `EchoMusicApp/Echo-Music` repository |

This survey does not make any of those projects' visual assets available to
DHUN. W3 still requires asset-by-asset provenance and permission.

### 4.11 Cross-project synthesis

| Pattern | Observed | DHUN rule proposed |
|---|---|---|
| Real UI screenshots | Strong on Spotube, RiMusic, InnerTune, ViMusic, OuterTune and Echo | Do not build an image-led landing page before the asset/licence gate |
| GitHub Releases | Common primary or fallback channel in all eight | Link the rolling `test` release, but call it **test / unverified**, never stable |
| F-Droid / Izzy | Common for mature Android-only clients | Do not show badges for channels DHUN does not have |
| “Unofficial” notice | Usually buried in a disclaimer or FAQ | DHUN should be more candid: concise notice near the first download and full status on `/download` |
| “May break” notice | Rare; closure/maintenance notices appear only after projects stop | Do not wait for failure. State the InnerTube maintenance risk while the project is alive |
| Project lifecycle | RiMusic, ViMusic, OuterTune and Harmony expose archived/inactive state | Site content must derive release/status labels from checked facts, not evergreen copy |
| Screenshots as proof | More persuasive than long adjective lists | One screenshot per major promise; no screenshot means footnote or schematic label |

## 5. DHUN's content truth contract

Every public claim must be one of:

1. **demonstrated** by a current screenshot or visible source/release fact;
2. **qualified** by a directly adjacent footnote;
3. **omitted**.

Candidate claims and their required qualifiers:

| Candidate site claim | Repository evidence | Required public wording boundary |
|---|---|---|
| No sign-in, no cookies, no PO tokens | ADR-001 / ADR-003 and current extraction doctrine | Say “No sign-in required”; do not claim the upstream service can never gate access |
| Free, GPL-3.0, no app ads, no telemetry | `LICENSE`, MASTER_PROMPT, current architecture | Keep the static site analytics-free too, or the no-telemetry message becomes ambiguous |
| Android 7.0+ | both modules `minSdk = 24`, CI `NewApi` gates | Footnote: API 24–25 hardware acceptance is still open |
| Windows desktop client | Compose Desktop/libVLC and rolling MSI | Footnote: unsigned test build; system VLC required; S3 native-surface checks open |
| Linux/macOS via JVM | MASTER_PROMPT platform wording | Do not put in a “supported platforms” strip until packaged and hardware-verified |
| Offline downloads | ADR-006 + merged UI/engine | Demonstrate only after the S3 offline round is accepted |
| Synced lyrics | LRCLIB → YTM → cache and user report | No accuracy/coverage percentage; availability varies by track/source |
| Widgets / tray / SMTC / jump lists / EQ | merged code | Separate “available” from “hardware-verified”; do not bundle them into one unchecked claim |
| Streaming from YouTube Music | accepted architecture | Prominent `Unofficial; upstream changes can interrupt playback` notice |
| “Stable” / “release” | not supported yet | Forbidden until S3/S6 close and a real versioned release is published |
| Audio quality number | no measured contract | Forbidden; do not say lossless, 256 kbps, hi-res, or copy Volta's FLAC metric |

Proposed concise notice (wording requires user approval):

> **Unofficial test build.** DHUN is not affiliated with YouTube or Google.
> Playback relies on upstream interfaces that can change without notice.

That notice should be visible on the landing page near the first download CTA,
not only in a footer. `/download` should carry the expanded prerequisites,
checksum instructions, signing warning, current S3 state and stable GitHub
source/release links.

## 6. Proposed Option-A experience (not yet approved)

### 6.1 Audience and job

Primary visitor: someone deciding in under a minute whether to try DHUN on
Android or Windows. Secondary visitor: a developer checking source, licence and
risk posture. The site is not a replacement for engineering documentation.

### 6.2 Exactly three routes

All routes are static and live under the repository base path `/DHUN/`:

1. `/DHUN/` — promise, proof, key features, status footnote, two CTAs;
2. `/DHUN/download/` — Android/Windows test artifacts, checksums,
   prerequisites, warnings and install guides;
3. `/DHUN/features/` — traceable feature matrix with platform and verification
   status.

Do not add blog, account, pricing, app dashboard or web-player routes in v1.

### 6.3 Landing-page sequence

A DHUN-specific adaptation of the useful Volta structure:

1. skip link and compact navigation (`Features`, `Download`, `GitHub`);
2. eyebrow: `OPEN SOURCE · NO SIGN-IN · NO APP ADS`;
3. short H1 (copy not decided; avoid unprovable superlatives);
4. one sentence naming Android + Desktop and unofficial YouTube Music source;
5. exactly two CTAs: **Download test build** and **View source**;
6. verification strip, not a platform boast: `ANDROID 7+* · WINDOWS* · GPL-3.0`;
7. one real hero composition (Android + Windows screenshots) or clearly labelled
   CSS schematics;
8. proof row: `NO ACCOUNT`, `OFFLINE*`, `SYNCED LYRICS*`, `DESKTOP CLIENT*`;
9. adjacent honesty footnotes;
10. three demonstrated feature sections: private-by-default entry, offline
    library, lyrics/player immersion;
11. secondary feature cards: widgets, tray/native controls, EQ, queues;
12. unofficial/upstream-risk panel;
13. closing repetition of the same two CTAs.

The rolling release must be labelled **Rolling UNVERIFIED development build**,
matching GitHub. The site must not hide that status to improve conversion.

### 6.4 Visual direction

- dark, artwork-led, but not a clone of Volta;
- DHUN's existing Material 3 / artwork / translucent-surface vocabulary;
- system font stack for v1 unless a font licence and self-hosting plan are
  reviewed first;
- no remote font, analytics, advertising, autoplay audio/video or tracking
  pixel;
- restrained motion with `prefers-reduced-motion` parity;
- screenshots carry meaningful alt text; decorative frames carry empty alt;
- readable without backdrop blur or JavaScript.

## 7. Asset and licence gate (W3)

No image or font lands before its rights are recorded.

### Preferred screenshot capture set

Capture during S3 rather than scheduling a competing device session:

- Android portrait: Home with mini-player, Full Player + synced lyrics, Library
  downloads;
- Android landscape: Home/Search/Library with compact docked mini-player (also
  round-4 acceptance evidence);
- Windows: standard window Home, fullscreen with compact docked mini-player,
  Full Player/lyrics, tray or settings only if it actually passed its check.

For each file record: device, OS, app commit/release digest, capture date,
screen, crop/edits, and licence/provenance.

### Content-safety constraint

Live music screenshots often contain copyrighted album artwork, artist photos,
titles and lyrics. Do **not** commit them merely because the app displayed them.
Use one of:

- a project-owned test fixture with user-created or CC0 artwork and synthetic
  metadata;
- artwork with a licence explicitly permitting redistribution, recorded beside
  the asset;
- a schematic CSS mockup labelled as an illustration.

Blurring a third-party cover is not automatically a licence. Lyrics are also
copyrighted content; a screenshot should use project-authored test text unless
permission is established.

### Asset acceptance

- no secrets, account identity, notification content or personal library data;
- no third-party trademark used as endorsement;
- lossless source retained outside Git if large; optimized WebP/AVIF plus
  fallback committed only when sizes are reasonable;
- width/height declared to prevent layout shift;
- dark and small-screen readability reviewed.

## 8. Generator and deployment analysis

### 8.1 Candidates

| Approach | Local verifiability | Fit | Cost/risk |
|---|---|---|---|
| Plain HTML/CSS | excellent | fine for one page | repeated nav/footer and three-route drift without templates |
| Eleventy | excellent with Node/npm | very small static generator | less built-in asset/routing structure; still a valid fallback |
| **Astro** | excellent with Node/npm | static-first components, route structure, image pipeline, minimal client JS | dependency surface larger than plain HTML; must pin lockfile and keep zero-JS defaults |
| Jekyll | poor here (Ruby/Bundler absent) | native legacy Pages convention | locally CI-blind; reject for this environment |
| Hugo | poor here (binary absent) | fast static output | locally CI-blind; reject for this environment |

**Recommendation, not decision:** Astro in `website/`, configured for static
output and `base: "/DHUN"`. It best fits three content routes and reusable claim
/ footnote / feature components while shipping no framework runtime by default.
Eleventy is the fallback if the dependency audit finds Astro disproportionate.

### 8.2 Repository boundary

Proposed layout after W0–W3 pass:

```text
website/
  package.json
  package-lock.json
  astro.config.mjs
  public/
  src/
    components/
    layouts/
    pages/{index,download,features}.astro
```

The website gets its own dependency files and tests. Do not add npm dependencies
at repository root and do not mix generated `dist/` into Git.

### 8.3 Pages migration

Current Pages mode is `legacy`, source `main:/`, and serves the README. W4 would
replace that with a dedicated GitHub Actions Pages deployment **only after one
real page builds locally**. Proposed isolated workflow:

1. checkout;
2. setup Node from pinned major;
3. `npm ci` in `website/`;
4. format/lint/content-contract tests;
5. `npm run build`;
6. link/base-path check against `website/dist`;
7. `upload-pages-artifact`;
8. `deploy-pages` only from `main`.

It must be a standalone workflow (for example `website-pages.yml`), not a job in
app `ci.yml`; a site failure must not redden Kotlin CI or block rolling test
artifacts. Pull requests build and inspect the static output but do not deploy.
The workflow's action versions must avoid the Node-20 warning currently emitted
by GitHub's auto-created legacy Pages workflow.

Migration acceptance includes checking both:

- <https://99ggprooo00-code.github.io/DHUN/> (canonical);
- the user-approved README link target.

A rollback is switching Pages back to `main:/`; because W4 is static and no
application target changes, rollback does not touch Android/Desktop releases.

## 9. Staged work and gates

The order is intentionally after or alongside the remaining hardware capture,
never instead of S3.

### W0 — Decide

- User selects Option A or B.
- If B: stop and write a new ADR; no site/player scaffold until accepted.
- Decide screenshot vs CSS schematic, notice tone/prominence, URL/custom domain,
  v1 i18n, and immediate README-link disposition.

**Gate:** explicit user answers recorded in this file.

### W1 — Diagnose Pages and front-door link

- ✅ Diagnose actual Pages settings and canonical URL.
- ✅ Disprove the empty-artifact/source-misconfiguration hypothesis.
- ⏳ Apply the user-approved README action.
- ⏳ Decide migration/rollback steps for the selected site scope.

**Gate:** canonical URL and README no longer disagree.

### W2 — Comparable research

- ✅ Spotube, RiMusic, InnerTune, ViMusic, OuterTune, Harmony Music, Moosync and
  Echo Music reviewed above with canonical URLs, screenshot/distribution and
  risk-framing findings.
- ⏳ Recheck any source that changes before implementation; several projects are
  archived or have impersonating third-party sites.

**Gate:** user approves the DHUN content posture, not merely the visual reference.

### W3 — Assets and claims

- Capture or design the agreed hero path.
- Record provenance/licence before committing each asset.
- Build a final claim-to-evidence ledger from current main and the latest S3
  report.
- Reject any screenshot that embeds unlicensed music art/lyrics.

**Gate:** at least one legal, current Android visual and one Windows visual, or
explicit approval for labelled CSS schematics.

### W4 — Scaffold

- Create `website/` with the selected static generator.
- Add its own PR build workflow.
- Implement one real landing page with actual copy, skip link, two CTAs,
  canonical metadata, OG metadata and `/DHUN` base-path checks.
- Change Pages deployment only after local and PR checks pass.

**Gate:** one content-complete page at the canonical URL; no app CI regression.

### W5 — Build out

- Add `/download/` and `/features/`.
- Generate artifact links from stable rolling-release URLs, never scrape an
  untrusted mirror.
- Keep checksums/instructions and verification labels explicit.
- Add structured data only where it describes current facts.

**Gate:** every visible product claim maps to the claim ledger.

### W6 — Accept

- keyboard-only and screen-reader landmark review;
- WCAG AA contrast and visible focus;
- reduced-motion check;
- 320px, common phone, tablet and desktop responsive review;
- Lighthouse accessibility/performance/best-practices run with recorded version;
- broken-link and `/DHUN` base-path crawl;
- no cookies, analytics, remote fonts or unexpected requests;
- user wording and visual sign-off.

**Gate:** user approval. Site acceptance does not close S3 or S6.

## 10. Open decisions — do not infer answers

1. **Product:** Option A static marketing/download site, or Option B browser
   player? Option B requires a new ADR.
2. **Hero evidence:** screenshots captured during S3, or labelled CSS mockups?
3. **Voice:** cautious engineering voice with a prominent unofficial/may-break
   notice, or a more confident headline with the same notice lower on the first
   viewport?
4. **URL:** stay on `99ggprooo00-code.github.io/DHUN/`, or plan a custom domain?
5. **Language:** English-only v1, or locale-prefixed routes from v1?
6. **README now:** correct line 2 to the canonical `-code` URL, remove it, or
   wait until W4 replaces the rendered README?

Until these are answered, W0 is open and implementation is blocked.
