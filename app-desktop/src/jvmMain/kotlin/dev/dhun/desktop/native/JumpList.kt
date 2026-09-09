package dev.dhun.desktop.native

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Candidate 27 — Windows taskbar jump list over base JNA + ole32 (no new
 * dependency: `net.java.dev.jna:jna` is already on the desktop classpath for
 * the SMTC path). Writes ONE kind of list: an `ICustomDestinationList`
 * user-tasks batch (recent tracks + separator + Play/Pause + Open), which is
 * the only jump-list surface that needs no AUMID registration and no custom
 * category registry key — deliberately, because both would require identity
 * control the paths this batch owns do not have.
 *
 * ## ABI references (all verified 2026-09-09 against the Windows SDK IDL and
 * at least two independent implementations before any code was written)
 *
 *  - CLSID_DestinationList {77F10CF0-3DB5-4966-B520-B7C54FD35ED6},
 *    IID_ICustomDestinationList {6332DEBF-87B5-4670-90C0-5E57B408A49E};
 *    vtable: SetAppID=3 BeginList=4 AppendCategory=5 AppendKnownCategory=6
 *    AddUserTasks=7 CommitList=8 (SDK `shobjidl_core.h` MIDL declaration).
 *  - CLSID_EnumerableObjectCollection {2D3468C1-36A7-43B6-AC24-D3F02FD9607A},
 *    IID_IObjectCollection {5632B1A4-E38A-400A-928A-D4CD63230295},
 *    IID_IObjectArray {92CA9DCD-5622-4BBA-A805-5E9F541BD8C9}.
 *  - CLSID_ShellLink {00021401-…}, IID_IShellLinkW {000214F9-…}; vtable:
 *    GetPath=3 SetPath=4 … SetDescription=8 … SetArguments=12 …
 *    SetIconLocation=18 … Resolve=20.
 *  - IID_IPropertyStore {886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99}; vtable:
 *    GetCount=3 GetAt=4 GetValue=5 SetValue=6 Commit=7. A task's visible
 *    label is the shell property PKEY_Title ({F29F85E0-4FF9-1068-AB91-…},
 *    pid 2) — `IShellLink` has no title setter. A separator is a link whose
 *    PKEY_AppUserModel_IsDestListSeparator ({9F4C2855-…}, pid 6) is VT_BOOL
 *    true.
 *  - PROPVARIANT crosses the ABI BY POINTER (`REFPROPVARIANT`); only vt@0 and
 *    the union at offset 8 are exercised (VT_LPWSTR pointer / VT_BOOL short).
 *
 * The COM call mechanism (`vtableCall`, GUID construction, guarded QI and
 * Release) mirrors `Smct.kt` — the one native-interop pattern this codebase
 * has already committed to.
 *
 * ## Behavior contract
 *
 *  - **Windows-only, fail-open off-OS**: [start] returns false on non-Windows
 *    before any library load; on Windows every native step is guarded and the
 *    FIRST failure disables the jump list permanently with one log line
 *    (retry storms are worse than a missing task list).
 *  - **Packaged-only**: task targets resolve from `jpackage.app-path`
 *    (already logged by the desktop entry point). Unpackaged dev runs would
 *    otherwise pin tasks to the JVM launcher executable, so the jump list
 *    stays off instead.
 *  - **No SetAppID**: the list is written for the process's default shell
 *    identity — the same identity the taskbar derives for the pinned/running
 *    app. If jpackage's shortcut identity ever diverges on real hardware,
 *    the symptom is "no jump list", never a crash (honest hardware gate).
 *  - **Tasks-only, so BeginList's removed-destinations array is released
 *    unread**: user removals apply to *destinations*, and we write none.
 *  - **Throttled + coalesced**: at most one commit per [MIN_INTERVAL_MS];
 *    requests arriving inside the window replace the pending batch (latest
 *    wins), so fast track changes collapse into one shell write.
 *  - `-Ddhun.jump-list=false` disables, matching the `-Ddhun.smct` /
 *    `-Ddhun.single-instance` convention.
 *  - The worker is a single daemon thread with its own COM apartment
 *    (`CoInitializeEx` STA, never uninitialized — process-lifetime thread),
 *    so the EDT never blocks on the shell and AWT's COM state is untouched.
 *  - The list is NOT removed on quit: jump lists are user-facing shell state
 *    by design and must survive the process (the taskbar reads them even
 *    while the app is closed).
 */
