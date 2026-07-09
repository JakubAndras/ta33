package com.example.ta33.di

import com.example.ta33.domain.usecase.OpenRouteInMapyCzUseCase
import com.example.ta33.presentation.AppViewModel
import com.example.ta33.presentation.ControlCollectionViewModel
import com.example.ta33.presentation.DownloadViewModel
import com.example.ta33.presentation.LiveLocationViewModel
import com.example.ta33.presentation.MapViewModel
import com.example.ta33.presentation.OverviewViewModel
import com.example.ta33.presentation.RouteDetailViewModel
import com.example.ta33.presentation.RouteListViewModel
import com.example.ta33.presentation.RunLogViewModel
import com.example.ta33.presentation.SettingsViewModel
import com.example.ta33.presentation.TimingViewModel
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
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

/** Lets Swift resolve shared ViewModels without touching the Koin API. */
object ViewModelProvider {
    fun appViewModel(): AppViewModel = KoinPlatform.getKoin().get() // FR-01
    fun routeListViewModel(): RouteListViewModel = KoinPlatform.getKoin().get() // FR-03
    fun routeDetailViewModel(): RouteDetailViewModel = KoinPlatform.getKoin().get() // FR-03
    fun downloadViewModel(): DownloadViewModel = KoinPlatform.getKoin().get() // FR-11
    fun liveLocationViewModel(): LiveLocationViewModel = KoinPlatform.getKoin().get() // FR-05
    fun runLogViewModel(): RunLogViewModel = KoinPlatform.getKoin().get() // FR-04
    fun controlCollectionViewModel(): ControlCollectionViewModel = KoinPlatform.getKoin().get() // FR-08
    fun timingViewModel(): TimingViewModel = KoinPlatform.getKoin().get() // FR-09
    fun mapViewModel(): MapViewModel = KoinPlatform.getKoin().get() // FR-06
    fun overviewViewModel(): OverviewViewModel = KoinPlatform.getKoin().get() // FR-10
    fun settingsViewModel(): SettingsViewModel = KoinPlatform.getKoin().get() // FR-10
}

/** FR-07: lets Swift resolve the open-route-in-mapy.cz use-case without touching the Koin API. */
fun openRouteInMapyCzUseCase(): OpenRouteInMapyCzUseCase = KoinPlatform.getKoin().get()
