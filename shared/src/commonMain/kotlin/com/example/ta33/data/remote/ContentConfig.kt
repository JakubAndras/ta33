package com.example.ta33.data.remote

/** Configurable content source. Final data not delivered yet (project-stack §10) - repoint by changing baseUrl only. */
data class ContentConfig(
    val baseUrl: String = DEV_PLACEHOLDER_BASE_URL,
    val manifestPath: String = "manifest.json",
) {
    fun manifestUrl(): String = resolve(manifestPath)

    /** Resolve a possibly-relative URL from the manifest against baseUrl. */
    fun resolve(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http")) pathOrUrl else baseUrl.trimEnd('/') + "/" + pathOrUrl.trimStart('/')

    companion object {
        // TODO(content): replace with the organizer's real host when data is delivered.
        const val DEV_PLACEHOLDER_BASE_URL = "https://example.invalid/ta33/"
    }
}
