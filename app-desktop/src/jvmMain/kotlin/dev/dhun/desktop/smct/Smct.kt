package dev.dhun.desktop.smct

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import java.util.UUID

/**
 * Phase 12 — SMTC spike, phase 1 (time-boxed per MASTER_PROMPT, 3 days).
 *
 * Proves — from a plain Win32 (unpackaged) JVM process, via JNA:
 *   1. the WinRT ABI is reachable (`RoGetActivationFactory` in combase.dll);
 *   2. the activation factory for `Windows.Media.Control.SystemMediaTransportControls`
 *      can be obtained as `ISystemMediaTransportControlsInterop`;
 *   3. `GetForWindow(HWND)` returns a **live** `ISystemMediaTransportControls`
 *      for DHUN's main window (verified via `IsTransportControlsButtonVisible`).
 *
 * Deliberately NOT in phase 1 (needs one round trip on the target machine
 * to pull two IIDs from the system winmd — procedure in
 * `docs/verification/12-desktop-native.md`):
 *   - `UpdateMetadata` (`ISMCSystemMetadata`) → now-playing tile + artwork
 *   - `ButtonPressed` event registration → media keys driving DHUN
 *
 * Failure mode: every step logs its HRESULT; the probe never throws into
 * the app. If the probe fails or proves flaky on the user's machine, the
 * documented fallback ships (RISK_REGISTER: "SMTC via JNA unstable → tray
 * + focused-window media keys" — see KNOWN_LIMITATIONS).
 *
 * Interop classes are SELF-CONTAINED (plain JNA `Structure`/`Library` over
 * user32.dll + combase.dll) — deliberately NOT `com.sun.jna.platform.*`
 * wrappers: the exact availability of the platform artifact's win32
 * helper classes across JNA versions/artifact splits is version-fragile
 * (CI round 5 + 6: `com.sun.jna.platform.win32.GUID` doesn't exist, and
 * `win32.Guid` / `User32.FindWindowW` failed to resolve against the
 * resolved artifacts). Plain structures + a local `Native.load("user32")`
 * interface compile against base JNA only.
 *
 * GUID / vtable sources (cross-referenced from the sandbox, 2026-09-04):
 *  - `ISystemMediaTransportControlsInterop` ddb0472d-…: MS docs
 *    (systemmediatransportcontrolsinterop.h) + AdamBraden/WindowsInteropWrappers
 *    `MIDL_INTERFACE("ddb0472d-c911-4a1f-86d9-dc3d71a95f5a")`.
 *  - `ISystemMediaTransportControls` 99FA3FF4-…: MPVMediaControl interop
 *    (datasone/MPVMediaControl) + windows-rs generated bindings.
 *  - Interop vtable = 6 IInspectable slots + `GetForWindow` at slot 6
 *    (offset 48 — matches an independent ctypes implementation).
 *  - `IsTransportControlsButtonVisible` is the first method of the modern
 *    (Windows 10+) `ISystemMediaTransportControls` IDL — slot 6. If the
 *    probe's "is-visible" step reports a failure while the earlier steps
 *    pass, treat it as a vtable-order finding (fix on the target machine),
 *    not as a crash risk: all calls are guarded.
 */
object Smct {

    /** System property flag; `java -Ddhun.smct=false` disables the probe. */
    const val FLAG = "dhun.smct"

    val isWindows: Boolean =
        System.getProperty("os.name", "").lowercase().contains("windows")

    data class ProbeResult(val step: String, val ok: Boolean, val detail: String)

    /**
     * Runs the full phase-1 probe for the top-level AWT window titled
     * [windowTitle] (DHUN's main window; AWT creates Frame windows under
     * class "SunAwtFrame" on Windows).
     * @return true only if every step returned S_OK. Never throws.
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
        try {
            // 0. HWND of the main window (title-unique: "DHUN").
            val hwnd = findHwnd(windowTitle)
            if (hwnd == null) {
                steps += ProbeResult("hwnd", false, "FindWindowW(SunAwtFrame, \"$windowTitle\") = NULL")
                report(steps, log)
                return false
            }
            steps += ProbeResult("hwnd", true, "ptr=$hwnd")

            // 1. WinRT ABI reachable.
            val lib = loadWinRt()
            steps += ProbeResult("abi", true, "RoGetActivationFactory resolved")

            // 2. Interop activation factory (HSTRING handle + REFIID).
            val classIdOut = Memory(Native.POINTER_SIZE.toLong())
            val hrCreate = lib.WindowsCreateString(
                WString(RUNTIME_CLASS_SMTC),
                RUNTIME_CLASS_SMTC.length,
                classIdOut,
            )
            val classId = classIdOut.getPointer(0)
            val factoryOut = Memory(Native.POINTER_SIZE.toLong())
            val hrFactory = if (classId != null) {
                lib.RoGetActivationFactory(classId, guidFromIid(IID_SMTC_INTEROP), factoryOut)
            } else {
                hrCreate
            }
            if (classId != null) lib.WindowsDeleteString(classId)
            val factory = factoryOut.getPointer(0)
            if (hrFactory != 0 || factory == null) {
                steps += ProbeResult("activate-factory", false, "HRESULT=0x${hrFactory.toString(16)}")
                report(steps, log)
                return false
            }
            steps += ProbeResult("activate-factory", true, "ptr=$factory")

            // 3. GetForWindow (interop vtable slot 6).
            val smtcOut = Memory(Native.POINTER_SIZE.toLong())
            val hrForWindow = vtableCall(
                factory,
                SLOT_INTEROP_GET_FOR_WINDOW,
                hwnd,
                guidFromIid(IID_SMTC),
                smtcOut,
            )
            val smtc = smtcOut.getPointer(0)
            try {
                if (hrForWindow != 0 || smtc == null) {
                    steps += ProbeResult("get-for-window", false, "HRESULT=0x${hrForWindow.toString(16)}")
                    report(steps, log)
                    return false
                }
                steps += ProbeResult("get-for-window", true, "ptr=$smtc")

                // 4. Live-object check (slot 6, out BOOL).
                val visibleOut = Memory(4L)
                val hrVisible = vtableCall(smtc, SLOT_SMTC_IS_TRANSPORT_CONTROLS_BUTTON_VISIBLE, visibleOut)
                val visible = visibleOut.getInt(0) != 0
                steps += ProbeResult(
                    "is-visible",
                    hrVisible == 0,
                    if (hrVisible == 0) "visible=$visible" else "HRESULT=0x${hrVisible.toString(16)}",
                )
                report(steps, log)
                return steps.all { it.ok }
            } finally {
                release(factory)
                if (smtc != null) release(smtc)
            }
        } catch (t: Throwable) {
            steps += ProbeResult("exception", false, "${t::class.java.simpleName}: ${t.message}")
            report(steps, log)
            return false
        }
    }

    /* ---------------- JNA interop (base-JNA only) ---------------- */

