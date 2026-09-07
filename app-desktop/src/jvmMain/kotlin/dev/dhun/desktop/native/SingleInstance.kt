package dev.dhun.desktop.native

import dev.dhun.data.DhunUserDirs
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 15a/27 — desktop single-instance guard.
 *
 * ## The gap this closes
 *
 * PR #28 removed the separate mini-player window and PR #34 removed every
 * `JOptionPane` path, so **within one process** DHUN can only ever own a single
 * window (audited in PR #38; `Main.kt`'s two `Window(` calls are mutually
 * exclusive). The path that audit could not cover is a **second process**:
 * double-clicking the shortcut twice, a pinned launch next to an autostart
 * entry, or an impatient relaunch while a slow cold start is still resolving
 * VLC and the SQLite driver. Each of those used to produce a *second* full DHUN
 * window, a *second* tray icon, a *second* SMTC session — and a second writer
 * on the same `userdata/dhun.db`, which one-connection SQLite is explicitly not
 * safe for (see the `DataLayer` `dbIo` contract in `shared`).
 *
 * ## How it works
 *
 * One loopback TCP port per (**user**, **data dir**) pair — the data dir is the
 * resource actually being protected, so two separate installs (each with their
 * own `userdata`) stay independent, and two OS users on one machine (fast user
 * switching / RDP) never see each other's instance:
 *
 *  - bind succeeds → this process is the **primary**. A daemon thread answers a
 *    tiny line protocol and, on `SHOW`, asks the caller's callback to surface
 *    the existing window.
 *  - bind fails → **verify before deferring**: connect and `PING` carrying this
 *    instance's identity token. Only a peer that answers `DHUN1 OK` to *that*
 *    token is treated as the live DHUN, in which case `SHOW` is sent and
 *    [Startup.SecondInstance] is returned so the caller can exit without
 *    creating *any* AWT, Compose, Koin or SQLite surface. A port held by an
 *    unrelated program — or by a *different* DHUN install/user whose derived
 *    port collided — answers `ERR` or nothing useful → **fail open**
 *    ([Startup.Unguarded]) and start normally, because "DHUN refuses to launch"
 *    is a strictly worse failure than "DHUN launched twice".
 *
 * The rendezvous point is a socket, not a lock file, so a crash, `kill -9` or a
 * power cut leaves nothing stale behind — the kernel reclaims the port with the
 * process. The trade-off is recorded honestly: if a foreign program squats the
 * port, the guard degrades to today's behavior instead of blocking startup.
 *
 * Bind semantics are OS-specific and handled explicitly: Windows gets an
 * *exclusive* bind (`reuseAddress = false` → `SO_EXCLUSIVEADDRUSE`), because
 * Windows' permissive `SO_REUSEADDR` would let the duplicate **hijack** the
 * port, consider itself primary, and open a second window after all. Linux and
 * macOS keep the JDK default, which cannot be hijacked without `SO_REUSEPORT`.
 *
 * ## Threading and safety
 *
 * Loopback only (`InetAddress.getLoopbackAddress()`); nothing is reachable from
 * the network. The accept loop runs on a **daemon** thread so it can never hold
 * the JVM open, and every per-connection read/write is bounded by
 * [READ_TIMEOUT_MS] so a stuck peer cannot wedge it. A peer may only cause the
 * existing window to be brought to front — it receives no track, queue or
 * settings data. This file is deliberately AWT-free and Compose-free: EDT
 * marshaling belongs to the caller ([dev.dhun.desktop.Main]), which keeps the
 * protocol independent of the window layer.
 *
 * Escape hatch, matching the existing `-Ddhun.smct=false` convention:
 * `java -Ddhun.single-instance=false` disables the guard (useful for
 * deliberately running two instances side by side while debugging).
 */
object SingleInstance {

    /** System property flag; `-Ddhun.single-instance=false` disables the guard. */
    const val FLAG = "dhun.single-instance"

    /**
     * Windows decides the bind semantics (see [start]), so the guard needs the
     * same OS probe the SMTC path uses — declared locally rather than read from
     * `Smct.isWindows` so single-instance never depends on that path.
     */
    internal val isWindows: Boolean =
        System.getProperty("os.name", "").lowercase().contains("windows")

    /** Line-protocol prefix. A peer that does not speak it is not DHUN. */
    internal const val PROTOCOL = "DHUN1"
    internal const val CMD_PING = "PING"
    internal const val CMD_SHOW = "SHOW"
    internal const val REPLY_OK = "DHUN1 OK"
    internal const val REPLY_ERR = "DHUN1 ERR"

