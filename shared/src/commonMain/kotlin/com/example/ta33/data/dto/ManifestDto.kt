package com.example.ta33.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ManifestDto(
    val version: Int,
    val content: ContentRefDto,
    val tiles: List<TileSetDto> = emptyList(),
)

@Serializable
data class ContentRefDto(val url: String, val bytes: Long? = null, val sha256: String? = null)

@Serializable
data class TileSetDto(
    val id: String, // stable, e.g. "adrspach-teplice"
    val url: String,
    val bytes: Long? = null, // expected size (nullable if host omits it)
    val format: String = "mbtiles",
    val sha256: String? = null,
)
