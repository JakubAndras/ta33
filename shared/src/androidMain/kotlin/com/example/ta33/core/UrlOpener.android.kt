package com.example.ta33.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.aakira.napier.Napier

class AndroidUrlOpener(private val context: Context) : UrlOpener {
    override fun open(url: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // launched from Application context (Koin androidContext)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Napier.w(e) { "No activity to open URL" }
        false
    }
}
