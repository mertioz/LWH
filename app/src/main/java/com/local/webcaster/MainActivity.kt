package com.local.webcaster

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.mediarouter.app.MediaRouteButton
import com.local.webcaster.cast.CastManager
import com.local.webcaster.cast.ExpandedControlsActivity
import com.local.webcaster.detection.QuickCastSelector
import com.local.webcaster.player.LocalPlayerActivity
import com.local.webcaster.security.SharedContentParser
import com.local.webcaster.ui.browser.BrowserDestination
import com.local.webcaster.ui.browser.BrowserScreen
import com.local.webcaster.ui.browser.BrowserViewModel
import com.local.webcaster.ui.theme.LocalWebCasterTheme
import com.local.webcaster.web.BrowserTabHost

class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<BrowserViewModel>()
    private var routeButton: MediaRouteButton? = null
    private lateinit var castManager: CastManager
    private lateinit var tabHost: BrowserTabHost
    private var activeWebView by mutableStateOf<WebView?>(null)
    private var notificationPermissionRequested = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.notify("Les controles Cast resteront disponibles dans CASTER, sans notification systeme.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LocalWebCasterApp
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        castManager = CastManager(this, app.mediaRelay) { routeButton?.performClick() == true }
        tabHost = BrowserTabHost(
            activity = this,
            app = app,
            viewModel = viewModel,
            restoredState = savedInstanceState?.getBundle(TAB_WEB_VIEW_STATES_KEY),
            openInNewTab = ::openNewTab,
            onActiveWebView = { activeWebView = it },
        )
        tabHost.activate(viewModel.state.value.activeTabId)
        enableEdgeToEdge()
        installBackHandler()

        setContent {
            LocalWebCasterTheme {
                val browserState by viewModel.state.collectAsState()
                val candidates by viewModel.candidates.collectAsState()
                val history by viewModel.history.collectAsState()
                val frequent by viewModel.frequent.collectAsState()
                val bookmarks by viewModel.bookmarks.collectAsState()
                val castState by castManager.state.collectAsState()

                LaunchedEffect(castState.connected) {
                    if (castState.connected) requestCastNotificationPermissionIfNeeded()
                }

                BrowserScreen(
                    state = browserState,
                    candidates = candidates,
                    castState = castState,
                    history = history,
                    frequent = frequent,
                    bookmarks = bookmarks,
                    activeWebView = activeWebView,
                    onAddressChange = viewModel::updateAddress,
                    onNavigate = {
                        hideKeyboard()
                        tabHost.activeController?.navigate(viewModel.state.value.address)
                        window.decorView.postDelayed(::hideKeyboard, KEYBOARD_HIDE_DELAY_MS)
                    },
                    onOpenUrl = {
                        hideKeyboard()
                        tabHost.activeController?.navigate(it)
                        window.decorView.postDelayed(::hideKeyboard, KEYBOARD_HIDE_DELAY_MS)
                    },
                    onBack = ::navigateBack,
                    onForward = { tabHost.activeController?.forward() },
                    onReload = { tabHost.activeController?.reloadOrStop(viewModel.state.value.loading) },
                    onHome = ::openHome,
                    onReturnToPage = viewModel::showCurrentPage,
                    onShowHistory = viewModel::showHistory,
                    onShowBookmarks = viewModel::showBookmarks,
                    onShowSettings = viewModel::showSettings,
                    onNewTab = { openNewTab(null) },
                    onSwitchTab = ::switchTab,
                    onCloseTab = ::closeTab,
                    onAddBookmark = viewModel::addCurrentBookmark,
                    onRemoveBookmark = viewModel::removeBookmark,
                    onRenameBookmark = viewModel::renameBookmark,
                    onDeleteHistory = viewModel::deleteHistory,
                    onToggleShield = viewModel::toggleShield,
                    onSiteAds = { viewModel.setSiteAds(it); tabHost.activeController?.webView?.reload() },
                    onSiteTrackers = { viewModel.setSiteTrackers(it); tabHost.activeController?.webView?.reload() },
                    onSitePopups = viewModel::setSitePopups,
                    onSiteQuickCast = viewModel::setSiteQuickCast,
                    onDesktopMode = { enabled ->
                        viewModel.setDesktopMode(enabled)
                        tabHost.activeController?.applySiteMode(reload = true)
                    },
                    onResetSitePreferences = {
                        viewModel.resetSitePreferences()
                        tabHost.activeController?.applySiteMode(reload = true)
                    },
                    onPopupSetting = viewModel::setBlockPopups,
                    onClearHistory = {
                        tabHost.activeController?.clearHistory()
                        viewModel.clearPersistentHistory()
                        viewModel.notify("Historique efface.")
                    },
                    onClearCookies = {
                        tabHost.activeController?.clearCookiesAndSiteData { viewModel.notify("Cookies et donnees de sites effaces.") }
                    },
                    onClearCache = {
                        tabHost.activeController?.clearCache()
                        viewModel.notify("Cache vide.")
                    },
                    onClearAllData = {
                        viewModel.clearPersistentHistory()
                        tabHost.activeController?.clearBrowsingData { viewModel.notify("Donnees de navigation effacees.") }
                        app.mediaRepository.resetForPage("")
                    },
                    onCast = castManager::cast,
                    onCastViaRelay = { castManager.cast(it, forceRelay = true) },
                    onQuickCast = {
                        if (!viewModel.state.value.sitePreferences.quickCast) {
                            viewModel.notify("Quick Cast est desactive pour ce site.")
                        } else {
                            QuickCastSelector.select(viewModel.candidates.value)?.let(castManager::cast)
                                ?: viewModel.notify("Aucun media compatible pour Quick Cast.")
                        }
                    },
                    onPlayNext = castManager::playNext,
                    onAddToQueue = castManager::addToQueue,
                    onLocalPlay = { candidate ->
                        if (castManager.state.value.hasMedia) viewModel.notify("Arretez la lecture Cast avant de lancer la lecture locale.")
                        else LocalPlayerActivity.start(this, candidate)
                    },
                    onCastButtonReady = { routeButton = it },
                    onCastToggle = castManager::togglePlayPause,
                    onCastSeek = castManager::seek,
                    onCastVolume = castManager::setVolume,
                    onCastSubtitle = castManager::setSubtitle,
                    onCastQueuePlay = castManager::playQueueItem,
                    onCastQueueRemove = castManager::removeQueueItem,
                    onCastQueueMove = castManager::moveQueueItem,
                    onCastQueueClear = castManager::clearUpcomingQueue,
                    onOpenExpandedCast = {
                        if (castManager.state.value.showController) {
                            startActivity(Intent(this, ExpandedControlsActivity::class.java))
                        }
                    },
                    onCastStop = castManager::stopMedia,
                    onMessageShown = viewModel::clearMessage,
                    onCastMessageShown = castManager::dismissMessage,
                )
            }
        }
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::tabHost.isInitialized) tabHost.onResume()
    }

    override fun onPause() {
        if (::tabHost.isInitialized) tabHost.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::tabHost.isInitialized) outState.putBundle(TAB_WEB_VIEW_STATES_KEY, tabHost.saveState())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::tabHost.isInitialized) tabHost.destroy()
        activeWebView = null
        routeButton = null
        castManager.release()
        super.onDestroy()
    }

    private fun installBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (viewModel.state.value.destination) {
                    BrowserDestination.BROWSER -> navigateBack()
                    BrowserDestination.HOME -> finish()
                    else -> viewModel.showCurrentPage()
                }
            }
        })
    }

    private fun navigateBack() {
        if (tabHost.activeController?.webView?.canGoBack() == true) tabHost.activeController?.back()
        else openHome()
    }

    private fun openHome() {
        viewModel.showHome()
        tabHost.activeController?.home()
    }

    private fun openNewTab(url: String?) {
        val id = viewModel.createTab(url)
        tabHost.activate(id)
    }

    private fun switchTab(tabId: String) {
        if (viewModel.switchTab(tabId)) tabHost.activate(tabId)
    }

    private fun closeTab(tabId: String) {
        tabHost.close(tabId)
        val activeId = viewModel.closeTab(tabId)
        tabHost.activate(activeId)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return
        val shared = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        } else {
            @Suppress("DEPRECATION") intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        }
        val url = SharedContentParser.extractUrl(shared)
        intent.removeExtra(Intent.EXTRA_TEXT)
        if (url == null) {
            viewModel.notify("Le partage ne contient pas d'adresse web valide.")
            return
        }
        tabHost.activeController?.navigate(url)
    }

    private fun hideKeyboard() {
        val inputMethod = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethod.hideSoftInputFromWindow(currentFocus?.windowToken ?: window.decorView.windowToken, 0)
        currentFocus?.clearFocus()
    }

    private fun requestCastNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationPermissionRequested ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionRequested = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val TAB_WEB_VIEW_STATES_KEY = "browser.tabs.webview.states"
        const val KEYBOARD_HIDE_DELAY_MS = 200L
    }
}
