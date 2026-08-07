package com.local.webcaster.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.local.webcaster.adblock.AdBlockEngine
import com.local.webcaster.adblock.AdBlockRequest
import com.local.webcaster.detection.MediaDetector
import com.local.webcaster.detection.MediaUrlClassifier
import com.local.webcaster.security.SafeLogger
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

class BrowserWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val mediaDetector: MediaDetector,
    private val adBlockEngine: AdBlockEngine,
    private val userAgent: String,
    private val documentStartInstalled: Boolean,
    private val callbacks: Callbacks,
) : WebViewClient() {
    private val currentPage = AtomicReference(HOME_URL)

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        currentPage.set(url)
        callbacks.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (!documentStartInstalled) DocumentStartInjector.fallback(view)
        callbacks.onPageFinished(url, view.title, view.canGoBack(), view.canGoForward())
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        currentPage.set(url)
        callbacks.onHistoryUpdated(url, view.canGoBack(), view.canGoForward())
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        return interceptRequest(request)
    }

    fun interceptRequest(request: WebResourceRequest): WebResourceResponse? {
        assetLoader.shouldInterceptRequest(request.url)?.let { return it }
        val url = request.url.toString()
        val mediaLike = MediaUrlClassifier.isPotentialMedia(url)
        val mediaTransport = MediaUrlClassifier.isMediaTransport(url)
        val block = adBlockEngine.shouldBlock(
            AdBlockRequest(url, currentPage.get(), request.isForMainFrame, mediaTransport)
        )
        if (mediaLike && !block) {
            val headers = capturePlaybackHeaders(request, url)
            mediaDetector.observeNetwork(url, currentPage.get(), headers = headers)
        }
        if (block) {
            SafeLogger.debug(
                "ADBLOCK_BLOCK main=${request.isForMainFrame} media=$mediaTransport url=${SafeLogger.redactedUrl(url)}"
            )
        }
        return if (block) emptyResponse(url) else null
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val scheme = request.url.scheme?.lowercase()
        if (scheme !in setOf("http", "https")) return true
        val blocked = adBlockEngine.shouldBlock(
            AdBlockRequest(
                url = request.url.toString(),
                pageUrl = currentPage.get(),
                isMainFrame = request.isForMainFrame,
                hasUserGesture = request.hasGesture(),
                isRedirect = request.isRedirect,
            )
        )
        if (blocked && request.isForMainFrame) callbacks.onError("Redirection publicitaire bloquee.")
        if (blocked) {
            SafeLogger.debug(
                "ADBLOCK_BLOCK navigation=true main=${request.isForMainFrame} url=${SafeLogger.redactedUrl(request.url.toString())}"
            )
        }
        return blocked
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        callbacks.onError("Certificat TLS invalide : navigation annulée.")
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
        if (request.isForMainFrame) callbacks.onError("Impossible de charger cette page.")
    }

    private fun emptyResponse(url: String): WebResourceResponse {
        val type = when {
            url.contains(".js", true) -> "application/javascript"
            url.contains(".css", true) -> "text/css"
            url.contains(Regex("\\.(png|jpg|jpeg|gif|webp)(?:[?#]|$)", RegexOption.IGNORE_CASE)) -> "image/gif"
            else -> "text/plain"
        }
        return WebResourceResponse(type, "UTF-8", 204, "Blocked", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
    }

    private fun capturePlaybackHeaders(request: WebResourceRequest, url: String): LinkedHashMap<String, String> {
        val headers = linkedMapOf<String, String>()
        val canonical = mapOf(
            "user-agent" to "User-Agent",
            "referer" to "Referer",
            "origin" to "Origin",
            "authorization" to "Authorization",
            "accept" to "Accept",
            "accept-language" to "Accept-Language",
            "cookie" to "Cookie",
        )
        request.requestHeaders.forEach { (name, value) ->
            canonical[name.lowercase()]?.let { safeName ->
                if (value.isNotBlank() && value.length <= MAX_HEADER_VALUE_LENGTH) headers[safeName] = value
            }
        }
        headers.putIfAbsent("User-Agent", userAgent)
        headers.putIfAbsent("Referer", currentPage.get())
        CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let { headers.putIfAbsent("Cookie", it) }
        return headers
    }

    interface Callbacks {
        fun onPageStarted(url: String)
        fun onPageFinished(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean)
        fun onHistoryUpdated(url: String, canGoBack: Boolean, canGoForward: Boolean)
        fun onError(message: String)
    }

    companion object {
        const val HOME_URL = "https://appassets.androidplatform.net/assets/home.html"
        private const val MAX_HEADER_VALUE_LENGTH = 16_384
    }
}
