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
// Rolling pre-release tag `test`. Assets are replaced on every push to main,
// so they are linked by URL only: never by digest or size (Part A §4.3).
const TEST_RELEASE = `${REPO}/releases/download/test`;

export default {
  url: SITE,
  repo: REPO,
  issues: `${REPO}/issues`,
  changelog: `${REPO}/blob/main/CHANGELOG.md`,
  licence: `${REPO}/blob/main/LICENSE`,
  thirdParty: `${REPO}/blob/main/THIRD_PARTY.md`,
  plan: `${REPO}/blob/main/.ai/WEBSITE_PLAN.md`,
  testReleasePage: `${REPO}/releases/tag/test`,
  releases: `${REPO}/releases`,

  title: "DHUN — a YouTube Music client with no sign-in",
  description:
    "DHUN is a free, open-source YouTube Music client for Android and Windows. No sign-in, no cookies, no ads, no telemetry — and downloads that stay on your device.",
  ogType: "website",

  nav: [
    { href: "/features/", label: "Features" },
    { href: "/ui/", label: "Interface" },
    { href: REPO, label: "Source", external: true },
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
  // The site does not distribute binaries. Its primary call to action is the
  // interface itself; the repository is where a build actually lives. Both
  // hrefs are asserted by scripts/website_quality.py, which fails on any link
  // to a release *asset*.
  ctaPrimary: { href: "/ui/", label: "See the interface" },
  ctaSecondary: { href: REPO, label: "Source on GitHub", external: true },

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
        "10-band equaliser on desktop (the Android equaliser is still open — see the honest list).",
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
    title: "What the build you can download actually is",
    items: [
      {
        label: "Rolling UNVERIFIED development build",
        text:
          "The download on this site is the rolling “test” pre-release, rebuilt from main on every push. It is not a release, it carries no support promise, and it is signed with a public throwaway test key rather than a store key.",
        source: "README.md test-builds policy; test-release.yml",
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
          "No Play Store, no F-Droid, no Microsoft Store, no versioned release — only a rolling pre-release that is replaced on every merge, signed with a public test key. This site does not hand out builds; the release page carries the files and the warnings that belong with them.",
        source: "MASTER_PROMPT §7 S6; .ai/ROADMAP.md",
      },
    ],
  },

  closing: {
    title: "Read it, run it, or build it yourself.",
    body:
      "The whole project is GPL-3.0 source, and the public binaries live on the release page as a rolling, unverified pre-release. If a claim on this site is wrong, the honesty tests in the repository fail the build — and you can check them.",
    source: "scripts/test_website_claims.py",
  },

  // The /ui/ page. Everything here is read out of the app's own design system
  // and UI code, and every drawing is labelled as a recreation: no screenshot
  // exists in this repository and none is fabricated (.ai/WEBSITE_PLAN.md §8).
  ui: {
    h1: "The interface, drawn from the code.",
    lead:
      "DHUN's screens are built from one Compose design system — the same colour tokens, shapes, spacing scale and type ramp on Android and Windows. This page shows what those surfaces look like, states the token values behind them, and is explicit about one thing: every drawing here is hand-written CSS and SVG, not a screenshot.",
    source:
      "shared/src/commonMain/kotlin/dev/dhun/design/ (DhunAppearance, DhunShapes, DhunSpacing, DhunTypography) and .../ui/ (home, search, browse, library, player, settings)",
    whyNoShots: {
      title: "Why there are no screenshots",
      body:
        "This repository contains no raster or vector artwork and no device or display is available to it, so there is nothing honest to paste in. Rather than borrow another project's screenshot or invent one, each surface below is recreated in CSS from the app's own tokens — geometry, spacing and colour — and labelled as a recreation wherever it appears. Six real captures are planned to replace them, listed with what each must show in the project's website plan.",
      source: ".ai/WEBSITE_PLAN.md Part A §8 and §9",
    },
    surfaces: [
      {
        id: "home",
        path: "shared/src/commonMain/kotlin/dev/dhun/ui/home/HomeScreen.kt",
        kicker: "ANDROID",
        title: "Home",
        body:
          "The first screen is a rail layout: a now-playing backdrop behind a dense list of tiles and rows, seeded by listening history, with the mini-player docked above the bottom navigation rather than floating over the content.",
        points: [
          "Quick picks and a “from your library” rail, seeded by history",
          "Docked mini-player above the tab bar — navigation stays reachable",
          "Bottom navigation: Home, Search, Library, Settings",
        ],
        note: "Drawn on the home page.",
        href: "/",
      },
      {
        id: "search",
        path: "shared/src/commonMain/kotlin/dev/dhun/ui/search/",
        kicker: "ANDROID",
        title: "Search",
        body:
          "Typing filters into songs, videos, albums, playlists and artists, and Enter does one documented thing rather than guessing: the row you have highlighted is submitted, and pressing Enter twice does not jump you somewhere unexpected.",
        points: [
          "Chips switch the result type without losing the query",
          "Rows carry artwork, title and artist or duration",
          "The Enter policy is asserted by the app's own UI tests",
        ],
        mockFile: "mockups/search-phone.njk",
      },
      {
        id: "player",
        path: "shared/src/commonMain/kotlin/dev/dhun/ui/player/FullPlayer.kt",
        kicker: "ANDROID",
        title: "Full player",
        body:
          "Artwork is sampled at runtime and blurred into the backdrop, so the player picks up the colour of whatever is playing instead of a fixed gradient. Tabs switch between the queue and synced lyrics; the transport row carries shuffle, repeat and the play disc.",
        points: [
          "Blurred, colour-sampled artwork backdrop",
          "Synced lyrics with the current line emphasised",
          "Queue as a tab, not a separate route",
        ],
        note: "Drawn on the features page.",
        href: "/features/",
      },
      {
        id: "settings",
        path: "shared/src/commonMain/kotlin/dev/dhun/ui/settings/SettingsScreen.kt",
        kicker: "ANDROID",
        title: "Settings, including the equaliser",
        body:
          "Settings is grouped into appearance, playback and storage, and sound. The equaliser is ten bands matching libVLC's geometry, with presets, a preamp and per-band gain from −20 dB to +20 dB; Android binds it to the current audio session, so it is the same control surface on both platforms.",
        points: [
          "10 bands: 60 Hz, 170 Hz, 310 Hz, 600 Hz, 1 kHz, 3 kHz, 6 kHz, 12 kHz, 14 kHz, 16 kHz",
          "Presets plus a preamp, and gain clamped to ±20 dB",
          "Resume-on-launch and a capped download cache",
        ],
        mockFile: "mockups/settings-phone.njk",
      },
      {
        id: "desktop",
        path: "app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/native/DhunTray.kt",
        kicker: "WINDOWS",
        title: "Desktop window",
        body:
          "The Windows build is the same Kotlin code in a different shell: a navigation rail on the left, the queue as a panel on the right, and the player docked along the bottom of the window. Closing the window leaves it in the system tray, where playback keeps its own controls.",
        points: [
          "Navigation rail and a permanent queue panel",
          "Docked player, media keys through the OS, jump lists of recent tracks",
          "One instance only — a second launch hands its request to the first",
        ],
        mockFile: "mockups/desktop-window.njk",
      },
    ],
    tokens: {
      title: "The design system behind every screen",
      lead:
        "These are the values the app compiles with, not a palette invented for a website. The dark set is the default; the light scheme mirrors it, and the site uses the same two schemes so a drawing here looks the way the app looks.",
      source: "shared/.../design/DhunAppearance.kt, DhunShapes.kt, DhunSpacing.kt, DhunTypography.kt",
      colors: [
        { name: "background", value: "#161616", use: "app canvas" },
        { name: "surface", value: "#1E1E1E", use: "cards, sheets" },
        { name: "surfaceVariant", value: "#262626", use: "raised rows" },
        { name: "surfaceElevated", value: "#303030", use: "dialogs" },
        { name: "surfaceHighest", value: "#363636", use: "controls on top" },
        { name: "accent", value: "#BB86FC", use: "brand ramp, dark" },
        { name: "accentContainer", value: "#3A2A5A", use: "selected states" },
        { name: "warning", value: "#FFB74D", use: "gates, warnings" },
      ],
      shapes: [
        { name: "xs", value: "4 px" },
        { name: "sm", value: "8 px" },
        { name: "md", value: "12 px" },
        { name: "lg", value: "16 px" },
        { name: "xl", value: "28 px" },
        { name: "xxl", value: "32 px" },
        { name: "full", value: "999 px" },
      ],
      spacing: [
        { name: "sp-1", value: 4 },
        { name: "sp-2", value: 8 },
        { name: "sp-3", value: 12 },
        { name: "sp-4", value: 16 },
        { name: "sp-5", value: 20 },
        { name: "sp-6", value: 24 },
        { name: "sp-8", value: 32 },
        { name: "sp-12", value: 48 },
      ],
      type: [
        { name: "display", value: "57 / 45 / 36" },
        { name: "headline", value: "32 / 28 / 24" },
        { name: "title", value: "22 / 16 / 14" },
        { name: "body", value: "16 / 14 / 12" },
        { name: "brand", value: "12 sp, 3 sp tracking" },
      ],
      target: "Touch targets: 44 dp minimum, 48 dp in lists",
    },
    contract: {
      title: "What the drawings are, and what they are not",
      body:
        "Every figure on this site is a recreation: hand-written HTML, CSS and inline SVG, with the app's token values and the app's layout rules. None is a photograph of a running app, and none shows a feature DHUN does not have — a mockup of an unshipped screen would be a lie with better production values. Track and artist text is placeholder wording, artwork is a token-coloured gradient, and device chrome such as the clock is illustrative.",
      rows: [
        { id: "mock-home-phone", surface: "Android Home, portrait", page: "/" },
        { id: "mock-player-phone", surface: "Android FullPlayer", page: "/" },
        { id: "mock-downloads-phone", surface: "Android Library → Downloads", page: "/features/" },
        { id: "mock-desktop-window", surface: "Windows desktop window", page: "/features/" },
        { id: "mock-search-phone", surface: "Android Search", page: "/ui/" },
        { id: "mock-settings-phone", surface: "Android Settings → Equalizer", page: "/ui/" },
      ],
      planned: [
        { id: "mock-widget", surface: "Android home screen with the Quick Play widget" },
        { id: "mock-lyrics", surface: "Android FullPlayer → Lyrics, mid-song" },
      ],
      plannedNote:
        "Two captures are still to be taken. Until they exist, nothing on the site pretends to show them — the widget and the lyrics tab are described in words only.",
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
      "A 10-band equaliser; the Android equaliser is an open item on the roadmap, not a shipped feature.",
      "A user-provided yt-dlp fallback for streams the in-JVM resolver cannot resolve.",
    ],
    requirements: [
      "Playback goes through libVLC, which DHUN neither bundles nor installs: install VLC first, and DHUN leaves it alone afterwards — including on uninstall.",
      "The MSI is not Authenticode-signed, so SmartScreen warns on first run. That is true of this test build, not a false positive.",
    ],
    source: "Phase 12 desktop native surface; Phase 15 EQ; README.md install notes",
  },

  // /features — what exists, and what does not.
  // The app's real surfaces, grouped the way the app groups them. Every line
  // was read out of the tree this session; a claim the code cannot support does
  // not belong here, and neither does a shipped feature in `notInDhun` below.
  // The app's real surfaces, grouped the way the app groups them. Every line
  // was read out of the tree this session; `source` is shown to the reader and
  // `path` is the full file the claim came from, which the build writes into
  // the page as a citation comment (asserted by website_quality.py, per
  // section — that is why a section may never lose it).
  featureGroups: [
    {
      title: "Getting to the music",
      items: [
        { text: "Home, seeded by listening history, with an endless radio", source: "Phase 07 / PR #68", path: "shared/src/commonMain/kotlin/dev/dhun/ui/home/HomeScreen.kt" },
        { text: "Search across songs, videos, albums, playlists and artists, with a documented Enter policy", source: "PR #129", path: "shared/src/commonMain/kotlin/dev/dhun/ui/search/" },
        { text: "Artist, album and playlist pages, each with its own actions", source: "Phase 09", path: "shared/src/commonMain/kotlin/dev/dhun/ui/browse/" },
        { text: "Library in four tabs: playlists, favourites, history and downloads", source: "Phase 10", path: "shared/src/commonMain/kotlin/dev/dhun/ui/library/LibraryScreen.kt" },
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
        { text: "10-band equaliser: presets, a preamp and per-band gain, −20 dB to +20 dB", source: "player/equalizer/EqualizerBands.kt", path: "shared/src/commonMain/kotlin/dev/dhun/player/equalizer/EqualizerBands.kt (COUNT = 10)" },
        { text: "Desktop equalisation uses libVLC's own 10-band geometry", source: "player/equalizer/VlcEqualizerCommand.kt", path: "shared/src/commonMain/kotlin/dev/dhun/player/equalizer/VlcEqualizerCommand.kt" },
        { text: "Android equalisation binds android.media.audiofx to the live audio session", source: "app-android/.../equalizer/AndroidEqualizerEngine.kt", path: "app-android/src/main/kotlin/dev/dhun/android/equalizer/AndroidEqualizerEngine.kt" },
        { text: "Settings for appearance, resume-on-launch and a capped download cache", source: "ui/settings/SettingsScreen.kt", path: "shared/src/commonMain/kotlin/dev/dhun/ui/settings/SettingsScreen.kt" },
      ],
    },
    {
      title: "Keeping it",
      items: [
        { text: "Persistent offline downloads that resume after the app closes", source: "ADR-006", path: "docs/decisions/ADR-006.md" },
        { text: "Library → Downloads keeps its own filter tab and offline badges", source: "Phase 10", path: "shared/src/commonMain/kotlin/dev/dhun/ui/library/LibraryScreen.kt" },
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
    { text: "No iOS, no web player, no browser client, no PWA.", source: "MASTER_PROMPT §1 and §7 (Web/PWA explicitly out of S1–S6)" },
    { text: "No published audio-quality figure. DHUN makes no bitrate, lossless or FLAC claim of any kind.", source: "no verified number exists in the repository" },
    { text: "No casting, no Android Auto, no CarPlay.", source: "not implemented anywhere in the tree" },
    { text: "No recommendation engine of its own: related tracks and the radio come from the same anonymous endpoints as everything else.", source: "ADR-001, Phase 15 endless-radio" },
    { text: "No store channel and no signed release: the only public artifact is the rolling test pre-release.", source: "app-android/.../keystores/dhun-test.p12, releases/tag/test" },
  ],

  install: {
    permissions:
      "Android permissions: internet, notifications, foreground media playback (the FOREGROUND_SERVICE_DATA_SYNC service type, used only while a download is running), wake lock, and a one-shot battery-optimisation exemption dialog. No contacts, SMS, location, camera, microphone, shared storage, overlay or accessibility access.",
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
