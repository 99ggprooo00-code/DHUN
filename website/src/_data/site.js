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
    { href: "/download/", label: "Download" },
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
  ctaPrimary: { href: "/download/", label: "Download DHUN" },
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
          "No Play Store, no F-Droid, no Microsoft Store, no versioned release. Sideload only onto a device you are willing to experiment with.",
        source: "MASTER_PROMPT §7 S6; .ai/ROADMAP.md",
      },
    ],
  },

  closing: {
    title: "Try it, or build it yourself.",
    body:
      "One APK for Android, one MSI for Windows, and the whole source under GPL-3.0. If a claim on this site is wrong, the honesty tests in the repository fail the build — and you can check them.",
    source: "scripts/test_website_claims.py",
  },

  // /download — the rolling test release, by URL only.
  downloads: {
    title: "Download DHUN",
    intro:
      "Everything here comes from the rolling “test” pre-release, which GitHub replaces on every push to main. The links are stable; the bytes are not. Verify the checksum sidecar before you install.",
    assets: [
      {
        name: "dhun-test.apk",
        url: `${TEST_RELEASE}/dhun-test.apk`,
        sha256Url: `${TEST_RELEASE}/dhun-test.apk.sha256`,
        label: "Universal APK — install this one",
        detail:
          "Contains every ABI DHUN ships. Works on any supported Android device; use this unless you have a specific reason to pick a split.",
        kind: "android",
        recommended: true,
      },
      {
        name: "dhun-test-arm64-v8a.apk",
        url: `${TEST_RELEASE}/dhun-test-arm64-v8a.apk`,
        sha256Url: `${TEST_RELEASE}/dhun-test-arm64-v8a.apk.sha256`,
        label: "ARM64 split",
        detail:
          "ABI split of the same build for 64-bit ARM devices. DHUN bundles no native code, so this is smaller only by packaging — the three APKs are not byte-identical.",
        kind: "android",
      },
      {
        name: "dhun-test-armeabi-v7a.apk",
        url: `${TEST_RELEASE}/dhun-test-armeabi-v7a.apk`,
        sha256Url: `${TEST_RELEASE}/dhun-test-armeabi-v7a.apk.sha256`,
        label: "ARMv7 split",
        detail: "ABI split of the same build for older 32-bit ARM devices.",
        kind: "android",
      },
      {
        name: "dhun-test.msi",
        url: `${TEST_RELEASE}/dhun-test.msi`,
        sha256Url: `${TEST_RELEASE}/dhun-test.msi.sha256`,
        label: "Windows installer",
        detail:
          "Per-user install (no administrator prompt) to %LOCALAPPDATA%\\DHUN. Playback needs a system VLC/libVLC install; DHUN neither installs nor removes VLC.",
        kind: "desktop",
      },
    ],
    steps: [
      {
        title: "Check the sidecar first",
        body:
          "Each file has a `.sha256` sidecar next to it. Download both, then run `sha256sum -c dhun-test.apk.sha256` (Linux/macOS) or `Get-FileHash dhun-test.apk -Algorithm SHA256` (Windows PowerShell) and compare. The digests change on every merge — this page deliberately does not print them.",
      },
      {
        title: "Install the APK",
        body:
          "Sideload it (your file manager, or `adb install dhun-test.apk`). Android will warn about installing an unknown app and about the test signing key; that is expected for a build signed with a public throwaway certificate. Use it on a device you are willing to experiment with, and note that it will not update over a differently-signed build.",
      },
      {
        title: "Install VLC for the desktop build",
        body:
          "The Windows client plays through libVLC, which it does not bundle. Install VLC first, then run the MSI. SmartScreen will warn that the installer is not Authenticode-signed — that is true of this test build, not a false positive.",
      },
    ],
    notAvailable: [
      "No stable versioned release — the only public artifacts are the rolling “test” pre-release.",
      "No Play Store, F-Droid, IzzyOnDroid, Microsoft Store or package-manager channel.",
      "No iOS or iPadOS build (and none planned here).",
      "No web player and no browser client.",
      "No macOS disk image or App Store build.",
    ],
    verify: {
      title: "Verify the download, per platform",
      lede:
        "Each file has a .sha256 sidecar in the same format as sha256sum: a digest, two spaces, the filename. Download both into one folder and run the command for your platform. A mismatch means the file is not what the release published, and you should delete it.",
      platforms: [
        { name: "Linux", command: "sha256sum -c dhun-test.apk.sha256" },
        { name: "macOS", command: "shasum -a 256 -c dhun-test.apk.sha256" },
        { name: "Windows (PowerShell)", command: "Get-FileHash .\\dhun-test.apk -Algorithm SHA256" },
      ],
      note:
        "PowerShell prints the digest instead of comparing it: compare it with the first field of the .sha256 file. The same three commands work for the .msi and for both ABI splits — only the filename changes.",
      source: "scripts/stage_artifact.py (sidecar format), README.md",
    },

    lifecycle: {
      title: "Upgrade and uninstall",
      lede:
        "Two platforms, two different answers to “how do I update this?” and “what does uninstalling take with it?”. Both answers below are the ones in the repository's own install notes.",
      android: {
        heading: "Android",
        upgrade:
          "The APKs are signed with the same committed test key, so a newer test APK installs straight over the previous one — no uninstall step, and the library and downloads survive the upgrade.",
        uninstall:
          "Uninstalling from the launcher or Settings → Apps → DHUN deletes the app-private tree with it: database, cached audio segments and image cache. Nothing was written to shared storage, and Android will not offer to keep the data.",
      },
      desktop: {
        heading: "Windows",
        upgrade:
          "The MSI is a per-user install (no Administrator prompt) under %LOCALAPPDATA%\\DHUN with a stable upgrade identity, so a newer build installs over the previous one. Clean-target cleanup and in-place upgrade data preservation are exercised on a disposable runner and still await verification on real hardware.",
        uninstall:
          "Uninstall from Settings → Apps → DHUN. Packaged runtime data is meant to live under the install directory (SQLite database plus audio cache) so it goes with the program instead of being left behind in %APPDATA%.",
        aside:
          "VLC is a separate installation: DHUN neither installs nor removes it.",
      },
      source: "README.md install/uninstall notes; app-desktop/build.gradle.kts (perUserInstall, upgradeUuid); scripts/check_msi_upgrade.ps1",
    },

    testKey: {
      title: "What the test signing key means",
      body:
        "The APKs are signed with a keystore committed to the repository (app-android/keystores/dhun-test.p12) — a test key, not a secret. Two consequences, both real: a new test APK installs over an old one without an uninstall, and the signature proves nothing about who built the file. Anyone with the repository can mint a same-key APK, so install only from the links on this page or from the GitHub release page.",
      source: "README.md test-builds policy; app-android/build.gradle.kts signingConfigs.testBuild",
    },

    noStable: {
      title: "What “no stable release” costs you",
      items: [
        "No version number to pin: a bug report has to name a commit, not a version.",
        "No previous build to roll back to — the release page carries the current bytes only.",
        "No in-app updater and no store channel, so every upgrade is a manual download and verify.",
        "The published bytes are replaced on every merge to main, so a link saved last week may hand you a different build today. The .sha256 sidecar is what tells you which build you actually have.",
      ],
      source: "README.md test-builds policy; test-release.yml",
    },

    buildIt: {
      title: "Or build it from source",
      body:
        "The APK and MSI above are debug-keystore-signed test artifacts. A release build needs JDK 17 and an Android SDK, and produces the same code from the same commit.",
      commands: [
        "./gradlew :app-android:assembleDebug   # Android debug APK",
        "./gradlew :app-desktop:run             # desktop (needs libVLC)",
      ],
      source: "README.md build section",
    },
    technicalNote:
      "Hosting reality: GitHub Pages sets its own caching and compression headers. This site cannot tune either, so it optimises what it controls — three static routes, one stylesheet, no client-side JavaScript and no third-party request.",
  },

  // /features — how the chain works, and what breaks when it does.
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
  featureGroups: [
    {
      title: "Getting to the music",
      items: [
        { text: "Home with history-seeded recommendations", source: "Phase 07 / PR #68" },
        { text: "Search, with a documented Enter policy", source: "PR #129" },
        { text: "Artist, album and playlist pages", source: "Phase 09" },
        { text: "Library: playlists, favourites, history and downloads", source: "Phase 10" },
      ],
    },
    {
      title: "Playing it",
      items: [
        { text: "Queue engine with shuffle and repeat", source: "Phase 06/08" },
        { text: "Next-track pre-buffering and an isolated temp cache", source: "ADR-005" },
        { text: "Immersive full player with a real blurred-artwork backdrop", source: "ADR-002, PR #68" },
        { text: "Synced lyrics from LRCLIB, then YouTube Music lyrics, then cache", source: "Phase 11" },
      ],
    },
    {
      title: "Keeping it",
      items: [
        { text: "Persistent offline downloads with resume", source: "ADR-006" },
        { text: "Android home-screen widget for quick play", source: "Phase 15 widgets" },
        { text: "Theme modes with six accent ramps", source: "DhunAppearance (candidate 28)" },
      ],
    },
    {
      title: "On the desktop",
      items: [
        { text: "System tray with playback control and close-to-tray", source: "Phase 12" },
        { text: "Media keys through the OS transport controls", source: "Phase 12 SMTC" },
        { text: "Taskbar jump lists, single-instance behaviour", source: "Phase 12" },
        { text: "10-band equaliser", source: "Phase 15 EQ" },
      ],
    },
  ],

  notInDhun: [
    { text: "No accounts and no cross-device sync — there is no server to sync through.", source: "ADR-001" },
    { text: "No import from Spotify, YouTube Music or any other service's library export.", source: "not implemented anywhere in the tree" },
    { text: "No iOS, no web player, no browser client, no PWA.", source: "MASTER_PROMPT §1 and §7 (Web/PWA explicitly out of S1–S6)" },
    { text: "No published audio-quality figure. DHUN makes no bitrate, lossless or FLAC claim of any kind.", source: "no verified number exists in the repository" },
    { text: "No casting, no Android Auto, no CarPlay.", source: "not implemented anywhere in the tree" },
    { text: "No Android equaliser yet — it is an open item, not a shipped feature.", source: ".ai/ROADMAP.md S4" },
    { text: "No recommendation engine of its own: related tracks and the radio come from the same anonymous endpoints as everything else.", source: "ADR-001, Phase 15 endless-radio" },
  ],

  install: {
    permissions:
      "Android permissions: internet, notifications, foreground media playback (the <code>FOREGROUND_SERVICE_DATA_SYNC</code> type, used only while a download is running), wake lock, and a one-shot battery-optimisation exemption dialog. No contacts, SMS, location, camera, microphone, shared storage, overlay or accessibility access.",
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