class JumpList(private val log: (String) -> Unit = {}) {

    companion object {

        /** System property flag; `-Ddhun.jump-list=false` disables the feature. */
        const val FLAG = "dhun.jump-list"

        val isWindows: Boolean =
            System.getProperty("os.name", "").lowercase().contains("windows")

        /** One shell commit per window; everything inside coalesces. */
        const val MIN_INTERVAL_MS = 1_500L

        // ---- GUIDs (verified; see class KDoc) -----------------------------
        internal const val CLSID_DESTINATION_LIST = "77f10cf0-3db5-4966-b520-b7c54fd35ed6"
        internal const val IID_ICUSTOM_DESTINATION_LIST = "6332debf-87b5-4670-90c0-5e57b408a49e"
        internal const val CLSID_ENUMERABLE_OBJECT_COLLECTION = "2d3468c1-36a7-43b6-ac24-d3f02fd9607a"
        internal const val IID_IOBJECT_COLLECTION = "5632b1a4-e38a-400a-928a-d4cd63230295"
        internal const val IID_IOBJECT_ARRAY = "92ca9dcd-5622-4bba-a805-5e9f541bd8c9"
        internal const val CLSID_SHELL_LINK = "00021401-0000-0000-c000-000000000046"
        internal const val IID_ISHELL_LINK_W = "000214f9-0000-0000-c000-000000000046"
        internal const val IID_IPROPERTY_STORE = "886d8eeb-8cf2-4446-8d02-cdba1dbdcf99"
        internal const val FMTID_TITLE = "f29f85e0-4ff9-1068-ab91-08002b27b3d9"
        internal const val PID_TITLE = 2
        internal const val FMTID_APPUSER_MODEL = "9f4c2855-9f79-4b39-a8d0-e1d42de1d5f3"
        internal const val PID_IS_DEST_LIST_SEPARATOR = 6

        // ---- vtable slots (verified; see class KDoc) ----------------------
        internal const val SLOT_QUERY_INTERFACE = 0
        internal const val SLOT_RELEASE = 2
        internal const val SLOT_COLL_ADD_OBJECT = 5
        internal const val SLOT_CDL_BEGIN_LIST = 4
        internal const val SLOT_CDL_ADD_USER_TASKS = 7
        internal const val SLOT_CDL_COMMIT_LIST = 8
        internal const val SLOT_LINK_SET_PATH = 4
        internal const val SLOT_LINK_SET_DESCRIPTION = 8
        internal const val SLOT_LINK_SET_ARGUMENTS = 12
        internal const val SLOT_LINK_SET_ICON_LOCATION = 18
        internal const val SLOT_STORE_SET_VALUE = 6
        internal const val SLOT_STORE_COMMIT = 7

        private const val S_OK = 0
        private const val S_FALSE = 1

        /** COM apartment result that still means "usable". */
        private val RPC_E_CHANGED_MODE: Int = 0x80010106.toInt()

        private const val CLSCTX_INPROC_SERVER = 1
        private const val COINIT_APARTMENTTHREADED = 0x2
        private const val COINIT_DISABLE_OLE1DDE = 0x4
        private const val VT_LPWSTR: Short = 31
        private const val VT_BOOL: Short = 11
        private const val VARIANT_TRUE: Short = -1

        /**
         * The task target. `jpackage.app-path` is the packaged exe; without
         * it (gradle run / bare java) tasks would target the JVM launcher —
         * so the feature stays off. Matches the property the desktop entry
         * point already logs at startup.
         */
        internal fun resolveExePath(): String? =
            System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

        /** Pure coalescing decision: delay before the next commit may run. */
        internal fun nextDelayMs(nowMs: Long, lastRunMs: Long, intervalMs: Long): Long =
            (intervalMs - (nowMs - lastRunMs)).coerceIn(0, intervalMs)
    }

