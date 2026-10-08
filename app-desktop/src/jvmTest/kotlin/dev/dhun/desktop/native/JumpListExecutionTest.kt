package dev.dhun.desktop.native

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** Regression gate for COM apartment affinity: even the zero-delay path must
 * run on the dedicated worker, never on the caller (including the Swing EDT). */
class JumpListExecutionTest {

    @Test
    fun `immediate and throttled commits always use the dedicated worker`() {
        val commits = LinkedBlockingQueue<Pair<Thread, List<JumpListEntry>>>()
        val jumpList = JumpList(
            log = {},
            windowsPlatform = true,
            packagedExePath = { "C:\\Program Files\\DHUN\\DHUN.exe" },
            commitOverride = { entries -> commits.put(Thread.currentThread() to entries) },
        )

        try {
            assertTrue(jumpList.start())
            val caller = Thread.currentThread()
            val first = listOf(JumpListEntry("First", "--first"))
            val second = listOf(JumpListEntry("Second", "--second"))

            jumpList.update(first)
            val firstCommit = assertNotNull(commits.poll(5, TimeUnit.SECONDS), "immediate commit timed out")
            assertEquals(first, firstCommit.second)
            assertEquals("dhun-jumplist", firstCommit.first.name)
            assertNotSame(caller, firstCommit.first, "COM work must not run on the calling thread")

            // A follow-up inside the throttle window is delayed, but must still
            // use that same apartment worker when its trailing run is due.
            jumpList.update(second)
            val secondCommit = assertNotNull(commits.poll(5, TimeUnit.SECONDS), "trailing commit timed out")
            assertEquals(second, secondCommit.second)
            assertEquals("dhun-jumplist", secondCommit.first.name)
            assertNotSame(caller, secondCommit.first, "throttled COM work must not run on the caller")
        } finally {
            jumpList.stop()
        }
    }
}
