/**
 * Sample catalogue — fictional, bundled, clearly labelled.
 *
 * Every title, artist, album and playlist here is invented. It exists so the
 * web interface can be seen and exercised without a working upstream: rows,
 * rails, grids, the queue, lyrics sync and the equaliser all need data, and
 * inventing it inside the UI code is how a demo starts lying about itself.
 *
 * It is shown under a persistent "SAMPLE DATA" notice in the UI, and
 * `scripts/test_app_web.py` asserts that notice exists.
 */

const ARTISTS = [
  { id: "ar-01", name: "Naya Koirala" },
  { id: "ar-02", name: "The Koshi Sessions" },
  { id: "ar-03", name: "Bhanu & the Lowlands" },
  { id: "ar-04", name: "Sundari Ensemble" },
];

const ALBUMS = [
  { id: "al-01", title: "Dharan Nights", artist: "Naya Koirala", year: 2024 },
  { id: "al-02", title: "Terai Field Recordings", artist: "The Koshi Sessions", year: 2023 },
  { id: "al-03", title: "Lowland Echoes", artist: "Bhanu & the Lowlands", year: 2025 },
  { id: "al-04", title: "Sundari", artist: "Sundari Ensemble", year: 2022 },
];

/** trackId, title, artist, albumId, durationMs. */
const TRACKS = [
  ["tr-01", "Monsoon Over Dharan", "Naya Koirala", "al-01", 227_000],
  ["tr-02", "Bijeuli", "Naya Koirala", "al-01", 194_000],
  ["tr-03", "Pahad Ko Bato", "Naya Koirala", "al-01", 251_000],
  ["tr-04", "Salt and Kerosene", "Naya Koirala", "al-01", 183_000],
  ["tr-05", "Koshi At Dawn", "The Koshi Sessions", "al-02", 306_000],
  ["tr-06", "Ferry Bells", "The Koshi Sessions", "al-02", 168_000],
  ["tr-07", "Bamboo Wireless", "The Koshi Sessions", "al-02", 242_000],
  ["tr-08", "Seven Bridges", "The Koshi Sessions", "al-02", 219_000],
  ["tr-09", "Lowland Echo", "Bhanu & the Lowlands", "al-03", 274_000],
  ["tr-10", "Tea Stall Radio", "Bhanu & the Lowlands", "al-03", 201_000],
  ["tr-11", "Night Bus to Biratnagar", "Bhanu & the Lowlands", "al-03", 288_000],
  ["tr-12", "Cardamom", "Bhanu & the Lowlands", "al-03", 176_000],
  ["tr-13", "Sundari Overture", "Sundari Ensemble", "al-04", 331_000],
  ["tr-14", "Raga for a Rainy Street", "Sundari Ensemble", "al-04", 295_000],
  ["tr-15", "Bansuri Interlude", "Sundari Ensemble", "al-04", 142_000],
  ["tr-16", "Closing Procession", "Sundari Ensemble", "al-04", 263_000],
  ["tr-17", "Rooftop in Bhanu Chowk", "Naya Koirala", "al-01", 210_000],
  ["tr-18", "The Long Switchback", "The Koshi Sessions", "al-02", 236_000],
  ["tr-19", "Sisnu", "Bhanu & the Lowlands", "al-03", 189_000],
  ["tr-20", "Evening Tanpura", "Sundari Ensemble", "al-04", 224_000],
].map(([id, title, artist, albumId, durationMs]) => ({
  id,
  title,
  artist,
  albumId,
  album: ALBUMS.find((a) => a.id === albumId)?.title ?? "",
  durationMs,
  /** No stream: this build has no playable audio source. See player.js. */
  streamUrl: null,
}));

const PLAYLISTS = [
  {
    id: "pl-01",
    name: "Liked Songs",
    /** The app treats Liked Songs as a folder inside Playlists. */
    isLikedFolder: true,
    trackIds: ["tr-01", "tr-05", "tr-09", "tr-13", "tr-17", "tr-19"],
  },
  {
    id: "pl-02",
    name: "Evening Drive",
    trackIds: ["tr-03", "tr-11", "tr-14", "tr-18"],
  },
  {
    id: "pl-03",
    name: "Monsoon Focus",
    trackIds: ["tr-06", "tr-07", "tr-15", "tr-20", "tr-02"],
  },
];

export default Object.freeze({
  artists: ARTISTS,
  albums: ALBUMS,
  tracks: TRACKS,
  playlists: PLAYLISTS,
});
