package com.example.ta33

import android.app.Application
import com.example.ta33.di.initKoin
import com.example.ta33.di.seedDevDataIfEmpty
import org.koin.android.ext.koin.androidContext

class Ta33Application : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@Ta33Application)
        }
        seedDevDataIfEmpty() // DEV/TESTING only (gated by DEV_SEED_ENABLED)
    }
}
