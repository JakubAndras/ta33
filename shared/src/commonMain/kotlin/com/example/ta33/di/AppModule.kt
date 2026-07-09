package com.example.ta33.di

import com.example.ta33.core.DefaultTicker
import com.example.ta33.core.IdGenerator
import com.example.ta33.core.SystemTimeProvider
import com.example.ta33.core.Ticker
import com.example.ta33.core.TimeProvider
import com.example.ta33.core.UuidGenerator
import com.example.ta33.data.db.Ta33Database
import com.example.ta33.data.location.LocationStream
import com.example.ta33.data.location.SharedLocationStream
import com.example.ta33.data.map.TileStoreImpl
import com.example.ta33.data.remote.ContentConfig
import com.example.ta33.data.remote.ContentRemoteDataSource
import com.example.ta33.data.remote.createHttpClient
import com.example.ta33.data.repository.AppPreferencesRepositoryImpl
import com.example.ta33.data.repository.OfflinePackageRepositoryImpl
import com.example.ta33.data.repository.ParticipantRepositoryImpl
import com.example.ta33.data.repository.PreparationRepositoryImpl
import com.example.ta33.data.repository.RouteRepositoryImpl
import com.example.ta33.data.repository.StaticAppInfoRepository
import com.example.ta33.data.repository.RunRepositoryImpl
import com.example.ta33.data.repository.TrackpointRepositoryImpl
import com.example.ta33.domain.geo.BreadcrumbThrottle
import com.example.ta33.domain.geo.ProximityEvaluator
import com.example.ta33.domain.map.MapTileConfig
import com.example.ta33.domain.map.TileStore
import com.example.ta33.domain.model.BreadcrumbConfig
import com.example.ta33.domain.model.GeofenceConfig
import com.example.ta33.domain.model.QrTimingConfig
import com.example.ta33.domain.qr.QrPayloadParser
import com.example.ta33.domain.repository.AppInfoRepository
import com.example.ta33.domain.repository.AppPreferencesRepository
import com.example.ta33.domain.repository.OfflinePackageRepository
import com.example.ta33.domain.repository.ParticipantRepository
import com.example.ta33.domain.repository.PreparationRepository
import com.example.ta33.domain.repository.RouteRepository
import com.example.ta33.domain.repository.RunRepository
import com.example.ta33.domain.repository.TrackpointRepository
import com.example.ta33.domain.usecase.CollectControlUseCase
import com.example.ta33.domain.usecase.EnsureLocalParticipantUseCase
import com.example.ta33.domain.usecase.FinishRunUseCase
import com.example.ta33.domain.usecase.ObserveAppStateUseCase
import com.example.ta33.domain.usecase.ObserveNotificationsEnabledUseCase
import com.example.ta33.domain.usecase.ObserveOverviewUseCase
import com.example.ta33.domain.usecase.SetNotificationsEnabledUseCase
import com.example.ta33.domain.usecase.HandleScannedQrUseCase
import com.example.ta33.domain.usecase.ObserveTimingUseCase
import com.example.ta33.domain.usecase.ObservePreparationStateUseCase
import com.example.ta33.domain.usecase.ObserveRouteDetailUseCase
import com.example.ta33.domain.usecase.ObserveRoutesUseCase
import com.example.ta33.domain.usecase.ObserveCollectionCandidateUseCase
import com.example.ta33.domain.usecase.ObserveRunLogUseCase
import com.example.ta33.domain.usecase.ObserveSelectedRouteUseCase
import com.example.ta33.domain.mapy.MapyCzUrlBuilder
import com.example.ta33.domain.usecase.ObserveTrackUseCase
import com.example.ta33.domain.usecase.OpenRouteInMapyCzUseCase
import com.example.ta33.domain.usecase.PrepareOfflinePackageUseCase
import com.example.ta33.domain.usecase.RecordBreadcrumbUseCase
import com.example.ta33.domain.usecase.SelectActiveRouteUseCase
import com.example.ta33.domain.usecase.StartRunUseCase
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
import com.example.ta33.presentation.navigation.StartDestinationResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val appModule = module {
    single { Ta33Database(get()) }
    // core
    single<TimeProvider> { SystemTimeProvider() }
    single<IdGenerator> { UuidGenerator() }
    // repositories
    single<ParticipantRepository> { ParticipantRepositoryImpl(get(), get(), get()) }
    single<RouteRepository> { RouteRepositoryImpl(get()) }
    single<RunRepository> { RunRepositoryImpl(get(), get()) }
    single<AppPreferencesRepository> { AppPreferencesRepositoryImpl(get()) } // FR-03 (Ta33Database)
    // FR-11 offline package download
    single { ContentConfig() } // dev default; repoint via baseUrl
    single { createHttpClient(get()) } // engine from platformModule
    single { ContentRemoteDataSource(get(), get()) } // HttpClient, ContentConfig
    single<PreparationRepository> { PreparationRepositoryImpl(get(), get()) } // db, TimeProvider
    single<OfflinePackageRepository> { OfflinePackageRepositoryImpl(get(), get(), get()) } // remote, RouteRepository, FileStorage
    // FR-05 live location + breadcrumb recording
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) } // app scope for shareIn
    single { BreadcrumbConfig() } // field-tunable thresholds
    single { BreadcrumbThrottle(get()) }
    single<LocationStream> { SharedLocationStream(get(), get()) } // provider (platformModule), appScope
    single<TrackpointRepository> { TrackpointRepositoryImpl(get()) } // db
    // use-cases
    factory { EnsureLocalParticipantUseCase(get()) }
    factory { StartRunUseCase(get(), get()) }
    factory { FinishRunUseCase(get(), get()) }
    factory { CollectControlUseCase(get(), get(), get(), get()) }
    factory { ObserveRunLogUseCase(get(), get()) }
    // FR-03 route browsing + selection
    factory { ObserveRoutesUseCase(get()) }
    factory { ObserveRouteDetailUseCase(get()) }
    factory { ObserveSelectedRouteUseCase(get(), get()) }
    factory { SelectActiveRouteUseCase(get(), get()) }
    // FR-11 use-cases
    factory { PrepareOfflinePackageUseCase(get(), get(), get()) }
    factory { ObservePreparationStateUseCase(get()) }
    // FR-05 use-cases
    factory { RecordBreadcrumbUseCase(get(), get(), get(), get()) } // stream, repo, throttle, IdGenerator
    factory { ObserveTrackUseCase(get()) }
    // FR-08 checkpoint collection / geofencing
    single { GeofenceConfig() }                                                   // field-tunable
    single { ProximityEvaluator(get()) }                                          // config
    factory { ObserveCollectionCandidateUseCase(get(), get(), get(), get()) }     // LocationStream(FR-05), RouteRepo(FR-03), RunRepo(FR-02), evaluator
    // FR-06 offline map data & aggregation
    single { MapTileConfig() }
    single<TileStore> { TileStoreImpl(get(), get(), get()) } // FileStorage, PreparationRepository, MapTileConfig
    factory { MapViewModel(get(), get(), get(), get(), get(), get()) }
    // FR-07 open route in mapy.cz
    single { MapyCzUrlBuilder() }
    factory { OpenRouteInMapyCzUseCase(get(), get()) } // MapyCzUrlBuilder, UrlOpener (from platformModule)
    // FR-09 timing (QR start/finish, elapsed, splits)
    single { QrTimingConfig() }                                             // field-tunable format
    single { QrPayloadParser() }                                            // pure
    single<Ticker> { DefaultTicker() }
    factory { HandleScannedQrUseCase(get(), get(), get(), get(), get()) }   // parser, RunRepo, StartRunUseCase, FinishRunUseCase, config
    factory { ObserveTimingUseCase(get(), get(), get()) }                   // RunRepo, TimeProvider, Ticker
    // viewmodel
    factory { RunLogViewModel(get()) }
    factory { RouteListViewModel(get(), get(), get()) }
    factory { RouteDetailViewModel(get(), get(), get()) }
    factory { DownloadViewModel(get(), get(), get()) }
    factory { LiveLocationViewModel(get(), get(), get(), get(), get()) }
    factory { ControlCollectionViewModel(get(), get(), get()) }                   // observeCandidate, CollectControlUseCase(FR-02), LocationPermissionController(FR-05)
    factory { TimingViewModel(get(), get()) }                                     // observeTiming, handleScannedQr (FR-09)
    // navigation / app state (FR-01)
    single { StartDestinationResolver() }
    factory { ObserveAppStateUseCase(get(), get(), get(), get(), get()) } // Seam A (also used by AppViewModel)
    factory { AppViewModel(get(), get()) }
    // FR-10 overview + settings
    single<AppInfoRepository> { StaticAppInfoRepository() }
    factory { ObserveOverviewUseCase(get(), get(), get(), get()) }
    factory { ObserveNotificationsEnabledUseCase(get()) }
    factory { SetNotificationsEnabledUseCase(get()) }
    factory { OverviewViewModel(get()) }
    factory { SettingsViewModel(get(), get(), get()) }
}
