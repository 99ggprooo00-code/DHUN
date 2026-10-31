# 37 — Home loading failure: evidence and phone diagnostics

> **Status: OPEN on the reported phone.** The live response and the shared parser
> have been probed, and concrete compatibility/diagnostic fixes are in the branch,
> but no Android device was available to the agent. A green build is not evidence
> that the phone is fixed. Do not close this investigation until the phone loads
> Home or its new technical detail identifies the remaining failure.

## 1. What was observed

On the reported phone, Home shows its greeting and mood chips, then:

> Could not load Home — The music service sent something DHUN couldn't read.

That text is the friendly message for `DhunError.Parse`; the greeting and chips
are intentionally outside the load-state content, so their presence is not a
second failure. Before this branch's diagnostic change, `HomeViewModel` discarded
the exception detail, making several different failures look identical.

## 2. Evidence collected on 2026-10-31

| Check | Result | What it means |
| --- | --- | --- |
| Compare PR #217's Home/parser files with `main` | No product change in this path | A regression introduced by the background/colour work is unlikely |
| Live probe run `41150840390` | Homepage HTTP 200; DHUN's client-version and visitor regexes matched; anonymous `/browse` HTTP 200 with and without context visitor data | The basic anonymous request was working from that runner at that time |
| Same probe, deliberately invalid client version | HTTP 404 | HTTP status and endpoint are essential diagnostic detail |
| Live parser probe run `41150960094`, US context | DHUN parser PASS: 2 sections, 30 items, continuation present | Today's US response was not universally incompatible with DHUN |
| Live parser probe run `41150960094`, Nepal (`gl=NP`) context | DHUN parser PASS: 2 sections, 20 items, continuation present | A country-only explanation is not supported by this sample |
| Both live responses | 3 raw sections; the unsupported `musicTastebuilderShelfRenderer` coexisted with valid shelves | Unknown section types must not discard valid shelves |
| Daily `extraction-health` runs | `ENVIRONMENT_BLOCKED` on GitHub runner IPs | This is not evidence about the phone and is not a parser-verdict |

The live JSON stayed on the runner. Only counts, renderer key names and parser
verdicts were emitted as check-run annotations. The temporary probe workflow is
removed after collecting this evidence.

**Not established:** which exception the phone received. Possibilities therefore
remain a device/network-specific HTTP rejection, a different served homepage, a
service experiment with another response shape, or another request-context
difference. The fixes below are justified by code/tests, not presented as the
phone's proven root cause.

## 3. What changed

- Home browse tabs accept both `tabRenderer` and `musicTabRenderer`.
- Malformed JSON and a non-object JSON root are reported as `Parse` with the
  endpoint, rather than being misclassified as `Unknown` and retried.
- Client-version discovery accepts JSON whitespace and, when discovery fails,
  reports the homepage HTTP status and response length without reporting its body.
- Home preserves a bounded, URL-redacted technical detail. The screen offers
  **Show technical details** below **Retry**; Android writes the same sanitized
  line to logcat with tag `DHUN`.
- Retry still calls the provider, now pinned by an explicit request-count test.
- Regression coverage includes a normal response, missing optional fields,
  unknown keys/section types, a valid empty section list, an empty/unrecognised
  envelope, malformed/non-object JSON, a broken shelf beside a valid shelf,
  HTTP errors, and `musicTabRenderer`.

No sample music is inserted, no error is permanently hidden, and a failed Retry
is not presented as success.

## 4. Simple phone check — no computer needed

Use the APK produced by the **latest** successful CI run for this branch (not an
older diagnostic-probe build).

1. Open the pull request in the phone browser.
2. Tap **Checks**, then the latest **Build APK** run.
3. Under **Artifacts**, download **debug-apk**.
4. Open the downloaded ZIP, then tap `dhun-test.apk`. If Android asks, allow
   installation from the browser or file manager for this one installation.
5. Install **over** the existing app. Do not uninstall first; uninstalling erases
   settings and downloaded music. If Android says the versions conflict, stop
   and report the message rather than deleting anything.
6. Open DHUN and go to **Home**.
7. If Home loads, try each mood chip, open another page and return to Home, then
   close and reopen the app. Record exactly what happened.
8. If Home still fails, tap **Show technical details** under **Retry**.
9. Take a screenshot (usually **Power + Volume Down** together) and send it. The
   detail is designed not to contain URLs, continuation tokens or response data.
10. Tap **Retry** once. If the message or detail changes, send that screenshot too.
11. Also note: Wi-Fi or mobile data, country, and whether a VPN, private DNS or
    ad-blocker is enabled. Do not include passwords or account details.

A screenshot of the expanded technical detail is the most useful next evidence.
Please do not judge the fix from the app merely opening or from CI being green.

## 5. Optional logcat, only if the screenshot is not enough

This needs a computer with Android Platform Tools (`adb`) and USB debugging.
The app writes the sanitized Home error as `DHUN` at error level.

1. Connect the phone by USB and allow its USB-debugging prompt.
2. Open a terminal in the Platform Tools folder.
3. Clear old logs: `adb logcat -c`
4. On the phone, open Home and wait for the error.
5. Save only DHUN error logs:
   `adb logcat -d -s DHUN:E "*:S" > dhun-home-log.txt`
6. Open `dhun-home-log.txt`, confirm it contains the line beginning
   `Home load failed`, and send that line or the file.

Do not post an unfiltered full-device log; it can contain unrelated personal or
network information.

## 6. Acceptance still required on Android

- Cold launch → Home loads real service content.
- Retry after a forced failure makes a new request and never shows false success.
- Every Home category either loads its own content or reports its real error.
- Navigate away/back and close/reopen: content and error states remain coherent.
- The new custom colour sliders are checked separately in
  [`35-background-visual-validation.md`](35-background-visual-validation.md),
  Check 3c.
