package com.example.ta33.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.ta33.core.IosNotifier
import com.example.ta33.core.IosUrlOpener
import com.example.ta33.core.Notifier
import com.example.ta33.core.UrlOpener
import com.example.ta33.data.connectivity.ConnectivityMonitor
import com.example.ta33.data.connectivity.IosConnectivityMonitor
import com.example.ta33.data.db.Ta33Database
import com.example.ta33.data.file.FileStorage
import com.example.ta33.data.file.IosFileStorage
import com.example.ta33.data.location.IosLocationPermissionController
import com.example.ta33.data.location.IosLocationProvider
import com.example.ta33.data.location.IosLocationTrackingController
import com.example.ta33.data.location.LocationPermissionController
import com.example.ta33.data.location.LocationProvider
import com.example.ta33.data.location.LocationTrackingController
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<SqlDriver> { NativeSqliteDriver(Ta33Database.Schema, "ta33.db") }
    single<HttpClientEngine> { Darwin.create() }
    single<FileStorage> { IosFileStorage() }
    single<ConnectivityMonitor> { IosConnectivityMonitor() }
    single<LocationProvider> { IosLocationProvider() }
    single<LocationPermissionController> { IosLocationPermissionController() }
    single<LocationTrackingController> { IosLocationTrackingController() }
    single<UrlOpener> { IosUrlOpener() }
    single<Notifier> { IosNotifier() }
}
