package com.local.webcaster

import android.os.Bundle
import android.webkit.WebView
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.mediarouter.app.MediaRouteButton
import com.local.webcaster.cast.CastManager
import com.local.webcaster.ui.browser.BrowserScreen
import com.local.webcaster.ui.browser.BrowserViewModel
import com.local.webcaster.ui.theme.LocalWebCasterTheme
import com.local.webcaster.web.BrowserController

class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<BrowserViewModel>()
    private var browserController: BrowserController? = null
    private var routeButton: MediaRouteButton? = null
    private lateinit var castManager: CastManager
    private var restoredWebViewState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LocalWebCasterApp
        restoredWebViewState = savedInstanceState?.getBundle(WEB_VIEW_STATE_KEY)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        castManager = CastManager(this, app.mediaRelay) { routeButton?.performClick() == true }
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (viewModel.state.value.destination) {
                    com.local.webcaster.ui.browser.BrowserDestination.BROWSER -> {
                        if (browserController?.webView?.canGoBack() == true) browserController?.back()
                        else {
                            viewModel.showHome()
                            browserController?.home()
                        }
                    }
                    com.local.webcaster.ui.browser.BrowserDestination.HOME -> finish()
                    else -> {
                        viewModel.showHome()
                        browserController?.home()
                    }
                }
            }
        })

        setContent {
            LocalWebCasterTheme {
                val browserState by viewModel.state.collectAsState()
                val candidates by viewModel.candidates.collectAsState()
                val history by viewModel.history.collectAsState()
                val frequent by viewModel.frequent.collectAsState()
                val bookmarks by viewModel.bookmarks.collectAsState()
                val castState by castManager.state.collectAsState()

                BrowserScreen(
                    state = browserState,
                    candidates = candidates,
                    castState = castState,
                    history = history,
                    frequent = frequent,
                    bookmarks = bookmarks,
                    webViewFactory = { webView ->
                        if (browserController == null) {
                            browserController = BrowserController(
                                this,
                                webView,
                                app.mediaDetector,
                                app.adBlockEngine,
                                app.preferences,
                                viewModel,
                            ).also { controller ->
                                val restored = restoredWebViewState?.let(controller::restoreState) == true
                                restoredWebViewState = null
                                if (!restored) controller.home()
                            }
                        }
                    },
                    onAddressChange = viewModel::updateAddress,
                    onNavigate = {
                        hideKeyboard()
                        browserController?.navigate(viewModel.state.value.address)
                        window.decorView.postDelayed(::hideKeyboard, KEYBOARD_HIDE_DELAY_MS)
                    },
                    onOpenUrl = {
                        hideKeyboard()
                        browserController?.navigate(it)
                        window.decorView.postDelayed(::hideKeyboard, KEYBOARD_HIDE_DELAY_MS)
                    },
                    onBack = {
                        if (browserController?.webView?.canGoBack() == true) browserController?.back()
                        else {
                            viewModel.showHome()
                            browserController?.home()
                        }
                    },
                    onForward = { browserController?.forward() },
                    onReload = { browserController?.reloadOrStop(viewModel.state.value.loading) },
                    onHome = {
                        viewModel.showHome()
                        browserController?.home()
                    },
                    onShowHistory = viewModel::showHistory,
                    onShowBookmarks = viewModel::showBookmarks,
                    onShowSettings = viewModel::showSettings,
                    onAddBookmark = viewModel::addCurrentBookmark,
                    onRemoveBookmark = viewModel::removeBookmark,
                    onRenameBookmark = viewModel::renameBookmark,
                    onDeleteHistory = viewModel::deleteHistory,
                    onToggleShield = {
                        viewModel.toggleShield()
                        browserController?.webView?.reload()
                    },
                    onPopupSetting = viewModel::setBlockPopups,
                    onClearHistory = {
                        browserController?.clearHistory()
                        viewModel.clearPersistentHistory()
                        viewModel.notify("Historique efface.")
                    },
                    onClearCookies = {
                        browserController?.clearCookiesAndSiteData {
                            viewModel.notify("Cookies et donnees de sites effaces.")
                        }
                    },
                    onClearCache = {
                        browserController?.clearCache()
                        viewModel.notify("Cache vide.")
                    },
                    onClearAllData = {
                        viewModel.clearPersistentHistory()
                        browserController?.clearBrowsingData {
                            viewModel.notify("Donnees de navigation effacees.")
                        }
                        app.mediaRepository.resetForPage("")
                    },
                    onCast = { castManager.cast(it) },
                    onCastViaRelay = { castManager.cast(it, forceRelay = true) },
                    onCastButtonReady = { routeButton = it },
                    onCastToggle = castManager::togglePlayPause,
                    onCastSeek = castManager::seek,
                    onCastStop = castManager::stopMedia,
                    onMessageShown = viewModel::clearMessage,
                    onCastMessageShown = castManager::dismissMessage,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        browserController?.onResume()
    }

    override fun onPause() {
        browserController?.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        browserController?.saveState()?.let { outState.putBundle(WEB_VIEW_STATE_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        browserController?.destroy()
        browserController = null
        routeButton = null
        castManager.release()
        super.onDestroy()
    }

    private fun hideKeyboard() {
        val inputMethod = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethod.hideSoftInputFromWindow(currentFocus?.windowToken ?: window.decorView.windowToken, 0)
        currentFocus?.clearFocus()
    }

    private companion object {
        const val WEB_VIEW_STATE_KEY = "browser.webview.state"
        const val KEYBOARD_HIDE_DELAY_MS = 200L
    }
}
