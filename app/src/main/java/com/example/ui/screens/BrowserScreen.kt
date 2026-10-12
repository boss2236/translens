package com.example.ui.screens

import android.annotation.SuppressLint
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import android.app.DownloadManager
import android.os.Environment
import android.os.Message
import android.webkit.URLUtil
import android.webkit.WebResourceResponse
import android.widget.Toast
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.data.adblock.AdBlocker
import com.example.ui.viewmodel.BrowserSettings
import java.io.ByteArrayInputStream
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import com.example.BuildConfig
import com.example.data.model.DetectedTextBlock
import com.example.ui.components.TranslationOverlay
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.Muted
import com.example.ui.theme.OnInk
import com.example.ui.theme.Surface2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** A translated capture plus the scroll position it was taken at, so it can follow scrolling. */
private class LiveFrame(val tabId: String, val blocks: List<DetectedTextBlock>, val scrollX: Int, val scrollY: Int)

/** What was long-pressed in a page. */
private class PressTarget(val linkUrl: String?, val imageUrl: String?)

private val IMAGE_URL = Regex("""\.(jpe?g|png|webp|gif|avif)(\?|$)""", RegexOption.IGNORE_CASE)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    controller: BrowserController,
    browserSettings: BrowserSettings,
    adBlocker: AdBlocker,
    liveEnabled: Boolean,
    status: String?,
    onToggleLive: () -> Unit,
    translateFrame: suspend (Bitmap) -> List<DetectedTextBlock>,
    onTranslateImageUrl: (String) -> Unit,
    onPageVisited: (url: String, title: String) -> Unit,
    onOpenMenu: () -> Unit
) {
    var scrollX by remember { mutableIntStateOf(0) }
    var scrollY by remember { mutableIntStateOf(0) }
    var contentVersion by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf<LiveFrame?>(null) }
    var busy by remember { mutableStateOf(false) }
    var peekOriginal by remember { mutableStateOf(false) }
    var hideForCapture by remember { mutableStateOf(false) }
    var pressTarget by remember { mutableStateOf<PressTarget?>(null) }

    val live by rememberUpdatedState(liveEnabled)
    val prefs by rememberUpdatedState(browserSettings)
    val translate by rememberUpdatedState(translateFrame)
    val visited by rememberUpdatedState(onPageVisited)
    controller.searchTemplate = browserSettings.searchEngine.template
    val activeTab = controller.active

    controller.captureThumbnail = { tab ->
        tab.webView?.let { wv -> captureSoftware(wv, scale = 0.4f)?.let { tab.thumbnail = it.asImageBitmap() } }
    }

    fun isActive(tab: BrowserTab) = controller.activeId == tab.id

    fun createWebView(context: Context, tab: BrowserTab): WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // Only the headless emulator lacks a GPU render node
        val isEmulator = Build.FINGERPRINT.contains("generic") || Build.HARDWARE.contains("ranchu")
        if (isEmulator && !File("/dev/dri/renderD128").exists()) setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        setBackgroundColor(android.graphics.Color.WHITE)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // Some sites block or serve broken pages to the "; wv" WebView user agent
            userAgentString = userAgentString.replace("; wv)", ")")
            controller.mobileUserAgent = userAgentString
            if (prefs.desktopMode) userAgentString = desktopUserAgent(userAgentString)
            textZoom = prefs.textZoom
            // New windows go through onCreateWindow so pop-ups can be filtered
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(this.settings, prefs.darkPages)
        }

        setFindListener { activeMatch, total, _ ->
            if (isActive(tab)) {
                controller.findCurrent = if (total == 0) 0 else activeMatch + 1
                controller.findTotal = total
            }
        }

        setDownloadListener { downloadUrl, userAgent, disposition, mimeType, _ ->
            startDownload(context, downloadUrl, userAgent, disposition, mimeType)
        }

        setOnScrollChangeListener { _, x, y, _, _ ->
            if (isActive(tab)) {
                scrollX = x
                scrollY = y
                if (live) contentVersion++
            }
        }

        // Long-press a link or image: open in new tab, translate image, copy...
        setOnLongClickListener { view ->
            val hit = (view as WebView).hitTestResult
            when (hit.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    pressTarget = PressTarget(hit.extra, null)
                    true
                }
                WebView.HitTestResult.IMAGE_TYPE -> {
                    pressTarget = PressTarget(null, hit.extra)
                    true
                }
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    val image = hit.extra
                    val msg = Handler(Looper.getMainLooper()) { m ->
                        pressTarget = PressTarget(m.data.getString("url") ?: image, image)
                        true
                    }.obtainMessage()
                    view.requestFocusNodeHref(msg)
                    true
                }
                else -> false
            }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (uri.scheme == "http" || uri.scheme == "https") {
                    // Ad redirects / click-hijacks to blocked domains go nowhere
                    if (prefs.adBlock && adBlocker.isBlocked(uri.host)) {
                        tab.blockedCount++
                        return true
                    }
                    return false
                }
                // intent://, market://, tel: etc. otherwise show ERR_UNKNOWN_URL_SCHEME
                try {
                    val intent = if (uri.scheme == "intent") {
                        Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    } else {
                        Intent(Intent.ACTION_VIEW, uri)
                    }
                    val fallback = intent.getStringExtra("browser_fallback_url")
                    try {
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                        if (fallback != null) view?.loadUrl(fallback)
                    }
                } catch (_: Exception) {}
                return true
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                // Runs off the main thread for every request the page makes
                val req = request ?: return null
                if (!prefs.adBlock || req.isForMainFrame || !adBlocker.isBlocked(req.url.host)) return null
                mainHandler.post { tab.blockedCount++ }
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun onPageCommitVisible(view: WebView?, pageUrl: String?) {
                if (prefs.adBlock) view?.evaluateJavascript(HIDE_ADS_JS, null)
            }

            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                tab.blockedCount = 0
                tab.loading = true
                pageUrl?.let { tab.url = it }
                if (isActive(tab)) frame = null
            }

            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                tab.loading = false
                pageUrl?.let { tab.url = it }
                view?.title?.takeIf { it.isNotBlank() }?.let { tab.title = it }
                tab.canGoBack = view?.canGoBack() == true
                if (pageUrl != null) visited(pageUrl, view?.title.orEmpty())
                if (prefs.adBlock) view?.evaluateJavascript(HIDE_ADS_JS, null)
                controller.changed()
                if (isActive(tab)) contentVersion++
            }

            override fun doUpdateVisitedHistory(view: WebView?, pageUrl: String?, isReload: Boolean) {
                pageUrl?.let { tab.url = it }
                tab.canGoBack = view?.canGoBack() == true
            }

            override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {
                if (isActive(tab)) {
                    frame = null // positions no longer match
                    contentVersion++
                }
            }

            override fun onLoadResource(view: WebView?, resUrl: String?) {
                // Lazy-loaded manga pages: re-translate when new images arrive
                if (isActive(tab) && live && !busy && resUrl != null && IMAGE_URL.containsMatchIn(resUrl)) contentVersion++
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                // Pop-ups/pop-unders not started by a tap are dropped
                if (prefs.popupBlock && !isUserGesture) {
                    tab.blockedCount++
                    return false
                }
                // target=_blank links open in a new tab next to this one (same group)
                val catcher = WebView(view.context)
                catcher.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url
                        if (prefs.adBlock && adBlocker.isBlocked(target.host)) {
                            tab.blockedCount++
                        } else {
                            controller.newTab(target.toString(), groupId = tab.groupId, parent = tab)
                        }
                        v.destroy()
                        return true
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = catcher
                resultMsg.sendToTarget()
                return true
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                tab.progress = newProgress / 100f
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) tab.title = title
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                icon?.let { tab.favicon = it.asImageBitmap() }
            }
        }

        loadUrl(tab.url)
    }

    // Apply settings that live on each WebView
    LaunchedEffect(browserSettings.textZoom, browserSettings.darkPages, browserSettings.desktopMode) {
        for (tab in controller.tabs) {
            val wv = tab.webView ?: continue
            wv.settings.textZoom = browserSettings.textZoom
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, browserSettings.darkPages)
            }
            val ua = if (browserSettings.desktopMode) desktopUserAgent(controller.mobileUserAgent) else controller.mobileUserAgent
            if (controller.mobileUserAgent.isNotEmpty() && wv.settings.userAgentString != ua) {
                wv.settings.userAgentString = ua
                wv.reload()
            }
        }
    }

    // Switching tabs: the live overlay belongs to the old page
    LaunchedEffect(controller.activeId) {
        frame = null
        controller.webView?.let {
            scrollX = it.scrollX
            scrollY = it.scrollY
        }
        contentVersion++
    }

    // Back: page history first, then close a tab that was opened from another one
    val parentOpen = activeTab?.parentId?.let { pid -> controller.tabs.any { it.id == pid } } == true
    BackHandler(enabled = activeTab?.canGoBack == true || parentOpen) {
        val tab = controller.active ?: return@BackHandler
        if (tab.webView?.canGoBack() == true) tab.webView?.goBack() else controller.close(tab.id)
    }

    // Live translation loop: whenever the page settles (scroll stops, page/images load), capture
    // what's visible, translate it, and lay the result over the page. Restarts on every change.
    LaunchedEffect(liveEnabled, contentVersion) {
        if (!liveEnabled) {
            frame = null
            return@LaunchedEffect
        }
        delay(350)
        val tab = controller.active ?: return@LaunchedEffect
        val wv = tab.webView ?: return@LaunchedEffect
        if (wv.width == 0 || wv.height == 0) return@LaunchedEffect
        val sx = wv.scrollX
        val sy = wv.scrollY
        val bitmap = captureSoftware(wv) ?: run {
            // Some pages only render through the GPU; copy the screen with the overlay hidden
            hideForCapture = true
            withFrameNanos { }
            withFrameNanos { }
            try {
                capturePixelCopy(wv)
            } finally {
                hideForCapture = false
            }
        } ?: return@LaunchedEffect
        if (BuildConfig.DEBUG) runCatching { saveDebugCapture(wv.context, bitmap) }

        busy = true
        try {
            // Text cut off at the edges is half a sentence; it gets translated once scrolled in
            val blocks = translate(bitmap).filter { it.boundingBox.top > 0.004f && it.boundingBox.bottom < 0.996f }
            frame = LiveFrame(tab.id, blocks, sx, sy)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .navigationBarsPadding()
    ) {
        if (controller.findMode) {
            FindBar(controller)
        } else {
            TopBar(
                url = activeTab?.url.orEmpty(),
                loading = activeTab?.loading == true,
                progress = activeTab?.progress ?: 0f,
                tabCount = controller.tabs.size,
                focusRequests = controller.focusUrlBar,
                onSubmit = { controller.load(it) },
                onTabs = { controller.openSwitcher() },
                onMenu = onOpenMenu
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            // One container; the active tab's WebView is swapped into it
            AndroidView(
                factory = { context -> FrameLayout(context) },
                update = { container ->
                    val tab = controller.active ?: return@AndroidView
                    val wv = tab.webView ?: createWebView(container.context, tab).also { tab.webView = it }
                    if (wv.parent !== container) {
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        container.removeAllViews()
                        container.addView(wv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            val current = frame
            if (liveEnabled && current != null && current.tabId == controller.activeId && !peekOriginal && !hideForCapture) {
                TranslationOverlay(
                    blocks = current.blocks,
                    modifier = Modifier
                        .offset { IntOffset(current.scrollX - scrollX, current.scrollY - scrollY) }
                        .fillMaxSize()
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                StatusPill(status = if (hideForCapture) null else status)
                TranslateButton(
                    live = liveEnabled,
                    busy = busy,
                    onToggle = onToggleLive,
                    onPeek = { peekOriginal = it }
                )
            }
        }

        // Chrome-style strip to hop between tabs of the current group
        controller.group(activeTab?.groupId)?.let { group -> GroupStrip(controller, group) }
    }

    pressTarget?.let { target ->
        PressMenu(
            target = target,
            inGroup = controller.group(activeTab?.groupId),
            onNewTab = { url, sameGroup ->
                controller.newTab(url, groupId = if (sameGroup) activeTab?.groupId else null, parent = activeTab, select = false)
            },
            onTranslateImage = onTranslateImageUrl,
            onDismiss = { pressTarget = null }
        )
    }
}

@Composable
private fun TopBar(
    url: String,
    loading: Boolean,
    progress: Float,
    tabCount: Int,
    focusRequests: Int,
    onSubmit: (String) -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    // New tab: jump straight into the address bar
    LaunchedEffect(focusRequests) { if (focusRequests > 0) runCatching { focusRequester.requestFocus() } }
    var focused by remember { mutableStateOf(false) }
    // The field always holds the full URL; while not editing, only the domain is drawn over it.
    // (Swapping the text on focus races with the keyboard.)
    var field by remember { mutableStateOf(TextFieldValue(url)) }
    LaunchedEffect(url) { if (!focused) field = TextFieldValue(url) }
    // The tap that focuses the field places the cursor right after focus; turn that first
    // cursor placement into select-all so typing replaces the URL (like Chrome)
    var selectAllPending by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    // Some keyboards send both an Enter key event and the IME "Go" action; submit once
    fun submit() {
        if (!focused) return
        focused = false
        val text = field.text
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        if (text.isNotBlank()) onSubmit(text)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 12.dp, end = 4.dp)
        ) {
            BasicTextField(
                value = field,
                onValueChange = { new ->
                    field = if (selectAllPending && new.text == field.text) {
                        new.copy(selection = TextRange(0, new.text.length))
                    } else {
                        new
                    }
                    if (new.text != field.text || new.selection.collapsed) selectAllPending = false
                },
                singleLine = true,
                textStyle = TextStyle(color = OnInk, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        // Hardware / emulator Enter key
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                            submit()
                            true
                        } else {
                            event.key == Key.Enter
                        }
                    }
                    .height(40.dp)
                    .onFocusChanged { state ->
                        if (state.isFocused == focused) return@onFocusChanged
                        selectAllPending = state.isFocused
                        field = if (state.isFocused) {
                            field.copy(selection = TextRange(0, field.text.length))
                        } else {
                            TextFieldValue(url)
                        }
                        focused = state.isFocused
                    },
                decorationBox = { inner ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Surface2)
                            .padding(horizontal = 14.dp)
                    ) {
                        if (!focused && url.startsWith("https://")) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Muted, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (focused && field.text.isEmpty()) {
                                Text("Search or type a URL", color = Muted, fontSize = 15.sp)
                            }
                            if (!focused) {
                                Text(displayHost(url), color = OnInk, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(Modifier.alpha(if (focused) 1f else 0f)) { inner() }
                        }
                        if (focused && field.text.isNotEmpty()) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Muted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { field = TextFieldValue("") }
                            )
                        }
                    }
                }
            )
            Spacer(Modifier.width(4.dp))
            // Tab count, like Chrome
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTabs)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .border(1.6.dp, OnInk, RoundedCornerShape(5.dp))
                ) {
                    Text(if (tabCount > 99) ":D" else "$tabCount", color = OnInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = OnInk)
            }
        }
        if (loading) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0.05f, 1f))
                    .height(2.dp)
                    .background(Accent)
            )
        }
    }
}

