/**
 * The sample catalogue: fictional rows, bundled, and labelled as fictional.
 *
 * WHY IT EXISTS. When the catalogue cannot be reached — a corporate proxy, a
 * rate limit, a network that blocks the API — an empty app cannot tell a visitor
 * whether the interface works. This gives the screens real rows to draw so the
 * design, the keyboard and the layout can still be judged. It is a fallback, not
 * a source: the moment the API answers, it is used instead.
 *
 * WHAT IT IS NOT. It carries no audio. Nothing here can be played, and the app
 * says so in one sentence on every screen it appears on, because a demo row that
 * pretends to play is the exact dishonesty this repository's tests exist to
 * catch. Every title, artist and album below is invented, and every identifier
 * starts with `demo-` so a stored row can never be mistaken for a catalogue one.
 */

const ARTISTS = [
  { id: "demo-ar-1", name: "Naya Koirala" },
  { id: "demo-ar-2", name: "The Koshi Sessions" },
  { id: "demo-ar-3", name: "Bhanu & the Lowlands" },
  { id: "demo-ar-4", name: "Sundari Ensemble" },
];

const ALBUMS = [
  { id: "demo-al-1", title: "Dharan Nights", artist: "Naya Koirala", year: "2024", language: "Nepali" },
  { id: "demo-al-2", title: "Terai Field Recordings", artist: "The Koshi Sessions", year: "2023", language: "Nepali" },
  { id: "demo-al-3", title: "Lowland Echoes", artist: "Bhanu & the Lowlands", year: "2025", language: "Nepali" },
  { id: "demo-al-4", title: "Sundari", artist: "Sundari Ensemble", year: "2022", language: "Nepali" },
];

const TRACKS = [
  ["demo-tr-1", "Monsoon Over Dharan", 1, 227],
  ["demo-tr-2", "Bijeuli", 1, 194],
  ["demo-tr-3", "Pahad Ko Bato", 1, 251],
  ["demo-tr-4", "Salt and Kerosene", 1, 183],
  ["demo-tr-5", "Rooftop in Bhanu Chowk", 1, 210],
  ["demo-tr-6", "Koshi At Dawn", 2, 306],
  ["demo-tr-7", "Ferry Bells", 2, 168],
  ["demo-tr-8", "Bamboo Wireless", 2, 242],
  ["demo-tr-9", "Seven Bridges", 2, 219],
  ["demo-tr-10", "The Long Switchback", 2, 236],
  ["demo-tr-11", "Lowland Echo", 3, 274],
  ["demo-tr-12", "Tea Stall Radio", 3, 201],
  ["demo-tr-13", "Night Bus to Biratnagar", 3, 288],
  ["demo-tr-14", "Cardamom", 3, 176],
  ["demo-tr-15", "Sisnu", 3, 189],
  ["demo-tr-16", "Sundari Overture", 4, 331],
  ["demo-tr-17", "Raga for a Rainy Street", 4, 295],
  ["demo-tr-18", "Bansuri Interlude", 4, 142],
  ["demo-tr-19", "Closing Procession", 4, 263],
  ["demo-tr-20", "Evening Tanpura", 4, 224],
];

/** A demo song in the shape `normaliseSong` returns, with no audio and no art. */
export function demoSong([id, title, albumIndex, duration]) {
  const album = ALBUMS[albumIndex - 1];
  const artist = ARTISTS.find((entry) => entry.name === album.artist) ?? ARTISTS[0];
  return {
    kind: "song",
    id,
    title,
    subtitle: "",
    artist: album.artist,
    artists: [{ id: artist.id, name: artist.name, image: "" }],
    album: { id: album.id, name: album.title },
    year: album.year,
    language: album.language,
    duration,
    image: "",
    imageSmall: "",
    stream: "",
    streamQuality: "",
    hasLyrics: false,
    explicit: false,
    playCount: 0,
    url: "",
    demo: true,
  };
}

export const DEMO_SONGS = Object.freeze(TRACKS.map((row) => Object.freeze(demoSong(row))));

