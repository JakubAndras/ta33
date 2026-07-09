package com.example.ta33.presentation

import com.example.ta33.domain.model.MapCamera
import com.example.ta33.domain.model.MapOverlay
import com.example.ta33.domain.model.MapTileSourceState

/**
 * Complete description of what the native MapLibre view must render.
 *
 * This is the FR-06 platform seam: the native map view (Android: Compose `AndroidView` + MapLibre
 * `MapView`; iOS: SwiftUI `UIViewRepresentable` + `MLNMapView`) observes this state and does all
 * drawing. Shared code never depends on MapLibre. Field-by-field rendering contract is in the FR-06
 * plan (`.claude/plans/fr-06-offline-map-data-aggregation.md`, §3.4):
 * - `tileSource` → build/skip the local basemap source (or show "mapa nestažena"/loading/error);
 * - `overlay.routePolyline` / `checkpointMarkers` / `livePosition` / `breadcrumb` → line/symbol/puck layers;
 * - `camera` → fit `bounds` on first load, else center on `focus`;
 * - `selectedMarkerId` → native highlight/callout (driven by `MapViewModel.onMarkerTapped`).
 */
data class MapUiState(
    val tileSource: MapTileSourceState = MapTileSourceState.NotDownloaded,
    val overlay: MapOverlay = MapOverlay(),
    val camera: MapCamera? = null, // initial camera; null until route loaded
    val isRouteLoaded: Boolean = false,
    val selectedMarkerId: String? = null, // from onMarkerTapped
    val loading: Boolean = true,
)
