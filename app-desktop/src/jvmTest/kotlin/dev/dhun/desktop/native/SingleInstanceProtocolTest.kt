package dev.dhun.desktop.native

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S4.2: the single-instance line protocol, exercised against a real [SingleInstance.Lease]
 * on an ephemeral loopback port — the first test coverage the guard has ever had.
 *
 * Deliberately *not* covered: [SingleInstance.start] itself, which binds the real
 * rendezvous port (24601+spread) and could signal a developer's running DHUN or
 * collide with a parallel test worker. The lease, the parser, and the command
 * mapping are the units under test; `Main`'s wiring of them is reviewed, not executed.
 */
class SingleInstanceProtocolTest {

    private class Harness {
        val received: MutableList<SingleInstance.RemoteCommand> =
            Collections.synchronizedList(mutableListOf())
        val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
        val lease = SingleInstance.Lease(server, "TOKEN", ::onCommand) { }

        private fun onCommand(command: SingleInstance.RemoteCommand) {
            received += command
        }

        fun begin() = lease.begin()

        fun close() = lease.close()

        fun awaitCommand(timeoutMs: Long = 5_000): SingleInstance.RemoteCommand? {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                received.firstOrNull()?.let { return it }
                Thread.sleep(10)
            }
            return received.firstOrNull()
        }
    }

    private fun Harness.exchange(command: String, useToken: String = "TOKEN"): Boolean {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), server.localPort), 2_000)
            socket.soTimeout = 2_000
            val input: InputStream = socket.getInputStream()
            val output: OutputStream = socket.getOutputStream()
            SingleInstance.exchange(input, output, command, useToken)
        } finally {
            runCatching { socket.close() }
        }
    }

    @Test
    fun `ping replies ok without invoking the callback`() {
        val h = Harness()
        h.begin()
        try {
            assertTrue(h.exchange("PING"))
            Thread.sleep(100)
            assertTrue(h.received.isEmpty())
        } finally {
            h.close()
        }
    }

    @Test
    fun `show replies ok and surfaces`() {
        val h = Harness()
        h.begin()
        try {
            assertTrue(h.exchange("SHOW"))
            assertEquals(SingleInstance.RemoteCommand.Show, h.awaitCommand())
        } finally {
            h.close()
        }
    }

    @Test
    fun `playpause replies ok and toggles without surfacing`() {
        val h = Harness()
        h.begin()
        try {
            assertTrue(h.exchange("PLAYPAUSE"))
            assertEquals(SingleInstance.RemoteCommand.PlayPause, h.awaitCommand())
        } finally {
            h.close()
        }
    }

    @Test
    fun `unknown commands are refused and invoke nothing`() {
        val h = Harness()
        h.begin()
        try {
            assertFalse(h.exchange("PLAY")) // the wire has no per-track verb (S4.2 scope)
            assertFalse(h.exchange("RM -RF"))
            Thread.sleep(100)
            assertTrue(h.received.isEmpty())
        } finally {
            h.close()
        }
    }

    @Test
    fun `a foreign identity token is refused and invokes nothing`() {
        val h = Harness()
        h.begin()
        try {
            assertFalse(h.exchange("SHOW", useToken = "someone-elses-token"))
            assertFalse(h.exchange("PLAYPAUSE", useToken = "someone-elses-token"))
            Thread.sleep(100)
            assertTrue(h.received.isEmpty())
        } finally {
            h.close()
        }
    }

    @Test
    fun `parseRequest accepts the protocol and rejects everything else`() {
        assertEquals(
            SingleInstance.Request("PING", "abc"),
            SingleInstance.parseRequest("DHUN1 PING abc"),
        )
        // Commands are case-insensitive on the wire; tokens are not.
        assertEquals(
            SingleInstance.Request("PLAYPAUSE", "MiXeD"),
            SingleInstance.parseRequest("DHUN1 playpause MiXeD"),
        )
        assertNull(SingleInstance.parseRequest("HTTP/1.1 GET /"))
        assertNull(SingleInstance.parseRequest(""))
        assertNull(SingleInstance.parseRequest("DHUN1"))
    }

    @Test
    fun `wireCommand maps every request with no fallback`() {
        assertEquals("SHOW", SingleInstance.wireCommand(SingleInstance.RemoteCommand.Show))
        assertEquals("PLAYPAUSE", SingleInstance.wireCommand(SingleInstance.RemoteCommand.PlayPause))
        assertEquals("PLAYPAUSE", SingleInstance.CMD_PLAY_PAUSE)
    }
}
