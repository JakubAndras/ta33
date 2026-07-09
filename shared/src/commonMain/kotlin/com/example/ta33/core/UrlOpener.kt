package com.example.ta33.core

/** Opens an external URL via the platform. Returns true if a handler (app/browser) accepted it. */
interface UrlOpener {
    fun open(url: String): Boolean
}
