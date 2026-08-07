package com.local.webcaster.ui.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.local.webcaster.LocalWebCasterApp
import com.local.webcaster.web.BrowserController
import com.local.webcaster.web.BrowserWebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.URI

enum class BrowserDestination { BROWSER, HOME, HISTORY, BOOKMARKS, SETTINGS }

data class BrowserUiState(
    val address: String = "",
    val currentUrl: String = "",
    val title: String = "Local Web Caster",
    val destination: BrowserDestination = BrowserDestination.HOME,
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val shieldEnabled: Boolean = true,
    val blockPopups: Boolean = true,
    val message: String? = null,
)

class BrowserViewModel(application: Application) : AndroidViewModel(application), BrowserController.Callbacks {
    private val app = application as LocalWebCasterApp
    private val _state = MutableStateFlow(BrowserUiState(blockPopups = app.preferences.blockPopups))
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()
    val candidates = app.mediaRepository.items
    val history = app.browserData.history
    val frequent = app.browserData.frequent
    val bookmarks = app.browserData.bookmarks

    fun updateAddress(value: String) = _state.update { it.copy(address = value) }
    fun showHome() = _state.update { it.copy(destination = BrowserDestination.HOME, address = "") }
    fun showHistory() = _state.update { it.copy(destination = BrowserDestination.HISTORY) }
    fun showBookmarks() = _state.update { it.copy(destination = BrowserDestination.BOOKMARKS) }
    fun showSettings() = _state.update { it.copy(destination = BrowserDestination.SETTINGS) }

    override fun onNavigationRequested(url: String) {
        app.mediaRepository.resetForPage(url)
    }

    override fun onPageStarted(url: String) {
        app.mediaRepository.resetForPage(url)
        _state.update {
            it.copy(
                currentUrl = url,
                address = displayUrl(url),
                destination = if (isHome(url)) BrowserDestination.HOME else BrowserDestination.BROWSER,
                loading = !isHome(url),
                progress = 0,
                shieldEnabled = shield(url),
            )
        }
    }

    override fun onPageFinished(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        if (isNetworkPage(url)) app.browserData.recordVisit(url, title)
        _state.update {
            it.copy(
                currentUrl = url,
                address = displayUrl(url),
                title = title?.take(500) ?: it.title,
                destination = if (isHome(url)) BrowserDestination.HOME else BrowserDestination.BROWSER,
                loading = false,
                progress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                shieldEnabled = shield(url),
            )
        }
    }

    override fun onHistoryUpdated(url: String, canGoBack: Boolean, canGoForward: Boolean) {
        val previousUrl = _state.value.currentUrl
        if (url != previousUrl) app.mediaRepository.resetForPage(url)
        else app.mediaRepository.updatePageUrl(url)
        if (url != previousUrl && isNetworkPage(url)) app.browserData.recordVisit(url, _state.value.title)
        _state.update {
            it.copy(
                currentUrl = url,
                address = displayUrl(url),
                destination = if (isHome(url)) BrowserDestination.HOME else BrowserDestination.BROWSER,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
            )
        }
    }

    override fun onProgress(progress: Int) = _state.update {
        it.copy(progress = progress, loading = progress < 100 && it.destination == BrowserDestination.BROWSER)
    }

    override fun onTitle(title: String) {
        val safeTitle = title.take(500)
        _state.update { it.copy(title = safeTitle) }
        if (isNetworkPage(_state.value.currentUrl)) {
            app.browserData.updateHistoryTitle(_state.value.currentUrl, safeTitle)
        }
    }

    override fun onError(message: String) = _state.update { it.copy(message = message, loading = false) }
    fun clearMessage() = _state.update { it.copy(message = null) }
    fun notify(message: String) = _state.update { it.copy(message = message) }

    fun toggleShield() {
        val host = host(_state.value.currentUrl)
        if (host.isBlank()) return
        val enabled = !_state.value.shieldEnabled
        app.adBlockEngine.setEnabledForSite(host, enabled)
        _state.update {
            it.copy(
                shieldEnabled = enabled,
                message = if (enabled) "Protection active pour $host" else "Protection desactivee pour $host",
            )
        }
    }

    fun setBlockPopups(enabled: Boolean) {
        app.preferences.blockPopups = enabled
        _state.update { it.copy(blockPopups = enabled) }
    }

    fun addCurrentBookmark() {
        val current = _state.value
        if (!isNetworkPage(current.currentUrl)) {
            notify("Ouvrez un site avant de l'ajouter aux favoris.")
            return
        }
        app.browserData.addBookmark(current.currentUrl, current.title)
        notify("Site ajoute aux favoris.")
    }

    fun removeBookmark(url: String) = app.browserData.removeBookmark(url)
    fun renameBookmark(url: String, title: String) = app.browserData.renameBookmark(url, title)
    fun deleteHistory(url: String) = app.browserData.deleteHistory(url)
    fun clearPersistentHistory() {
        app.browserData.clearHistory()
        _state.update { it.copy(canGoBack = false, canGoForward = false) }
    }

    private fun shield(url: String): Boolean = host(url)
        .takeIf(String::isNotBlank)
        ?.let(app.adBlockEngine::isEnabledForSite)
        ?: true

    private fun host(url: String) = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
    private fun displayUrl(url: String) = if (isHome(url)) "" else url
    private fun isHome(url: String) = url == BrowserWebViewClient.HOME_URL ||
        url.contains("appassets.androidplatform.net/assets/home.html")

    private fun isNetworkPage(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme?.lowercase() in setOf("http", "https") &&
            !uri.host.orEmpty().equals("appassets.androidplatform.net", ignoreCase = true)
    }.getOrDefault(false)
}
