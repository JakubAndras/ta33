package com.example.ta33.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.ta33.core.AndroidNotifier
import com.example.ta33.core.AndroidUrlOpener
import com.example.ta33.core.Notifier
import com.example.ta33.core.UrlOpener
import com.example.ta33.data.connectivity.AndroidConnectivityMonitor
import com.example.ta33.data.connectivity.ConnectivityMonitor
import com.example.ta33.data.db.Ta33Database
import com.example.ta33.data.file.AndroidFileStorage
import com.example.ta33.data.file.FileStorage
import com.example.ta33.data.location.AndroidLocationPermissionController
import com.example.ta33.data.location.AndroidLocationProvider
import com.example.ta33.data.location.AndroidLocationTrackingController
import com.example.ta33.data.location.LocationPermissionController
import com.example.ta33.data.location.LocationProvider
import com.example.ta33.data.location.LocationTrackingController
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<SqlDriver> { AndroidSqliteDriver(Ta33Database.Schema, androidContext(), "ta33.db") }
    single<HttpClientEngine> { OkHttp.create() }
    single<FileStorage> { AndroidFileStorage(androidContext()) }
    single<ConnectivityMonitor> { AndroidConnectivityMonitor(androidContext()) }
    single<LocationProvider> { AndroidLocationProvider(androidContext()) }
    single<LocationPermissionController> { AndroidLocationPermissionController(androidContext()) }
    single<LocationTrackingController> { AndroidLocationTrackingController(androidContext()) }
    single<UrlOpener> { AndroidUrlOpener(androidContext()) }
    single<Notifier> { AndroidNotifier(androidContext()) }
}
