package dev.dhun.desktop.smct

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Windows System Media Transport Controls integration.
 *
 * This file intentionally uses only base JNA. The WinRT object model is
 * reached through the ABI exposed by combase.dll:
 *
 *   RoGetActivationFactory → ISystemMediaTransportControlsInterop →
 *   GetForWindow(HWND) → ISystemMediaTransportControls
 *
 * The phase-2 path updates the music display properties, registers the
 * ButtonPressed event, and maps Windows transport buttons back to DHUN. The
 * app remains safe when WinRT is unavailable: every native call is guarded,
 * and Main.kt keeps the AWT tray/keyboard path alive as the fallback.
 *
 * ABI references (checked 2026-09-05):
 * - Windows SDK ISystemMediaTransportControlsInterop:
 *   ddb0472d-c911-4a1f-86d9-dc3d71a95f5a
 * - Windows SDK ISystemMediaTransportControls:
 *   99fa3ff4-1742-42a6-902e-087d41f965ec
 * - windows-rs generated Windows.Media vtables, including the exact
 *   ButtonPressed, DisplayUpdater, and MusicProperties slot order
 * - SystemMediaTransportControlsButtonPressedEventHandler:
 *   0557e996-7b23-5bae-aa81-ea0d671143a4
 */
object Smct {

    /** System property flag; `java -Ddhun.smct=false` disables SMTC. */
    const val FLAG = "dhun.smct"

    val isWindows: Boolean =
        System.getProperty("os.name", "").lowercase().contains("windows")

    data class ProbeResult(val step: String, val ok: Boolean, val detail: String)

    /** Buttons exposed by Windows' [ISystemMediaTransportControls] event. */
    enum class Button {
        Play,
        Pause,
        Stop,
        Next,
        Previous,
        FastForward,
        Rewind,
        Record,
        ChannelUp,
        ChannelDown,
        Unknown,
    }

