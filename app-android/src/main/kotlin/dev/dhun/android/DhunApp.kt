package dev.dhun.android

import android.app.Application
import dev.dhun.android.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class DhunApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // In production Koin starts exactly once per process. Under
        // Robolectric, the JVM is SHARED across test classes while the
        // Application is re-created for each — a leftover GlobalContext
        // from the previous class made every later test class crash with
        // KoinApplicationAlreadyStartedException before its tests ran.
        // Clearing first is a no-op in production and makes the app
        // re-bootable in a shared JVM.
        runCatching { GlobalContext.stopKoin() }
        startKoin {
            androidContext(this@DhunApp)
            modules(appModule)
        }
    }
}