/** Tap: live translation on/off. Press and hold: peek at the original page. */
@Composable
private fun TranslateButton(live: Boolean, busy: Boolean, onToggle: () -> Unit, onPeek: (Boolean) -> Unit) {
    val background by animateColorAsState(if (live) Accent else Surface2, label = "translate_bg")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(background)
            .pointerInput(live) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        onPeek(false)
                    },
                    onLongPress = { if (live) onPeek(true) },
                    onTap = { onToggle() }
                )
            }
    ) {
        Icon(
            Icons.Default.Translate,
            contentDescription = if (live) "Turn off live translation" else "Translate this page live",
            tint = if (live) Ink else OnInk
        )
        if (busy) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = if (live) Ink.copy(alpha = 0.55f) else Accent,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

@Composable
fun StatusPill(status: String?, modifier: Modifier = Modifier) {
    var lastStatus by remember { mutableStateOf("") }
    if (status != null) lastStatus = status
    AnimatedVisibility(visible = status != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            text = lastStatus,
            color = OnInk,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface2.copy(alpha = 0.96f))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

private fun displayHost(url: String): String {
    val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()
    return (host ?: url).removePrefix("www.")
}

private val mainHandler = Handler(Looper.getMainLooper())

private val HIDE_ADS_JS =
    "(function(){if(document.getElementById('tl-adblock'))return;var s=document.createElement('style');" +
        "s.id='tl-adblock';s.textContent=\"${AdBlocker.COSMETIC_CSS}\";(document.head||document.documentElement).appendChild(s);})()"

private fun desktopUserAgent(mobile: String): String =
    mobile.replace(Regex("""\(Linux; Android[^)]*\)"""), "(X11; Linux x86_64)").replace(" Mobile", "")

private fun startDownload(context: Context, url: String, userAgent: String, disposition: String?, mimeType: String?) {
    val name = URLUtil.guessFileName(url, disposition, mimeType)
    try {
        val request = DownloadManager.Request(android.net.Uri.parse(url))
            .setMimeType(mimeType)
            .addRequestHeader("User-Agent", userAgent)
            .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        Toast.makeText(context, "Downloading $name", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        // blob:/data: links etc. — let another app handle it
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
private fun FindBar(controller: BrowserController) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun close() {
        controller.webView?.clearMatches()
        controller.findMode = false
    }
    BackHandler { close() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink)
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 12.dp, end = 4.dp)
    ) {
        BasicTextField(
            value = query,
            onValueChange = {
                query = it
                controller.webView?.findAllAsync(it)
            },
            singleLine = true,
            textStyle = TextStyle(color = OnInk, fontSize = 15.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { controller.webView?.findNext(true) }),
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .focusRequester(focus),
            decorationBox = { inner ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Surface2)
                        .padding(horizontal = 14.dp)
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Find in page", color = Muted, fontSize = 15.sp)
                        inner()
                    }
                    if (query.isNotEmpty()) {
                        Text("${controller.findCurrent}/${controller.findTotal}", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        )
        IconButton(onClick = { controller.webView?.findNext(false) }) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous", tint = OnInk)
        }
        IconButton(onClick = { controller.webView?.findNext(true) }) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", tint = OnInk)
        }
        IconButton(onClick = { close() }) {
            Icon(Icons.Default.Close, contentDescription = "Close find", tint = OnInk)
        }
    }
}

fun formatUrl(query: String, searchTemplate: String = "https://www.google.com/search?q=%s"): String {
    val trimmed = query.trim()
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        // host:port (localhost:8080, 192.168.1.5:3000) — local servers are usually plain http
        Regex("""^[\w.-]+:\d+(/.*)?$""").matches(trimmed) -> "http://$trimmed"
        trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
        else -> searchTemplate.replace("%s", java.net.URLEncoder.encode(trimmed, "UTF-8"))
    }
}

