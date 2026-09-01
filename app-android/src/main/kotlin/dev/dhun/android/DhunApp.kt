package dev.dhun.android

import android.app.Application
import dev.dhun.android.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DhunApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DhunApp)
            modules(appModule)
        }
    }
}