    /** Win32 GUID (4+2+2+8 bytes; first three fields little-endian in memory). */
    private class WinGuid : Structure() {
        var data1: Int = 0
        var data2: Short = 0
        var data3: Short = 0
        var data4: ByteArray = ByteArray(8)

        override fun getFieldOrder(): List<String> = listOf("data1", "data2", "data3", "data4")
    }

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

    /**
     * combase.dll WinRT ABI. HSTRING is a **handle** (pointer-sized struct),
     * not a raw string — build it with WindowsCreateString, free it with
     * WindowsDeleteString (both also in combase.dll).
     */
    private interface WinRt : Library {
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

    /**
     * Moves the top-level AWT window [windowTitle] by (dx, dy) px.
     * Windows-only (user32 SetWindowPos, no size/z-order/activation changes);
     * used by the mini-player's in-content drag.
     */
    fun moveWindow(windowTitle: String, dx: Int, dy: Int): Boolean = if (!isWindows) false else
        runCatching {
            val hwnd = User32Lib.INSTANCE.FindWindowW(WString("SunAwtFrame"), WString(windowTitle))
                ?: return false
            val rect = WinRect()
            if (User32Lib.INSTANCE.GetWindowRect(hwnd, rect) == 0) return false
            val flags = SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE
            User32Lib.INSTANCE.SetWindowPos(hwnd, null, rect.left + dx, rect.top + dy, 0, 0, flags) != 0
        }.getOrDefault(false)

    /**
     * UUID string → Win32 GUID structure. The Java UUID is big-endian 128-bit;
     * the Win32 layout is the first 4 bytes as a little-endian int, the next
     * two shorts little-endian, the last 8 bytes as-is.
     */
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

    /**
     * Calls vtable slot [slot] of [obj] (WinRT object = IInspectable*).
     * Args are marshaled by JNA (Long = C long/HWND, WinGuid as a Structure,
     * Memory = out pointer slot).
     */
    private fun vtableCall(obj: Pointer, slot: Int, vararg args: Any?): Int {
        val vtbl = obj.getPointer(0) ?: error("null vtable")
        val fnPtr = vtbl.getPointer((slot * Native.POINTER_SIZE).toLong())
        val fn = Function.getFunction(fnPtr)
        val all = arrayOfNulls<Any>(args.size + 1)
        all[0] = obj
        System.arraycopy(args, 0, all, 1, args.size)
        return fn.invokeInt(all)
    }

    /** IInspectable::Release (slot 2). Best-effort. */
    private fun release(obj: Pointer) = runCatching { vtableCall(obj, SLOT_RELEASE) }

    private fun report(steps: List<ProbeResult>, log: (String) -> Unit) {
        val verdict = if (steps.all { it.ok }) "PASS" else "FAIL"
        log(
            "SMTC probe $verdict — " +
                steps.joinToString(" | ") { "${it.step}=${if (it.ok) "ok" else "FAIL"} (${it.detail})" },
        )
    }

    /* ---------------- constants ---------------- */

    private const val RUNTIME_CLASS_SMTC = "Windows.Media.Control.SystemMediaTransportControls"
    private const val IID_SMTC_INTEROP = "ddb0472d-c911-4a1f-86d9-dc3d71a95f5a"
    private const val IID_SMTC = "99FA3FF4-1742-42A6-902E-087D41F965EC"

    // WinRT vtable layout: slots 0-5 are IInspectable (QI, AddRef, Release,
    // GetIids, GetRuntimeClassName, GetTrustLevel); interface methods follow
    // in IDL order.
    private const val SLOT_RELEASE = 2
    private const val SLOT_INTEROP_GET_FOR_WINDOW = 6
    private const val SLOT_SMTC_IS_TRANSPORT_CONTROLS_BUTTON_VISIBLE = 6
}