/** Renders the WebView's visible area without anything drawn on top of it (our overlay). */
private fun captureSoftware(webView: WebView, scale: Float = 1f): Bitmap? = try {
    if (webView.width == 0 || webView.height == 0) throw IllegalStateException()
    val bitmap = createBitmap((webView.width * scale).toInt(), (webView.height * scale).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    canvas.translate(-webView.scrollX.toFloat(), -webView.scrollY.toFloat())
    webView.draw(canvas)
    if (looksBlank(bitmap)) null else bitmap
} catch (_: Exception) {
    null
}

private fun looksBlank(bitmap: Bitmap): Boolean {
    val first = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    for (iy in 1..9) for (ix in 1..9) {
        if (bitmap.getPixel(bitmap.width * ix / 10, bitmap.height * iy / 10) != first) return false
    }
    return true
}

/** Copies the on-screen pixels (includes GPU-composited content). */
private suspend fun capturePixelCopy(webView: WebView): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val activity = webView.context.findActivity() ?: return null
    val location = IntArray(2)
    webView.getLocationInWindow(location)
    val rect = Rect(location[0], location[1], location[0] + webView.width, location[1] + webView.height)
    val bitmap = createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
    val ok = suspendCancellableCoroutine { cont ->
        try {
            PixelCopy.request(activity.window, rect, bitmap, { result ->
                if (cont.isActive) cont.resume(result == PixelCopy.SUCCESS)
            }, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
            if (cont.isActive) cont.resume(false)
        }
    }
    return if (ok) bitmap else null
}

private fun saveDebugCapture(context: Context, bitmap: Bitmap) {
    File(context.cacheDir, "last_capture.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Slim bar under the page with the current group's tabs. */
@Composable
private fun GroupStrip(controller: BrowserController, group: TabGroup) {
    val tabs = controller.tabsIn(group.id)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Ink)
            .padding(start = 12.dp, end = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { controller.openSwitcher() }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(group.color))
            Spacer(Modifier.width(8.dp))
            Text(
                group.name,
                color = OnInk,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp)
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        ) {
            items(tabs, key = { it.id }) { tab ->
                val selected = tab.id == controller.activeId
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (selected) Surface2 else Color.Transparent)
                        .border(1.5.dp, if (selected) group.color else Color.Transparent, CircleShape)
                        .clickable { controller.select(tab.id) }
                ) {
                    TabIcon(tab, size = 18.dp)
                }
            }
        }
        IconButton(onClick = { controller.newTab(groupId = group.id, parent = controller.active, focusAddressBar = true) }) {
            Icon(Icons.Default.Add, contentDescription = "New tab in group", tint = OnInk)
        }
    }
}