    private val disabled = AtomicBoolean(false)
    private val lock = Any()
    private var pending: List<JumpListEntry>? = null
    private var lastRunAt = 0L
    private var executor: ScheduledExecutorService? = null
    private var trailingQueued = false
    @Volatile
    private var lastCommitted: List<JumpListEntry>? = null

    /**
     * Gates and arms the feature. Idempotent. False (with a log line) on
     * non-Windows, when disabled by flag, or when unpackaged — in every case
     * the app continues without a jump list.
     */
    fun start(): Boolean {
        if (!isWindows) {
            log("jump list: skipped (not Windows)")
            return false
        }
        if (System.getProperty(FLAG, "true") == "false") {
            log("jump list: disabled (-$FLAG=false)")
            return false
        }
        if (resolveExePath() == null) {
            log("jump list: disabled (not a packaged install — no jpackage.app-path)")
            return false
        }
        synchronized(lock) {
            if (executor == null) {
                lastRunAt = System.currentTimeMillis() - MIN_INTERVAL_MS
                executor = Executors.newSingleThreadScheduledExecutor { r ->
                    Thread(r, "dhun-jumplist").apply { isDaemon = true }
                }
            }
        }
        log("jump list: enabled")
        return true
    }

    /**
     * Requests a rewrite with the given task list; safe from any thread.
     * Runs immediately if the throttle window has elapsed, otherwise queues a
     * single trailing run. Identical content is dropped (the shell already
     * shows it).
     */
    fun update(entries: List<JumpListEntry>) {
        if (disabled.get() || executor == null) return
        if (entries == lastCommitted) return
        val delayMs: Long
        synchronized(lock) {
            pending = entries
            delayMs = nextDelayMs(System.currentTimeMillis(), lastRunAt, MIN_INTERVAL_MS)
        }
        if (delayMs == 0L) {
            drain()
        } else {
            val queue = synchronized(lock) {
                if (trailingQueued) false else { trailingQueued = true; true }
            }
            if (queue) {
                executor?.schedule({
                    synchronized(lock) { trailingQueued = false }
                    drain()
                }, delayMs, TimeUnit.MILLISECONDS)
            }
        }
    }

    /** Runs on the worker; takes the latest pending batch and commits it. */
    private fun drain() {
        val entries = synchronized(lock) {
            pending.also { pending = null }
        } ?: return
        lastCommitted = entries
        val startedAt = System.currentTimeMillis()
        synchronized(lock) { lastRunAt = startedAt }
        runCatching { commit(entries) }.onFailure { t ->
            disable("native call trapped: $t")
        }
    }

    private fun disable(reason: String) {
        if (disabled.compareAndSet(false, true)) {
            log("jump list: disabled ($reason)")
        }
    }

    /* ---------------- COM plumbing (mirrors Smct.kt's pattern) ---------- */

    /** Win32 GUID (4+2+2+8 bytes; first three fields little-endian in memory). */
    internal class ComGuid : Structure() {
        @JvmField var data1: Int = 0
        @JvmField var data2: Short = 0
        @JvmField var data3: Short = 0
        @JvmField var data4: ByteArray = ByteArray(8)

        override fun getFieldOrder(): List<String> = listOf("data1", "data2", "data3", "data4")
    }

    /** PROPERTYKEY = GUID fmtid + DWORD pid. */
    internal class PropKey : Structure() {
        @JvmField var data1: Int = 0
        @JvmField var data2: Short = 0
        @JvmField var data3: Short = 0
        @JvmField var data4: ByteArray = ByteArray(8)
        @JvmField var pid: Int = 0

