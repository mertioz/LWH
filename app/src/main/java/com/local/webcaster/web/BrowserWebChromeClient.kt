package com.local.webcaster.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.createBitmap
import com.local.webcaster.security.SafeLogger

class BrowserWebChromeClient(
    private val mainWebView: WebView,
    private val blockPopups: () -> Boolean,
    private val openInNewTab: (String) -> Unit,
    private val onPopupBlocked: () -> Unit,
    private val onProgress: (Int) -> Unit,
    private val onTitle: (String) -> Unit,
) : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)
    override fun onReceivedTitle(view: WebView, title: String?) { title?.let(onTitle) }
    override fun getDefaultVideoPoster(): Bitmap? = createBitmap(1, 1)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        if (blockPopups() && !isUserGesture) {
            SafeLogger.debug("ADBLOCK_POPUP blocked=true userGesture=false")
            onPopupBlocked()
            return false
        }
        val popup = WebView(view.context)
        popup.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
        }
        popup.webViewClient = object : WebViewClient() {
            private var handled = false
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (!handled && url.startsWith("http")) {
                    handled = true
                    if (isUserGesture) openInNewTab(url) else mainWebView.loadUrl(url)
                    view.stopLoading()
                    view.destroy()
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                if (request.url.scheme !in setOf("http", "https")) {
                    view.destroy()
                    return true
                }
                return false
            }

            override fun onReceivedError(
                view: WebView,
                request: android.webkit.WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                if (request.isForMainFrame) view.destroy()
            }
        }
        (resultMsg.obj as WebView.WebViewTransport).webView = popup
        resultMsg.sendToTarget()
        return true
    }
}