/** Favicon, or the site's first letter. */
@Composable
internal fun TabIcon(tab: BrowserTab, size: Dp) {
    val icon = tab.favicon
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(3.dp)))
    } else {
        val letter = displayHost(tab.url).firstOrNull()?.uppercaseChar() ?: '•'
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size).clip(CircleShape).background(Surface2)
        ) {
            Text(letter.toString(), color = OnInk, fontSize = (size.value * 0.55f).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PressMenu(
    target: PressTarget,
    inGroup: TabGroup?,
    onNewTab: (url: String, sameGroup: Boolean) -> Unit,
    onTranslateImage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                target.linkUrl ?: target.imageUrl.orEmpty(),
                color = Muted,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            target.imageUrl?.let { image ->
                MenuRow(Icons.Default.Translate, "Translate image") { onTranslateImage(image); onDismiss() }
            }
            target.linkUrl?.let { link ->
                MenuRow(Icons.Default.Tab, "Open in new tab") { onNewTab(link, false); onDismiss() }
                if (inGroup != null) {
                    MenuRow(Icons.Default.Layers, "Open in new tab in \"${inGroup.name}\"") { onNewTab(link, true); onDismiss() }
                }
                MenuRow(Icons.Default.ContentCopy, "Copy link") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Link", link))
                    onDismiss()
                }
                MenuRow(Icons.Default.Share, "Share link") {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), null)
                    )
                    onDismiss()
                }
            }
            target.imageUrl?.let { image ->
                if (target.linkUrl == null) {
                    MenuRow(Icons.Default.Tab, "Open image in new tab") { onNewTab(image, false); onDismiss() }
                }
                MenuRow(Icons.Default.Download, "Download image") {
                    startDownload(context, image, WebSettings.getDefaultUserAgent(context), null, null)
                    onDismiss()
                }
            }
        }
    }
}
