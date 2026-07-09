package com.example.ta33.core

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

class IosUrlOpener : UrlOpener {
    override fun open(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        val app = UIApplication.sharedApplication
        if (!app.canOpenURL(nsUrl)) return false
        // UIApplication must be touched on the main thread.
        dispatch_async(dispatch_get_main_queue()) {
            app.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
        return true
    }
}
