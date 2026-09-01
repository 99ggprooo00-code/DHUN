# 01 — Extraction Spike (findings, live-updated)

Status: IN PROGRESS. Sandbox = datacenter IP, no cookies, no keys.

## Verified so far (2026-09-01)
| Probe | Endpoint | Result |
|---|---|---|
| Search (songs filter) | `/youtubei/v1/search`, WEB_REMIX 1.20250310.01.00, client-name 67 | HTTP 200 · 198 KB · 20 `musicResponsiveListItemRenderer` items · videoId extraction OK (`utwMHfDZ6SA` = Bohemian Rhapsody) |
| Radio/related | `/youtubei/v1/next` + `playlistId=RDAMVM<videoId>` | HTTP 200 · 556 KB · 50 `playlistPanelVideoRenderer` tracks, correct bylines |

Conclusion: InnerTube **metadata path is open without PO tokens** from a
datacenter IP, as the Doctrine predicted. Fixtures saved to
`tests/fixtures/` (search-songs-bohemian-rhapsody.json,
next-radio-utwMHfDZ6SA.json).

## Parser notes
- Search item titles: `flexColumns[].musicResponsiveListItemFlexCellRenderer.text`
  — runs pattern returned null; `.text` shape differs (simpleText vs runs).
  Resolve in the Kotlin parser against the committed fixture (no more live calls needed).
- `playlistItemData.videoId` is the reliable ID location on search items.

## Remaining for this phase (the actual kill-switch test)
- [ ] NewPipe Extractor stream resolution → audible audio on real hardware
      (needs the Kotlin/Gradle probe CLI — Phase 01 continues in Phase 02 setup)
- [ ] yt-dlp fallback probe (desktop)