    /**
     * First candidate port, and the reason it is **24601**:
     *  - above 1024, so no root/`net.ipv4.ip_unprivileged_port_start` problem;
     *  - below Linux's default `ip_local_port_range` (32768-60999) so the kernel
     *    never hands it to an *outbound* socket — DHUN opens many of those
     *    (InnerTube, artwork, stream fetch). An ephemeral collision makes the
     *    primary's bind fail and the guard silently degrade to fail-open, i.e.
     *    the duplicate window comes back at random;
     *  - below the Windows/macOS ephemeral start (49152) for the same reason.
     * Verified by simulation: an earlier candidate base of 47837 landed inside
     * the Linux ephemeral range and lost the port to a loopback client socket.
     */
    internal const val BASE_PORT = 24601

    /** Per-(user, data dir) spread, so coexisting users/installs do not collide. */
    internal const val PORT_SPREAD = 512

    internal const val BACKLOG = 4
    internal const val CONNECT_TIMEOUT_MS = 800
    internal const val READ_TIMEOUT_MS = 800
    internal const val MAX_LINE_BYTES = 64
    internal const val MAX_REQUESTS_PER_CONNECTION = 4
    internal const val ACCEPT_RETRY_SLEEP_MS = 50L

    /** What [start] decided, and therefore what the caller must do next. */
    sealed interface Startup {
        /**
         * This process owns the instance. Hold the [lease] for the process
         * lifetime and [Lease.close] it on the way out so the port is released
         * immediately for the next launch.
         */
        class Primary(val lease: Lease) : Startup

        /**
         * A live DHUN was found and asked to surface its window. The caller must
         * exit now — **before** touching AWT, Compose, Koin or the database — so
         * the second launch never produces a window of its own.
         */
        data object SecondInstance : Startup

        /**
         * The guard could not be established (disabled by [FLAG], or the port is
         * held by something that is not DHUN). The caller starts normally; this
         * is the old behavior, not a failure.
         */
        data class Unguarded(val reason: String) : Startup
    }

    /** Result of the "is a live DHUN already listening?" probe. */
    private sealed interface SignalResult {
        data object Signalled : SignalResult
        data class NotDhun(val detail: String) : SignalResult
    }

