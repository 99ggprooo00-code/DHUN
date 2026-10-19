/**
 * Single source of copy and links for the whole site.
 *
 * Everything a translator would need to touch lives here, so i18n stays
 * additive later (Part A §7) — but no i18n is built in v1.
 *
 * Truth contract (Part A §4): every claim below is either demonstrated by a
 * shipped fact, qualified by an adjacent footnote, or omitted. Each claim
 * carries a `source` note that the templates emit as an HTML comment citing
 * the ADR or PR it comes from, so a reviewer can trace it in the built HTML.
 */

const REPO = "https://github.com/99ggprooo00-code/DHUN";
const SITE = "https://99ggprooo00-code.github.io/DHUN";
const PATH = "/DHUN";
// Fixed public pre-release; the rolling `test` tag is a private draft.
const PUBLIC_RELEASE = `${REPO}/releases/tag/v1.00.002`;

export default {
  url: SITE,
  path: PATH,
  repo: REPO,
  issues: `${REPO}/issues`,
  changelog: `${REPO}/blob/main/CHANGELOG.md`,
  licence: `${REPO}/blob/main/LICENSE`,
  thirdParty: `${REPO}/blob/main/THIRD_PARTY.md`,
  plan: `${REPO}/blob/main/.ai/WEBSITE_PLAN.md`,
  publicReleasePage: PUBLIC_RELEASE,
  releases: `${REPO}/releases`,

  title: "DHUN — a YouTube Music client with no sign-in",
  description:
    "DHUN is a free, open-source YouTube Music client for Android and Windows. No sign-in, no cookies, no ads, no telemetry — and downloads that stay on your device.",
  ogType: "website",

  // The navigation the shell renders. Three destinations and one external link,
  // each carrying the icon its label draws — the same geometry the app's own
  // navigation uses for Home, Search and Library (see website/eleventy.config.js).
  nav: [
    { href: "/features/", label: "Features", icon: "check" },
    { href: "/ui/", label: "Interface", icon: "widget" },
    { href: REPO, label: "Source", icon: "external", external: true },
  ],

  // The app's six accent ramps, in the app's order, offered by the appearance
  // control in the footer. The colours come from css/tokens.css (generated from
  // the app's token file); these are the names.
  // Where the appearance control's two choices come from. Cited in the emitted
  // HTML so the claim-traceability rule (`scripts/website_quality.py`) can follow
  // it: the ramps and the theme names are the app's, read out of its token file
  // by website/tools/sync-tokens.mjs.
  appearanceSource:
    "app-web/src/css/tokens.css (generated from DhunAppearance.kt by website/tools/sync-tokens.mjs); website/css/shell.css",
  accents: [
    { id: "brand", label: "Brand", default: true },
    { id: "azure", label: "Azure" },
    { id: "jade", label: "Jade" },
    { id: "amber", label: "Amber" },
    { id: "rose", label: "Rose" },
    { id: "cyan", label: "Cyan" },
  ],

  // Badge row. Each is a fact, not a slogan.
  badges: [
    { label: "FREE", source: "GPL-3.0 — LICENSE, THIRD_PARTY.md" },
    { label: "GPL-3.0", source: "LICENSE" },
    { label: "NO SIGN-IN", source: "ADR-001, ADR-003 — no cookies, no PO tokens" },
    { label: "NO ADS", source: "No advertising or analytics dependency in any module" },
  ],

  h1: "Music without an account.",
  lead:
    "DHUN plays YouTube Music on Android and Windows through its own tokenless extraction chain. There is no sign-in to create, no cookie to hand over, and no PO token to solve — and what you save for offline listening stays on your device.",
  leadSource: "ADR-001 (extraction engine), ADR-003 (staged identity chain), ADR-006 (offline downloads)",
  ctaPrimary: { href: "/app/", label: "Open the web app" },
  ctaSecondary: { href: REPO, label: "Source on GitHub", external: true },
  ctaNote:
    "DHUN's web app runs at /app/ — the same shell, navigation, keyboard shortcuts, equaliser, synced lyrics and legal pages as the Android and Windows builds, installed from this page by your browser and usable offline once it has loaded. It talks to YouTube Music directly from your browser, so a refusal by that origin is shown on screen rather than hidden.",
  ctaNoteSource:
    "ADR-008 amendment 2026-10-09 (2) (deployment); app-web/src (service worker, keyboard shortcuts, endpoint setting)",

  // Verified real device captures (`website/src/assets/screenshots/*.webp`),
  // mapped 1-to-1 to `.ai/WEBSITE_PLAN.md` Part A §9 and deep-linked into `/app/`.
  screenshots: {
    "home-phone": {
      id: "mock-home-phone",
      variantClass: "mock--phone",
      frameClass: "shot--phone",
      src: "/assets/screenshots/android-home-portrait.webp",
      width: 1080,
      height: 2288,
      eager: true,
      alt: "DHUN Android Home screen showing greeting header, mood filter chips, Recommended songs grid, Listen again section, docked mini-player, and bottom navigation bar",
      badge: "Captured on Android phone · DHUN v1.00.001",
      caption:
        "Home screen with mood filter chips, Recommended songs, Listen again, docked mini-player, and three-tab navigation.",
      appHref: "/app/",
      appLabel: "Open Home in the app",
    },
    "player-phone": {
      id: "mock-player-phone",
      variantClass: "mock--phone",
      frameClass: "shot--phone",
      src: "/assets/screenshots/android-full-player.webp",
      width: 1080,
      height: 2278,
      eager: false,
      alt: "DHUN Android Full Player screen showing blurred artwork, track title Dhun by Rockheads, seek bar at 0:00 of 4:18, transport controls, and bottom action row",
      badge: "Captured on Android phone · DHUN v1.00.001",
      caption:
        "Full-screen player sheet with blurred artwork backdrop, seek scrubber, shuffle, previous, play, next, and repeat transport, and queue and lyrics actions.",
      appHref: "/app/#/player",
      appLabel: "Open Full player in the app",
    },
    "lyrics-phone": {
      id: "mock-lyrics",
      variantClass: "mock--phone",
      frameClass: "shot--phone",
      src: "/assets/screenshots/android-player-lyrics.webp",
      width: 1080,
      height: 2288,
      eager: false,
      alt: "DHUN Android Full Player screen with synced lyrics in Devanagari overlay active over the blurred artwork card",
      badge: "Captured on Android phone · DHUN v1.00.001",
      caption:
        "Synced lyrics overlay inside the full player with active CC toggle and timestamped Devanagari lines.",
      appHref: "/app/#/player/lyrics",
      appLabel: "Open Synced lyrics in the app",
    },
    "queue-phone": {
      id: "mock-queue-phone",
      variantClass: "mock--phone",
      frameClass: "shot--phone",
      src: "/assets/screenshots/android-player-queue.webp",
      width: 1080,
      height: 2282,
      eager: false,
      alt: "DHUN Android Full Player Up next queue sheet showing 1 of 24 tracks, drag-to-reorder handles, and Related tab",
      badge: "Captured on Android phone · DHUN v1.00.001",
      caption:
        "Up next queue and Related sheet inside the full player with drag-to-reorder handles and active track indicator.",
      appHref: "/app/#/player/queue",
      appLabel: "Open Up next queue in the app",
    },
    "search-phone": {
      id: "mock-search-phone",
      variantClass: "mock--phone",
      frameClass: "shot--phone",
      src: "/assets/screenshots/android-search.webp",
      width: 1080,
      height: 2283,
      eager: false,
      alt: "DHUN Android Search screen showing search input field and filter chips for All, Songs, Artists, Albums, Playlists, and Videos",
      badge: "Captured on Android phone · DHUN v1.00.001",
      caption:
        "Search screen with YouTube Music query bar and filter chips for All, Songs, Artists, Albums, Playlists, and Videos.",
      appHref: "/app/#/search",
      appLabel: "Open Search in the app",
    },
    "playlists-phone": {
      id: "mock-playlists-phone",
      variantClass: "mock--phone",
      frameClass: "shot--phone",
      src: "/assets/screenshots/android-library-playlists.webp",
      width: 1080,
      height: 2288,
      eager: false,
      alt: "DHUN Android Library Playlists tab showing Liked Songs pinned folder, custom playlists, and New playlist button",
      badge: "Captured on Android phone · DHUN v1.00.001",
      caption:
        "Library Playlists tab with the pinned Liked Songs folder, custom playlists, and New playlist action.",
      appHref: "/app/#/library/playlists",
      appLabel: "Open Playlists in the app",
    },
    "downloads-phone": {
      id: "mock-downloads-phone",
      variantClass: "mock--phone",
      frameClass: "shot--phone",
      src: "/assets/screenshots/android-library-downloads.webp",
      width: 1080,
      height: 2286,
      eager: false,
      alt: "DHUN Android Library Downloads tab showing storage usage bar, Clear all action, and a downloaded track row with delete button",
      badge: "Captured on Android phone · DHUN v1.00.001",
      caption:
        "Library Downloads tab showing device storage usage bar, Clear all control, and offline track management. This screen exists on Android and Windows only: a browser has no equivalent of ADR-006's device-side store, so the web build at /app/ has no Downloads tab.",
      // No appHref: the web build has no Downloads screen to open. A link here
      // would land on Playlists under a caption that promises Downloads, which
      // is the sort of mismatch this site exists not to make.
    },
    "history-phone": {
      id: "mock-history-phone",
      variantClass: "mock--phone",
      frameClass: "shot--phone",
      src: "/assets/screenshots/android-library-history.webp",
      width: 1080,
      height: 2300,
      eager: false,
      alt: "DHUN Android Library History tab showing local play count, Clear all button, and Today play history row",
      badge: "Captured on Android phone · DHUN v1.00.001",
      caption:
        "Library History tab with chronological Today play entries and confirmation-backed Clear all control.",
      appHref: "/app/#/library/history",
      appLabel: "Open History in the app",
    },
    "settings-phone": {
      id: "mock-settings-phone",
      variantClass: "mock--phone",
      frameClass: "shot--phone",
      src: "/assets/screenshots/android-equalizer.webp",
      width: 1080,
      height: 2304,
      eager: false,
      alt: "DHUN Android Settings screen showing Dark and Light theme chips, six accent colour swatches, cache cap options, About and Legal row, and 5-band Equalizer with presets and preamp",
      badge: "Captured on Android phone · DHUN v1.00.001",
      caption:
        "Settings & Equalizer with Dark and Light modes, six accent palettes, cache cap, About & Legal entry, presets, preamp, and 5-band sliders.",
      appHref: "/app/#/settings",
      appLabel: "Open Settings in the app",
    },
    "desktop-window": {
      id: "mock-desktop-window",
      variantClass: "mock--desktop",
      frameClass: "shot--desktop",
      src: "/assets/screenshots/windows-desktop-window.webp",
      width: 1050,
      height: 1199,
      eager: false,
      alt: "DHUN Windows desktop window showing native title bar, Home feed with four-column card grids, bottom mini-player with volume slider, and navigation bar",
      badge: "Captured on Windows desktop · DHUN v1.00.001",
      caption:
        "Windows desktop window (Compose Multiplatform JVM) with wide four-column grids and bottom mini-player with volume slider.",
      appHref: "/app/#/home",
      appLabel: "Open Desktop layout in the app",
    },
  },

  // Live /app/ centerpiece showcase on the front page.
  appShowcase: {
    kicker: "THE WEB APP ITSELF",
    title: "Click through the real interface at /app/ before installing anything.",
    lede:
      "Alongside the Android and Windows captures on this site, the shipped interface runs at /app/ with the same design tokens, navigation state machine, 10-band equaliser curves, synced lyrics parser, and About & Legal pages. Pick any destination below to jump straight into that screen. The player, lyrics and queue are only reachable once something is playing — a first visit has nothing to open there yet, and the app says so rather than showing an empty sheet.",
    source: "app-web/src/js/main.js; docs/verification/29-web-app-mirror.md; ADR-008 amendment 2026-10-09 (2)",
    routes: [
      { label: "Home feed & quick picks", detail: "Three-section feed, mood chips, and docked mini-player", href: "/app/", cta: "Launch /app/ Home" },
      { label: "Search & filter chips", detail: "Query bar, Songs, Artists, Albums, Playlists, and Videos filters, and recent searches", href: "/app/#/search", cta: "Launch /app/ Search" },
      { label: "Expanded Full Player", detail: "Blurred artwork sheet, transport controls, seek scrubber — opens once a track is playing", href: "/app/#/?player=open", cta: "Launch /app/ Player" },
      { label: "Synced lyrics (LRC)", detail: "Timestamped Devanagari & English lines with active-line highlight — opens once a track is playing", href: "/app/#/?player=open&ptab=lyrics", cta: "Launch /app/ Lyrics" },
      { label: "Up Next Queue", detail: "Queue tab, track counter, and related tracks — opens once a track is playing", href: "/app/#/?player=open&ptab=queue", cta: "Launch /app/ Queue" },
      { label: "Library — Playlists, Liked Songs & History", detail: "Your playlists, Liked Songs and what you have played — kept in this browser, with no account", href: "/app/#/library/playlists", cta: "Launch /app/ Library" },
      { label: "Settings & 10-band Equalizer", detail: "Dark and Light themes, 6 accents, 18 VLC presets, ±20 dB bands", href: "/app/#/settings", cta: "Launch /app/ Settings" },
      { label: "About, Privacy & Legal", detail: "Bundled Privacy Policy, Terms, GPL-3.0 & Apache-2.0 licences, Support", href: "/app/#/about-legal", cta: "Launch /app/ About & Legal" },
    ],
  },

  // What changed recently — the PR is the evidence, and the three entries are
  // the rounds that changed what a visitor of /app/ actually sees. Deliberately
  // short: the real changelog is CHANGELOG.md, and a second one here would rot.
  whatsNew: {
    kicker: "WHAT CHANGED",
    title: "Three rounds that changed what /app/ does, with the pull request that carried each one.",
    lede:
      "The site and the web app are built from the same repository, so the interface at /app/ moves when the app does. These are the last three rounds that changed something a visitor can see — every line is a merged change, not a roadmap, and the pull request next to it is the evidence.",
    source: "CHANGELOG.md [Unreleased]; .ai/DEBUG_LOG.md; docs/verification/29-web-app-mirror.md",
    entries: [
      {
        pr: "PR #184",
        href: `${REPO}/pull/184`,
        title: "The detail screens became the app's, and Light started working.",
        detail:
          "Albums, artists and playlists now draw the headers the application draws: the artist hero with its 16:10 crop and the theme's veil, the album's two meta lines taken from the source's own response, and Play with Shuffle instead of Queue. Choosing Light used to paint the bar pale over a dark page — the appearance settings theme the whole document now. The bundled demo catalogue also stopped calling its albums songs and its playlists artists.",
      },
      {
        pr: "PR #183",
        href: `${REPO}/pull/183`,
        title: "The source badge stopped lying, and Home got its greeting and moods.",
        detail:
          "When the live source could not be reached and the sample rows answered, every screen still said “YouTube Music”. The badge now names the source that produced the rows on screen. Home also opens the way the app's Home does — a greeting from the device's own clock and the app's five mood chips — and a mood is a real search, not a filter over rows that were already loaded.",
      },
      {
        pr: "PR #182",
        href: `${REPO}/pull/182`,
        title: "The interface got its first real layout review, and lost seven defects.",
        detail:
          "Found by opening real captures of the built app: the shell let the document scroll instead of owning the viewport, a 260px cover sat in a 260px tile, the phone hero had no gutter, a failure notice wrapped to one word per line, a toast covered the mini player, and pressing a player tab threw the visitor back to the cover. Each is fixed and measured.",
      },
    ],
  },

  // Platform strip: what actually ships, with the honest status of each.
  platforms: [
    {
      name: "Android",
      detail: "Android 7.0 (API 24) and newer",
      status: "primary target",
      source: "minSdk = 24 in app-android/build.gradle.kts",
    },
    {
      name: "Windows",
      detail: "Desktop client, needs a system VLC install",
      status: "hardware-gated",
      source: "Phase 12 desktop native surface; S3 desktop checklist still open",
    },
    {
      name: "Linux / macOS",
      detail: "Same JVM build, run from source",
      status: "never hardware-verified",
      source: "MASTER_PROMPT §1 — free via the JVM build; the S3 checklists cover Android and Windows",
    },
  ],

  // 4-up stat row. Every cell is footnoted under the row.
  stats: [
    { value: "0", label: "accounts required", note: "a" },
    { value: "GPL-3.0", label: "free forever", note: "b" },
    { value: "24+", label: "Android API level", note: "c" },
    { value: "0", label: "trackers in the app", note: "d" },
  ],
  footnotes: [
    {
      id: "a",
      text:
        "DHUN has no sign-in, no account system and no cookie jar. Playback and metadata use the project's own InnerTube client chain, and the desktop fallback talks to a user-provided yt-dlp binary — nothing authenticates as you.",
      source: "ADR-001, ADR-003",
    },
    {
      id: "b",
      text:
        "The whole project is GPL-3.0: the app, the shared Kotlin modules and this site. There is no paid tier, no trial and no account to upsell.",
      source: "LICENSE, THIRD_PARTY.md",
    },
    {
      id: "c",
      text:
        "minSdk is 24, i.e. Android 7.0. The desktop client is built from the same Kotlin code for the JVM: Windows is the tested platform, Linux and macOS run the same build but have never been checked on hardware.",
      source: "app-android/build.gradle.kts, MASTER_PROMPT §1",
    },
    {
      id: "d",
      text:
        "No analytics, no crash reporting, no advertising SDK. This site has no client-side JavaScript at all, and a test in the repository fails the build if that stops being true of the pages' script tags.",
      source: "scripts/website_quality.py, dependency list in THIRD_PARTY.md",
    },
  ],

  // The three feature sections on the front page.
  features: [
    {
      id: "no-sign-in",
      kicker: "NO SIGN-IN",
      title: "Nothing to log into.",
      body:
        "Search, browse, playlists, lyrics and playback all work anonymously. DHUN never shows a sign-in wall, never keeps a session cookie, and does not solve a PO token or run BotGuard attestation to play a track.",
      source: "ADR-001 (own client chain, no cookies), ADR-003 (staged identity waves)",
      mock: "mock-player-phone",
      mockFile: "mockups/player-phone.njk",
      points: [
        "Metadata comes from the project's own thin InnerTube client — no third-party API key.",
        "Playback resolution walks a staged chain of anonymous player clients.",
        "Session corroboration is fail-open: no token, no block.",
      ],
      why:
        "Why there is no sign-in at all: a signed-in client needs a session — a cookie jar, a PO token, BotGuard attestation — and DHUN has no server of its own to authenticate against. The extraction chain is deliberately tokenless by design, so the anonymous path is not a limitation the app works around; it is the only path the app has.",
      whySource: "ADR-001 (own tokenless client chain), ADR-003 (staged identity waves)",
    },
    {
      id: "offline",
      kicker: "OFFLINE",
      title: "Downloads that survive the app closing.",
      body:
        "Save a track and it is written to app-private storage with a resumable range download, then served from disk ahead of the network on every later play — including with no connection at all.",
      source: "ADR-006 (persistent offline downloads: schema v3, range-resume engine, offline-first routing)",
      mock: "mock-downloads-phone",
      mockFile: "mockups/downloads-phone.njk",
      points: [
        "Range-resume download engine — an interrupted save continues where it stopped.",
        "Offline-first routing: a downloaded track never hits the network to start.",
        "Deleting the app deletes the downloads with it; nothing is written to shared storage.",
      ],
    },
    {
      id: "desktop",
      kicker: "DESKTOP",
      title: "A desktop app, not a phone app in a window.",
      body:
        "The Windows build is a real Compose Desktop application: tray icon, media keys through the system transport controls, taskbar jump lists, single-instance behaviour and a 10-band equaliser.",
      source: "Phase 12 desktop native surface; S3 desktop checklist is still open",
      mock: "mock-desktop-window",
      mockFile: "mockups/desktop-window.njk",
      points: [
        "Close-to-tray keeps playback alive; the tray menu drives it.",
        "System transport controls and jump lists are wired natively.",
        "10-band equaliser on desktop (Android uses a 5-band system AudioEffect binding).",
      ],
    },
  ],

  risk: {
    title: "This is borrowed time, and we say so.",
    paragraphs: [
      "DHUN streams from YouTube Music by talking to YouTube directly. YouTube actively enforces PO tokens and SABR on those endpoints, and a hand-rolled extractor is what stopped ViMusic, RiMusic, InnerTune and OuterTune. DHUN's answer is not \"solved\" — it is a maintenance contract: its own sequential player-client chain, a pinned recovery watch on NewPipe Extractor that is deliberately not the primary engine, and a daily CI drill that resolves a real track and checks the audio bytes.",
      "When that chain breaks, playback breaks until it is patched. That is the honest cost of a client with no sign-in, and it is the reason the public build is labelled unverified rather than stable.",
    ],
    source: "ADR-001, MASTER_PROMPT §2 (the doctrine)",
    link: { href: `${REPO}/blob/main/docs/decisions/ADR-001-extraction-engine.md`, label: "Read ADR-001" },
  },

  status: {
    title: "What the public pre-release is — and is not",
    items: [
      {
        label: "Unverified public pre-release",
        text:
          "A fixed pre-release is available from GitHub Releases for testing. It carries no support promise and uses a public throwaway Android test key rather than a store key. The rolling test channel is a separate private draft for repository collaborators.",
        source: "docs/releases/v1.00.002.md; .github/workflows/test-release.yml",
      },
      {
        label: "Hardware gates still open",
        text:
          "Android and desktop acceptance on real hardware (S3) is not finished, so the project does not claim \"works on your device\" for every device. The open items are listed in the roadmap rather than hidden.",
        source: "MASTER_PROMPT §7 Stage S3; .ai/ROADMAP.md current gates",
      },
      {
        label: "Nothing is store-ready",
        text:
          "There is no Play Store or Microsoft Store distribution, and neither the Android APK nor the Windows MSI is store-signed. This is a pre-release, not a stable release. This site does not hand out builds; the release page carries the files and the warnings that belong with them.",
        source: "docs/releases/v1.00.002.md; .ai/ROADMAP.md",
      },
    ],
  },

  closing: {
    title: "Read it, try /app/, or build it yourself.",
    body:
      "The whole project is GPL-3.0 source. You can inspect every screen in the live app right now, or view the public pre-release and its warnings on GitHub Releases. If a claim on this site is wrong, the honesty tests in the repository fail the build — and you can check them.",
    source: "scripts/test_website_claims.py",
  },

  // The /ui/ page: real device screenshots + live /app/ links + design tokens.
  ui: {
    h1: "Real captures, shared tokens, and the live /app/ interface.",
    lead:
      "DHUN's screens are built from one Compose design system — the same colour tokens, shapes, spacing scale and type ramp on Android and Windows. Below are ten real captures from Android phone and Windows desktop builds alongside direct deep links into the running app and the token values compiled into the app.",
    source:
      "shared/src/commonMain/kotlin/dev/dhun/design/ (DhunAppearance, DhunShapes, DhunSpacing, DhunTypography) and .../ui/ (home, search, browse, library, player, settings, legal)",
    whyNoShots: {
      title: "Provenance and content safety",
      body:
        "All ten captures were taken on an Android phone and a Windows desktop running DHUN, stripped of EXIF, XMP, ICC and PNG text chunks prior to commit, and had third-party album artwork and lyrics blurred so no unpermitted copyrighted art is redistributed in Git.",
      source: ".ai/WEBSITE_PLAN.md Part A §9; docs/verification/SCREENSHOT_PROVENANCE.md",
    },
    surfaces: [
      {
        id: "home",
        path: "shared/src/commonMain/kotlin/dev/dhun/ui/home/HomeScreen.kt",
        kicker: "ANDROID · HOME",
        title: "Home",
        body:
          "The first screen is a rail layout: a greeting header, mood filter chips, Recommended songs and Listen again sections seeded by listening history, with the mini-player docked above the bottom navigation bar.",
        points: [
          "Mood chips, Recommended songs, and Listen again rails",
          "Docked mini-player above the tab bar — navigation stays reachable",
          "Bottom navigation: Home, Search, Library, plus top-bar Settings",
        ],
        mockFile: "mockups/home-phone.njk",
      },
      {
        id: "search",
        path: "shared/src/commonMain/kotlin/dev/dhun/ui/search/SearchScreen.kt",
        kicker: "ANDROID · SEARCH",
        title: "Search",
        body:
          "Typing filters into songs, artists, albums, playlists and videos, and Enter does one documented thing rather than guessing: the row you have highlighted is submitted, and recent searches can be removed individually or cleared after confirmation.",
        points: [
          "Filter chips switch the result type without losing the query",
          "Recent searches include per-query delete and confirmation-backed Clear all",
          "The Enter policy is asserted by the app's own UI tests",
        ],
        mockFile: "mockups/search-phone.njk",
      },
      {
        id: "player",
        path: "shared/src/commonMain/kotlin/dev/dhun/ui/player/FullPlayer.kt",
        kicker: "ANDROID · FULL PLAYER",
        title: "Full player",
        body:
          "Artwork is sampled at runtime and blurred into the backdrop, so the player picks up the colour of whatever is playing instead of a fixed gradient. The bottom row switches between synced lyrics, queue, sleep timer, download, and playlist actions.",
        points: [
          "Blurred, colour-sampled artwork backdrop",
          "Seek bar with elapsed and total duration plus shuffle/repeat controls",
          "Bottom action bar for Lyrics (CC), Up next queue, Sleep timer, Download, and Add to playlist",
        ],
        mockFile: "mockups/player-phone.njk",
      },
      {
        id: "lyrics",
        path: "shared/src/commonMain/kotlin/dev/dhun/ui/player/SyncedLyrics.kt",
        kicker: "ANDROID · SYNCED LYRICS",
        title: "Synced lyrics overlay",
        body:
          "Tapping the CC toggle in the full player opens synced lyrics fetched from LRCLIB (falling back to YouTube Music lyrics and cached locally in SQLDelight), highlighting the active timestamped line as playback progresses.",
        points: [
          "LRCLIB timestamped LRC lines with YouTube Music fallback",
          "Active line highlighted against a frosted dark overlay card",
          "Supports Devanagari and Latin scripts with local caching",
        ],
        mockFile: "mockups/lyrics-phone.njk",
      },
      {
        id: "queue",
        path: "shared/src/commonMain/kotlin/dev/dhun/ui/player/PlayerTabs.kt",
        kicker: "ANDROID · UP NEXT QUEUE",
        title: "Up next queue & related",
        body:
          "The queue sheet slides over the full player with tabs for Up next and Related tracks, showing the current position in the queue and drag handles for reordering upcoming songs.",
        points: [
          "Up next and Related tabs inside the expanded player sheet",
          "Track position counter (1 of 24) and drag-to-reorder handles",
          "Per-row remove button to trim upcoming tracks without interrupting playback",
        ],
        mockFile: "mockups/queue-phone.njk",
      },
      {
        id: "playlists",
        path: "shared/src/commonMain/kotlin/dev/dhun/ui/library/LibraryScreen.kt",
        kicker: "ANDROID · LIBRARY PLAYLISTS",
        title: "Library — Playlists",
        body:
          "Your Library opens on Playlists, pinning Liked Songs as an automatic folder at the top alongside user-created local playlists and a New playlist action.",
        points: [
          "Pinned Liked Songs folder automatically collects favourited tracks",
          "Custom local playlists stored in SQLDelight with track counts",
          "Switchable sub-tabs for Playlists, Downloads, and History",
        ],
        mockFile: "mockups/playlists-phone.njk",
      },
      {
        id: "downloads",
        path: "shared/src/commonMain/kotlin/dev/dhun/ui/library/LibraryScreen.kt",
        kicker: "ANDROID · LIBRARY DOWNLOADS",
        title: "Library — Downloads",
        body:
          "The Downloads sub-tab shows total offline items, a storage usage bar against device capacity, a confirmation-backed Clear all action, and individual downloaded tracks that play from disk before touching the network.",
        points: [
          "Resumable range downloads saved in app-private storage (ADR-006)",
          "Storage usage bar and item counter",
          "Per-track delete button and confirmation-backed Clear all",
        ],
        mockFile: "mockups/downloads-phone.njk",
      },
      {
        id: "history",
        path: "shared/src/commonMain/kotlin/dev/dhun/ui/library/LibraryScreen.kt",
        kicker: "ANDROID · LIBRARY HISTORY",
        title: "Library — History",
        body:
          "Listening history is recorded purely on-device to seed Home recommendations and chronological Today/Earlier sections, with a confirmation-backed Clear all action that deletes every row from the local database.",
        points: [
          "Chronological play history grouped by recency",
          "Confirmation-backed Clear all with honest failure reporting",
          "Never uploaded or synced outside your device",
        ],
        mockFile: "mockups/history-phone.njk",
      },
      {
        id: "settings",
        path: "shared/src/commonMain/kotlin/dev/dhun/ui/settings/SettingsScreen.kt",
        kicker: "ANDROID · SETTINGS & EQUALIZER",
        title: "Settings, Equalizer & Legal",
        body:
          "Settings groups Appearance (Dark/Light and six accent palettes), Downloads cache cap, About & Legal (Privacy Policy, Terms, GPL-3.0 & Apache-2.0 licences, Third-Party Notices, Support, Security Reporting), and the built-in Equalizer with presets and preamp.",
        points: [
          "Dark and Light themes plus Violet, Amber, Teal, Rose, Sky, and Lime accents",
          "In-app About & Legal pages rendered offline from bundled Markdown",
          "Equalizer with genre presets, preamp slider, and per-band dB controls",
        ],
        mockFile: "mockups/settings-phone.njk",
      },
      {
        id: "desktop",
        path: "app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/native/DhunTray.kt",
        kicker: "WINDOWS · DESKTOP WINDOW",
        title: "Windows desktop window",
        body:
          "The Windows build runs the same shared Compose Multiplatform UI in a native desktop window with a wide four-column card grid, bottom mini-player with inline volume slider, system tray integration, and OS media keys.",
        points: [
          "Wide multi-column card grid and desktop window chrome",
          "Docked mini-player with volume slider, OS media keys, and taskbar jump lists",
          "Close-to-tray behaviour and single-instance handoff",
        ],
        mockFile: "mockups/desktop-window.njk",
      },
    ],
    tokens: {
      title: "The design system behind every screen",
      lead:
        "These are the values the app compiles with, not a palette invented for a website. The table below is read out of the app's token file when this page is built, so a ramp the app changes is a ramp this page changes; the dark set is the default and the light scheme is shown beside it.",
      source: "app-web/src/css/tokens.css (from shared/.../design/DhunAppearance.kt, DhunShapes.kt, DhunSpacing.kt, DhunTypography.kt)",
    },
    contract: {
      title: "Every screenshot on this site, its provenance, and its live /app/ route",
      body:
        "Every figure on this site is a real capture from an Android phone or Windows desktop running DHUN, converted to metadata-stripped WebP and paired with a deep link into the live app.",
      rows: [
        { id: "mock-home-phone", surface: "Android Home, portrait (android-home-portrait.webp)", page: "/, /ui/" },
        { id: "mock-player-phone", surface: "Android FullPlayer (android-full-player.webp)", page: "/, /ui/" },
        { id: "mock-lyrics", surface: "Android FullPlayer → Synced Lyrics (android-player-lyrics.webp)", page: "/features/, /ui/" },
        { id: "mock-queue-phone", surface: "Android FullPlayer → Up Next Queue (android-player-queue.webp)", page: "/features/, /ui/" },
        { id: "mock-search-phone", surface: "Android Search (android-search.webp)", page: "/ui/" },
        { id: "mock-playlists-phone", surface: "Android Library → Playlists (android-library-playlists.webp)", page: "/ui/" },
        { id: "mock-downloads-phone", surface: "Android Library → Downloads (android-library-downloads.webp)", page: "/, /features/, /ui/" },
        { id: "mock-history-phone", surface: "Android Library → History (android-library-history.webp)", page: "/ui/" },
        { id: "mock-settings-phone", surface: "Android Settings & Equalizer (android-equalizer.webp)", page: "/ui/" },
        { id: "mock-desktop-window", surface: "Windows desktop window (windows-desktop-window.webp)", page: "/, /features/, /ui/" },
      ],
      planned: [
        { id: "mock-widget", surface: "Android home screen with the Quick Play 4×2 launcher widget" },
      ],
      plannedNote:
        "One launcher-level capture remains planned in the §9 backlog (the Android home-screen Quick Play widget outside the app). Every in-app surface is captured above.",
      source: ".ai/WEBSITE_PLAN.md Part A §9 (backlog, machine-checked both ways)",
    },
  },

  chain: {
    title: "How the extraction chain works — and how it breaks",
    lede:
      "DHUN talks to YouTube's own endpoints with no account and no token. That is a choice with a maintenance bill, and the repository pays it in the open rather than in a support inbox.",
    stages: [
      {
        title: "Metadata",
        body:
          "Search, browse, playlists and artist pages come from DHUN's own thin InnerTube client, which reads the current client version off the homepage instead of pinning one that would expire.",
        source: "ADR-001",
      },
      {
        title: "Stream resolution",
        body:
          "A resolver walks a fixed list of tokenless player identities and stops at the first that returns playable audio. The walk is fanned out into staged waves, because the sequential worst case was measured in minutes, not seconds.",
        source: "ADR-001, ADR-003",
      },
      {
        title: "Desktop fallback",
        body:
          "When a stream will not resolve, the desktop build can call a yt-dlp binary you provide, on PATH or via DHUN_YTDLP. Android cannot do this: there is no Python runtime there, so it uses the in-JVM resolver only.",
        source: "README.md; ADR-001",
      },
      {
        title: "Recovery watch",
        body:
          "NewPipe Extractor stays pinned and watched, deliberately outside the active chain: it re-enters as an option when the daily drill goes green on it. No local fork, no patched copy to maintain.",
        source: "ADR-001",
      },
      {
        title: "The daily drill",
        body:
          "extraction-health.yml runs a live playback probe once a day, resolves a real track, checks the audio bytes, and opens or closes a rot-drill issue when the answer changes.",
        source: "ADR-001; extraction-health.yml",
      },
    ],
    cost:
      "When the chain breaks, playback breaks until it is patched — for everyone at once, with no server-side fix to deploy. That is the honest cost of a client with no sign-in, and the reason the public build is labelled unverified rather than stable.",
  },

  // /features — what only the desktop build can do, and what it needs.
  desktopOnly: {
    title: "What the Windows desktop build adds",
    lede:
      "The desktop client is a real Compose Desktop application, not the phone layout in a window. Some capabilities exist only there — and two requirements arrive with them.",
    items: [
      "Tray icon and close-to-tray: closing the window keeps playback alive, and the tray menu controls it.",
      "System media keys through the OS transport controls, plus taskbar jump lists and single-instance behaviour.",
      "A 10-band equaliser matching libVLC's geometry (Android binds its equaliser to the device's system AudioEffect session).",
      "A user-provided yt-dlp fallback for streams the in-JVM resolver cannot resolve.",
    ],
    requirements: [
      "Playback goes through libVLC, which DHUN neither bundles nor installs: install VLC first, and DHUN leaves it alone afterwards — including on uninstall.",
      "The MSI is not Authenticode-signed, so SmartScreen warns on first run. That is true of this test build, not a false positive.",
    ],
    source: "Phase 12 desktop native surface; Phase 15 EQ; README.md install notes",
  },

  featureGroups: [
    {
      title: "Getting to the music",
      items: [
        { text: "Home, seeded by listening history, with an endless radio", source: "Phase 07 / PR #68", path: "shared/src/commonMain/kotlin/dev/dhun/ui/home/HomeScreen.kt" },
        { text: "Search across songs, videos, albums, playlists and artists, with a documented Enter policy", source: "PR #129", path: "shared/src/commonMain/kotlin/dev/dhun/ui/search/" },
        { text: "Artist, album and playlist pages, each with its own actions", source: "Phase 09", path: "shared/src/commonMain/kotlin/dev/dhun/ui/browse/" },
        { text: "Library in Playlists, Downloads and History tabs with Liked Songs pinned at the top", source: "Phase 10", path: "shared/src/commonMain/kotlin/dev/dhun/ui/library/LibraryScreen.kt" },
        { text: "Reorderable playlists — drag a track to move it", source: "ui/components/ReorderableList.kt", path: "shared/src/commonMain/kotlin/dev/dhun/ui/components/ReorderableList.kt" },
      ],
    },
    {
      title: "Playing it",
      items: [
        { text: "Queue engine with shuffle, repeat and a reorderable queue panel", source: "Phase 06/08", path: "shared/src/commonMain/kotlin/dev/dhun/ui/player/PlayerTabs.kt" },
        { text: "Next-track pre-buffering and an isolated temp cache", source: "ADR-005", path: "docs/decisions/ADR-005.md" },
        { text: "Full player with a real blurred-artwork backdrop, tabs and a play disc", source: "ui/player/FullPlayer.kt", path: "shared/src/commonMain/kotlin/dev/dhun/ui/player/FullPlayer.kt" },
        { text: "Synced lyrics: LRCLIB, then YouTube Music lyrics, then the local cache", source: "Phase 11", path: "shared/src/commonMain/kotlin/dev/dhun/ui/player/SyncedLyrics.kt" },
        { text: "Sleep timer that cycles through durations and can be turned off", source: "presentation/player/PlayerViewModel.kt", path: "shared/src/commonMain/kotlin/dev/dhun/presentation/player/PlayerViewModel.kt" },
      ],
    },
    {
      title: "Sound",
      items: [
        { text: "Equaliser with genre presets, a preamp and per-band gain, −20 dB to +20 dB", source: "player/equalizer/EqualizerBands.kt", path: "shared/src/commonMain/kotlin/dev/dhun/player/equalizer/EqualizerBands.kt (COUNT = 10)" },
        { text: "Desktop equalisation uses libVLC's own 10-band geometry", source: "player/equalizer/VlcEqualizerCommand.kt", path: "shared/src/commonMain/kotlin/dev/dhun/player/equalizer/VlcEqualizerCommand.kt" },
        { text: "Android equalisation binds android.media.audiofx to the live audio session", source: "app-android/.../equalizer/AndroidEqualizerEngine.kt", path: "app-android/src/main/kotlin/dev/dhun/android/equalizer/AndroidEqualizerEngine.kt" },
        { text: "Settings for appearance, resume-on-launch, capped download cache, and offline About & Legal pages", source: "ui/settings/SettingsScreen.kt", path: "shared/src/commonMain/kotlin/dev/dhun/ui/settings/SettingsScreen.kt" },
      ],
    },
    {
      title: "Keeping it",
      items: [
        { text: "Persistent offline downloads that resume after the app closes", source: "ADR-006", path: "docs/decisions/ADR-006.md" },
        { text: "Library → Downloads keeps its own filter tab, storage bar and offline badges", source: "Phase 10", path: "shared/src/commonMain/kotlin/dev/dhun/ui/library/LibraryScreen.kt" },
        { text: "Quick Play home-screen widget, sized to a 4×2 launcher slot", source: "app-android AndroidManifest.xml", path: "app-android/src/main/AndroidManifest.xml (DhunQuickPlayWidgetProvider)" },
        { text: "Theme modes with accent ramps read from the shared design system", source: "DhunAppearance", path: "shared/src/commonMain/kotlin/dev/dhun/design/DhunAppearance.kt" },
      ],
    },
    {
      title: "On the desktop",
      items: [
        { text: "System tray with playback control and close-to-tray", source: "app-desktop native/DhunTray.kt", path: "app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/native/DhunTray.kt" },
        { text: "Media keys through the OS transport controls", source: "app-desktop smct/Smct.kt", path: "app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/smct/Smct.kt" },
        { text: "Taskbar jump lists of recent tracks, and a single running instance", source: "app-desktop native/JumpList.kt", path: "app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/native/JumpList.kt" },
        { text: "Playback needs a system VLC install; it is never bundled", source: "README.md install notes", path: "README.md" },
      ],
    },
  ],

  notInDhun: [
    { text: "No accounts and no cross-device sync — there is no server to sync through.", source: "ADR-001" },
    { text: "No import from Spotify, YouTube Music or any other service's library export.", source: "not implemented anywhere in the tree" },
    {
      text: "No iOS build. Android and Windows are the native targets; the browser is served by the web app at /app/, which is the same interface rather than a second native client.",
      source: "docs/decisions/ADR-008-browser-web-player-target.md (2026-10-16 amendment), app-web/README.md",
    },
    { text: "No published audio-quality figure. DHUN makes no bitrate, lossless or FLAC claim of any kind.", source: "no verified number exists in the repository" },
    { text: "No casting, no Android Auto, no CarPlay.", source: "not implemented anywhere in the tree" },
    { text: "No recommendation engine of its own: related tracks and the radio come from the same anonymous endpoints as everything else.", source: "ADR-001, Phase 15 endless-radio" },
    { text: "No store channel and no store-signed release; only a public pre-release is available for testing.", source: "app-android/.../keystores/dhun-test.p12, docs/releases/v1.00.002.md" },
  ],

  install: {
    permissions:
      "Android permissions: internet, notifications, media playback and downloads as foreground services (each declared only while it is running), wake lock, and a one-shot battery-optimisation exemption dialog. No contacts, SMS, location, camera, microphone, shared storage, overlay or accessibility access. The full list is in the app manifest, one file in the repository.",
    source: "app-android/src/main/AndroidManifest.xml",
    privacy:
      "No analytics, no telemetry, no crash reporting, no advertising SDK. This website loads nothing from a third-party origin — no CDN, no font service, no analytics script.",
    sourceTwo: "no client-side JavaScript is built; scripts/website_quality.py asserts it",
  },

  footer: {
    licence:
      "DHUN is free software under the GNU General Public License v3.0, and so is this site.",
    independence:
      "DHUN is not affiliated with, endorsed by or connected to YouTube or Google. YouTube Music is a trademark of Google LLC; song metadata and artwork belong to their respective owners and are fetched at runtime, never bundled.",
    builtWith: "Built with Eleventy. No client-side JavaScript, no cookies, no tracking.",
  },
};
