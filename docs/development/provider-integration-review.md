# Provider / integration review — 2026-10-15

This is an engineering decision record, not legal approval, proof of lyric-content
rights, or a successful authenticated live-service acceptance test. No new credential
or backend is fabricated to make a parity checklist green.

## Existing LRCLIB adapters: actionable contract review

Reviewed the live public documentation at <https://lrclib.net/docs> using the page
retrieval tool. This tool can retrieve public documentation even though sandbox
shell commands are limited to the development-host allowlist. Earlier notes about
unavailable live terms are superseded only for the pages explicitly listed here.

The current documentation requires client identification including app/version and a
project URL, sequential requests, 200–500 ms spacing, and respecting `Retry-After`.
429 responses can be non-JSON edge responses. `/api/get` duration is constrained to
1–3600 seconds. Anonymous access remains documented; optional application registration
is not a credential or content-redistribution license.

Implemented `LrcLibAccess` and wired one shared instance in each platform host:

- `/api/get` and `/api/search` identify DHUN and the actual Android build version /
  packaged desktop installer version (development when no installer version exists).
- Both endpoints share a cancellation-safe mutex; responses are consumed/closed before
  another starts, followed by 250 ms spacing. Explicit picker fan-out cannot bypass it.
- 429 and 503 establish origin-wide backoff from delay-seconds or IMF-fixdate
  `Retry-After`; missing headers conservatively default to 60 seconds. Unrecognized
  supplied formats fail closed for the process instead of ignoring a server delay.
  Cooldown expiry uses monotonic elapsed time after interpreting the header, so a
  wall-clock jump cannot expire a server-requested delay early. No automatic request
  retry is added. Queued cancellation does not reach HTTP.
- Response bytes are bounded to 2 MB and JSON nesting is checked before recursive
  tree parsing. Inter-request delay is relative, so wall-clock jumps cannot bypass it. Error bodies are not
  assumed to be JSON. Out-of-range duration is omitted rather than sent as invalid data.
- Tests use MockEngine, synthetic metadata, fake clocks and controlled suspension to
  verify identity, spacing, cross-adapter backoff, dates/overflow, cancellation and size
  bounds. These do not prove live API reliability, uptime, quota or content rights.

The documentation now also mentions Lyricsfile YAML/`hasWordSync`; this does not mean
DHUN already parses that format. Existing LRC/enhanced-LRC remains supported, and TTML
uses explicit local import. YAML ingestion would require a bounded safe-parser and
format/fixture review before any claim of runtime word-sync parity for that format.

## Translation: DeepL reviewed, implementation/acceptance still gated

Public documents reviewed:

- <https://developers.deepl.com/api-reference/translate/request-translation>
- <https://developers.deepl.com/docs/resources/privacy>
- <https://www.deepl.com/en/pro-license>, especially §§3.2, 3.3, 8.1, 8.3 and 9
- <https://www.deepl.com/en/privacy>, especially §4

Important findings for the future implementation:

- The documented API uses HTTPS POST, `Authorization: DeepL-Auth-Key ...`, UTF-8
  plain-text arrays and `target_lang`; the request-size limit is 128 KiB. Do not put
  keys in URLs or text in diagnostics, and disable credential-bearing redirects.
- Current terms distinguish paid API handling from Free/Developer. §3.3.3 permits
  perpetual Content/Processed Content storage for Developer, and §8.3.11 prohibits
  personal-data processing on that plan. Do not make one blanket retention promise
  for both endpoints. Paid API Pro is the candidate for a privacy-conscious BYO-key
  integration; this is not approval to charge an account or send any lyric text.
- §§8.1/8.3 require rights/authorizations for submitted content and secure credentials;
  §8.3.3 requires visible DeepL/domain attribution for unmodified API output. DeepL's
  terms do not confer rights to upload or redistribute third-party lyrics.
- Paid privacy §4 describes temporary processing/no model improvement for those paid
  services, subject to the actual agreement. Link the live policy rather than promising
  universal deletion/retention behavior inside DHUN.
- A subscription/account, supported territory and valid key are external dependencies.
  Do not request/paste credentials in chat. They must be entered only into an app UI
  backed by Android Keystore / Windows user-bound protection, never SQLite preferences,
  logs, command-line arguments, a repository, or an unprotected JSON configuration.
- Before enabling a runtime adapter: implement and test secure storage failure/removal,
  default-off/manual consent (with content-rights and cost disclosure), target selection,
  provider abstraction, request bounds, rate limits, cancellation, partial results,
  content/provider/target-keyed bounded cache, exact track/source guards and original
  timing preservation. Authenticated live acceptance must use an owner's consented
  account; mocks are insufficient. No translation toggle is added before it works.

No API key was supplied or transmitted, no paid API call was made, and translation is
not marked complete. The privacy/cost policy and physical credential-store acceptance
must be settled before calling the integration production-ready.

## Other providers and integrations: explicit no-go pending evidence

Existing YouTube/KuGou adapters remain present; this review does not establish new
terms/content permission for them. Additional lyric aggregators are not approved just
because their source code is public. Recognition needs a documented authorized service
and microphone-consent lifecycle; Last.fm needs a registered app/account-linking and
secure secrets; Discord needs opt-in listening disclosure and supported desktop IPC;
Listen Together needs an approved authenticated backend and room/retention review.

No working-service, licensed-content or full-parity claim is made for these candidates.

## Native acceptance gate

Hosted API 24/35 Robolectric and desktop/Windows packaging tests verify code/runtime
contracts, not physical-device rendering, native credential-vault behavior, input/focus,
TalkBack, language quality, audio behavior or low-memory performance. Physical Android
and Windows access is not connected to this workspace. A device owner must supply
actual acceptance results, or explicitly approve a revised emulator/VM acceptance
criterion. Never silently substitute a green build for physical acceptance; do not merge
while the agreed acceptance gate or requested implementation remains incomplete.