        override fun getFieldOrder(): List<String> = listOf("data1", "data2", "data3", "data4", "pid")
    }

    /**
     * PROPVARIANT heads: vt + 3 reserved words, union at offset 8. The
     * structure is passed by pointer (REFPROPVARIANT), so only the fields the
     * callee reads must exist.
     */
    internal class PropVariantString : Structure() {
        @JvmField var vt: Short = 0
        @JvmField var r1: Short = 0
        @JvmField var r2: Short = 0
        @JvmField var r3: Short = 0
        @JvmField var pwszVal: Pointer? = null

        override fun getFieldOrder(): List<String> = listOf("vt", "r1", "r2", "r3", "pwszVal")
    }

    internal class PropVariantBool : Structure() {
        @JvmField var vt: Short = 0
        @JvmField var r1: Short = 0
        @JvmField var r2: Short = 0
        @JvmField var r3: Short = 0
        @JvmField var boolVal: Short = 0

        override fun getFieldOrder(): List<String> = listOf("vt", "r1", "r2", "r3", "boolVal")
    }

    /** Minimal ole32 surface (base JNA; no jna-platform). */
    private interface Ole32Lib : Library {
        fun CoInitializeEx(pvReserved: Pointer?, dwCoInit: Int): Int
        fun CoCreateInstance(
            clsid: ComGuid?,
            pUnkOuter: Pointer?,
            dwClsContext: Int,
            riid: ComGuid?,
            ppv: Pointer?,
        ): Int

        fun CoTaskMemFree(pv: Pointer?)
    }

    private val ole32 by lazy {
        Native.load("ole32", Ole32Lib::class.java)
    }
    private val comInitialized = AtomicBoolean(false)

    /** STA apartment for this worker thread's lifetime; never uninitialized. */
    private fun ensureCom(): Boolean {
        if (comInitialized.get()) return true
        val hr = ole32.CoInitializeEx(null, COINIT_APARTMENTTHREADED or COINIT_DISABLE_OLE1DDE)
        val usable = hr == S_OK || hr == S_FALSE || hr == RPC_E_CHANGED_MODE
        if (usable) comInitialized.set(true) else disable("CoInitializeEx HRESULT=${hrHex(hr)}")
        return usable
    }

    private fun guidFrom(iid: String): ComGuid {
        val u = java.util.UUID.fromString(iid)
        val b = ByteArray(16)
        val hi = u.mostSignificantBits
        val lo = u.leastSignificantBits
        for (i in 0 until 8) {
            b[i] = ((hi ushr ((7 - i) * 8)) and 0xFF).toByte()
            b[8 + i] = ((lo ushr ((7 - i) * 8)) and 0xFF).toByte()
        }
        val g = ComGuid()
        g.data1 = ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
            ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
        g.data2 = (((b[4].toInt() and 0xFF) shl 8) or (b[5].toInt() and 0xFF)).toShort()
        g.data3 = (((b[6].toInt() and 0xFF) shl 8) or (b[7].toInt() and 0xFF)).toShort()
        g.data4 = b.copyOfRange(8, 16)
        return g
    }

    private fun propKey(fmtid: String, pid: Int): PropKey {
        val g = guidFrom(fmtid)
        val k = PropKey()
        k.data1 = g.data1
        k.data2 = g.data2
        k.data3 = g.data3
        k.data4 = g.data4
        k.pid = pid
        return k
    }

    /** Calls vtable slot [slot] of a COM object (`this` first, as the ABI wants). */
    private fun vtableCall(obj: Pointer, slot: Int, vararg args: Any?): Int {
        val vtbl = obj.getPointer(0) ?: error("null vtable")
        val fnPtr = vtbl.getPointer((slot * Native.POINTER_SIZE).toLong())
        val fn = Function.getFunction(fnPtr)
        val all = arrayOfNulls<Any>(args.size + 1)
        all[0] = obj
        System.arraycopy(args, 0, all, 1, args.size)
        return fn.invokeInt(all)
    }

