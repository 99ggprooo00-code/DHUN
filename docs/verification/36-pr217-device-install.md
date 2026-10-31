# 36 — Installing the PR #217 build on an Android phone (beginner procedure)

> **Status: procedure only. None of it has been executed.**
> The agent's environment has no phone, no `adb`, no USB bus, no display and no
> JDK, so the APK was never downloaded, installed or launched from here. Every
> step below is unverified until you do it, and `docs/verification/35-background-
> visual-validation.md` stays **OPEN** until then. Nothing in this file is
> evidence; it is instructions.
>
> **Recovery warning (2026-10-31):** do not reuse artifact `13028524117` from the
> `c8d5dc1` Build APK run as a loading fix; the phone rejected it. Use only the
> `debug-apk` artifact from a successful Build APK run on the current PR head and
> complete [verification §38](38-native-application-recovery.md).

## 0. Which build this is

| | |
|---|---|
| Pull request | #217, branch `arena/09e5238e-dhun` |
| **Take the APK from** | the **`Build APK`** (artifact `debug-apk`) or **`test-release` → `apk`** run whose commit is **the PR head shown on the PR page**. Any older run is a different build; a docs-only commit still moves the head, so check the commit, not the date. |
| Artifact contents | `dhun-test.apk` (universal), `dhun-test-arm64-v8a.apk`, `dhun-test-armeabi-v7a.apk`, each with a `.sha256` and a `.provenance.txt`. The `debug-apk` artifact holds the same three files under Gradle names (`app-android-universal-debug.apk`, …). |
| Package name | `dev.dhun.android` — unchanged, so an upgrade keeps settings |
| Version shown by Android | `1.00.<workflow-run>-test.<attempt>`; the increasing integer versionCode is allocated from the build time |
| Minimum Android | 7.0 (API 24) |
| Signing | `app-android/keystores/dhun-test.p12`, the committed test key; CI verifies its certificate on every APK variant |

The procedure deliberately names no run number: PR artifacts expire after 14
days and every push produces new ones, so a hard-coded run id is how a
validation ends up testing a build that is not the head. Open the pull request,
click the green tick on the top commit, and pick the `Build APK` run for **that**
commit. Record the run id, the artifact name and the `.sha256` value you
installed in §7.

The agent could not fetch any artifact bytes: this sandbox may only reach
`github.com`, `api.github.com`, `codeload.github.com`, npm and PyPI, and artifact
downloads redirect to Azure Blob storage, which is blocked here. So the identity
above comes from the workflow and `app-android/build.gradle.kts`, not from
opening an APK. The `test-release` → `apk` zip carries a `.sha256` next to each
APK (`sha256sum dhun-test.apk` must print the same value). The simpler `debug-apk`
zip contains only the three APKs, so keep its Actions run page as the build
identity and record the exact file you installed.

**Which file to install:** the universal one — `dhun-test.apk` in the `apk`
artifact, or `app-android-universal-debug.apk` in `debug-apk` — works on every
phone that can run DHUN. On an arm64-v8a phone, `dhun-test-arm64-v8a.apk` or
`app-android-arm64-v8a-debug.apk` is the equivalent smaller choice. All variants
from one run carry the same package name, version identity and signing key.

## 1. Will it install over the DHUN you already have?

Use a **newly built** artifact after the packaging fix. Older `debug-apk`
artifacts used public versionCode `7`; Android can reject one over a newer test
build with the generic "package appears to be invalid" message. New Build APK and
test-release packages instead allocate versionCode from the same Unix-time
sequence, so their universal, ARM64 and ARM32 variants do not downgrade merely
because the two workflows have different run counters.

Every variant keeps `dev.dhun.android` and is signed with the committed test key.
CI runs both `aapt dump badging` and `apksigner verify --print-certs` on all three
APKs. This checks the actual uploaded candidates rather than inferring their
identity from Gradle inputs. A same-second CI build can have the same versionCode,
which Android permits as a reinstall.

| What is installed now | What a current test artifact should do |
|---|---|
| nothing | install normally |
| public `v1.00.002` (code 7) | install as an update and keep settings |
| an older rolling-test or PR test build | install as an update when the candidate was allocated later; do not install an older artifact over a newer one |
| a locally built APK with another certificate | still refuse the update; changing versionCode cannot bypass Android's signature protection |

This is a packaging correction, not proof that the reported phone install has
succeeded. Keep the old app installed for Check G; do not uninstall it merely to
retry an obsolete artifact.

## 2. No cable — the simplest path

1. On the phone, open <https://github.com/99ggprooo00-code/DHUN/pull/217/checks>
   in Chrome and sign in to GitHub if asked (the artifact needs a signed-in
   session; an Actions artifact is not public like a release asset).
2. Find the latest **Build APK** check for the PR's top commit and tap it.
3. Scroll to the bottom of the page to the **Artifacts** box.
4. Tap **debug-apk** to download. You get `debug-apk.zip`.
5. Open the phone's **Files** app → **Downloads**, long-press `debug-apk.zip` →
   **Extract**.
6. Open the extracted folder and tap **app-android-universal-debug.apk**.
7. Android asks to allow installs from that app (Chrome or Files):
   tap **Settings** in the prompt, turn on **Allow from this source**, go back.
8. Tap **Install**. If it offers **Update** instead, that is the good case: your
   settings are kept.