    /**
     * A retained SMTC object. Call [close] exactly once when the app exits.
     * The class is public because the desktop entry point owns its lifetime;
     * it does not expose raw ABI pointers to the rest of the application.
     */
    class Session internal constructor(
        private val winRt: WinRt,
        private val smtc: Pointer,
        private val log: (String) -> Unit,
    ) {
        private var displayUpdater: Pointer? = null
        private var musicProperties: Pointer? = null
        private var musicProperties2: Pointer? = null
        private var buttonHandler: ComButtonHandler? = null
        private var buttonToken: Long? = null
        private var closed = false

        /** Initializes metadata and optional hardware-button handling. */
        internal fun initialize(onButton: ((Button) -> Unit)?): Boolean {
            val updaterOut = pointerOut()
            val hrUpdater = vtableCall(smtc, SLOT_SMTC_DISPLAY_UPDATER, updaterOut)
            val updater = updaterOut.getPointer(0)
            if (hrUpdater != S_OK || updater == null) {
                log("SMTC phase 2: DisplayUpdater failed HRESULT=${hrHex(hrUpdater)}")
                return false
            }
            displayUpdater = updater

            val musicOut = pointerOut()
            val hrMusic = vtableCall(updater, SLOT_DISPLAY_MUSIC_PROPERTIES, musicOut)
            val music = musicOut.getPointer(0)
            if (hrMusic != S_OK || music == null) {
                log("SMTC phase 2: MusicProperties failed HRESULT=${hrHex(hrMusic)}")
                return false
            }
            musicProperties = music
            musicProperties2 = queryInterface(music, IID_MUSIC_PROPERTIES2)

            // Enable the controls that DHUN can service. Individual property
            // failures are non-fatal on older Windows builds; metadata and
            // ButtonPressed are the phase-2 readiness gate.
            setBoolean(SLOT_SMTC_SET_ENABLED, true)
            setBoolean(SLOT_SMTC_SET_PLAY_ENABLED, true)
            setBoolean(SLOT_SMTC_SET_PAUSE_ENABLED, true)
            setBoolean(SLOT_SMTC_SET_PREVIOUS_ENABLED, true)
            setBoolean(SLOT_SMTC_SET_NEXT_ENABLED, true)

            if (onButton != null) {
                val handler = ComButtonHandler(
                    onButton = { args ->
                        val button = readButton(args)
                        onButton(button)
                    },
                )
                val tokenOut = Memory(8L)
                val hrButton = vtableCall(
                    smtc,
                    SLOT_SMTC_ADD_BUTTON_PRESSED,
                    handler.pointer,
                    tokenOut,
                )
                if (hrButton != S_OK) {
                    log("SMTC phase 2: ButtonPressed failed HRESULT=${hrHex(hrButton)}")
                    handler.releaseOwnedReference()
                    return false
                }
                buttonHandler = handler
                buttonToken = tokenOut.getLong(0)
            }
            return true
        }

        /** Sends title/artist/album and a remote thumbnail to the Windows tile. */
        @Synchronized
        fun updateMetadata(title: String?, artist: String?, album: String?, thumbnailUrl: String?) {
            if (closed) return
            runCatching {
                updateMetadataUnsafe(title, artist, album, thumbnailUrl)
            }.onFailure { log("SMTC metadata native call trapped: $it") }
        }

        private fun updateMetadataUnsafe(
            title: String?,
            artist: String?,
            album: String?,
            thumbnailUrl: String?,
        ) {
            val updater = displayUpdater ?: return
            val music = musicProperties ?: return
            val safeTitle = title.orEmpty()
            val safeArtist = artist.orEmpty()
            val safeAlbum = album.orEmpty()
            if (safeTitle.isBlank() && safeArtist.isBlank()) {
                vtableCall(updater, SLOT_DISPLAY_CLEAR_ALL)
                return
            }

            // MediaPlaybackType_Music = 1.
            vtableCall(updater, SLOT_DISPLAY_SET_TYPE, MEDIA_PLAYBACK_TYPE_MUSIC)
            setHString(music, SLOT_MUSIC_SET_TITLE, safeTitle)
            setHString(music, SLOT_MUSIC_SET_ALBUM_ARTIST, safeArtist)
            setHString(music, SLOT_MUSIC_SET_ARTIST, safeArtist)
            musicProperties2?.let { setHString(it, SLOT_MUSIC2_SET_ALBUM_TITLE, safeAlbum) }

            // CreateFromUri is a WinRT reference; Windows fetches the image
            // for the system tile. A failed artwork request never blocks text.
            setThumbnail(updater, thumbnailUrl)
            val hrUpdate = vtableCall(updater, SLOT_DISPLAY_UPDATE)
            if (hrUpdate != S_OK) log("SMTC metadata update failed HRESULT=${hrHex(hrUpdate)}")
        }

        /** Mirrors DHUN's [PlaybackState] as MediaPlaybackStatus. */
        fun setPlaybackState(status: PlaybackStatus) {
            if (closed) return
            val hr = runCatching {
                vtableCall(smtc, SLOT_SMTC_SET_PLAYBACK_STATUS, status.abiValue)
            }.getOrDefault(E_FAIL)
            if (hr != S_OK) log("SMTC playback state failed HRESULT=${hrHex(hr)}")
        }

        /** Enables/disables previous and next based on the shared queue. */
        fun setNavigationButtons(hasPrevious: Boolean, hasNext: Boolean) {
            if (closed) return
            setBoolean(SLOT_SMTC_SET_PREVIOUS_ENABLED, hasPrevious)
            setBoolean(SLOT_SMTC_SET_NEXT_ENABLED, hasNext)
        }

        /** Removes the event registration and releases all retained WinRT objects. */
        @Synchronized
        fun close() {
            if (closed) return
            closed = true
            val token = buttonToken
            if (token != null) {
                runCatching {
                    vtableCall(smtc, SLOT_SMTC_REMOVE_BUTTON_PRESSED, token)
                }.onFailure { log("SMTC ButtonPressed removal failed: $it") }
            }
            buttonToken = null
            buttonHandler?.releaseOwnedReference()
            buttonHandler = null
            musicProperties2?.let(::release)
            musicProperties?.let(::release)
            displayUpdater?.let(::release)
            release(smtc)
            musicProperties2 = null
            musicProperties = null
            displayUpdater = null
        }

        private fun setBoolean(slot: Int, value: Boolean) {
            val hr = runCatching { vtableCall(smtc, slot, if (value) 1 else 0) }.getOrDefault(E_FAIL)
            if (hr != S_OK) log("SMTC control slot $slot failed HRESULT=${hrHex(hr)}")
        }

        private fun readButton(args: Pointer): Button {
            val out = Memory(4L)
            val hr = runCatching { vtableCall(args, SLOT_BUTTON_ARGS_GET_BUTTON, out) }.getOrDefault(E_FAIL)
            if (hr != S_OK) {
                log("SMTC ButtonPressed args failed HRESULT=${hrHex(hr)}")
                return Button.Unknown
            }
            return when (out.getInt(0)) {
                BUTTON_PLAY -> Button.Play
                BUTTON_PAUSE -> Button.Pause
                BUTTON_STOP -> Button.Stop
                BUTTON_NEXT -> Button.Next
                BUTTON_PREVIOUS -> Button.Previous
                BUTTON_FAST_FORWARD -> Button.FastForward
                BUTTON_REWIND -> Button.Rewind
                BUTTON_RECORD -> Button.Record
                BUTTON_CHANNEL_UP -> Button.ChannelUp
                BUTTON_CHANNEL_DOWN -> Button.ChannelDown
                else -> Button.Unknown
            }
        }

        private fun setHString(obj: Pointer, slot: Int, value: String): Boolean {
            val hstring = createHString(winRt, value) ?: return false
            return try {
                val hr = vtableCall(obj, slot, hstring)
                if (hr != S_OK) log("SMTC string slot $slot failed HRESULT=${hrHex(hr)}")
                hr == S_OK
            } finally {
                winRt.WindowsDeleteString(hstring)
            }
        }

        private fun setThumbnail(updater: Pointer, thumbnailUrl: String?) {
            val url = thumbnailUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            if (url == null) {
                runCatching { vtableCall(updater, SLOT_DISPLAY_SET_THUMBNAIL, null) }
                return
            }
            val uri = createUri(winRt, url)
            if (uri == null) return
            try {
                val reference = createRandomAccessStreamReference(winRt, uri)
                if (reference == null) return
                try {
                    val hr = vtableCall(updater, SLOT_DISPLAY_SET_THUMBNAIL, reference)
                    if (hr != S_OK) log("SMTC thumbnail failed HRESULT=${hrHex(hr)}")
                } finally {
                    release(reference)
                }
            } finally {
                release(uri)
            }
        }
    }