    private fun queryInterface(obj: Pointer, iid: String): Pointer? {
        val out = pointerOut()
        val hr = runCatching { vtableCall(obj, SLOT_QUERY_INTERFACE, guidFrom(iid), out) }
            .getOrDefault(-1)
        return out.getPointer(0)?.takeIf { hr == S_OK }
    }

    private fun release(obj: Pointer?) {
        if (obj != null) runCatching { vtableCall(obj, SLOT_RELEASE) }
    }

    private fun pointerOut(): Memory = Memory(Native.POINTER_SIZE.toLong())

    /**
     * Native UTF-16 string buffer for PROPVARIANT.pwszVal (JNA `Memory`
     * writes `char[]` at the platform wchar width — 2 bytes on Windows).
     */
    private fun wideStringPointer(s: String): Memory {
        val m = Memory((s.length + 1) * 2L)
        m.write(0, s.toCharArray(), 0, s.length)
        m.setChar(s.length * 2L, '\u0000')
        return m
    }

    private fun hrHex(hr: Int): String = "0x%08X".format(hr)

    /* ---------------- the actual commit --------------------------------- */

    /** One BeginList → AddUserTasks → CommitList transaction. */
    private fun commit(entries: List<JumpListEntry>) {
        if (!ensureCom()) return
        val exe = resolveExePath() ?: run { disable("exe path vanished"); return }

        val listOut = pointerOut()
        val hrList = ole32.CoCreateInstance(
            guidFrom(CLSID_DESTINATION_LIST), null, CLSCTX_INPROC_SERVER,
            guidFrom(IID_ICUSTOM_DESTINATION_LIST), listOut,
        )
        val list = listOut.getPointer(0)
        if (hrList != S_OK || list == null) {
            disable("CoCreateInstance(DestinationList) HRESULT=${hrHex(hrList)}")
            return
        }
        try {
            val removedOut = pointerOut()
            val minSlots = Memory(8)
            val hrBegin = runCatching {
                vtableCall(list, SLOT_CDL_BEGIN_LIST, minSlots, guidFrom(IID_IOBJECT_ARRAY), removedOut)
            }.getOrDefault(-1)
            if (hrBegin != S_OK) {
                disable("BeginList HRESULT=${hrHex(hrBegin)}")
                return
            }
            // Tasks-only list: the removed array carries user-removed
            // *destinations*, which do not constrain user tasks. Release it
            // unread and say so in the KDoc. Only touched after a successful
            // BeginList — never Release a pointer a failed call left behind.
            release(removedOut.getPointer(0))

            val collOut = pointerOut()
            val hrColl = ole32.CoCreateInstance(
                guidFrom(CLSID_ENUMERABLE_OBJECT_COLLECTION), null, CLSCTX_INPROC_SERVER,
                guidFrom(IID_IOBJECT_COLLECTION), collOut,
            )
            val coll = collOut.getPointer(0)
            if (hrColl != S_OK || coll == null) {
                disable("CoCreateInstance(EnumerableObjectCollection) HRESULT=${hrHex(hrColl)}")
                return
            }
            try {
                var built = 0
                for (entry in entries) {
                    val link = buildTaskLink(exe, entry) ?: run {
                        disable("task link '${entry.title}' failed")
                        return
                    }
                    val hrAdd = runCatching { vtableCall(coll, SLOT_COLL_ADD_OBJECT, link) }.getOrDefault(-1)
                    release(link)
                    if (hrAdd != S_OK) {
                        disable("AddObject HRESULT=${hrHex(hrAdd)}")
                        return
                    }
                    built++
                }

                val tasksOut = pointerOut()
                val hrTasks = runCatching {
                    vtableCall(coll, SLOT_QUERY_INTERFACE, guidFrom(IID_IOBJECT_ARRAY), tasksOut)
                }.getOrDefault(-1)
                val tasks = tasksOut.getPointer(0)
                if (hrTasks != S_OK || tasks == null) {
                    disable("collection→IObjectArray HRESULT=${hrHex(hrTasks)}")
                    return
                }
                try {
                    val hrAddTasks = runCatching {
                        vtableCall(list, SLOT_CDL_ADD_USER_TASKS, tasks)
                    }.getOrDefault(-1)
                    if (hrAddTasks != S_OK) {
                        disable("AddUserTasks HRESULT=${hrHex(hrAddTasks)}")
                        return
                    }
                    val hrCommit = runCatching {
                        vtableCall(list, SLOT_CDL_COMMIT_LIST)
                    }.getOrDefault(-1)
                    if (hrCommit != S_OK) {
                        disable("CommitList HRESULT=${hrHex(hrCommit)}")
                        return
                    }
                    log("jump list: committed $built task(s)")
                } finally {
                    release(tasks)
                }
            } finally {
                release(coll)
            }
        } finally {
            release(list)
        }
    }

