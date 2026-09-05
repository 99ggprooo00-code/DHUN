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

    private val fetch: (String) -> InputStream = { url ->
        fetches++
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
}