    enum class PlaybackStatus(val abiValue: Int) {
        Closed(0),
        Changing(1),
        Stopped(2),
        Playing(3),
        Paused(4),
    }

    /**
     * Runs the activation/readiness probe. No phase-2 objects are retained;
     * this is safe to call from diagnostics and returns false on non-Windows.
     */
    fun probe(windowTitle: String, log: (String) -> Unit = ::println): Boolean {
        if (!isWindows) {
            log("SMTC probe: skipped (not Windows)")
            return false
        }
        if (System.getProperty(FLAG, "true") == "false") {
            log("SMTC probe: disabled (-$FLAG=false)")
            return false
        }
        val steps = ArrayList<ProbeResult>()
        val controls = acquire(windowTitle, steps, log)
        if (controls == null) {
            report(steps, log)
            return false
        }
        release(controls.smtc)
        report(steps, log)
        return steps.all { it.ok }
    }

    /**
     * Opens a phase-2 session. A null result means that the documented tray
     * and keyboard fallback should remain active. [onButton] is dispatched
     * from a native callback thread; callers must hand work to their scope.
     */
    fun connect(
        windowTitle: String,
        onButton: (Button) -> Unit,
        log: (String) -> Unit = ::println,
    ): Session? {
        if (!isWindows || System.getProperty(FLAG, "true") == "false") return null
        val steps = ArrayList<ProbeResult>()
        val controls = acquire(windowTitle, steps, log) ?: run {
            report(steps, log)
            return null
        }
        val session = Session(controls.winRt, controls.smtc, log)
        val initialized = runCatching { session.initialize(onButton) }
            .onFailure { log("SMTC phase 2 native call trapped: $it") }
            .getOrDefault(false)
        if (!initialized) {
            session.close()
            steps += ProbeResult("phase2", false, "metadata/event registration")
            report(steps, log)
            return null
        }
        steps += ProbeResult("phase2", true, "metadata + ButtonPressed registered")
        report(steps, log)
        return session
    }

    private data class Controls(val winRt: WinRt, val smtc: Pointer)

