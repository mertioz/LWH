package com.local.webcaster.ui.browser

import android.text.format.DateUtils
import android.webkit.WebView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.local.webcaster.cast.CastUiState
import com.local.webcaster.data.Bookmark
import com.local.webcaster.data.HistoryEntry
import com.local.webcaster.detection.MediaCandidate
import com.local.webcaster.localmedia.LocalMediaItem
import com.local.webcaster.localmedia.LocalMediaUiState
import com.local.webcaster.ui.cast.CastMiniController
import com.local.webcaster.ui.cast.CastQueueSheet
import com.local.webcaster.ui.localmedia.LocalMediaScreen
import com.local.webcaster.ui.media.MediaCandidateSheet

@Composable
fun BrowserScreen(
    state: BrowserUiState,
    candidates: List<MediaCandidate>,
    castState: CastUiState,
    history: List<HistoryEntry>,
    frequent: List<HistoryEntry>,
    bookmarks: List<Bookmark>,
    localMediaState: LocalMediaUiState,
    activeWebView: WebView?,
    onAddressChange: (String) -> Unit,
    onNavigate: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onReturnToPage: () -> Unit,
    onShowHistory: () -> Unit,
    onShowBookmarks: () -> Unit,
    onShowLocalMedia: () -> Unit,
    onShowSettings: () -> Unit,
    onPickLocalMedia: () -> Unit,
    onSelectLocalMedia: (Int) -> Unit,
    onPreviousLocalMedia: () -> Unit,
    onNextLocalMedia: () -> Unit,
    onCastLocalMedia: (LocalMediaItem) -> Unit,
    onPlayLocalMedia: (LocalMediaItem) -> Unit,
    onPlayNextLocalMedia: (LocalMediaItem) -> Unit,
    onQueueLocalMedia: (LocalMediaItem) -> Unit,
    onLocalSlideshowEnabled: (Boolean) -> Unit,
    onLocalSlideshowInterval: (Int) -> Unit,
    onLocalSlideshowNext: () -> Unit,
    onNewTab: () -> Unit,
    onSwitchTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onAddBookmark: () -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onRenameBookmark: (String, String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onToggleShield: () -> Unit,
    onSiteAds: (Boolean) -> Unit,
    onSiteTrackers: (Boolean) -> Unit,
    onSitePopups: (Boolean) -> Unit,
    onSiteQuickCast: (Boolean) -> Unit,
    onDesktopMode: (Boolean) -> Unit,
    onResetSitePreferences: () -> Unit,
    onPopupSetting: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onClearCookies: () -> Unit,
    onClearCache: () -> Unit,
    onClearAllData: () -> Unit,
    onCast: (MediaCandidate) -> Unit,
    onCastViaRelay: (MediaCandidate) -> Unit,
    onQuickCast: () -> Unit,
    onPlayNext: (MediaCandidate) -> Unit,
    onAddToQueue: (MediaCandidate) -> Unit,
    onLocalPlay: (MediaCandidate) -> Unit,
    onRescanMedia: (onComplete: () -> Unit) -> Unit,
    onCastButtonReady: (MediaRouteButton?) -> Unit,
    onCastToggle: () -> Unit,
    onCastSeek: (Long) -> Unit,
    onCastVolume: (Float) -> Unit,
    onCastSubtitle: (Long?) -> Unit,
    onCastQueuePlay: (Int) -> Unit,
    onCastQueueRemove: (Int) -> Unit,
    onCastQueueMove: (Int, Int) -> Unit,
    onCastQueueClear: () -> Unit,
    onOpenExpandedCast: () -> Unit,
    onCastStop: () -> Unit,
    onMessageShown: () -> Unit,
    onCastMessageShown: () -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var tabsOpen by remember { mutableStateOf(false) }
    var protectionOpen by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var clearAction by remember { mutableStateOf<ClearAction?>(null) }
    var mediaRescanInProgress by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val rootFocusManager = LocalFocusManager.current
    val rootKeyboard = LocalSoftwareKeyboardController.current

    BackHandler(enabled = sheetOpen || tabsOpen || protectionOpen || queueOpen) {
        when {
            queueOpen -> queueOpen = false
            protectionOpen -> protectionOpen = false
            tabsOpen -> tabsOpen = false
            else -> sheetOpen = false
        }
    }

    LaunchedEffect(state.destination) {
        if (state.destination == BrowserDestination.BROWSER) {
            rootFocusManager.clearFocus(force = true)
            rootKeyboard?.hide()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    LaunchedEffect(castState.message) {
        castState.message?.let {
            snackbar.showSnackbar(it)
            onCastMessageShown()
        }
    }
    LaunchedEffect(candidates.isEmpty()) {
        if (candidates.isEmpty()) sheetOpen = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            BrowserToolbar(
                state = state,
                castState = castState,
                isBookmarked = bookmarks.any { it.url == state.currentUrl },
                onAddressChange = onAddressChange,
                onNavigate = onNavigate,
                onBack = when (state.destination) {
                    BrowserDestination.BROWSER -> onBack
                    BrowserDestination.HOME -> onHome
                    else -> onReturnToPage
                },
                onForward = onForward,
                onReload = onReload,
                onHome = onHome,
                onShowHistory = onShowHistory,
                onShowBookmarks = onShowBookmarks,
                onShowLocalMedia = onShowLocalMedia,
                onShowSettings = onShowSettings,
                onNewTab = onNewTab,
                onShowTabs = { tabsOpen = true },
                onAddBookmark = onAddBookmark,
                onToggleShield = { protectionOpen = true },
                onCastButtonReady = onCastButtonReady,
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = castState.showController,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                CastMiniController(
                    castState,
                    onCastToggle,
                    onCastSeek,
                    onCastVolume,
                    onCastSubtitle,
                    { queueOpen = true },
                    onOpenExpandedCast,
                    onCastStop,
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = candidates.isNotEmpty() && state.destination == BrowserDestination.BROWSER,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallFloatingActionButton(
                        onClick = onQuickCast,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Icon(Icons.Rounded.Cast, "Quick Cast") }
                    SmallFloatingActionButton(
                        onClick = { sheetOpen = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        BadgedBox(badge = { Badge { Text(candidates.size.coerceAtMost(99).toString()) } }) {
                            Icon(Icons.Rounded.VideoLibrary, "Videos detectees")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { FrameLayout(it) },
                update = { container ->
                    val webView = activeWebView
                    if (webView != null && container.getChildAt(0) !== webView) {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        container.removeAllViews()
                        container.addView(
                            webView,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedContent(
                targetState = state.destination,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(tween(170)) togetherWith fadeOut(tween(110)) },
                label = "Destination",
            ) { destination ->
                when (destination) {
                    BrowserDestination.BROWSER -> Unit
                    BrowserDestination.HOME -> HomeContent(
                        address = state.address,
                        recent = history.take(3),
                        frequent = frequent.take(3),
                        bookmarks = bookmarks.take(3),
                        onAddressChange = onAddressChange,
                        onNavigate = onNavigate,
                        onOpenUrl = onOpenUrl,
                        onDeleteHistory = onDeleteHistory,
                        onRemoveBookmark = onRemoveBookmark,
                        onShowHistory = onShowHistory,
                        onShowBookmarks = onShowBookmarks,
                        onShowLocalMedia = onShowLocalMedia,
                    )
                    BrowserDestination.LOCAL_MEDIA -> LocalMediaScreen(
                        state = localMediaState,
                        castState = castState,
                        onPickMedia = onPickLocalMedia,
                        onSelect = onSelectLocalMedia,
                        onPrevious = onPreviousLocalMedia,
                        onNext = onNextLocalMedia,
                        onCastNow = onCastLocalMedia,
                        onLocalPlay = onPlayLocalMedia,
                        onPlayNext = onPlayNextLocalMedia,
                        onAddToQueue = onQueueLocalMedia,
                        onSlideshowEnabled = onLocalSlideshowEnabled,
                        onSlideshowInterval = onLocalSlideshowInterval,
                        onSlideshowNext = onLocalSlideshowNext,
                    )
                    BrowserDestination.HISTORY -> HistoryContent(
                        history = history,
                        onOpenUrl = onOpenUrl,
                        onDelete = onDeleteHistory,
                        onClear = { clearAction = ClearAction.HISTORY },
                    )
                    BrowserDestination.BOOKMARKS -> BookmarksContent(
                        bookmarks = bookmarks,
                        onOpenUrl = onOpenUrl,
                        onRemove = onRemoveBookmark,
                        onRename = onRenameBookmark,
                    )
                    BrowserDestination.SETTINGS -> SettingsContent(
                        blockPopups = state.blockPopups,
                        onPopupSetting = onPopupSetting,
                        onClearHistory = { clearAction = ClearAction.HISTORY },
                        onClearCookies = { clearAction = ClearAction.COOKIES },
                        onClearCache = { clearAction = ClearAction.CACHE },
                        onClearAll = { clearAction = ClearAction.ALL },
                    )
                }
            }
            if (state.loading && state.destination == BrowserDestination.BROWSER) {
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                )
            }
        }
    }

    if (sheetOpen) {
        MediaCandidateSheet(
            candidates = candidates,
            castState = castState,
            isRescanning = mediaRescanInProgress,
            onDismiss = { sheetOpen = false },
            onRescan = {
                if (!mediaRescanInProgress) {
                    mediaRescanInProgress = true
                    onRescanMedia { mediaRescanInProgress = false }
                }
            },
            onCast = { candidate ->
                onCast(candidate)
                if (candidate.unavailableReason == null && !candidate.isDrm) sheetOpen = false
            },
            onCastViaRelay = { candidate ->
                onCastViaRelay(candidate)
                if (candidate.unavailableReason == null && !candidate.isDrm) sheetOpen = false
            },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onLocalPlay = onLocalPlay,
        )
    }

    if (tabsOpen) {
        TabSwitcherSheet(
            tabs = state.tabs,
            activeTabId = state.activeTabId,
            onDismiss = { tabsOpen = false },
            onNewTab = { tabsOpen = false; onNewTab() },
            onSelect = { tabsOpen = false; onSwitchTab(it) },
            onClose = onCloseTab,
        )
    }

    if (protectionOpen) {
        SiteProtectionSheet(
            state = state,
            onDismiss = { protectionOpen = false },
            onAds = onSiteAds,
            onTrackers = onSiteTrackers,
            onPopups = onSitePopups,
            onQuickCast = onSiteQuickCast,
            onDesktopMode = onDesktopMode,
            onReset = onResetSitePreferences,
        )
    }

    if (queueOpen) {
        CastQueueSheet(
            queue = castState.queue,
            onDismiss = { queueOpen = false },
            onPlay = onCastQueuePlay,
            onRemove = onCastQueueRemove,
            onMove = onCastQueueMove,
            onClear = onCastQueueClear,
        )
    }

    clearAction?.let { action ->
        AlertDialog(
            onDismissRequest = { clearAction = null },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text(action.title) },
            text = { Text(action.description) },
            confirmButton = {
                Button(onClick = {
                    clearAction = null
                    when (action) {
                        ClearAction.HISTORY -> onClearHistory()
                        ClearAction.COOKIES -> onClearCookies()
                        ClearAction.CACHE -> onClearCache()
                        ClearAction.ALL -> onClearAllData()
                    }
                }) { Text("Effacer") }
            },
            dismissButton = {
                TextButton(onClick = { clearAction = null }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun BrowserToolbar(
    state: BrowserUiState,
    castState: CastUiState,
    isBookmarked: Boolean,
    onAddressChange: (String) -> Unit,
    onNavigate: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onShowHistory: () -> Unit,
    onShowBookmarks: () -> Unit,
    onShowLocalMedia: () -> Unit,
    onShowSettings: () -> Unit,
    onNewTab: () -> Unit,
    onShowTabs: () -> Unit,
    onAddBookmark: () -> Unit,
    onToggleShield: () -> Unit,
    onCastButtonReady: (MediaRouteButton?) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.destination != BrowserDestination.HOME) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour")
                    }
                }
                if (state.destination != BrowserDestination.BROWSER) {
                    Text(
                        when (state.destination) {
                            BrowserDestination.HOME -> "CASTER"
                            BrowserDestination.LOCAL_MEDIA -> "Médias locaux"
                            BrowserDestination.HISTORY -> "Historique"
                            BrowserDestination.BOOKMARKS -> "Favoris"
                            BrowserDestination.SETTINGS -> "Réglages"
                            BrowserDestination.BROWSER -> ""
                        },
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = if (state.destination == BrowserDestination.HOME) 2.sp else 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    AddressField(
                        value = state.address,
                        shieldEnabled = state.shieldEnabled,
                        onValueChange = onAddressChange,
                        onNavigate = onNavigate,
                        onToggleShield = onToggleShield,
                        focusManager = focusManager,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (castState.frameworkAvailable == false) {
                    IconButton(onClick = {}, enabled = false) {
                        Icon(Icons.Rounded.Cast, "Google Cast indisponible")
                    }
                } else {
                    val context = LocalContext.current
                    AndroidView(
                        factory = {
                            MediaRouteButton(it).also { button ->
                                button.contentDescription = "Choisir un appareil Cast"
                                CastButtonFactory.setUpMediaRouteButton(
                                    context,
                                    ContextCompat.getMainExecutor(context),
                                    button,
                                ).addOnSuccessListener { onCastButtonReady(button) }
                                    .addOnFailureListener { onCastButtonReady(null) }
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        onRelease = { onCastButtonReady(null) },
                    )
                }
                IconButton(onClick = onShowTabs) {
                    if (state.tabs.size > 1) {
                        BadgedBox(badge = { Badge { Text(state.tabs.size.toString()) } }) {
                            Icon(Icons.Rounded.Tab, "Onglets")
                        }
                    } else {
                        Icon(Icons.Rounded.Tab, "Onglets")
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, "Options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (state.destination == BrowserDestination.BROWSER) {
                            DropdownMenuItem(
                                text = { Text(if (state.loading) "Arrêter le chargement" else "Actualiser") },
                                leadingIcon = {
                                    Icon(if (state.loading) Icons.Rounded.Close else Icons.Rounded.Refresh, null)
                                },
                                onClick = { menuOpen = false; onReload() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Nouvel onglet") },
                            leadingIcon = { Icon(Icons.Rounded.Tab, null) },
                            onClick = { menuOpen = false; onNewTab() },
                        )
                        DropdownMenuItem(
                            text = { Text("Accueil") },
                            leadingIcon = { Icon(Icons.Rounded.Home, null) },
                            onClick = { menuOpen = false; onHome() },
                        )
                        DropdownMenuItem(
                            text = { Text("Page suivante") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null) },
                            enabled = state.destination == BrowserDestination.BROWSER && state.canGoForward,
                            onClick = { menuOpen = false; onForward() },
                        )
                        DropdownMenuItem(
                            text = { Text("Médias locaux") },
                            leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) },
                            onClick = { menuOpen = false; onShowLocalMedia() },
                        )
                        DropdownMenuItem(
                            text = { Text("Historique") },
                            leadingIcon = { Icon(Icons.Rounded.History, null) },
                            onClick = { menuOpen = false; onShowHistory() },
                        )
                        DropdownMenuItem(
                            text = { Text("Favoris") },
                            leadingIcon = { Icon(Icons.Rounded.Bookmark, null) },
                            onClick = { menuOpen = false; onShowBookmarks() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (isBookmarked) "Deja dans les favoris" else "Ajouter aux favoris") },
                            leadingIcon = { Icon(if (isBookmarked) Icons.Rounded.Star else Icons.Rounded.BookmarkAdd, null) },
                            enabled = !isBookmarked && state.destination == BrowserDestination.BROWSER,
                            onClick = { menuOpen = false; onAddBookmark() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Parametres") },
                            leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                            onClick = { menuOpen = false; onShowSettings() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressField(
    value: String,
    shieldEnabled: Boolean,
    onValueChange: (String) -> Unit,
    onNavigate: () -> Unit,
    onToggleShield: () -> Unit,
    focusManager: FocusManager,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val container by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(160),
        label = "Address focus",
    )
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        placeholder = { Text("Rechercher", maxLines = 1) },
        leadingIcon = {
            IconButton(onClick = onToggleShield) {
                Icon(
                    if (shieldEnabled) Icons.Rounded.Shield else Icons.Rounded.Security,
                    if (shieldEnabled) "Protection active" else "Protection désactivée",
                    tint = if (shieldEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = container,
            unfocusedContainerColor = container,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = {
            focusManager.clearFocus()
            keyboard?.hide()
            onNavigate()
        }),
    )
}

@Composable
private fun HomeContent(
    address: String,
    recent: List<HistoryEntry>,
    frequent: List<HistoryEntry>,
    bookmarks: List<Bookmark>,
    onAddressChange: (String) -> Unit,
    onNavigate: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onShowHistory: () -> Unit,
    onShowBookmarks: () -> Unit,
    onShowLocalMedia: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Du téléphone\nà la télé",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-1.4).sp,
                    )
                    Text(
                        "Diffusez vos vidéos du Web et vos médias locaux.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                TextField(
                    value = address,
                    onValueChange = onAddressChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Rechercher sur le Web") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        FilledTonalIconButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                onNavigate()
                            },
                            modifier = Modifier.padding(end = 6.dp),
                        ) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Ouvrir", Modifier.size(20.dp)) }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        onNavigate()
                    }),
                )
            }
            item {
                Surface(
                    onClick = onShowLocalMedia,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = Color(0xFF28204A),
                    contentColor = Color(0xFFF7F2FF),
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Color(0xFF28204A), Color(0xFF453388))))
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).background(Color.White.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(20.dp)) }
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Médias locaux", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Vos photos et vidéos sur grand écran",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE1D8FF),
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Accès rapide")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(QUICK_LINKS, key = { it.url }) { link ->
                            QuickLinkCard(link, onOpenUrl)
                        }
                    }
                }
            }
            if (bookmarks.isNotEmpty()) {
                item { SectionHeader("Favoris", "Tout voir", onShowBookmarks) }
                items(bookmarks, key = { "bookmark-${it.url}" }) { bookmark ->
                    SiteRow(
                        title = bookmark.title,
                        domain = bookmark.domain,
                        onClick = { onOpenUrl(bookmark.url) },
                        action = {
                            IconButton(onClick = { onRemoveBookmark(bookmark.url) }) {
                                Icon(Icons.Rounded.Close, "Retirer des favoris")
                            }
                        },
                    )
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionHeader("Pages récentes", "Historique", onShowHistory) }
                items(recent, key = { "recent-${it.url}" }) { entry ->
                    SiteRow(
                        title = entry.title,
                        domain = entry.domain,
                        supporting = relativeTime(entry.lastVisited),
                        onClick = { onOpenUrl(entry.url) },
                        action = {
                            IconButton(onClick = { onDeleteHistory(entry.url) }) {
                                Icon(Icons.Rounded.Close, "Retirer de l'historique")
                            }
                        },
                    )
                }
            }
            if (frequent.isNotEmpty()) {
                item { SectionHeader("Sites fréquents") }
                items(frequent, key = { "frequent-${it.url}" }) { entry ->
                    SiteRow(
                        title = entry.title,
                        domain = entry.domain,
                        supporting = "${entry.visitCount} visites",
                        onClick = { onOpenUrl(entry.url) },
                        action = {
                            IconButton(onClick = { onDeleteHistory(entry.url) }) {
                                Icon(Icons.Rounded.Close, "Retirer")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(
    history: List<HistoryEntry>,
    onOpenUrl: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    InternalListPage(
        title = "Historique",
        subtitle = if (history.isEmpty()) "Aucune page enregistree" else "${history.size} page${if (history.size > 1) "s" else ""}",
        action = if (history.isNotEmpty()) ({ TextButton(onClick = onClear) { Text("Tout effacer") } }) else null,
    ) {
        if (history.isEmpty()) {
            item { EmptyState(Icons.Rounded.History, "Aucun historique", "Les sites visites apparaitront ici.") }
        } else {
            items(history, key = { it.url }) { entry ->
                SiteRow(
                    title = entry.title,
                    domain = entry.domain,
                    supporting = relativeTime(entry.lastVisited),
                    onClick = { onOpenUrl(entry.url) },
                    action = {
                        IconButton(onClick = { onDelete(entry.url) }) {
                            Icon(Icons.Rounded.DeleteOutline, "Supprimer")
                        }
                    },
                )
                HorizontalDivider(Modifier.padding(start = 52.dp))
            }
        }
    }
}

@Composable
private fun BookmarksContent(
    bookmarks: List<Bookmark>,
    onOpenUrl: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRename: (String, String) -> Unit,
) {
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    InternalListPage(
        title = "Favoris",
        subtitle = if (bookmarks.isEmpty()) "Aucun site enregistre" else "${bookmarks.size} site${if (bookmarks.size > 1) "s" else ""}",
    ) {
        if (bookmarks.isEmpty()) {
            item { EmptyState(Icons.Rounded.Bookmark, "Aucun favori", "Ajoutez le site ouvert depuis le menu du navigateur.") }
        } else {
            items(bookmarks, key = { it.url }) { bookmark ->
                SiteRow(
                    title = bookmark.title,
                    domain = bookmark.domain,
                    onClick = { onOpenUrl(bookmark.url) },
                    action = {
                        Row {
                            IconButton(onClick = { editing = bookmark }) {
                                Icon(Icons.Rounded.Edit, "Renommer")
                            }
                            IconButton(onClick = { onRemove(bookmark.url) }) {
                                Icon(Icons.Rounded.DeleteOutline, "Supprimer")
                            }
                        }
                    },
                )
                HorizontalDivider(Modifier.padding(start = 52.dp))
            }
        }
    }
    editing?.let { bookmark ->
        RenameBookmarkDialog(
            bookmark = bookmark,
            onDismiss = { editing = null },
            onSave = { title ->
                onRename(bookmark.url, title)
                editing = null
            },
        )
    }
}

@Composable
private fun SettingsContent(
    blockPopups: Boolean,
    onPopupSetting: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onClearCookies: () -> Unit,
    onClearCache: () -> Unit,
    onClearAll: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("Parametres", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onPopupSetting(!blockPopups) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("Bloquer les popups", fontWeight = FontWeight.SemiBold)
                            Text("Empeche les nouvelles fenetres non sollicitees.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = blockPopups, onCheckedChange = onPopupSetting)
                    }
                }
            }
            item { Text("Donnees de navigation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    SettingsAction(Icons.Rounded.History, "Effacer l'historique", "Conserve les favoris et les connexions.", onClearHistory)
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsAction(Icons.Rounded.Cookie, "Effacer cookies et donnees de sites", "Vous deconnecte des sites web.", onClearCookies)
                    HorizontalDivider(Modifier.padding(start = 56.dp))
                    SettingsAction(Icons.Rounded.Cached, "Vider le cache", "Supprime les fichiers web temporaires.", onClearCache)
                }
            }
            item {
                OutlinedButton(onClick = onClearAll, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.DeleteSweep, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Effacer toutes les donnees de navigation")
                }
            }
            item {
                Text(
                    "Les cookies et sessions de connexion sont conserves automatiquement tant que vous ne les effacez pas ici.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InternalListPage(
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    action?.invoke()
                }
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}

@Composable
private fun SiteRow(
    title: String,
    domain: String,
    onClick: () -> Unit,
    supporting: String? = null,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                domain.firstOrNull()?.uppercase() ?: "W",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(domain.takeIf(String::isNotBlank), supporting).joinToString("  ·  "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action()
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun QuickLinkCard(link: QuickLink, onOpenUrl: (String) -> Unit) {
    Surface(
        onClick = { onOpenUrl(link.url) },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Language, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(link.label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SettingsAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RenameBookmarkDialog(bookmark: Bookmark, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by remember(bookmark.url) { mutableStateOf(bookmark.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer le favori") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(200) },
                label = { Text("Nom") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onSave(title) }, enabled = title.isNotBlank()) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun relativeTime(timestamp: Long): String = DateUtils.getRelativeTimeSpanString(
    timestamp,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
).toString()

private data class QuickLink(val label: String, val url: String)

private val QUICK_LINKS = listOf(
    QuickLink("Google", "https://www.google.com"),
    QuickLink("YouTube", "https://www.youtube.com"),
    QuickLink("Vimeo", "https://vimeo.com"),
    QuickLink("Dailymotion", "https://www.dailymotion.com"),
)

private enum class ClearAction(val title: String, val description: String) {
    HISTORY("Effacer l'historique ?", "Les favoris, cookies et connexions seront conserves."),
    COOKIES("Effacer les cookies et donnees de sites ?", "Vous serez deconnecte des sites web. L'historique et les favoris seront conserves."),
    CACHE("Vider le cache ?", "Les sites pourront etre un peu plus lents lors de leur prochain chargement."),
    ALL("Effacer toutes les donnees de navigation ?", "L'historique, les cookies, les connexions, le stockage des sites et le cache seront effaces. Les favoris seront conserves."),
}
