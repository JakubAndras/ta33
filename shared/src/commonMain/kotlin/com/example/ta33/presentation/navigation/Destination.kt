package com.example.ta33.presentation.navigation

import kotlinx.serialization.Serializable

/** The three top-level tabs. Stable order (bottom-nav order). SKIE exposes this as a Swift enum. */
enum class TopLevelDestination { DENIK, MAPA, PREHLED }

/**
 * Shared navigation CONTRACT — the single source of truth for which destinations exist
 * and which typed arguments each carries. It does NOT model a back-stack: the native
 * layers (Compose Navigation / SwiftUI NavigationStack) own navigation history (stack §3).
 *
 * @Serializable enables Compose Navigation 2.8+ typed routes directly and a serialization
 * round-trip guarantee; SKIE turns this sealed interface into a Swift enum for exhaustive `switch`.
 */
@Serializable
sealed interface Destination {

    /** Offline-readiness / preparation gate (FR-11 flow — screen built later). */
    @Serializable
    data object Preparation : Destination

    /** The main tabbed shell; carries the initially selected tab. */
    @Serializable
    data class Main(val tab: TopLevelDestination = TopLevelDestination.DENIK) : Destination

    /** Detail of a route (FR-03) — referenced by the contract; screen built later. */
    @Serializable
    data class RouteDetail(val routeId: String) : Destination

    /** Resume / view an in-progress run — used at startup when a run is active. */
    @Serializable
    data class RunActive(val runId: String) : Destination

    /** QR start-scan gate (FR-09) — referenced only. `runId` null when starting fresh. */
    @Serializable
    data class StartScan(val runId: String? = null) : Destination

    /** QR finish-scan gate (FR-09) — referenced only. */
    @Serializable
    data class FinishScan(val runId: String) : Destination

    /** "Control collected" confirmation (FR-08) — referenced only. */
    @Serializable
    data class ControlCollected(val runId: String, val controlId: String) : Destination
}
