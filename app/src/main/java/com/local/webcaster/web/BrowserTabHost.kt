package com.local.webcaster.web

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import com.local.webcaster.LocalWebCasterApp
import com.local.webcaster.ui.browser.BrowserViewModel

class BrowserTabHost(
    private val activity: Activity,
    private val app: LocalWebCasterApp,
    private val viewModel: BrowserViewModel,
    restoredState: Bundle?,
    private val openInNewTab: (String) -> Unit,
    private val onActiveWebView: (WebView) -> Unit,
) {
    private data class Session(
        val webView: WebView,
        val controller: BrowserController,
        var lastUsed: Long,
    )

    private val sessions = linkedMapOf<String, Session>()
    private val savedStates = linkedMapOf<String, Bundle>()
    private var activeTabId: String? = null

    init {
        restoredState?.keySet()?.forEach { key ->
            restoredState.getBundle(key)?.let { savedStates[key] = it }
        }
    }

    val activeController: BrowserController?
        get() = activeTabId?.let(sessions::get)?.controller

    fun activate(tabId: String) {
        if (tabId == activeTabId && sessions[tabId] != null) {
            onActiveWebView(sessions.getValue(tabId).webView)
            return
        }
        activeTabId?.let { oldId ->
            sessions[oldId]?.let { old ->
                old.controller.onPause()
                old.lastUsed = System.currentTimeMillis()
            }
        }
        val session = sessions[tabId] ?: create(tabId)
        activeTabId = tabId
        session.lastUsed = System.currentTimeMillis()
        session.controller.onResume()
        session.controller.activateRequestInterception()
        session.controller.applySiteMode()
        onActiveWebView(session.webView)
        session.controller.rescanMedia()
        trimSessions()
    }

    fun close(tabId: String) {
        savedStates.remove(tabId)
        sessions.remove(tabId)?.let { session ->
            session.controller.onPause()
            session.controller.destroy()
        }
        if (activeTabId == tabId) activeTabId = null
    }

    fun onResume() = activeController?.onResume()
    fun onPause() = activeController?.onPause()

    fun saveState(): Bundle = Bundle().also { output ->
        savedStates.forEach(output::putBundle)
        sessions.forEach { (id, session) -> output.putBundle(id, session.controller.saveState()) }
    }

    fun destroy() {
        sessions.values.forEach { it.controller.destroy() }
        sessions.clear()
        savedStates.clear()
        activeTabId = null
    }

    private fun create(tabId: String): Session {
        val webView = WebView(activity)
        val controller = BrowserController(
            activity,
            webView,
            app.mediaDetector,
            app.adBlockEngine,
            app.preferences,
            viewModel.callbacksFor(tabId),
            openInNewTab,
        )
        val restored = savedStates.remove(tabId)?.let(controller::restoreState) == true
        if (!restored) {
            val url = viewModel.tabUrl(tabId)
            if (url == BrowserWebViewClient.HOME_URL) controller.home() else controller.navigate(url)
        }
        return Session(webView, controller, System.currentTimeMillis()).also { sessions[tabId] = it }
    }

    private fun trimSessions() {
        while (sessions.size > MAX_LIVE_WEB_VIEWS) {
            val target = sessions.entries
                .filterNot { it.key == activeTabId }
                .minByOrNull { it.value.lastUsed }
                ?: return
            savedStates[target.key] = target.value.controller.saveState()
            target.value.controller.destroy()
            sessions.remove(target.key)
        }
    }

    private companion object {
        const val MAX_LIVE_WEB_VIEWS = 3
    }
}
