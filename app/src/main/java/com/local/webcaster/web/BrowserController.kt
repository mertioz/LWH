package com.local.webcaster.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import com.local.webcaster.adblock.AdBlockEngine
import com.local.webcaster.data.PreferencesRepository
import com.local.webcaster.detection.MediaDetector
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.lang.ref.WeakReference

class BrowserController(
    context: Context,
    val webView: WebView,
    private val detector: MediaDetector,
    adBlockEngine: AdBlockEngine,
    private val preferences: PreferencesRepository,
    private val callbacks: Callbacks,
) {
    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    init {
        configure(context, detector, adBlockEngine)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(context: Context, detector: MediaDetector, adBlockEngine: AdBlockEngine) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true
            displayZoomControls = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        WebMediaBridge(detector).install(webView) { webView.url.orEmpty() }
        val documentStart = DocumentStartInjector.install(webView)
        val webViewClient = BrowserWebViewClient(
            assetLoader, detector, adBlockEngine, webView.settings.userAgentString, documentStart,
            object : BrowserWebViewClient.Callbacks {
                override fun onPageStarted(url: String) = callbacks.onPageStarted(url)
                override fun onPageFinished(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
                    CookieManager.getInstance().flush()
                    callbacks.onPageFinished(url, title, canGoBack, canGoForward)
                }
                override fun onHistoryUpdated(url: String, canGoBack: Boolean, canGoForward: Boolean) =
                    callbacks.onHistoryUpdated(url, canGoBack, canGoForward)
                override fun onError(message: String) = callbacks.onError(message)
            }
        )
        webView.webViewClient = webViewClient
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            val clientReference = WeakReference(webViewClient)
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
                object : ServiceWorkerClientCompat() {
                    override fun shouldInterceptRequest(request: android.webkit.WebResourceRequest) =
                        clientReference.get()?.interceptRequest(request)
                }
            )
        }
        webView.webChromeClient = BrowserWebChromeClient(
            webView, { preferences.blockPopups }, callbacks::onProgress, callbacks::onTitle
        )
    }

    fun navigate(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return
        val target = when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            trimmed.matches(Regex("^[\\w.-]+\\.[a-zA-Z]{2,}(?:/.*)?$")) -> "https://$trimmed"
            else -> preferences.searchEngineTemplate.format(
                URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
            )
        }
        prepareNavigation(target)
        webView.loadUrl(target)
    }

    fun back() {
        if (!webView.canGoBack()) return
        val history = webView.copyBackForwardList()
        history.getItemAtIndex(history.currentIndex - 1)?.url?.let(::prepareNavigation)
        webView.goBack()
    }
    fun forward() {
        if (!webView.canGoForward()) return
        val history = webView.copyBackForwardList()
        history.getItemAtIndex(history.currentIndex + 1)?.url?.let(::prepareNavigation)
        webView.goForward()
    }
    fun reloadOrStop(isLoading: Boolean) {
        if (isLoading) webView.stopLoading()
        else {
            webView.url?.let(::prepareNavigation)
            webView.reload()
        }
    }
    fun home() {
        prepareNavigation(BrowserWebViewClient.HOME_URL)
        webView.loadUrl(BrowserWebViewClient.HOME_URL)
    }

    private fun prepareNavigation(url: String) {
        callbacks.onNavigationRequested(url)
        if (com.local.webcaster.detection.MediaUrlClassifier.isPotentialMedia(url)) {
            val headers = buildMap {
                put("User-Agent", webView.settings.userAgentString)
                CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
            }
            detector.observeNetwork(url, url, headers = headers)
        }
    }
    fun onResume() = webView.onResume()
    fun onPause() {
        CookieManager.getInstance().flush()
        webView.onPause()
    }
    fun saveState(): Bundle = Bundle().also(webView::saveState)
    fun restoreState(state: Bundle): Boolean = webView.restoreState(state) != null
    fun destroy() = webView.destroy()

    fun clearHistory() {
        webView.clearHistory()
    }

    fun clearCookiesAndSiteData(onComplete: () -> Unit = {}) {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            onComplete()
        }
        WebStorage.getInstance().deleteAllData()
        webView.clearFormData()
    }

    fun clearCache() {
        webView.clearCache(true)
    }

    fun clearBrowsingData(onComplete: () -> Unit = {}) {
        clearHistory()
        clearCache()
        clearCookiesAndSiteData(onComplete)
    }

    fun currentHost(): String = runCatching { URI(webView.url.orEmpty()).host.orEmpty() }.getOrDefault("")

    interface Callbacks {
        fun onNavigationRequested(url: String)
        fun onPageStarted(url: String)
        fun onPageFinished(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean)
        fun onHistoryUpdated(url: String, canGoBack: Boolean, canGoForward: Boolean)
        fun onProgress(progress: Int)
        fun onTitle(title: String)
        fun onError(message: String)
    }
}