    /**
     * Decides this process's role. Call it as early as possible in `main()` —
     * before any window, DI or database work — so [Startup.SecondInstance] can
     * exit cheaply.
     *
     * @param onShow invoked on the guard's daemon thread when another launch asks
     *   this instance to surface. The caller owns EDT marshaling.
     */
    fun start(onShow: () -> Unit, log: (String) -> Unit = ::println): Startup {
        if (System.getProperty(FLAG, "true") == "false") {
            return Startup.Unguarded("$FLAG=false")
        }
        val key = instanceKey()
        val port = portFor(key)
        val token = identityToken(key)

        val server: ServerSocket? = try {
            val candidate = ServerSocket()
            try {
                // Windows: SO_REUSEADDR (the JDK default) lets a second process
                // HIJACK an already-bound port, which would silently defeat this
                // guard — the duplicate would bind, consider itself primary, and
                // open a second window after all. Binding with reuse disabled
                // maps to SO_EXCLUSIVEADDRUSE there, so the second bind fails and
                // the launcher correctly detects the running instance.
                // Linux/macOS keep the JDK default: they cannot hijack a
                // listening port without SO_REUSEPORT, and reuse avoids a
                // spurious bind failure over a lingering TIME_WAIT tuple.
                if (isWindows) candidate.reuseAddress = false
                candidate.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port), BACKLOG)
                candidate
            } catch (t: Throwable) {
                runCatching { candidate.close() }
                log("single-instance: cannot own 127.0.0.1:$port (${t::class.java.simpleName}: ${t.message}) — checking for a running instance")
                null
            }
        } catch (t: Throwable) {
            log("single-instance: cannot create a socket (${t::class.java.simpleName}: ${t.message}) — starting unguarded")
            null
        }
        if (server != null) {
            val lease = Lease(server, token, onShow, log)
            lease.begin()
            log("single-instance: primary — listening on 127.0.0.1:$port for \"$key\" (id $token)")
            return Startup.Primary(lease)
        }

        return when (val signal = signalRunningInstance(port, token)) {
            SignalResult.Signalled -> {
                log("single-instance: live DHUN on 127.0.0.1:$port was asked to surface — this process exits")
                Startup.SecondInstance
            }
            is SignalResult.NotDhun -> {
                log(
                    "single-instance: port $port is taken but not by DHUN (${signal.detail}) — " +
                        "starting unguarded (fail-open, the app must never refuse to launch)",
                )
                Startup.Unguarded(signal.detail)
            }
        }
    }

    /**
     * The primary's handle on the rendezvous port. [close] is idempotent and
     * safe from any thread; the accept thread is a daemon, so forgetting to
     * close it cannot keep the JVM alive.
     */
    class Lease internal constructor(
        private val server: ServerSocket,
        private val identityToken: String,
        private val onShow: () -> Unit,
        private val log: (String) -> Unit,
    ) {
        private val closed = AtomicBoolean(false)

        /**
         * Port this lease bound, captured at construction so it stays accurate
         * for diagnostics after [close] (a closed `ServerSocket` stops
         * reporting it).
         */
        val port: Int = runCatching { server.localPort }.getOrDefault(-1)

        internal fun begin() {
            val thread = Thread({ acceptLoop() }, "dhun-single-instance")
            thread.isDaemon = true
            thread.start()
        }

        private fun acceptLoop() {
            while (!closed.get()) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    if (closed.get()) return
                    // Transient accept failure: keep the rendezvous alive rather
                    // than silently becoming a deaf listener (a deaf listener
                    // would make the NEXT launch fail open and duplicate).
                    log("single-instance: accept failed (${e::class.java.simpleName}: ${e.message}) — retrying")
                    sleepQuietly(ACCEPT_RETRY_SLEEP_MS)
                    continue
                } catch (t: Throwable) {
                    if (closed.get()) return
                    log("single-instance: accept trapped (${t::class.java.simpleName}) — retrying")
                    sleepQuietly(ACCEPT_RETRY_SLEEP_MS)
                    continue
                }
                try {
                    handle(socket)
                } catch (t: Throwable) {
                    log("single-instance: connection trapped (${t::class.java.simpleName}: ${t.message})")
                } finally {
                    runCatching { socket.close() }
                }
            }
        }

        private fun handle(socket: Socket) {
            socket.soTimeout = READ_TIMEOUT_MS
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            var requests = 0
            while (requests < MAX_REQUESTS_PER_CONNECTION) {
                val line = readLine(input) ?: return
                requests++
                val request = parseRequest(line)
                when {
                    request == null -> {
                        writeLine(output, REPLY_ERR)
                        return
                    }
                    request.token != identityToken -> {
                        // A DIFFERENT DHUN instance (another OS user, or another
                        // install with its own dataDir) happens to own this port.
                        // Refuse, so that launch fails open and starts normally
                        // instead of exiting behind a window it can never see.
                        log("single-instance: rejected a request for id ${request.token} (this instance is $identityToken)")
                        writeLine(output, REPLY_ERR)
                        return
                    }
                    request.command == CMD_PING -> writeLine(output, REPLY_OK)
                    request.command == CMD_SHOW -> {
                        writeLine(output, REPLY_OK)
                        log("single-instance: another launch of THIS install asked to surface — reusing the tray \"Open DHUN\" focus path")
                        runCatching { onShow() }
                            .onFailure { log("single-instance: surface callback failed (${it::class.java.simpleName}: ${it.message})") }
                    }
                    else -> {
                        writeLine(output, REPLY_ERR)
                        return
                    }
                }
            }
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { server.close() }
                .onFailure { log("single-instance: release failed (${it::class.java.simpleName}: ${it.message})") }
            log("single-instance: lease released (port $port)")
        }

        private fun sleepQuietly(ms: Long) {
            try {
                Thread.sleep(ms)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Asks whatever owns [port] whether it is a live DHUN **for this same
     * install/user**, and if so tells it to surface. Two commands on one
     * connection: `PING` proves identity *before* `SHOW` commits this process to
     * exiting. Anything else — a foreign program, or a different DHUN instance
     * that happens to share the port — means fail open.
     */
    private fun signalRunningInstance(port: Int, token: String): SignalResult {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            when {
                !exchange(input, output, CMD_PING, token) ->
                    SignalResult.NotDhun("no matching $PROTOCOL reply to PING")
                !exchange(input, output, CMD_SHOW, token) ->
                    SignalResult.NotDhun("no matching $PROTOCOL reply to SHOW")
                else -> SignalResult.Signalled
            }
        } catch (t: Throwable) {
            SignalResult.NotDhun("${t::class.java.simpleName}: ${t.message}")
        } finally {
            runCatching { socket.close() }
        }
    }

    internal fun exchange(input: InputStream, output: OutputStream, command: String, token: String): Boolean {
        writeLine(output, "$PROTOCOL $command $token")
        return readLine(input) == REPLY_OK
    }

    internal fun writeLine(output: OutputStream, line: String) {
        output.write("$line\n".toByteArray(Charsets.UTF_8))
        output.flush()
    }

    /** Reads one `\n`-terminated line, bounded by [MAX_LINE_BYTES] and soTimeout. */
    internal fun readLine(input: InputStream): String? {
        val buffer = ByteArray(MAX_LINE_BYTES)
        var count = 0
        while (count < buffer.size) {
            val value = input.read()
            if (value < 0) return null
            if (value == '\n'.code) break
            buffer[count++] = value.toByte()
        }
        if (count == 0) return null
        return String(buffer, 0, count, Charsets.UTF_8).trim()
    }

    /** One parsed request line: `DHUN1 <COMMAND> <identityToken>`. */
    internal data class Request(val command: String, val token: String)

    /**
     * Parses `DHUN1 <COMMAND> <identityToken>`. Null when the peer does not
     * speak the protocol at all; a non-null [Request] still has to carry this
     * instance's [identityToken] before [Lease] serves it.
     */
    internal fun parseRequest(line: String): Request? {
        val prefix = "$PROTOCOL "
        if (!line.startsWith(prefix)) return null
        val parts = line.substring(prefix.length).trim().split(' ').filter { it.isNotEmpty() }
        val command = parts.getOrNull(0)?.uppercase().orEmpty()
        if (command.isEmpty()) return null
        return Request(command, parts.getOrNull(1).orEmpty())
    }

    /**
     * Identity of the instance being guarded: the user plus the data dir. The
     * data dir is what two processes must not share (one SQLite connection, one
     * audio cache, one tray identity), so it — not the executable — is the key.
     */
    internal fun instanceKey(
        user: String = System.getProperty("user.name").orEmpty(),
        dataDir: String = runCatching { DhunUserDirs.dataDir().absolutePath }
            .getOrElse { System.getProperty("user.home").orEmpty() },
    ): String = "$user|$dataDir"

    /**
     * Deterministic port for [key]. Same inputs → same port in every process,
     * which is what makes the rendezvous work without a lock file or a
     * discovery step. Spread over [PORT_SPREAD] ports to keep different
     * users/installs apart.
     */
    internal fun portFor(key: String): Int = BASE_PORT + positiveMod(stableHash(key), PORT_SPREAD)

    /**
     * FNV-1a 32-bit, written out rather than `String.hashCode` so the mapping is
     * explicit and stable across JVM versions, then avalanche-mixed by [mix].
     */
    internal fun stableHash(text: String): Int {
        var hash = 0x811C9DC5.toInt()
        for (char in text) {
            hash = hash xor char.code
            hash *= 0x01000193
        }
        return mix(hash)
    }

    /**
     * MurmurHash3 `fmix32`. FNV-1a's *low* bits avalanche poorly, and [portFor]
     * reads exactly those (mod 512): measured on 300 near-identical keys
     * (`userN|/home/userN/DHUN`) the unmixed hash hit only 141 of 512 slots, so
     * unrelated installs piled onto one port. Mixing first makes the spread
     * uniform. Collisions are then merely a wasted handshake — see
     * [identityToken], which is what actually keeps two different instances
     * from deferring to each other.
     */
    internal fun mix(value: Int): Int {
        var h = value
        h = h xor (h ushr 16)
        h *= 0x85EBCA6B.toInt()
        h = h xor (h ushr 13)
        h *= 0xC2B2AE35.toInt()
        h = h xor (h ushr 16)
        return h
    }

    /**
     * Proof-of-identity carried in every request line, so a port shared by
     * accident can never make one instance defer to a *different* one.
     *
     * Without it, a collision is not harmless: user B's launch would PING user
     * A's instance, be told "yes, DHUN is running", SHOW a window B cannot see,
     * and then exit — B's DHUN would silently never start. Two 32-bit halves
     * (the key and its reverse) make an accidental match ~1 in 4 billion while
     * keeping the token short enough for [MAX_LINE_BYTES] and leaking no path.
     */
    internal fun identityToken(key: String): String =
        hex8(stableHash(key).toLong() and 0xFFFFFFFFL) +
            hex8(stableHash(key.reversed()).toLong() and 0xFFFFFFFFL)

    /** Locale-independent 8-digit hex (unlike `String.format`, which is not). */
    private fun hex8(value: Long): String = value.toString(16).padStart(8, '0')

    private fun positiveMod(value: Int, modulus: Int): Int {
        val result = value % modulus
        return if (result < 0) result + modulus else result
    }
}