    /** Acquires SMTC and checks the real IsEnabled property (slot 10). */
    private fun acquire(
        windowTitle: String,
        steps: MutableList<ProbeResult>,
        log: (String) -> Unit,
    ): Controls? {
        try {
            val hwnd = findHwnd(windowTitle)
            if (hwnd == null) {
                steps += ProbeResult("hwnd", false, "FindWindowW(SunAwtFrame, \"$windowTitle\") = NULL")
                return null
            }
            steps += ProbeResult("hwnd", true, "ptr=$hwnd")

            val lib = loadWinRt()
            steps += ProbeResult("abi", true, "RoGetActivationFactory resolved")

            val classIdOut = pointerOut()
            val hrCreate = lib.WindowsCreateString(
                WString(RUNTIME_CLASS_SMTC),
                RUNTIME_CLASS_SMTC.length,
                classIdOut,
            )
            val classId = classIdOut.getPointer(0)
            if (classId == null || hrCreate != S_OK) {
                steps += ProbeResult("activate-factory", false, "WindowsCreateString HRESULT=${hrHex(hrCreate)}")
                return null
            }

            val factoryOut = pointerOut()
            val hrFactory = try {
                lib.RoGetActivationFactory(classId, guidFromIid(IID_SMTC_INTEROP), factoryOut)
            } finally {
                lib.WindowsDeleteString(classId)
            }
            val factory = factoryOut.getPointer(0)
            if (hrFactory != S_OK || factory == null) {
                steps += ProbeResult("activate-factory", false, "HRESULT=${hrHex(hrFactory)}")
                return null
            }
            steps += ProbeResult("activate-factory", true, "ptr=$factory")

            val smtcOut = pointerOut()
            val hrForWindow = try {
                vtableCall(
                    factory,
                    SLOT_INTEROP_GET_FOR_WINDOW,
                    hwnd,
                    guidFromIid(IID_SMTC),
                    smtcOut,
                )
            } finally {
                release(factory)
            }
            val smtc = smtcOut.getPointer(0)
            if (hrForWindow != S_OK || smtc == null) {
                steps += ProbeResult("get-for-window", false, "HRESULT=${hrHex(hrForWindow)}")
                return null
            }
            steps += ProbeResult("get-for-window", true, "ptr=$smtc")

            // ISystemMediaTransportControls::IsEnabled is the fifth declared
            // property, therefore IInspectable slots 0..5 + method offset 4
            // = slot 10. The old activation-only probe called slot 6, which
            // is PlaybackStatus and was not a valid bool liveness check.
            val enabledOut = Memory(4L)
            val hrEnabled = vtableCall(smtc, SLOT_SMTC_GET_ENABLED, enabledOut)
            if (hrEnabled != S_OK) {
                release(smtc)
                steps += ProbeResult("is-enabled", false, "HRESULT=${hrHex(hrEnabled)}")
                return null
            }
            steps += ProbeResult("is-enabled", true, "enabled=${enabledOut.getInt(0) != 0}")
            return Controls(lib, smtc)
        } catch (t: Throwable) {
            steps += ProbeResult("exception", false, "${t::class.java.simpleName}: ${t.message}")
            log("SMTC native call trapped: $t")
            return null
        }
    }

    /* ---------------- window movement used by the mini-player ---------------- */

    /** Win32 RECT (pixels). */
    private class WinRect : Structure() {
        var left: Int = 0
        var top: Int = 0
        var right: Int = 0
        var bottom: Int = 0

        override fun getFieldOrder(): List<String> = listOf("left", "top", "right", "bottom")
    }

    /** Minimal user32.dll surface (HWND = Pointer; no jna-platform needed). */
    private interface User32Lib : Library {
        companion object {
            val INSTANCE: User32Lib = Native.load("user32", User32Lib::class.java)
        }

        fun FindWindowW(lpClassName: WString?, lpWindowName: WString?): Pointer?
        fun GetWindowRect(hWnd: Pointer?, rect: WinRect?): Int
        fun SetWindowPos(
            hWnd: Pointer?,
            hWndInsertAfter: Pointer?,
            x: Int,
            y: Int,
            cx: Int,
            cy: Int,
            uFlags: Int,
        ): Int
    }

    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010

    /** Moves the top-level AWT window [windowTitle] by (dx, dy) pixels. */
    fun moveWindow(windowTitle: String, dx: Int, dy: Int): Boolean = if (!isWindows) false else
        runCatching {
            val hwnd = User32Lib.INSTANCE.FindWindowW(WString("SunAwtFrame"), WString(windowTitle))
                ?: return false
            val rect = WinRect()
            if (User32Lib.INSTANCE.GetWindowRect(hwnd, rect) == 0) return false
            val flags = SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE
            User32Lib.INSTANCE.SetWindowPos(hwnd, null, rect.left + dx, rect.top + dy, 0, 0, flags) != 0
        }.getOrDefault(false)

