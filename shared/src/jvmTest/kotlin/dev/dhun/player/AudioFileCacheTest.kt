package dev.dhun.player

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioFileCacheTest {

    private lateinit var dir: File
    private val bodies = HashMap<String, ByteArray>()
    private var fetches = 0

    /** User-Agent seen by the network layer, per fetch, in order. */
    private val seenUserAgents = ArrayList<String?>()

    private val fetch: (String, String?) -> InputStream = { url, userAgent ->
        fetches++
        seenUserAgents += userAgent
        bodies[url]?.let { ByteArrayInputStream(it) } ?: throw IOException("404 $url")
    }

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("dhun-audio-cache").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun cache(maxBytes: Long) = AudioFileCache(dir, maxBytes, fetch)

    private fun body(url: String, size: Int) {
        bodies[url] = ByteArray(size) { (it % 251).toByte() }
    }

    @Test
    fun downloadPresentsTheResolvingIdentityToTheNetworkLayer() {
        // googlevideo answers a byte read whose User-Agent differs from the
        // InnerTube identity that resolved the URL with 403 — the agent has
        // to travel from StreamInfo all the way down to the socket.
        body("u/ua", 100)
        val c = cache(10_000)

        assertNotNull(c.download("uauauauaua1", "u/ua", userAgent = "Cobalt/25.lts"))
        assertEquals(listOf<String?>("Cobalt/25.lts"), seenUserAgents)

        // No identity reported (e.g. a non-InnerTube engine) → null, and the
        // transport falls back to its own default rather than sending none.
        body("u/ub", 100)
        assertNotNull(c.download("uauauauaua2", "u/ub"))
        assertEquals(listOf<String?>("Cobalt/25.lts", null), seenUserAgents)
    }

    @Test
    fun downloadStoresCompleteFileAndSecondCallIsAHit() {
        body("u/a", 1000)
        val c = cache(10_000)
        val f = assertNotNull(c.download("aaaaaaaaaaa", "u/a"))
        assertEquals(1000, f.length())
        assertTrue(f.name.endsWith(AudioFileCache.AUDIO_SUFFIX))
        assertTrue(c.has("aaaaaaaaaaa"))
        assertEquals(1000, c.cachedBytes("aaaaaaaaaaa"))
        assertEquals(1, fetches)

        assertEquals(f, c.download("aaaaaaaaaaa", "u/a-rotated-url"))
        assertEquals(1, fetches, "cache hit must not touch the network")
        assertNull(dir.listFiles { x -> x.name.endsWith(AudioFileCache.PART_SUFFIX) }?.firstOrNull())
    }

    @Test
    fun lruEvictsOldestWhenOverBudget() {
        body("u/1", 400); body("u/2", 400); body("u/3", 400)
        val c = cache(1000)
        c.download("id_________1", "u/1")
        c.download("id_________2", "u/2")
        // Hit #1 → it becomes most-recently-used; #2 is now the oldest.
        assertNotNull(c.fileFor("id_________1"))
        c.download("id_________3", "u/3") // 1200 > 1000 → evict one
        assertTrue(c.totalBytes() <= 1000)
        assertFalse(c.has("id_________2"), "LRU victim must be the least recently used")
        assertTrue(c.has("id_________1"))
        assertTrue(c.has("id_________3"), "the just-downloaded file is never its own victim")
        assertEquals(listOf("id_________3", "id_________1"), c.ids())
    }

    @Test
    fun fileLargerThanBudgetIsNeverStored() {
        body("u/big", 5000)
        val c = cache(1000)
        assertNull(c.download("bigbigbigbi", "u/big"))
        assertNull(c.download("bigbigbigbi", "u/big", expectedBytes = 5000))
        assertEquals(0, c.totalBytes())
        assertEquals(0, dir.listFiles()?.size ?: 0, "no complete or partial leftovers")
    }

    @Test
    fun shortReadAgainstExpectedLengthIsDiscarded() {
        body("u/short", 300)
        val c = cache(10_000)
        assertNull(c.download("shortshorts", "u/short", expectedBytes = 1000))
        assertFalse(c.has("shortshorts"))
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }

    @Test
    fun networkFailureIsAMissNotAnException() {
        val c = cache(10_000)
        assertNull(c.download("missingmiss", "u/nope"))
        assertFalse(c.has("missingmiss"))
    }

    @Test
    fun cancelAbortsAndLeavesNoPartial() {
        body("u/c", 200_000)
        val c = cache(10_000_000)
        val cancel = AtomicBoolean(false)
        val f = c.download("cancelcance", "u/c", cancel = cancel, onProgress = { cancel.set(true) })
        assertNull(f)
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }

    @Test
    fun unsafeIdsAreRefusedAsPaths() {
        body("u/x", 10)
        val c = cache(10_000)
        assertNull(c.download("../etc/pass", "u/x"))
        assertNull(c.download("", "u/x"))
        assertFalse(c.has("../etc/pass"))
        assertNull(c.fileFor("a/b"))
        assertEquals(0, fetches)
    }

    @Test
    fun stalePartialsAreSweptOnOpen() {
        File(dir, "leftover____" + AudioFileCache.PART_SUFFIX).writeBytes(ByteArray(10))
        File(dir, "keepkeepkee" + AudioFileCache.AUDIO_SUFFIX).writeBytes(ByteArray(10))
        val c = cache(10_000)
        assertEquals(listOf("keepkeepkee"), c.ids())
        assertEquals(1, dir.listFiles()?.size)
    }

    @Test
    fun evictToBudgetAfterShrinkAndClear() {
        body("u/1", 400); body("u/2", 400)
        cache(10_000).apply {
            download("id_________1", "u/1")
            download("id_________2", "u/2")
        }
        val shrunk = cache(500)
        assertEquals(800, shrunk.totalBytes(), "opening does not evict by itself")
        shrunk.evictToBudget()
        assertEquals(400, shrunk.totalBytes())
        assertTrue(shrunk.has("id_________2"), "newest survives")
        shrunk.clear()
        assertEquals(0, shrunk.totalBytes())
        assertTrue(shrunk.ids().isEmpty())
    }

    // ---- ADR-005 next-track pre-buffering and temporary cache lifecycle ----

    @Test
    fun tempPrebufferDoesNotCountTowardsPermanentBudgetUntilPromoted() {
        body("u/perm", 500)
        body("u/temp", 400)
        val c = cache(1000)

        assertNotNull(c.download("perm________", "u/perm"))
        assertEquals(500, c.totalBytes())
        assertTrue(c.has("perm________"))

        val tempFile = assertNotNull(c.downloadTemp("temp________", "u/temp"))
        assertTrue(tempFile.name.endsWith(AudioFileCache.TEMP_SUFFIX))
        assertTrue(c.hasTemp("temp________"))
        assertFalse(c.has("temp________")) // not yet permanent
        assertEquals(500, c.totalBytes(), "temporary buffer is excluded from permanent total")

        // Promote temp to permanent
        val promoted = assertNotNull(c.promoteTempToPermanent("temp________"))
        assertTrue(promoted.name.endsWith(AudioFileCache.AUDIO_SUFFIX))
        assertTrue(c.has("temp________"))
        assertFalse(c.hasTemp("temp________"))
        assertEquals(900, c.totalBytes())
    }

    @Test
    fun unplayedTempFilesAreSweptOnClearTempAndStartup() {
        body("u/temp1", 200)
        body("u/temp2", 200)
        val c = cache(1000)
        c.downloadTemp("temp1_______", "u/temp1")
        c.downloadTemp("temp2_______", "u/temp2")
        assertTrue(c.hasTemp("temp1_______"))
        assertTrue(c.hasTemp("temp2_______"))

        // Sparing temp1
        c.clearTemp(keepVideoId = "temp1_______")
        assertTrue(c.hasTemp("temp1_______"))
        assertFalse(c.hasTemp("temp2_______"))

        // Sweeping all
        c.clearTemp()
        assertFalse(c.hasTemp("temp1_______"))

        // Re-create temp file and verify startup sweep
        File(dir, "temp_stale___" + AudioFileCache.TEMP_SUFFIX).writeBytes(ByteArray(100))
        val reloaded = cache(1000)
        assertFalse(reloaded.hasTemp("temp_stale___"))
    }
}