export function demoAlbum(entry) {
  const songs = DEMO_SONGS.filter((song) => song.album.id === entry.id);
  return {
    kind: "album",
    id: entry.id,
    title: entry.title,
    artist: entry.artist,
    artists: [{ id: ARTISTS.find((artist) => artist.name === entry.artist)?.id ?? "", name: entry.artist }],
    year: entry.year,
    language: entry.language,
    songCount: songs.length,
    duration: songs.reduce((total, song) => total + song.duration, 0),
    image: "",
    songs: [...songs],
    url: "",
    demo: true,
  };
}

export function demoArtist(entry) {
  const songs = DEMO_SONGS.filter((song) => song.artist === entry.name);
  const albums = ALBUMS.filter((album) => album.artist === entry.name).map(demoAlbum);
  return {
    artist: {
      kind: "artist",
      id: entry.id,
      title: entry.name,
      image: "",
      imageSmall: "",
      description: "An invented artist from DHUN's bundled sample catalogue.",
      followerCount: 0,
      verified: false,
      dominantLanguage: "Nepali",
      dominantType: "artist",
      url: "",
      demo: true,
    },
    songs: [...songs],
    albums,
  };
}

export const DEMO_ALBUMS = Object.freeze(ALBUMS.map((entry) => Object.freeze(demoAlbum(entry))));
export const DEMO_ARTISTS = Object.freeze(ARTISTS.map((entry) => Object.freeze(demoArtist(entry))));

export const DEMO_PLAYLISTS = Object.freeze([
  Object.freeze({
    kind: "playlist",
    id: "demo-pl-1",
    title: "Dharan after dark",
    description: "An invented playlist in DHUN's sample catalogue.",
    owner: "DHUN",
    year: "2025",
    language: "Nepali",
    songCount: 5,
    duration: DEMO_SONGS.slice(0, 5).reduce((total, song) => total + song.duration, 0),
    image: "",
    songs: DEMO_SONGS.slice(0, 5),
    url: "",
    demo: true,
  }),
  Object.freeze({
    kind: "playlist",
    id: "demo-pl-2",
    title: "Koshi river road",
    description: "An invented playlist in DHUN's sample catalogue.",
    owner: "DHUN",
    year: "2025",
    language: "Nepali",
    songCount: 5,
    duration: DEMO_SONGS.slice(5, 10).reduce((total, song) => total + song.duration, 0),
    image: "",
    songs: DEMO_SONGS.slice(5, 10),
    url: "",
    demo: true,
  }),
]);

/** Search the sample catalogue the way the API would, so filters still work. */
export function searchDemo(kind, query) {
  const needle = String(query ?? "").toLowerCase().trim();
  const matches = (value) => !needle || String(value ?? "").toLowerCase().includes(needle);
  if (kind === "albums") return DEMO_ALBUMS.filter((album) => matches(album.title) || matches(album.artist));
  if (kind === "artists") return DEMO_ARTISTS.map((entry) => entry.artist).filter((artist) => matches(artist.title));
  if (kind === "playlists") return DEMO_PLAYLISTS.filter((playlist) => matches(playlist.title));
  return DEMO_SONGS.filter((song) => matches(song.title) || matches(song.artist) || matches(song.album.name));
}

/** One entity for a demo route, or null when the id is not a demo id. */
export function demoEntity(kind, id) {
  if (kind === "album") return DEMO_ALBUMS.find((album) => album.id === id) ?? null;
  if (kind === "playlist") return DEMO_PLAYLISTS.find((playlist) => playlist.id === id) ?? null;
  if (kind === "artist") return DEMO_ARTISTS.find((entry) => entry.artist.id === id) ?? null;
  return null;
}

/** Home's shelves, built from the sample catalogue. */
export function demoHome() {
  return [
    { id: "trending", title: "Trending now", kind: "songs", items: DEMO_SONGS.slice(0, 8) },
    { id: "albums", title: "New albums", kind: "albums", items: DEMO_ALBUMS },
    { id: "artists", title: "Artists to know", kind: "artists", items: DEMO_ARTISTS.map((entry) => entry.artist) },
    { id: "playlists", title: "Playlists", kind: "playlists", items: DEMO_PLAYLISTS },
  ];
}

export const DEMO_NOTICE =
  "Sample catalogue — the JioSaavn API could not be reached. These rows are invented examples and carry no audio.";