    /* ---------------- JNA ABI helpers ---------------- */

    /** Win32 GUID (4+2+2+8 bytes; first three fields little-endian in memory). */
    private class WinGuid : Structure() {
        var data1: Int = 0
        var data2: Short = 0
        var data3: Short = 0
        var data4: ByteArray = ByteArray(8)

        override fun getFieldOrder(): List<String> = listOf("data1", "data2", "data3", "data4")
    }

    /** combase.dll WinRT ABI. HSTRING is a pointer-sized handle. */
    internal interface WinRt : Library {
        fun WindowsCreateString(sourceString: WString?, length: Int, hstring: Pointer?): Int
        fun WindowsDeleteString(hstring: Pointer?)
        fun RoGetActivationFactory(classId: Pointer?, iid: WinGuid, ppv: Pointer): Int
    }

    private fun loadWinRt(): WinRt = runCatching { Native.load("combase", WinRt::class.java) }
        .recoverCatching { Native.load("WindowsCore", WinRt::class.java) }
        .getOrThrow()

    private fun findHwnd(windowTitle: String): Pointer? = runCatching {
        User32Lib.INSTANCE.FindWindowW(WString("SunAwtFrame"), WString(windowTitle))
    }.getOrNull()

    private fun pointerOut(): Memory = Memory(Native.POINTER_SIZE.toLong())

    /** UUID string → Win32 GUID structure. */
    private fun guidFromIid(iid: String): WinGuid {
        val u = UUID.fromString(iid)
        val b = ByteArray(16)
        val hi = u.mostSignificantBits
        val lo = u.leastSignificantBits
        for (i in 0 until 8) {
            b[i] = ((hi ushr ((7 - i) * 8)) and 0xFF).toByte()
            b[8 + i] = ((lo ushr ((7 - i) * 8)) and 0xFF).toByte()
        }
        val g = WinGuid()
        g.data1 = ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
            ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
        g.data2 = (((b[4].toInt() and 0xFF) shl 8) or (b[5].toInt() and 0xFF)).toShort()
        g.data3 = (((b[6].toInt() and 0xFF) shl 8) or (b[7].toInt() and 0xFF)).toShort()
        g.data4 = b.copyOfRange(8, 16)
        return g
    }

    private fun guidBytes(iid: String): ByteArray = guidFromIid(iid).let {
        it.write()
        it.pointer.getByteArray(0, 16)
    }

    private fun createHString(winRt: WinRt, value: String): Pointer? {
        val out = pointerOut()
        val hr = winRt.WindowsCreateString(WString(value), value.length, out)
        return out.getPointer(0)?.takeIf { hr == S_OK }
    }

    private fun activateFactory(winRt: WinRt, runtimeClass: String, iid: String): Pointer? {
        val classId = createHString(winRt, runtimeClass) ?: return null
        val out = pointerOut()
        val hr = try {
            winRt.RoGetActivationFactory(classId, guidFromIid(iid), out)
        } finally {
            winRt.WindowsDeleteString(classId)
        }
        return out.getPointer(0)?.takeIf { hr == S_OK }
    }

    private fun createUri(winRt: WinRt, url: String): Pointer? {
        val factory = activateFactory(winRt, RUNTIME_CLASS_URI, IID_URI_FACTORY) ?: return null
        val hstring = createHString(winRt, url) ?: run {
            release(factory)
            return null
        }
        val out = pointerOut()
        val hr = try {
            vtableCall(factory, SLOT_URI_FACTORY_CREATE_URI, hstring, out)
        } finally {
            winRt.WindowsDeleteString(hstring)
            release(factory)
        }
        return out.getPointer(0)?.takeIf { hr == S_OK }
    }

    private fun createRandomAccessStreamReference(winRt: WinRt, uri: Pointer): Pointer? {
        val factory = activateFactory(winRt, RUNTIME_CLASS_STREAM_REFERENCE, IID_STREAM_REFERENCE_STATICS)
            ?: return null
        val out = pointerOut()
        val hr = try {
            // Statics vtable: CreateFromFile = slot 6, CreateFromUri = slot 7.
            vtableCall(factory, SLOT_STREAM_REFERENCE_CREATE_FROM_URI, uri, out)
        } finally {
            release(factory)
        }
        return out.getPointer(0)?.takeIf { hr == S_OK }
    }

