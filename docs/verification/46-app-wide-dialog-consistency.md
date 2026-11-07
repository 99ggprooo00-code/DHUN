# 46 — App-wide dialog consistency and responsive header

Date: 2026-11-07. Work branch: `arena/b3400d42-dhun`, based on current `main`
`da2199650a37fd66abb903dca3a583fcffaf78b3` (which includes PR #222).

## Audit and prioritization

The requester supplied 18 Android screenshots across player, queue/Related,
menus, library, settings, dialogs, and the component catalogue. I compared the
screens with the current Compose implementations and the merged Lyrics Controls
surface. This is a targeted pass, not an attempt to restyle every screen.

| Priority | Evidence / inconsistency | Root cause | Decision |
|---|---|---|---|
| High — unreadable modal content | Requester captures `Screenshot_2026-11-07-20-52-48-617_dev.dhun.android.jpg` (Lyrics matches) and `Screenshot_2026-11-07-21-05-35-252_dev.dhun.android.jpg`, `21-05-44-921`, `21-05-47-493`, `21-06-07-650` (Settings confirmations) show dark default text over dark translucent dialog bodies. | Several callers painted a `GlassCard` inside a raw `Dialog` but did not establish `LocalContentColor`; some confirmation titles/messages also relied on default text style. `GlassDialog` existed but was not the shared path. | Fixed at the dialog foundation and migrated affected screens. Explicit primary/secondary/error text roles remain in specialized content. |
| Medium — inconsistent confirmation hierarchy/footer | `Screenshot_2026-11-07-21-04-23-173_dev.dhun.android.jpg` (Reset settings) contrasts with `21-04-32-612` (Clear downloads): title/body contrast and Cancel/confirm alignment differ across equivalent actions. | Settings, Search, Library, and playlist mutations each owned their own repeated footer/layout; one footer pushed Cancel to the opposite edge while others grouped both actions on the right. | Added one `DhunConfirmDialog` for standard confirm/cancel mutations, with explicit title/body/error roles, right-grouped actions, and visible pending/retry states. Mutation ownership and result-driven close behavior remain in the callers. |
| Medium — duplicate dialog shells | Lyrics match search, country selection, playlist create/rename, and confirmation flows each paired `Dialog` and `GlassCard` locally with their own width/inset behavior. | The existing canonical `GlassDialog` was not used consistently, so default text color and dialog sizing could drift. | Reused `GlassDialog` for tailored form/list dialogs; the country picker retains its wider cap and bounded list. Artwork-backed lyrics controls and menu surfaces stay on their dedicated materials. |
| Low — narrow catalogue header | `Screenshot_2026-11-07-21-04-13-459_dev.dhun.android.jpg` shows “Close” wrapping beside the long test-build component-catalogue title. | The title competed for the same unbounded `Row` width as the close action. | Gave the title the remaining width with one-line ellipsis and kept “Close” on one line. |

Queue/Related, track overflow, and the full player already use their dedicated
artwork-backed surfaces and were not changed. Sparse Library/Downloads content
was reviewed but left as natural flexible page space rather than adding filler.

## Root-cause fixes

- `GlassDialog` now provides the active DHUN `textPrimary` as
  `LocalContentColor` and `bodyMedium` as the default text style, in addition to
  its existing opaque-base glass material, shared padding, shape, and default
  width. It accepts a maximum width for deliberately wider content.
- New `DhunConfirmDialog` centralizes the common confirmation hierarchy,
  secondary copy, polite live-region error text, grouped footer, pending
  spinner/label, disabled dismiss/confirm actions while a mutation is pending,
  and retry label. It remains presentation-only; callers keep the existing
  repository/ViewModel callbacks and success/failure state.
- Migrated Settings confirmation actions, Search recent-history confirmation,
  Library download/history confirmations, and playlist deletion to that
  component. Playlist create/rename, Country/Region, and Lyrics Match dialogs
  now use `GlassDialog`; the Lyrics Match body explicitly distinguishes primary,
  secondary, and error text and keeps its long content scrollable.
- The catalogue header now allocates a bounded title slot and preserves the
  close action at phone width.
- Updated `docs/design/glass-system.md` so the implementation contract matches
  the shared dialog system and documents why direct one-off `Dialog` +
  `GlassCard` confirmation implementations should not return.

## Functionality preserved

No preference keys, navigation, search/provider behavior, playlist operations,
download management, playback/lyrics state machines, or async mutation logic
were changed. Back/outside dismiss stays blocked during in-flight writes where
it was blocked before; Cancel and confirm remain wired to the same callbacks.
The generic confirmation regression test checks that pending actions are
inert, then verifies Cancel and retry/confirm callbacks after pending ends.

## Visual and test evidence

Before evidence is the requester-supplied Android captures listed above (not
agent-captured device evidence and not redistributed in the repository). The
Compose/Skia after evidence was rendered by [Visual evidence run 42388109719](https://github.com/99ggprooo00-code/DHUN/actions/runs/42388109719):
10 visual tests passed, with 73 PNG frames and zero failures/errors. Its
inspectable manifest is Git blob
[`e0d59a33f4faf3118592e80e178ebe2bfbcf492f`](https://api.github.com/repos/99ggprooo00-code/DHUN/git/blobs/e0d59a33f4faf3118592e80e178ebe2bfbcf492f).
The `compose-visual-evidence` workflow artifact contains the PNGs and test
reports; dark/light dialog and Lyrics Match frames, the 1.3x text captures, and
the narrow catalogue header were retrieved through the manifest and inspected.
They show readable content, visible actions, and the expected ellipsized title;
this is renderer evidence, not device validation. Key frame names:

- `confirm-dialog-dark.png`, `confirm-dialog-light.png` — shared confirmation
  hierarchy and DHUN foreground in both themes;
- `lyrics-match-picker-dark.png`, `lyrics-match-picker-light.png` — the actual
  Lyrics Match picker, including local TTML import, in both themes;
- `confirm-dialog-large-text-dark.png`, `confirm-dialog-large-text-light.png` —
  compact phone canvas at 1.3x font scale;
- `component-catalog-header-phone-dark.png` — close action at a 360dp viewport;
- `dialog-dark.png`, `dialog-light.png` — shared default-content-color check.

These are renderer frames, not proof of physical-device rendering.

| Check | Result | Notes |
|---|---|---|
| `git diff --check` | PASS | Clean after the source/test changes. |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | PASS | 521 tests passed, including design-system source guards. |
| Compose/Skia visual and interaction suite | PASS | Run 42388109719: 10 tests passed, 0 failures/errors; 73 PNG frames. Includes both-theme dialog frames, actual Lyrics Match flows, pending/retry callbacks, 1.3x large-text captures, and narrow catalogue close action. |
| Shared/Desktop JVM and Android Robolectric CI | PARTIAL | PR checks `build`, `build-and-test`, and `compose` passed. The [APK job](https://github.com/99ggprooo00-code/DHUN/actions/runs/42388109855/job/128449754126) reached `:app-android:testDebugUnitTest` but could not fetch Robolectric artifact `org.robolectric:android-all-instrumented:12-robolectric-7732740-i7`; Android unit tests therefore have no result in this run. No tests were removed or weakened. |
| Local Gradle compile/tests | NOT RUN | Installed and verified Temurin JDK 17.0.9 under `~/.cache/toolchain/jdk4py`. Gradle 8.14.2 is not cached and its wrapper download host is outside the allowed outbound-host set; GitHub Actions is the validation path for Kotlin/Compose. |
| Android emulator | NOT TESTED | No emulator validation is claimed. |
| Physical Android device | NOT TESTED | User screenshots are the before reference; no after-device capture was made. |

## Deferred observations

- Queue/Related and the compact track menu were deliberately not polished again;
  the supplied captures already show their dedicated surfaces, aligned actions,
  and legible rows. Replacing these with a generic dialog would reduce coherence.
- Full-library typography, art/blur behavior on OEM Android renderers, and
  device-specific dynamic type/hit-target checks still require real device QA.

## Review

Open for review: [PR #223](https://github.com/99ggprooo00-code/DHUN/pull/223).
