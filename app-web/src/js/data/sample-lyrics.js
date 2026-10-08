/**
 * Sample lyrics — original text written for this repository, bundled so the
 * synced-lyrics surface can be exercised without a network round trip.
 *
 * The app's chain is LRCLIB → YouTube Music lyrics → cache
 * (shared/src/commonMain/kotlin/dev/dhun/lyrics/); from a browser origin both
 * are unreachable, so a track with no bundled LRC shows "No lyrics for this
 * track" rather than invented words. These entries are labelled sample data by
 * the same notice as the catalogue.
 *
 * Format is standard LRC, parsed by js/lyrics.js (a mirror of LrcParser.kt).
 */

export default Object.freeze({
  "tr-01": `[ti:Monsoon Over Dharan]
[ar:Naya Koirala]

[00:00.00]The rain comes down the tin roof
[00:06.40]like a drum that forgot the beat
[00:12.90]Streetlights blur in the water
[00:19.20]and the whole town moves its feet
[00:26.00]
[00:31.50]Monsoon over Dharan
[00:37.80]hold the window, hold the light
[00:44.10]Every road a river
[00:50.30]and the river runs all night
[01:02.00]
[01:20.00]I keep your letter folded
[01:26.30]in a book I never read
[01:32.70]Some things stay for the weather
[01:39.00]and some things stay instead`,

  "tr-05": `[ti:Koshi At Dawn]
[ar:The Koshi Sessions]

[00:00.00]Before the ferries waken
[00:07.20]the sand is cold and grey
[00:13.60]A heron writes one letter
[00:19.90]and the current takes it away
[00:27.00]
[00:34.00]Koshi at dawn
[00:40.20]carrying the hills down to the sea
[00:47.50]Koshi at dawn
[00:53.80]carrying the hills away from me`,

  "tr-13": `[ti:Sundari Overture]
[ar:Sundari Ensemble]

[00:00.00]♪
[00:12.00]Rise, the morning is a ribbon
[00:24.00]tied around the banyan tree
[00:38.00]Rise, the morning is a ribbon
[00:52.00]and the ribbon is for thee`,
});
