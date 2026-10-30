# 36 — Installing the PR #217 build on an Android phone (beginner procedure)

> **Status: procedure only. None of it has been executed.**
> The agent's environment has no phone, no `adb`, no USB bus, no display and no
> JDK, so the APK was never downloaded, installed or launched from here. Every
> step below is unverified until you do it, and `docs/verification/35-background-
> visual-validation.md` stays **OPEN** until then. Nothing in this file is
> evidence; it is instructions.

## 0. Which build this is

| | |
|---|---|
| Pull request | #217, branch `arena/09e5238e-dhun` |
| Commit | `125a4c78c0a3fa3ee65bbabc5d5bc7250c823549` |
| CI on that commit | `CI` 41044202480 (Ubuntu + Windows), `Build APK` 41044210173, `test-release` 41044210169 — all green |
| APK artifact | `apk` on run **41044210169**, artifact id `12970818781`, artifact digest `sha256:9feaabde158cef8ee15c26267a57a3ac4210dc6ecb0994156d2b2bf15b152c73` |
| Same APKs, other name | artifact `debug-apk` on run 41044210173, id `12970749912`, digest `sha256:cb47fcdf9216496cdae24d402a43a8bd927268e5641214e5151e2dfeb0c776b4` |
| Inside the zip | `dhun-test.apk` (universal), `dhun-test-arm64-v8a.apk`, `dhun-test-armeabi-v7a.apk`, each with a `.sha256` and a `.provenance.txt` |
| Package name | `dev.dhun.android` |
| Version shown by Android | `1.00.002`, version code `7` |
| Minimum Android | 8.0 (API 24) |

The agent could not fetch the artifact bytes: this sandbox may only reach
`github.com`, `api.github.com`, `codeload.github.com`, npm and PyPI, and artifact
downloads redirect to Azure Blob storage, which is blocked here. The identity
above therefore comes from the workflow, the run metadata and `app-android/build.gradle.kts`
— not from opening the APK. Compare the `.sha256` inside the zip with
`sha256sum` if you want to confirm the bytes yourself.

**Which file to install:** `dhun-test.apk` (the universal one). It works on every
phone that can run DHUN. The per-ABI files are only smaller.

## 1. Will it install over the DHUN you already have?

Every CI build — the rolling `test` release and these artifacts — is signed with
the same committed test key (`app-android/keystores/dhun-test.p12`), so Android
will not refuse it for a **signature** mismatch.

What can still refuse it is the **version code**, which is `7` in these artifacts:

| What is installed now | What happens when you install this build |
|---|---|
| nothing | installs normally |
| the public `v1.00.002` prerelease (code 7) | installs as an update, settings kept |
| a rolling `test` build (code `1000 + run × 1000`, so > 1000) | **refused: it is a downgrade**. Use the ADB path in §3 (`adb install -r -d`), which keeps your settings, or uninstall first — uninstalling erases saved settings and therefore loses the upgrade test (Check G). |

If you want Check G (upgrade keeps your colours), keep the old build installed
and use §3.

## 2. No cable — the simplest path

1. On the phone, open <https://github.com/99ggprooo00-code/DHUN/actions/runs/41044210169>
   in Chrome and sign in to GitHub if asked (the artifact needs a signed-in
   session; an Actions artifact is not public like a release asset).
2. Scroll to the bottom of the page to the **Artifacts** box.
3. Tap **apk** to download. You get `apk.zip`.
4. Open the phone's **Files** app → **Downloads**, long-press `apk.zip` →
   **Extract**.
5. Open the extracted folder and tap **dhun-test.apk**.
6. Android asks to allow installs from that app (Chrome or Files):
   tap **Settings** in the prompt, turn on **Allow from this source**, go back.
7. Tap **Install**. If it offers **Update** instead, that is the good case: your
   settings are kept.
8. Tap **Open**.

## 3. With a computer and a cable (keeps your settings, enables Check G)

Do this if §2 ends in "App not installed", or if you want to test the upgrade
from an older build without losing data.

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

4. Download the `apk` artifact from the run page above on the computer and unzip
   it, so that `dhun-test.apk` sits in a folder you know.
5. In that folder:

   ```
   adb install -r -d dhun-test.apk
   ```

   `-r` reinstalls and keeps data; `-d` allows the version-code downgrade
   described in §1. Expect `Success`.
6. Launch it from the phone's app drawer.

## 4. If Android refuses to install

| Message | What it means | What to do |
|---|---|---|
| `App not installed` with no detail | usually the version-code downgrade | §3, or uninstall the old DHUN first (and say so in §7 — Check G becomes untested) |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | the old build was signed with a different key (for example a locally built debug APK from Android Studio) | uninstall the old DHUN, then install |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | no space | free ~150 MB |
| "For your security, your phone is not allowed to install unknown apps from this source" | the toggle in §2 step 6 | turn it on for the app you are installing from |
| `adb: no devices found` | cable/driver/authorisation | re-plug, unlock the phone, `adb kill-server`, accept the prompt |

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
