package com.example.ta33.domain.usecase

import com.example.ta33.core.UrlOpener
import com.example.ta33.domain.mapy.MapyCzUrlBuilder
import com.example.ta33.domain.mapy.MapyRouteType
import com.example.ta33.domain.model.RouteDetail

sealed interface OpenRouteResult {
    data class Opened(val url: String) : OpenRouteResult
    data class NoAppAvailable(val url: String) : OpenRouteResult // no app/browser accepted it
    data object NoRoute : OpenRouteResult                        // route has no controls
}

class OpenRouteInMapyCzUseCase(
    private val urlBuilder: MapyCzUrlBuilder,
    private val urlOpener: UrlOpener,
) {
    operator fun invoke(
        detail: RouteDetail,
        routeType: MapyRouteType = MapyRouteType.FOOT_HIKING,
    ): OpenRouteResult {
        val url = urlBuilder.build(detail.controls, routeType) ?: return OpenRouteResult.NoRoute
        return if (urlOpener.open(url)) {
            OpenRouteResult.Opened(url)
        } else {
            OpenRouteResult.NoAppAvailable(url)
        }
    }
}
