package com.local.webcaster.ui.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.local.webcaster.LocalWebCasterApp
import com.local.webcaster.data.PersistedTab
import com.local.webcaster.data.SitePreferences
import com.local.webcaster.security.UrlValidator
import com.local.webcaster.web.BrowserController
import com.local.webcaster.web.BrowserWebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.URI
import java.util.UUID

enum class BrowserDestination { BROWSER, HOME, HISTORY, BOOKMARKS, SETTINGS }

data class BrowserTab(
    val id: String,
    val url: String,
    val title: String,
    val lastAccessed: Long,
)

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
    val sitePreferences: SitePreferences = SitePreferences(),
    val blockedCount: Int = 0,
    val tabs: List<BrowserTab> = emptyList(),
    val activeTabId: String = "",
    val message: String? = null,
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LocalWebCasterApp
    private val restoredTabs = app.preferences.restoreTabs()
        .map { BrowserTab(it.id, it.url, it.title, it.lastAccessed) }
        .ifEmpty { listOf(newHomeTab()) }
    private val restoredActiveId = app.preferences.restoredActiveTabId()
        ?.takeIf { id -> restoredTabs.any { it.id == id } }
        ?: restoredTabs.maxByOrNull(BrowserTab::lastAccessed)?.id
        ?: restoredTabs.first().id
    private val initialTab = restoredTabs.first { it.id == restoredActiveId }
    private val tabStates = mutableMapOf<String, BrowserUiState>()
    private val _state = MutableStateFlow(
        pageState(initialTab).copy(
            tabs = restoredTabs,
            activeTabId = restoredActiveId,
            blockPopups = app.preferences.blockPopups,
        )
    )

    val state: StateFlow<BrowserUiState> = _state.asStateFlow()
    val candidates = app.mediaRepository.items
    val history = app.browserData.history
    val frequent = app.browserData.frequent
    val bookmarks = app.browserData.bookmarks

    init {
        restoredTabs.forEach { tab -> tabStates[tab.id] = pageState(tab) }
        persistTabs()
    }

    fun callbacksFor(tabId: String): BrowserController.Callbacks = object : BrowserController.Callbacks {
        override fun onNavigationRequested(url: String) = this@BrowserViewModel.onNavigationRequested(tabId, url)
        override fun onPageStarted(url: String) = this@BrowserViewModel.onPageStarted(tabId, url)
        override fun onPageFinished(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) =
            this@BrowserViewModel.onPageFinished(tabId, url, title, canGoBack, canGoForward)
        override fun onHistoryUpdated(url: String, canGoBack: Boolean, canGoForward: Boolean) =
            this@BrowserViewModel.onHistoryUpdated(tabId, url, canGoBack, canGoForward)
        override fun onProgress(progress: Int) = mutateTab(tabId) {
            it.copy(progress = progress, loading = progress < 100 && it.destination == BrowserDestination.BROWSER)
        }
        override fun onTitle(title: String) = this@BrowserViewModel.onTitle(tabId, title)
        override fun onError(message: String) = mutateTab(tabId) { it.copy(message = message, loading = false) }
        override fun onRequestBlocked() = mutateTab(tabId) {
            it.copy(blockedCount = (it.blockedCount + 1).coerceAtMost(Int.MAX_VALUE))
        }
    }

    fun updateAddress(value: String) {
        _state.update { it.copy(address = value) }
        cacheActive()
    }

    fun showHome() = mutateActive {
        it.copy(destination = BrowserDestination.HOME, address = "", currentUrl = BrowserWebViewClient.HOME_URL)
    }
    fun showHistory() = mutateActive { it.copy(destination = BrowserDestination.HISTORY) }
    fun showBookmarks() = mutateActive { it.copy(destination = BrowserDestination.BOOKMARKS) }
    fun showSettings() = mutateActive { it.copy(destination = BrowserDestination.SETTINGS) }
    fun showCurrentPage() = mutateActive {
        it.copy(destination = if (isHome(it.currentUrl)) BrowserDestination.HOME else BrowserDestination.BROWSER)
    }

    fun createTab(initialUrl: String? = null): String {
        cacheActive()
        val safeUrl = initialUrl?.let(UrlValidator::normalize) ?: BrowserWebViewClient.HOME_URL
        val tab = BrowserTab(
            id = UUID.randomUUID().toString(),
            url = safeUrl,
            title = if (safeUrl == BrowserWebViewClient.HOME_URL) "Nouvel onglet" else host(safeUrl).ifBlank { "Nouvel onglet" },
            lastAccessed = System.currentTimeMillis(),
        )
        val tabs = (_state.value.tabs + tab).takeLast(MAX_TABS)
        val evicted = _state.value.tabs.map(BrowserTab::id).toSet() - tabs.map(BrowserTab::id).toSet()
        evicted.forEach(tabStates::remove)
        tabStates[tab.id] = pageState(tab)
        activate(tab.id, tabs)
        return tab.id
    }

    fun switchTab(tabId: String): Boolean {
        if (tabId == _state.value.activeTabId) return true
        if (_state.value.tabs.none { it.id == tabId }) return false
        cacheActive()
        activate(tabId, _state.value.tabs)
        return true
    }

    fun closeTab(tabId: String): String {
        val current = _state.value
        val index = current.tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return current.activeTabId
        tabStates.remove(tabId)
        var tabs = current.tabs.filterNot { it.id == tabId }
        if (tabs.isEmpty()) {
            val replacement = newHomeTab()
            tabs = listOf(replacement)
            tabStates[replacement.id] = pageState(replacement)
        }
        val active = if (current.activeTabId == tabId) {
            tabs.getOrNull(index.coerceAtMost(tabs.lastIndex))?.id ?: tabs.first().id
        } else current.activeTabId
        if (active != current.activeTabId) activate(active, tabs)
        else {
            _state.update { it.copy(tabs = tabs) }
            cacheActive()
            persistTabs()
        }
        return active
    }

    fun tabUrl(tabId: String): String = _state.value.tabs.firstOrNull { it.id == tabId }?.url
        ?: BrowserWebViewClient.HOME_URL

    fun toggleShield() {
        val current = _state.value
        val enabled = !(current.sitePreferences.ads || current.sitePreferences.trackers)
        updateSitePreferences { it.copy(ads = enabled, trackers = enabled) }
        notify(if (enabled) "Protection active pour ${host(current.currentUrl)}" else "Protection desactivee pour ce site")
    }

    fun setSiteAds(enabled: Boolean) = updateSitePreferences { it.copy(ads = enabled) }
    fun setSiteTrackers(enabled: Boolean) = updateSitePreferences { it.copy(trackers = enabled) }
    fun setSitePopups(enabled: Boolean) = updateSitePreferences { it.copy(popups = enabled) }
    fun setSiteQuickCast(enabled: Boolean) = updateSitePreferences { it.copy(quickCast = enabled) }
    fun setDesktopMode(enabled: Boolean) = updateSitePreferences { it.copy(desktopMode = enabled) }

    fun resetSitePreferences() {
        val domain = host(_state.value.currentUrl)
        if (domain.isBlank()) return
        app.preferences.resetSitePreferences(domain)
        mutateActive { it.copy(sitePreferences = SitePreferences(), shieldEnabled = true) }
        notify("Preferences reinitialisees pour $domain")
    }

    fun setBlockPopups(enabled: Boolean) {
        app.preferences.blockPopups = enabled
        _state.update { it.copy(blockPopups = enabled) }
        cacheActive()
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
        mutateActive { it.copy(canGoBack = false, canGoForward = false) }
    }

    fun clearMessage() = mutateActive { it.copy(message = null) }
    fun notify(message: String) = mutateActive { it.copy(message = message) }

    private fun onNavigationRequested(tabId: String, url: String) {
        if (isActive(tabId)) app.mediaRepository.resetForPage(url)
    }

    private fun onPageStarted(tabId: String, url: String) {
        if (isActive(tabId)) app.mediaRepository.resetForPage(url)
        mutateTab(tabId) {
            it.copy(
                currentUrl = url,
                address = displayUrl(url),
                destination = if (isHome(url)) BrowserDestination.HOME else BrowserDestination.BROWSER,
                loading = !isHome(url),
                progress = 0,
                blockedCount = 0,
                shieldEnabled = shield(url),
                sitePreferences = sitePreferences(url),
            )
        }
        updateTabMetadata(tabId, url = url)
    }

    private fun onPageFinished(tabId: String, url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        if (isNetworkPage(url)) app.browserData.recordVisit(url, title)
        val safeTitle = title?.take(500)
        mutateTab(tabId) {
            it.copy(
                currentUrl = url,
                address = displayUrl(url),
                title = safeTitle ?: it.title,
                destination = if (isHome(url)) BrowserDestination.HOME else BrowserDestination.BROWSER,
                loading = false,
                progress = 100,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                shieldEnabled = shield(url),
                sitePreferences = sitePreferences(url),
            )
        }
        updateTabMetadata(tabId, url, safeTitle)
    }

    private fun onHistoryUpdated(tabId: String, url: String, canGoBack: Boolean, canGoForward: Boolean) {
        val previousUrl = tabStates[tabId]?.currentUrl.orEmpty()
        if (isActive(tabId)) {
            if (url != previousUrl) app.mediaRepository.resetForPage(url) else app.mediaRepository.updatePageUrl(url)
        }
        if (url != previousUrl && isNetworkPage(url)) app.browserData.recordVisit(url, tabStates[tabId]?.title)
        mutateTab(tabId) {
            it.copy(
                currentUrl = url,
                address = displayUrl(url),
                destination = if (isHome(url)) BrowserDestination.HOME else BrowserDestination.BROWSER,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                sitePreferences = sitePreferences(url),
                shieldEnabled = shield(url),
            )
        }
        updateTabMetadata(tabId, url = url)
    }

    private fun onTitle(tabId: String, title: String) {
        val safeTitle = title.take(500)
        mutateTab(tabId) { it.copy(title = safeTitle) }
        val url = tabStates[tabId]?.currentUrl.orEmpty()
        if (isNetworkPage(url)) app.browserData.updateHistoryTitle(url, safeTitle)
        updateTabMetadata(tabId, title = safeTitle)
    }

    private fun updateSitePreferences(transform: (SitePreferences) -> SitePreferences) {
        val current = _state.value
        val domain = host(current.currentUrl)
        if (domain.isBlank()) return
        val updated = transform(current.sitePreferences)
        app.preferences.setSitePreferences(domain, updated)
        mutateActive { it.copy(sitePreferences = updated, shieldEnabled = updated.ads || updated.trackers) }
    }

    private fun mutateActive(transform: (BrowserUiState) -> BrowserUiState) = mutateTab(_state.value.activeTabId, transform)

    private fun mutateTab(tabId: String, transform: (BrowserUiState) -> BrowserUiState) {
        val base = if (isActive(tabId)) _state.value else tabStates[tabId] ?: return
        val updated = transform(base)
        tabStates[tabId] = updated
        if (isActive(tabId)) _state.value = updated
    }

    private fun updateTabMetadata(tabId: String, url: String? = null, title: String? = null) {
        val now = System.currentTimeMillis()
        val tabs = _state.value.tabs.map { tab ->
            if (tab.id == tabId) tab.copy(
                url = url ?: tab.url,
                title = title?.takeIf(String::isNotBlank) ?: tab.title,
                lastAccessed = if (isActive(tabId)) now else tab.lastAccessed,
            ) else tab
        }
        _state.update { it.copy(tabs = tabs) }
        cacheActive()
        persistTabs()
    }

    private fun activate(tabId: String, tabs: List<BrowserTab>) {
        val now = System.currentTimeMillis()
        val updatedTabs = tabs.map { if (it.id == tabId) it.copy(lastAccessed = now) else it }
        val tab = updatedTabs.first { it.id == tabId }
        val target = (tabStates[tabId] ?: pageState(tab)).copy(
            tabs = updatedTabs,
            activeTabId = tabId,
            blockPopups = app.preferences.blockPopups,
            sitePreferences = sitePreferences(tab.url),
            shieldEnabled = shield(tab.url),
            message = null,
        )
        tabStates[tabId] = target
        _state.value = target
        app.mediaRepository.resetForPage(tab.url)
        persistTabs()
    }

    private fun cacheActive() {
        val current = _state.value
        tabStates[current.activeTabId] = current
    }

    private fun persistTabs() {
        val current = _state.value
        app.preferences.saveTabs(
            current.tabs.map { PersistedTab(it.id, it.url, it.title, it.lastAccessed) },
            current.activeTabId,
        )
    }

    private fun pageState(tab: BrowserTab): BrowserUiState = BrowserUiState(
        address = displayUrl(tab.url),
        currentUrl = tab.url,
        title = tab.title,
        destination = if (isHome(tab.url)) BrowserDestination.HOME else BrowserDestination.BROWSER,
        shieldEnabled = shield(tab.url),
        blockPopups = app.preferences.blockPopups,
        sitePreferences = sitePreferences(tab.url),
    )

    private fun sitePreferences(url: String): SitePreferences = host(url)
        .takeIf(String::isNotBlank)?.let(app.preferences::sitePreferences) ?: SitePreferences()

    private fun shield(url: String): Boolean = sitePreferences(url).let { it.ads || it.trackers }
    private fun isActive(tabId: String): Boolean = tabId == _state.value.activeTabId
    private fun host(url: String) = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
    private fun displayUrl(url: String) = if (isHome(url)) "" else url
    private fun isHome(url: String) = url == BrowserWebViewClient.HOME_URL ||
        url.contains("appassets.androidplatform.net/assets/home.html")

    private fun isNetworkPage(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme?.lowercase() in setOf("http", "https") &&
            !uri.host.orEmpty().equals("appassets.androidplatform.net", ignoreCase = true)
    }.getOrDefault(false)

    private companion object {
        const val MAX_TABS = 12
        fun newHomeTab() = BrowserTab(
            id = UUID.randomUUID().toString(),
            url = BrowserWebViewClient.HOME_URL,
            title = "Nouvel onglet",
            lastAccessed = System.currentTimeMillis(),
        )
    }
}