    /** One IShellLink for a task row (or a separator row). */
    private fun buildTaskLink(exe: String, entry: JumpListEntry): Pointer? {
        val linkOut = pointerOut()
        val hrLink = ole32.CoCreateInstance(
            guidFrom(CLSID_SHELL_LINK), null, CLSCTX_INPROC_SERVER,
            guidFrom(IID_ISHELL_LINK_W), linkOut,
        )
        val link = linkOut.getPointer(0)
        if (hrLink != S_OK || link == null) return null
        try {
            if (runCatching { vtableCall(link, SLOT_LINK_SET_PATH, WString(exe)) }.getOrDefault(-1) != S_OK) {
                release(link)
                return null
            }
            if (entry.isSeparator) {
                val store = queryInterface(link, IID_IPROPERTY_STORE) ?: run { release(link); return null }
                try {
                    val pv = PropVariantBool()
                    pv.vt = VT_BOOL
                    pv.boolVal = VARIANT_TRUE
                    val hrSet = runCatching {
                        vtableCall(store, SLOT_STORE_SET_VALUE, propKey(FMTID_APPUSER_MODEL, PID_IS_DEST_LIST_SEPARATOR), pv)
                    }.getOrDefault(-1)
                    val hrCommit = if (hrSet == S_OK) {
                        runCatching { vtableCall(store, SLOT_STORE_COMMIT) }.getOrDefault(-1)
                    } else hrSet
                    // release(store) is owned by the finally below — never also here.
                    if (hrCommit != S_OK) { release(link); return null }
                } finally {
                    release(store)
                }
            } else {
                if (runCatching { vtableCall(link, SLOT_LINK_SET_ARGUMENTS, WString(entry.arguments)) }
                        .getOrDefault(-1) != S_OK) { release(link); return null }
                if (runCatching { vtableCall(link, SLOT_LINK_SET_ICON_LOCATION, WString(exe), 0) }
                        .getOrDefault(-1) != S_OK) { release(link); return null }
                if (runCatching { vtableCall(link, SLOT_LINK_SET_DESCRIPTION, WString(entry.title)) }
                        .getOrDefault(-1) != S_OK) { release(link); return null }
                // The popup label is PKEY_Title on the link's property store.
                val store = queryInterface(link, IID_IPROPERTY_STORE) ?: run { release(link); return null }
                try {
                    val value = wideStringPointer(entry.title)
                    val pv = PropVariantString()
                    pv.vt = VT_LPWSTR
                    pv.pwszVal = value
                    val hrSet = runCatching {
                        vtableCall(store, SLOT_STORE_SET_VALUE, propKey(FMTID_TITLE, PID_TITLE), pv)
                    }.getOrDefault(-1)
                    val hrCommit = if (hrSet == S_OK) {
                        runCatching { vtableCall(store, SLOT_STORE_COMMIT) }.getOrDefault(-1)
                    } else hrSet
                    if (hrCommit != S_OK) { release(link); return null }
                } finally {
                    release(store)
                }
            }
            return link
        } catch (t: Throwable) {
            release(link)
            throw t
        }
    }
}
