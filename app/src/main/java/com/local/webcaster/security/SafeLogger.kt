package com.local.webcaster.security

import android.util.Log
import com.local.webcaster.BuildConfig
import java.net.URI

object SafeLogger {
    fun debug(message: String) {
        if (BuildConfig.DEBUG) runCatching { Log.d("LocalWebCaster", message) }
    }
    fun warn(message: String, error: Throwable? = null) {
        runCatching { Log.w("LocalWebCaster", message, if (BuildConfig.DEBUG) error else null) }
    }

    fun redactedUrl(url: String): String = runCatching {
        val uri = URI(url)
        val extension = uri.path.orEmpty().substringAfterLast('/', "")
            .substringAfterLast('.', "")
            .takeIf { it.length in 1..8 && it.all(Char::isLetterOrDigit) }
            ?.let { ".$it" }
            .orEmpty()
        val port = uri.port.takeIf { it >= 0 }?.let { ":$it" }.orEmpty()
        "${uri.scheme}://${uri.host}$port/...$extension"
    }.getOrDefault("<url invalide>")
}
