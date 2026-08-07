package com.local.webcaster.web

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

object DocumentStartInjector {
    fun install(webView: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false
        WebViewCompat.addDocumentStartJavaScript(webView, DetectionScript.script, setOf("*"))
        return true
    }

    fun fallback(webView: WebView) {
        webView.evaluateJavascript(DetectionScript.script, null)
    }
}
