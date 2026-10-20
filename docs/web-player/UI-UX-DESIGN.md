# UI and UX design — DHUN Web (`/app/`)

The web counterpart of [`docs/ui-ux-design.md`](../ui-ux-design.md). It describes
the interface that exists: its structure, its tokens, its components, and the
accessibility behaviour that the browser checks measure. It does not describe
screens that are not built.

The web build is a second client. It shares the DHUN look, not the Android or
Windows code. The tokens are in `app-web/src/css/tokens.css`, and the styles for
the whole interface are in `app-web/src/css/app.css`.

## 1. Principles

1. **Dark first.** The default theme is dark. Light and system are available.
2. **Look like DHUN, behave like a browser page.** Frosted surfaces and the
   DHUN accents, but normal links, normal focus and normal back navigation.
3. **Say what happened.** A control that cannot work says why, next to itself.
4. **Phone and desktop are both first-class.** The same screens reflow; the
   player is always reachable.

## 2. Structure

| Region | Narrow windows (below 840 px) | Wide windows (840 px and above) |
|---|---|---|
| Top bar | The app bar (`.dhun-app` bar area) | The app bar spans the window |
| Navigation | Bottom navigation with Home, Search, Library | A side rail beside the content (`rail` area) |
| Content | One column of shelves and rows | The main area beside the rail |
| Player | The player bar; the full player opens over the content with `player=open` | The same |

The grid areas are set in `app.css` (`bar`, `rail`, `main`). This table records
the layout the stylesheet defines; it does not record how each region looks on a
real device, which is not measured (see §10).

The breakpoints in `app.css` are: 380 px (very narrow), 480 px landscape with a
short height, 599 px and 600 px (columns), 839 px and 840 px (wide layout), and
the height-based rules for short windows.

The three destinations are defined in `NAV_TABS` in `router.js`: **Home**,
**Search**, **Library**.

## 3. Tokens

`tokens.css` holds the colour, type, spacing, radius and motion values. The
themes are `dark`, `light` and `system`. The accent is one of six: `brand`
(labelled **Violet**), `azure`, `jade`, `amber`, `rose`, `cyan`, chosen in
Settings → Appearance.

The stylesheet cannot use a custom property inside a media query, so breakpoints
are written as literal values (this is noted in `tokens.css`).

## 4. Components

| Component | Used for | Notes |
|---|---|---|
| Hero | The first Home item | Drawn large. It is the top result of the trending query, not a marked "featured" item. |
| Shelf (rail) | Home sections and related lists | Horizontal, one row of cards or rows. |
| Track row | Lists of songs | Title, artists, a play control and a menu. |
| Entity hero | Album, artist, playlist headers | Kicker, title, meta and actions. |
| Chip | Tabs, theme and accent choices, filters | Uses `role="tab"` or `role="group"` as the context needs. |
| Sheet | Playlist editing, shortcut list, confirmations | A dialog. Tab is held inside it; Escape closes it. |
| Player bar and full player | Playback | The full player has a Queue and a Lyrics tab (a real `tablist`). |
| Notice and toast | Status and errors | Live regions (`aria-live`). |
| Error state | A failed request | The sentence the build wrote, then "Technical detail". |
| Sample badge | Sample rows | Labelled wherever a sample row is drawn. |

## 5. Language and tone

- Plain statements. A failure says what happened in one sentence. Browser and
  server text is shown only under "Technical detail".
- No marketing language inside the app. The site has its own voice
  (see [`.ai/WEBSITE_PLAN.md`](../../.ai/WEBSITE_PLAN.md)).
- Titles on empty states say what to do next ("No playlists yet").

## 6. Motion

Motion is limited to short transitions. `prefers-reduced-motion: reduce` removes
them. The stylesheet has a block for that media query.

## 7. Contrast and forced colours

- `forced-colors: active` has its own rules, so controls keep their boundaries
  when the system overrides colours.
- The browser checks (see [TESTING.md](TESTING.md)) measure contrast on the
  built pages.

## 8. Print

A print stylesheet exists. It is not measured by any check in the repository.

## 9. What the browser checks measure

The browser job in `website/tests/browser.mjs` measures, on the served pages:

- viewports (phone, tablet and wide sizes);
- keyboard reachability of the main controls;
- contrast;
- reduced motion;
- axe accessibility rules;
- the icon set.

It does not measure the audio, the feel of touch targets on a real phone, or the
behaviour of a screen reader.

## 10. Not verified

- A manual screen-reader pass over the player and the library.
- Real-device touch testing.
- Visual regression against a stored reference set.
