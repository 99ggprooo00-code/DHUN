package dev.dhun.extraction

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.detailString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class YtDlpStreamResolverTest {
    @Test
    fun windowsFindsExeOnPathIncludingSpacesWithoutWhichOrPython() {
        val directory = Files.createTempDirectory("dhun windows tools ").toFile()
        try {
            val exe = File(directory, "yt-dlp.exe").apply { writeText("test fixture") }
            assertEquals(listOf(exe.absolutePath), YtDlpStreamResolver.locate(mapOf("Path" to "\"${directory.path}\""), windows = true))
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun explicitBinaryOverrideWins() {
        assertEquals(listOf("C:\\Tools with spaces\\yt-dlp.exe"), YtDlpStreamResolver.locate(
            mapOf("DHUN_YTDLP" to "C:\\Tools with spaces\\yt-dlp.exe"), windows = true,
        ))
    }

    @Test
    fun windowsUsesAnInstalledPythonLauncherOnlyAsFallback() {
        val directory = Files.createTempDirectory("dhun launcher ").toFile()
        try {
            val py = File(directory, "py.exe").apply { writeText("test fixture") }
            assertEquals(listOf(py.absolutePath, "-3", "-m", "yt_dlp"), YtDlpStreamResolver.locate(mapOf("Path" to directory.path), windows = true))
            assertEquals(listOf("yt-dlp.exe"), YtDlpStreamResolver.locate(emptyMap(), windows = true))
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun windowsDoesNotLaunchTheUninstalledPythonStoreAlias() {
        val directory = Files.createTempDirectory("dhun aliases").toFile()
        try {
            val aliases = File(directory, "Microsoft/WindowsApps").apply { mkdirs() }
            File(aliases, "python.exe").writeText("Store alias fixture")
            assertEquals(listOf("yt-dlp.exe"), YtDlpStreamResolver.locate(mapOf("Path" to aliases.path), windows = true))
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun unixStillFindsTheExecutable() {
        val directory = Files.createTempDirectory("dhun tools").toFile()
        try {
            val exe = File(directory, "yt-dlp").apply { writeText("test fixture"); setExecutable(true) }
            assertEquals(listOf(exe.absolutePath), YtDlpStreamResolver.locate(mapOf("PATH" to directory.path), windows = false))
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun missingToolIsNotReportedAsAnOfflineNetwork() = runBlocking {
        val resolver = YtDlpStreamResolver(listOf("missing"), startProcess = { throw IOException("not found") })
        val error = (resolver.resolve("track") as DhunResult.Failure).error
        assertTrue(error is DhunError.Unknown)
        assertTrue(error.detailString().orEmpty().contains("Install yt-dlp"))
    }

    @Test
    fun returnsOneUrlWithTheMatchingAgentAndDoesNotReadUserConfig() = runBlocking {
        val process = TestProcess(stdout = "https://media.example/audio\n")
        var args = emptyList<String>()
        val resolver = YtDlpStreamResolver(listOf("yt-dlp"), startProcess = { args = it; process })
        val info = (resolver.resolve("track") as DhunResult.Success).value
        assertEquals("https://media.example/audio", info.audioUrl)
        assertEquals(YtDlpStreamResolver.YTDLP_USER_AGENT, info.userAgent)
        assertTrue("--ignore-config" in args)
        assertEquals("bestaudio/best", args[args.indexOf("-f") + 1])
        assertTrue(process.disposed)
        assertEquals(1, process.destroyCalls, "inner and outer cleanup must share one disposal")
    }

    @Test
    fun drainsBothPipesBeforeWaitingForExitAndBoundsRetainedOutput() = runBlocking {
        // Simulates a child blocked by full output pipes: it can only exit
        // AFTER both readers consume EOF. waitFor-before-read times out.
        val process = TestProcess(
            stdout = "progress\n".repeat(10_000) + "https://media.example/audio\n",
            stderr = "warning\n".repeat(10_000),
            waitForDrain = true,
        )
        val resolver = YtDlpStreamResolver(listOf("yt-dlp"), startProcess = { process }, timeoutSeconds = 1)
        val result = withTimeout(5_000) { resolver.resolve("track") }
        assertTrue(result is DhunResult.Success)
        assertEquals("https://media.example/audio", result.value.audioUrl)
    }

    @Test
    fun cancellationKillsTheProcessInsteadOfWaitingForItsSixtySecondTimeout() = runBlocking {
        val process = TestProcess(running = true)
        val started = CompletableDeferred<Unit>()
        val resolver = YtDlpStreamResolver(listOf("yt-dlp"), startProcess = { started.complete(Unit); process })
        val task = async { resolver.resolve("track") }
        withTimeout(5_000) { started.await() }
        withTimeout(5_000) { task.cancelAndJoin() }
        assertTrue(task.isCancelled)
        assertTrue(process.disposed)
        assertEquals(1, process.destroyCalls, "inner and outer cleanup must share one disposal")
        assertFalse(process.isAlive())
    }

    @Test
    fun deadlineIncludesOutputDrainAfterTheChildHasExited() = runBlocking {
        // Child exit alone is not EOF: launchers can leave a descendant with
        // the output handle open. This stream unblocks only during cleanup.
        val pipe = AwaitCloseInputStream()
        val process = TestProcess(outputOverride = pipe)
        val resolver = YtDlpStreamResolver(listOf("yt-dlp"), startProcess = { process }, timeoutSeconds = 1)
        val result = withTimeout(5_000) { resolver.resolve("track") }
        assertTrue(result is DhunResult.Failure)
        assertTrue(result.error is DhunError.Network)
        assertTrue(result.error.detailString().orEmpty().contains("process/output timeout"))
        assertTrue(pipe.wasClosed)
        assertTrue(process.disposed)
        assertEquals(1, process.destroyCalls, "inner and outer cleanup must share one disposal")
    }

    @Test
    fun startPermissionFailureIsTypedAndDoesNotClaimTheNetworkIsOffline() = runBlocking {
        val resolver = YtDlpStreamResolver(listOf("yt-dlp"), startProcess = { throw SecurityException("denied") })
        val result = resolver.resolve("track") as DhunResult.Failure
        assertTrue(result.error is DhunError.Unknown)
        assertTrue(result.error.detailString().orEmpty().contains("denied permission"))
    }

    @Test
    fun aTrackIdContaining429IsNotAnHttpRateLimit() {
        val error = classifyYtDlpFailure("ERROR: [youtube] abc429xyz: Requested format is not available")
        assertFalse(error is DhunError.RateLimited)
        assertTrue(classifyYtDlpFailure("ERROR: HTTP 429") is DhunError.RateLimited)
    }

    @Test
    fun invalidProcessConfigurationIsRejected() {
        assertFailsWith<IllegalArgumentException> { YtDlpStreamResolver(emptyList()) }
        assertFailsWith<IllegalArgumentException> { YtDlpStreamResolver(listOf("yt-dlp"), timeoutSeconds = 0) }
    }

    @Test
    fun failureClassificationKeepsTheReasonAndRedactsStreamUrls() {
        val error = classifyYtDlpFailure("ERROR: Video unavailable: https://media.example/stream?signature=SECRET")
        assertTrue(error is DhunError.Unavailable)
        assertTrue(error.detailString().orEmpty().contains("Video unavailable"))
        assertFalse(error.detailString().orEmpty().contains("SECRET"))
        assertTrue(classifyYtDlpFailure("ERROR: HTTP Error 429: Too Many Requests") is DhunError.RateLimited)
        assertTrue(classifyYtDlpFailure("python: No module named yt_dlp").detailString().orEmpty().contains("Install yt-dlp"))
    }

    private class TestProcess(
        stdout: String = "",
        stderr: String = "",
        running: Boolean = false,
        waitForDrain: Boolean = false,
        outputOverride: InputStream? = null,
    ) : Process() {
        private val ended = CountDownLatch(if (running) 1 else 0)
        private val drained = CountDownLatch(if (waitForDrain) 2 else 0)
        private fun pipe(text: String) = object : ByteArrayInputStream(text.toByteArray()) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, length).also { if (it < 0) drained.countDown() }
        }
        private val out = outputOverride ?: pipe(stdout)
        private val err = pipe(stderr)
        private val input = ByteArrayOutputStream()
        @Volatile var disposed = false
        @Volatile var destroyCalls = 0
        override fun getInputStream() = out
        override fun getErrorStream() = err
        override fun getOutputStream() = input
        override fun waitFor(): Int { ended.await(); drained.await(); return 0 }
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean =
            ended.await(timeout, unit) && drained.await(timeout, unit)
        override fun exitValue(): Int = if (isAlive()) throw IllegalThreadStateException() else 0
        override fun isAlive(): Boolean = ended.count != 0L
        override fun destroy() { destroyCalls++; disposed = true; ended.countDown() }
    }

    private class AwaitCloseInputStream : InputStream() {
        private val closed = CountDownLatch(1)
        @Volatile var wasClosed = false
        override fun read(): Int { closed.await(); return -1 }
        override fun close() { wasClosed = true; closed.countDown() }
    }

}