    private fun queryInterface(obj: Pointer, iid: String): Pointer? {
        val out = pointerOut()
        val hr = runCatching { vtableCall(obj, SLOT_QUERY_INTERFACE, guidFromIid(iid), out) }.getOrDefault(E_FAIL)
        return out.getPointer(0)?.takeIf { hr == S_OK }
    }

    /** Calls vtable slot [slot] of a WinRT object (IInspectable*). */
    private fun vtableCall(obj: Pointer, slot: Int, vararg args: Any?): Int {
        val vtbl = obj.getPointer(0) ?: error("null vtable")
        val fnPtr = vtbl.getPointer((slot * Native.POINTER_SIZE).toLong())
        val fn = Function.getFunction(fnPtr)
        val all = arrayOfNulls<Any>(args.size + 1)
        all[0] = obj
        System.arraycopy(args, 0, all, 1, args.size)
        return fn.invokeInt(all)
    }

    /** IInspectable::Release (slot 2). Best effort. */
    private fun release(obj: Pointer) = runCatching { vtableCall(obj, SLOT_RELEASE) }

    private fun report(steps: List<ProbeResult>, log: (String) -> Unit) {
        val verdict = if (steps.all { it.ok }) "PASS" else "FAIL"
        log(
            "SMTC probe $verdict — " +
                steps.joinToString(" | ") { "${it.step}=${if (it.ok) "ok" else "FAIL"} (${it.detail})" },
        )
    }

    private class ComButtonHandler(private val onButton: (Pointer) -> Unit) {
        private val refs = AtomicInteger(1)
        private val vtable = Memory((4L * Native.POINTER_SIZE))
        val pointer = Memory(Native.POINTER_SIZE.toLong())

        // Keep strong references: JNA may call these much later from a native
        // Windows event thread, after the registration call has returned.
        private val query = object : QueryInterfaceCallback {
            override fun invoke(thisPointer: Pointer, riid: Pointer, ppvObject: Pointer): Int {
                val requested = runCatching { riid.getByteArray(0, 16) }.getOrNull()
                val supported = requested?.contentEquals(guidBytes(IID_BUTTON_HANDLER)) == true ||
                    requested?.contentEquals(guidBytes(IID_IUNKNOWN)) == true
                if (!supported) {
                    ppvObject.setPointer(0, null)
                    return E_NOINTERFACE
                }
                ppvObject.setPointer(0, pointer)
                refs.incrementAndGet()
                return S_OK
            }
        }
        private val addRef = object : AddRefCallback {
            override fun invoke(thisPointer: Pointer): Int = refs.incrementAndGet()
        }
        private val releaseRef = object : ReleaseCallback {
            override fun invoke(thisPointer: Pointer): Int = refs.updateAndGet { (it - 1).coerceAtLeast(0) }
        }
        private val invoke = object : InvokeCallback {
            override fun invoke(thisPointer: Pointer, sender: Pointer, args: Pointer): Int {
                runCatching { onButton(args) }
                return S_OK
            }
        }

        init {
            vtable.setPointer(0, CallbackReference.getFunctionPointer(query))
            vtable.setPointer(Native.POINTER_SIZE.toLong(), CallbackReference.getFunctionPointer(addRef))
            vtable.setPointer((2L * Native.POINTER_SIZE), CallbackReference.getFunctionPointer(releaseRef))
            vtable.setPointer((3L * Native.POINTER_SIZE), CallbackReference.getFunctionPointer(invoke))
            pointer.setPointer(0, vtable)
        }

        fun releaseOwnedReference() {
            refs.updateAndGet { (it - 1).coerceAtLeast(0) }
        }
    }

    private interface QueryInterfaceCallback : Callback {
        fun invoke(thisPointer: Pointer, riid: Pointer, ppvObject: Pointer): Int
    }

    private interface AddRefCallback : Callback {
        fun invoke(thisPointer: Pointer): Int
    }

    private interface ReleaseCallback : Callback {
        fun invoke(thisPointer: Pointer): Int
    }

    private interface InvokeCallback : Callback {
        fun invoke(thisPointer: Pointer, sender: Pointer, args: Pointer): Int
    }

    /* ---------------- ABI constants ---------------- */

