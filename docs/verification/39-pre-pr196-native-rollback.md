# 39 — Owner-directed native rollback to the pre-PR #196 snapshot

**Date:** 2026-10-31

**PR:** #217 (open; not merged)

**Requested direction:** restore the app to how it was before PR #196 and keep the website as it is.

## 1. Exact rollback point

PR #196 is open and unmerged. It is a web-policy change, not a native-app merge.
Its base is commit **`12498caa02607edd74504161f388fc2e11d34a4c`** (`12498ca`),
the merge of PR #195. That commit is therefore the unambiguous repository snapshot
immediately before PR #196.

The native source and native tests under these paths were restored from `12498ca`:

- `app-android/src/`
- `app-desktop/src/`
- `shared/src/`

This deliberately removes the later native recovery/background/library changes
rather than attempting another isolated repair. Post-#196 native-only static
design tests (`scripts/test_design_system.py` and its allowlist) were removed
with the implementation they tested; the pre-#196 Kotlin test suites were
restored with the source.

## 2. What was intentionally preserved

The current website and browser application were not rolled back. In particular,
these paths have no diff from the pre-rollback head:

- `website/`
- `app-web/`
- `web-spike/`
- `docs/web-player/`

Three shared production files and one legal test remain current instead of being
restored, because they serve the preserved website/legal corpus rather than
reintroducing the rolled-back app screens or recovery behaviour:

- `shared/src/commonMain/kotlin/dev/dhun/design/DhunIcons.kt` — the current web
  build generates `app-web/src/js/icons.js` directly from this catalogue.
- `shared/src/commonMain/kotlin/dev/dhun/legal/LegalContent.kt` — generated from
  the current canonical legal documents and checked for parity with the web
  fixture.
- `shared/src/commonMain/kotlin/dev/dhun/ui/legal/AboutLegalScreen.kt` — keeps the
  pre-#196 screen and its existing divider, with only the current legal status
  wording required by that corpus. The newer `DhunDivider` dependency is not
  retained; exact-head CI run `41184568603` identified that incompatibility.
- `shared/src/jvmTest/kotlin/dev/dhun/legal/MarkdownParserTest.kt` — checks the
  current nine-control Support table. Restoring the obsolete three-control
  expectation caused the shared-test failure in exact-head CI run `41184736659`;
  it did not indicate an app-source rollback difference.

The current Android/Desktop Gradle files and Actions workflows also remain.
They are packaging/identity infrastructure, not a restoration of post-#196 app
behaviour. Keeping the current non-public test-version guard allows the existing
Build APK workflow to produce upgrade-safe test artifacts for this rollback.
No release, tag, publication, protected asset, or existing APK was changed.

## 3. Verification on the rollback working tree

- ✅ `python3 -m unittest discover -s scripts -p 'test_*.py'` — **506 tests passed**.
- ✅ `python3 scripts/validate_fixtures.py` — **39 JSON fixtures valid**; this is
  syntax validation, not Kotlin execution.
- ✅ `python3 scripts/gen_legal_content.py --check` — seven pages and both generated
  targets current.
- ✅ `npm run check` — current web icons, web build, **340 web tests**, and all 20
  web-player documents passed after retaining the shared current icon catalogue.
- ✅ `git diff --check` — no whitespace errors.
- ✅ Byte/tree comparison confirmed no rollback diff in `website/`, `app-web/`,
  `web-spike/`, or `docs/web-player/`.
- ✅ Apart from the documented shared website/legal production and test exceptions
  above, the native source paths have no diff from `12498ca`.

## 4. Limits and acceptance status

- ⚠️ No local JDK/Android SDK, emulator, phone, or interactive Windows environment
  is available here. Local Kotlin compilation and device verification were not
  performed; the exact pushed head must pass the existing GitHub Actions gates.
- 🟡 CI success will establish compilation/tests/package integrity only. It will
  not prove that the rollback matches the owner's phone expectation.
- 🟡 Android phone acceptance and interactive Windows acceptance remain owner
  checks on a new Actions artifact.
- 🔴 Artifact `13034650536` and the earlier recovery candidates belong to the
  superseded recovery line. They are not artifacts of this rollback and must not
  be offered as evidence for it.
- ⛔ No merge or release is authorized by this record.

The earlier R9–R12 recovery ledger in
[`38-native-application-recovery.md`](38-native-application-recovery.md) is kept
as historical evidence, not as the active implementation direction.
