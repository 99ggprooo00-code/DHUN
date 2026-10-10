package dev.dhun.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView

/**
 * API < 31 cold-start launcher. This class must stay a raw
 * [android.app.Activity] with **no** Compose, AndroidX, or
 * [MainActivity] class references in its bytecode.
 *
 * Why: ART may verify every method of the launcher activity before
 * `onCreate`. [MainActivity] is a `ComponentActivity` whose method bodies
 * reference Compose 1.8 `GraphicsLayer`, which references
 * `android.graphics.RenderEffect` (API 31). On API 29/30 that class load
 * is process death that looks like "installs, never opens" — and it
 * happens before the Views connecting screen in MainActivity can paint.
 *
 * This activity inflates [R.layout.activity_connecting] (framework Views
 * only), then starts MainActivity by **class name string** so the constant
 * pool of *this* class does not mention MainActivity. Android 12+
 * (API 31) still launches MainActivity directly via the v31 activity-alias.
 */
class LaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_connecting)
            findViewById<TextView>(R.id.connecting_version)?.text = "v${appVersionName()}"
        } catch (t: Throwable) {
            Log.e(TAG, "connecting layout failed", t)
        }
        window.decorView.post { startMain() }
    }

    private fun startMain() {
        try {
            val next = Intent().apply {
                setClassName(packageName, MAIN_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                action = intent?.action
                intent?.data?.let { data = it }
                intent?.extras?.let { putExtras(it) }
            }
            startActivity(next)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            finish()
        } catch (t: Throwable) {
            Log.e(TAG, "failed to start MainActivity", t)
        }
    }

    private fun appVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    private companion object {
        const val TAG = "DHUN"
        const val MAIN_ACTIVITY = "dev.dhun.android.MainActivity"
    }
}
