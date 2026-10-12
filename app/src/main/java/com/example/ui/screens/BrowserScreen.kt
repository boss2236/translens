package com.example.ui.screens

import android.annotation.SuppressLint
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

/** Lets the menu drive the WebView (reload, forward, open a bookmark...) and read page state. */
class BrowserController {
    var webView: WebView? = null
    var searchTemplate: String = "https://www.google.com/search?q=%s"
    var mobileUserAgent: String = ""
    var blockedCount by mutableIntStateOf(0)
    var findMode by mutableStateOf(false)
    var findCurrent by mutableIntStateOf(0)
    var findTotal by mutableIntStateOf(0)
    val url: String get() = webView?.url.orEmpty()
    val title: String get() = webView?.title.orEmpty()
    fun load(url: String) = webView?.loadUrl(formatUrl(url, searchTemplate))
}

/** A translated capture plus the scroll position it was taken at, so it can follow scrolling. */
private class LiveFrame(val blocks: List<DetectedTextBlock>, val scrollX: Int, val scrollY: Int)

private val IMAGE_URL = Regex("""\.(jpe?g|png|webp|gif|avif)(\?|$)""", RegexOption.IGNORE_CASE)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    controller: BrowserController,
    startUrl: String,
    browserSettings: BrowserSettings,
    adBlocker: AdBlocker,
    liveEnabled: Boolean,
    status: String?,
    onToggleLive: () -> Unit,
    translateFrame: suspend (Bitmap) -> List<DetectedTextBlock>,
    onPageVisited: (url: String, title: String) -> Unit,
    onOpenMenu: () -> Unit
) {
    var url by remember { mutableStateOf(startUrl) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var scrollX by remember { mutableIntStateOf(0) }
    var scrollY by remember { mutableIntStateOf(0) }
    var contentVersion by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf<LiveFrame?>(null) }
    var busy by remember { mutableStateOf(false) }
    var peekOriginal by remember { mutableStateOf(false) }
    var hideForCapture by remember { mutableStateOf(false) }

    val live by rememberUpdatedState(liveEnabled)
    val prefs by rememberUpdatedState(browserSettings)
    controller.searchTemplate = browserSettings.searchEngine.template

    // Apply settings that live on the WebView itself
    LaunchedEffect(browserSettings.textZoom, browserSettings.darkPages, browserSettings.desktopMode) {
        val wv = controller.webView ?: return@LaunchedEffect
        wv.settings.textZoom = browserSettings.textZoom
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, browserSettings.darkPages)
        }
        val ua = if (browserSettings.desktopMode) desktopUserAgent(controller.mobileUserAgent) else controller.mobileUserAgent
        if (wv.settings.userAgentString != ua) {
            wv.settings.userAgentString = ua
            wv.settings.loadWithOverviewMode = true
            wv.reload()
        }
    }
    val translate by rememberUpdatedState(translateFrame)
    val visited by rememberUpdatedState(onPageVisited)

    BackHandler(enabled = canGoBack) { controller.webView?.goBack() }

    // Live translation loop: whenever the page settles (scroll stops, page/images load), capture
    // what's visible, translate it, and lay the result over the page. Restarts on every change.
    LaunchedEffect(liveEnabled, contentVersion) {
        if (!liveEnabled) {
            frame = null
            return@LaunchedEffect
        }
        delay(350)
        val wv = controller.webView ?: return@LaunchedEffect
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
            frame = LiveFrame(blocks, sx, sy)
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
                url = url,
                loading = loading,
                progress = progress,
                onSubmit = { controller.load(it) },
                onMenu = onOpenMenu
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // Only the headless emulator lacks a GPU render node
                        val isEmulator = Build.FINGERPRINT.contains("generic") || Build.HARDWARE.contains("ranchu")
                        if (isEmulator && !File("/dev/dri/renderD128").exists()) {
                            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        }
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
                            // Pop-ups go through onCreateWindow so they can be filtered
                            setSupportMultipleWindows(true)
                            javaScriptCanOpenWindowsAutomatically = true
                        }
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(this.settings, prefs.darkPages)
                        }

                        setFindListener { active, total, _ ->
                            controller.findCurrent = if (total == 0) 0 else active + 1
                            controller.findTotal = total
                        }

                        setDownloadListener { downloadUrl, userAgent, disposition, mimeType, _ ->
                            startDownload(context, downloadUrl, userAgent, disposition, mimeType)
                        }

                        setOnScrollChangeListener { _, x, y, _, _ ->
                            scrollX = x
                            scrollY = y
                            if (live) contentVersion++
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val uri = request?.url ?: return false
                                if (uri.scheme == "http" || uri.scheme == "https") {
                                    // Ad redirects / click-hijacks to blocked domains go nowhere
                                    if (prefs.adBlock && adBlocker.isBlocked(uri.host)) {
                                        controller.blockedCount++
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
                                mainHandler.post { controller.blockedCount++ }
                                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                            }

                            override fun onPageCommitVisible(view: WebView?, pageUrl: String?) {
                                if (prefs.adBlock) view?.evaluateJavascript(HIDE_ADS_JS, null)
                            }

                            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                                controller.blockedCount = 0
                                loading = true
                                frame = null
                                pageUrl?.let { url = it }
                            }

                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                loading = false
                                pageUrl?.let { url = it }
                                canGoBack = view?.canGoBack() == true
                                if (pageUrl != null) visited(pageUrl, view?.title.orEmpty())
                                if (prefs.adBlock) view?.evaluateJavascript(HIDE_ADS_JS, null)
                                contentVersion++
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, pageUrl: String?, isReload: Boolean) {
                                pageUrl?.let { url = it }
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onScaleChanged(view: WebView?, oldScale: Float, newScale: Float) {
                                frame = null // positions no longer match
                                contentVersion++
                            }

                            override fun onLoadResource(view: WebView?, resUrl: String?) {
                                // Lazy-loaded manga pages: re-translate when new images arrive
                                if (live && !busy && resUrl != null && IMAGE_URL.containsMatchIn(resUrl)) contentVersion++
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message
                            ): Boolean {
                                // Pop-ups/pop-unders not started by a tap are dropped
                                if (prefs.popupBlock && !isUserGesture) {
                                    controller.blockedCount++
                                    return false
                                }
                                // Everything else (target=_blank links) opens in this same tab
                                val catcher = WebView(view.context)
                                catcher.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                                        val target = request.url
                                        if (prefs.adBlock && adBlocker.isBlocked(target.host)) {
                                            controller.blockedCount++
                                        } else {
                                            view.loadUrl(target.toString())
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
                                progress = newProgress / 100f
                            }
                        }

                        loadUrl(formatUrl(startUrl, prefs.searchEngine.template))
                        controller.webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            val current = frame
            if (liveEnabled && current != null && !peekOriginal && !hideForCapture) {
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
    }
}

@Composable
private fun TopBar(
    url: String,
    loading: Boolean,
    progress: Float,
    onSubmit: (String) -> Unit,
    onMenu: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // The field always holds the full URL; while not editing, only the domain is drawn over it.
    // (Swapping the text on focus races with the keyboard.)
    var field by remember { mutableStateOf(TextFieldValue(url)) }
    LaunchedEffect(url) { if (!focused) field = TextFieldValue(url) }
    // The tap that focuses the field places the cursor right after focus; turn that first
    // cursor placement into select-all so typing replaces the URL (like Chrome)
    var selectAllPending by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() {
        if (field.text.isNotBlank()) onSubmit(field.text)
        keyboard?.hide()
        focusManager.clearFocus(force = true)
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
private fun captureSoftware(webView: WebView): Bitmap? = try {
    val bitmap = createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
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