    private const val S_OK = 0
    private const val E_FAIL = -2147467259
    private const val E_NOINTERFACE = -2147467262

    private const val RUNTIME_CLASS_SMTC = "Windows.Media.SystemMediaTransportControls"
    private const val RUNTIME_CLASS_URI = "Windows.Foundation.Uri"
    private const val RUNTIME_CLASS_STREAM_REFERENCE = "Windows.Storage.Streams.RandomAccessStreamReference"

    private const val IID_SMTC_INTEROP = "ddb0472d-c911-4a1f-86d9-dc3d71a95f5a"
    private const val IID_SMTC = "99FA3FF4-1742-42A6-902E-087D41F965EC"
    private const val IID_BUTTON_HANDLER = "0557e996-7b23-5bae-aa81-ea0d671143a4"
    private const val IID_IUNKNOWN = "00000000-0000-0000-c000-000000000046"
    private const val IID_MUSIC_PROPERTIES2 = "00368462-97d3-44b9-b00f-008afcefaf18"
    private const val IID_URI_FACTORY = "44a9796f-723e-4fdf-a218-033e75b0c084"
    private const val IID_STREAM_REFERENCE_STATICS = "857309dc-3fbf-4e7d-986f-ef3b1a07a964"

    // IInspectable slots 0..5 are QueryInterface/AddRef/Release/GetIids/
    // GetRuntimeClassName/GetTrustLevel.
    private const val SLOT_QUERY_INTERFACE = 0
    private const val SLOT_RELEASE = 2
    private const val SLOT_INTEROP_GET_FOR_WINDOW = 6

    // ISystemMediaTransportControls (Windows.Media generated ABI).
    private const val SLOT_SMTC_SET_PLAYBACK_STATUS = 7
    private const val SLOT_SMTC_DISPLAY_UPDATER = 8
    private const val SLOT_SMTC_GET_ENABLED = 10
    private const val SLOT_SMTC_SET_ENABLED = 11
    private const val SLOT_SMTC_SET_PLAY_ENABLED = 13
    private const val SLOT_SMTC_SET_PAUSE_ENABLED = 17
    private const val SLOT_SMTC_SET_PREVIOUS_ENABLED = 25
    private const val SLOT_SMTC_SET_NEXT_ENABLED = 27
    private const val SLOT_SMTC_ADD_BUTTON_PRESSED = 32
    private const val SLOT_SMTC_REMOVE_BUTTON_PRESSED = 33

    // ISystemMediaTransportControlsDisplayUpdater.
    private const val SLOT_DISPLAY_SET_TYPE = 7
    private const val SLOT_DISPLAY_SET_THUMBNAIL = 11
    private const val SLOT_DISPLAY_MUSIC_PROPERTIES = 12
    private const val SLOT_DISPLAY_CLEAR_ALL = 16
    private const val SLOT_DISPLAY_UPDATE = 17

    // IMusicDisplayProperties and IMusicDisplayProperties2.
    private const val SLOT_MUSIC_SET_TITLE = 7
    private const val SLOT_MUSIC_SET_ALBUM_ARTIST = 9
    private const val SLOT_MUSIC_SET_ARTIST = 11
    private const val SLOT_MUSIC2_SET_ALBUM_TITLE = 7

    // ISystemMediaTransportControlsButtonPressedEventArgs.
    private const val SLOT_BUTTON_ARGS_GET_BUTTON = 6

    // IUriRuntimeClassFactory and IRandomAccessStreamReferenceStatics.
    private const val SLOT_URI_FACTORY_CREATE_URI = 6
    private const val SLOT_STREAM_REFERENCE_CREATE_FROM_URI = 7

    private const val MEDIA_PLAYBACK_TYPE_MUSIC = 1
    private const val BUTTON_PLAY = 0
    private const val BUTTON_PAUSE = 1
    private const val BUTTON_STOP = 2
    private const val BUTTON_RECORD = 3
    private const val BUTTON_FAST_FORWARD = 4
    private const val BUTTON_REWIND = 5
    private const val BUTTON_NEXT = 6
    private const val BUTTON_PREVIOUS = 7
    private const val BUTTON_CHANNEL_UP = 8
    private const val BUTTON_CHANNEL_DOWN = 9

    private fun hrHex(hr: Int): String = "0x${Integer.toUnsignedString(hr, 16).padStart(8, '0')}"
}
