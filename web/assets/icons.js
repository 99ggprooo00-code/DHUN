/* ===========================================================================
   DHUN Web — inline SVG icon set.

   Material Design icon outlines, 24x24 viewBox, rendered at currentColor so
   they inherit the token text colours exactly as DhunIcons does in the app.
   Inline rather than an icon font: no extra request, no FOUT, and GitHub
   Pages serves them straight from the bundle.
   =========================================================================== */

const svg = (paths, extra = "") =>
  `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true" ${extra}>${paths}</svg>`;

export const ICONS = {
  search: svg('<path d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>'),

  play: svg('<path d="M8 5v14l11-7z"/>'),

  pause: svg('<path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>'),

  skipNext: svg('<path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/>'),

  skipPrevious: svg('<path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/>'),

  shuffle: svg('<path d="M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z"/>'),

  repeat: svg('<path d="M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z"/>'),

  repeatOne: svg('<path d="M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1l-2 1v1h1.5v4H13z"/>'),

  volume: svg('<path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0 0 14 7.97v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/>'),

  volumeOff: svg('<path d="M16.5 12A4.5 4.5 0 0 0 14 7.97v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51A8.8 8.8 0 0 0 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3 3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4 9.91 6.09 12 8.18V4z"/>'),

  close: svg('<path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>'),

  musicNote: svg('<path d="M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6z"/>'),

  queue: svg('<path d="M3 10h11v2H3v-2zm0-4h11v2H3V6zm0 8h7v2H3v-2zm13-1v6l5-3-5-3z"/>'),

  plug: svg('<path d="M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7a5 5 0 0 0 0 10h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4a3.1 3.1 0 0 1 0 6.2h-4V17h4a5 5 0 0 0 0-10z"/>'),

  expand: svg('<path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/>'),

  /* Two animated states, rendered as live SVG rather than glyphs. */
  equalizer: `<svg class="icon icon-sm eq" viewBox="0 0 24 24" aria-hidden="true">
    <rect class="eq-bar" x="5" y="10" width="3" height="9" rx="1.5"/>
    <rect class="eq-bar eq-bar-2" x="10.5" y="6" width="3" height="13" rx="1.5"/>
    <rect class="eq-bar eq-bar-3" x="16" y="12" width="3" height="7" rx="1.5"/>
  </svg>`,

  spinner: `<svg class="icon spin" viewBox="0 0 24 24" aria-hidden="true">
    <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor"
      stroke-width="2.5" stroke-linecap="round" stroke-dasharray="42" stroke-dashoffset="14"/>
  </svg>`,
};