9. Tap **Open**.

## 3. With a computer and a cable (diagnostic option)

Do this only if a **current** artifact still ends in "App not installed". ADB can
show Android's precise error and `-r` keeps settings; it is no longer the normal
way to bypass the old artifact's versionCode downgrade.

**One-time phone setup**

1. Settings → **About phone** → tap **Build number** seven times. It says
   "You are now a developer".
2. Settings → **System** → **Developer options** → turn on **USB debugging**.
3. Plug the phone into the computer. On the phone, tap **Allow** on the
   "Allow USB debugging?" prompt (tick "Always allow").

**One-time computer setup**

1. Download *Platform tools* for your OS from
   <https://developer.android.com/tools/releases/platform-tools> and unzip it.
2. Open a terminal **in that folder**:
   - macOS: right-click the folder → *Services* → *New Terminal at Folder*
   - Windows: type `cmd` in the folder's address bar and press Enter
   - Linux: right-click → *Open in Terminal*
3. Check the phone is seen:

   ```
   adb devices
   ```

   You want one line with your phone's serial and `device` on the right. If it
   says `unauthorized`, unlock the phone and accept the prompt. If it says
   `no permissions` on Linux, run `adb kill-server` then `adb devices` again.

**Install**

4. Download the `debug-apk` artifact from the Build APK run above on the computer
   and unzip it, so that `app-android-universal-debug.apk` is in a folder you know.
5. In that folder:

   ```
   adb install -r app-android-universal-debug.apk
   ```

   `-r` reinstalls and keeps data. Do **not** add `-d` to hide a downgrade: use
   the latest artifact instead. If it fails, record the complete
   `INSTALL_FAILED_...` reason; that is more useful than the phone's generic dialog.
6. Launch it from the phone's app drawer only after `Success`.

## 4. If Android refuses to install

| Message | What it means | What to do |
|---|---|---|
| `App not installed` / `package appears to be invalid` | generic Android message; the old code-7 artifact could cause a downgrade, but corruption and signature conflicts are also possibilities | retry only with the new artifact; if it persists, use §3 to capture the exact reason rather than uninstalling first |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | the candidate is older than the installed package | download the latest run's artifact; do not use an older frozen link |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | the old build was signed with a different key (for example a locally built debug APK from Android Studio) | report it; uninstalling erases settings and is a separate, explicit choice |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | no space | free ~150 MB |
| "For your security, your phone is not allowed to install unknown apps from this source" | the toggle in §2 step 7 | turn it on for the app you are installing from |
| `adb: no devices found` | cable/driver/authorisation | re-plug, unlock the phone, `adb kill-server`, accept the prompt |

**Observed report (2026-10-31):** an arm64-v8a phone showed "App not installed —
package appears to be invalid" for the earlier PR artifact. No ADB failure code
was captured, so downgrade is a supported packaging suspect, not a proven device
root cause. The replacement build must still be installed on that phone before
this section can be marked verified.

## 5. What to check once it opens

`Settings` (the gear on Library) → **Appearance** → **Backgrounds**. The screen
you should see has, in this order: **Background blur**, **Dynamic colours**, then
one row per surface — **Main pages**, **Navigation bar**, **Miniplayer**,
**Full player** — each with its current colour, and inside each surface the two
overrides under *Overrides for this surface*.

The checks are written out in
[`35-background-visual-validation.md`](35-background-visual-validation.md). This
is the mapping to the A–H list:

| Check | Where in doc 35 |
|---|---|
| A — Blur OFF leaves no thumbnail, on every surface | Check 1, plus Check 7 (layer order) |
| B — Dynamic colours ON + Blur OFF responds to artwork | Check 2, Check 4 |
| C — Blur ON + Dynamic colours OFF, four independent colours | Check 3 and **Check 3b** (the regression from the audit) |
| D — both ON, artwork-responsive and smooth on track change | Check 2, Check 4 |
| E — settings and UI readable over bright and dark artwork | Check 5 |
| F — settings survive a restart | Check 2 last row, §3 record |
| G — upgrading keeps existing colours | **Check 8** |
| H — Android 8–11 fallback (no real blur) | **Check 6** — only if you have such a device or emulator; otherwise record "untested" |

Use at least three covers: one very bright (white/near-white), one very dark,
one highly saturated. The point of the bright and dark ones is that the earlier
contrast failure in this PR was in the **light** theme with a light cover.

## 6. Screenshots to take

Power + volume-down takes a screenshot on almost every Android phone.

1. Blur OFF + Dynamic colours OFF, main pages
2. Blur OFF + Dynamic colours ON, main pages
3. Blur ON + Dynamic colours OFF, main pages
4. Blur ON + Dynamic colours ON, main pages
5. Full player with a manually chosen colour, blur ON (Check 3b)
6. A screen showing the navigation bar and the mini-player in two different colours
7. Blur OFF + Dynamic colours ON, full player and lyrics (Check A)
8. The Backgrounds settings screen
9. Optional: the same settings screen over a bright cover

## 7. Where to record the result

Fill in §3 (environment) and the result column of `35-background-visual-validation.md`,
including: phone model, Android version and API level, the artifact name and
`.sha256` value you installed, the SHA-256 of the APK as Android reports it if
you used ADB, and every FAIL with the cover art that triggered it. A PASS without
a build identity and a device is not evidence.
