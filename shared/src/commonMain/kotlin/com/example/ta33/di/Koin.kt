package com.example.ta33.di

import com.example.ta33.dev.DEV_SEED_ENABLED
import com.example.ta33.dev.DevSeed
import com.example.ta33.domain.usecase.OpenRouteInMapyCzUseCase
import com.example.ta33.presentation.AppViewModel
import com.example.ta33.presentation.ControlCollectionViewModel
import com.example.ta33.presentation.DenikViewModel
import com.example.ta33.presentation.DownloadViewModel
import com.example.ta33.presentation.LiveLocationViewModel
import com.example.ta33.presentation.MapViewModel
import com.example.ta33.presentation.MapaViewModel
import com.example.ta33.presentation.OverviewViewModel
import com.example.ta33.presentation.SandboxViewModel
import com.example.ta33.presentation.RouteDetailViewModel
import com.example.ta33.presentation.RouteListViewModel
import com.example.ta33.presentation.RunLogViewModel
import com.example.ta33.presentation.SettingsViewModel
import com.example.ta33.presentation.TimingViewModel
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.mp.KoinPlatform

fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication {
    Napier.base(DebugAntilog())
    return startKoin {
        appDeclaration()
        modules(platformModule, appModule)
    }
}

/** Convenience entry point for iOS (Swift has no Koin DSL). */
fun doInitKoin() {
    initKoin()
}

/**
 * DEV / TESTING ONLY — seeds sample content so the app boots into the READY shell.
 * No-op unless [DEV_SEED_ENABLED]. Called from the debug app entry points after Koin init.
 */
fun seedDevDataIfEmpty() {
    if (!DEV_SEED_ENABLED) return
    val seed: DevSeed = KoinPlatform.getKoin().get()
    CoroutineScope(Dispatchers.Default).launch { seed.seedIfEmpty() }
}

/** Lets Swift resolve shared ViewModels without touching the Koin API. */
object ViewModelProvider {
    fun appViewModel(): AppViewModel = KoinPlatform.getKoin().get() // FR-01
    fun routeListViewModel(): RouteListViewModel = KoinPlatform.getKoin().get() // FR-03
    fun routeDetailViewModel(): RouteDetailViewModel = KoinPlatform.getKoin().get() // FR-03
    fun downloadViewModel(): DownloadViewModel = KoinPlatform.getKoin().get() // FR-11
    fun liveLocationViewModel(): LiveLocationViewModel = KoinPlatform.getKoin().get() // FR-05
    fun runLogViewModel(): RunLogViewModel = KoinPlatform.getKoin().get() // FR-04
    fun denikViewModel(): DenikViewModel = KoinPlatform.getKoin().get() // RD-01
    fun controlCollectionViewModel(): ControlCollectionViewModel = KoinPlatform.getKoin().get() // FR-08
    fun timingViewModel(): TimingViewModel = KoinPlatform.getKoin().get() // FR-09
    fun mapViewModel(): MapViewModel = KoinPlatform.getKoin().get() // FR-06
    fun mapaViewModel(): MapaViewModel = KoinPlatform.getKoin().get() // RD-02 (schematic map)
    fun overviewViewModel(): OverviewViewModel = KoinPlatform.getKoin().get() // FR-10
    fun settingsViewModel(): SettingsViewModel = KoinPlatform.getKoin().get() // FR-10
    fun sandboxViewModel(): SandboxViewModel = KoinPlatform.getKoin().get() // UI-12 DEV Sandbox
}

/** FR-07: lets Swift resolve the open-route-in-mapy.cz use-case without touching the Koin API. */
fun openRouteInMapyCzUseCase(): OpenRouteInMapyCzUseCase = KoinPlatform.getKoin().get()
