package com.local.webcaster.web

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.local.webcaster.detection.MediaDetector
import com.local.webcaster.security.MessageValidator

class WebMediaBridge(private val detector: MediaDetector) {
    fun install(webView: WebView, pageUrl: () -> String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(
            webView,
            BRIDGE_NAME,
            setOf("*"),
            object : WebViewCompat.WebMessageListener {
                override fun onPostMessage(
                    view: WebView,
                    message: WebMessageCompat,
                    sourceOrigin: Uri,
                    isMainFrame: Boolean,
                    replyProxy: JavaScriptReplyProxy,
                ) {
                    // Document-start scripts run in same- and cross-origin iframes too. Accept their
                    // validated observations, but always associate them with the current top-level page.
                    if (sourceOrigin.scheme !in setOf("http", "https")) return
                    val text = message.data ?: return
                    MessageValidator.parse(text, pageUrl())?.let(detector::observe)
                }
            }
        )
    }

    companion object { const val BRIDGE_NAME = "LocalCasterBridge" }
}
