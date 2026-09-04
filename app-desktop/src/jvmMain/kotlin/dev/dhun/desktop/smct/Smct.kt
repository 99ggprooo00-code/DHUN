package dev.dhun.desktop.smct

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.GUID
import com.sun.jna.platform.win32.User32
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
            steps += ProbeResult("hwnd", true, "0x${hwnd.toString(16)}")

            // 1. WinRT ABI reachable.
            val lib = loadWinRt()
            steps += ProbeResult("abi", true, "RoGetActivationFactory resolved")

            // 2. Interop activation factory.
            val factoryOut = Memory(Native.POINTER_SIZE)
            val hrFactory = lib.RoGetActivationFactory(
                WString(RUNTIME_CLASS_SMTC),
                GUID(UUID.fromString(IID_SMTC_INTEROP)),
                factoryOut,
            )
            val factory = factoryOut.getPointer(0)
            if (hrFactory != 0 || factory == null) {
                steps += ProbeResult("activate-factory", false, "HRESULT=0x${hrFactory.toString(16)}")
                report(steps, log)
                return false
            }
            steps += ProbeResult("activate-factory", true, "ptr=0x${factory.toLong().toString(16)}")

            // 3. GetForWindow (interop vtable slot 6).
            val smtcOut = Memory(Native.POINTER_SIZE)
            val hrForWindow = vtableCall(
                factory,
                SLOT_INTEROP_GET_FOR_WINDOW,
                hwnd,
                GUID(UUID.fromString(IID_SMTC)),
                smtcOut,
            )
            val smtc = smtcOut.getPointer(0)
            try {
                if (hrForWindow != 0 || smtc == null) {
                    steps += ProbeResult("get-for-window", false, "HRESULT=0x${hrForWindow.toString(16)}")
                    report(steps, log)
                    return false
                }
                steps += ProbeResult("get-for-window", true, "ptr=0x${smtc.toLong().toString(16)}")

                // 4. Live-object check (slot 6, out BOOL).
                val visibleOut = Memory(4)
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

    /* ---------------- internals ---------------- */

    /** RoGetActivationFactory(HSTRING classId, REFIID, void**) → HRESULT. */
    private interface WinRt : Library {
        fun RoGetActivationFactory(clsid: WString, iid: GUID, ppv: Pointer): Int
    }

    private fun loadWinRt(): WinRt = runCatching { Native.load("combase", WinRt::class.java) }
        .recoverCatching { Native.load("WindowsCore", WinRt::class.java) }
        .getOrThrow()

    private fun findHwnd(windowTitle: String): Long? = runCatching {
        val h = User32.INSTANCE.FindWindowW("SunAwtFrame", windowTitle)
        if (h == null) null else h.toLong()
    }.getOrNull()

    /**
     * Moves the top-level AWT window [windowTitle] by (dx, dy) px.
     * Windows-only (JNA SetWindowPos, no size/z-order/activation changes);
     * used by the mini-player's in-content drag.
     */
    fun moveWindow(windowTitle: String, dx: Int, dy: Int): Boolean = if (!isWindows) false else
        runCatching {
            val hwnd = User32.INSTANCE.FindWindowW("SunAwtFrame", windowTitle) ?: return false
            val rect = User32.RECT()
            User32.INSTANCE.GetWindowRect(hwnd, rect) || return false
            val flags = User32.SWP_NOSIZE or User32.SWP_NOZORDER or User32.SWP_NOACTIVATE
            User32.INSTANCE.SetWindowPos(hwnd, null, rect.left + dx, rect.top + dy, 0, 0, flags)
        }.getOrDefault(false)

    /**
     * Calls vtable slot [slot] of [obj] (WinRT object = IInspectable*).
     * Args are marshaled by JNA (Long = C long/HWND, GUID by value,
     * Memory = out pointer slot).
     */
    private fun vtableCall(obj: Pointer, slot: Int, vararg args: Any?): Int {
        val vtbl = obj.getPointer(0) ?: error("null vtable")
        val fnPtr = vtbl.getPointer(slot * Native.POINTER_SIZE)
        val fn = Function.getFunction(fnPtr)
        val all = arrayOfNulls<Any>(args.size + 1)
        all[0] = obj
        System.arraycopy(args, 0, all, 1, args.size)
        return fn.invokeInt(*all)
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
