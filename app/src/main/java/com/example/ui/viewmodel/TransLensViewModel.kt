package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import com.example.data.adblock.AdBlocker
import com.example.data.adblock.FilterList
import com.example.data.db.BookmarkItem
import com.example.data.db.BrowsingHistoryItem
import com.example.data.db.TransLensDatabase
import com.example.data.model.DetectedTextBlock
import com.example.data.model.OcrScriptMode
import com.example.data.model.TranslationEngine
import com.example.data.ocr.OcrEngine
import com.example.data.ocr.OcrResult
import com.example.data.translation.OfflineTranslationService
import com.example.data.translation.OfflineTranslationService.Companion.displayName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private const val MAX_IMAGE_SIDE = 2560
private const val DEFAULT_URL = "https://www.google.com"

enum class Screen { BROWSER, IMAGE, CAMERA, SETTINGS }

enum class SearchEngine(val label: String, val template: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    BRAVE("Brave", "https://search.brave.com/search?q=%s")
}

data class BrowserSettings(
    val adBlock: Boolean = true,
    val popupBlock: Boolean = true,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val darkPages: Boolean = false,
    val textZoom: Int = 100,
    val desktopMode: Boolean = false,
    val enabledLists: Set<String> = setOf("adguard_dns"),
    val customLists: List<String> = emptyList()
)

data class FilterStatus(val domains: Int = 0, val updating: Boolean = false, val lastUpdated: Long = 0L)

/** Where frames come from; each source remembers which script it last saw. */
enum class FrameSource { BROWSER, CAMERA, IMAGE }

data class TransLensUiState(
    val screen: Screen = Screen.BROWSER,
    val homeUrl: String = DEFAULT_URL,
    val liveEnabled: Boolean = false,
    val status: String? = null,
    val imageBitmap: Bitmap? = null,
    val imageBlocks: List<DetectedTextBlock> = emptyList(),
    val imageBusy: Boolean = false,
    val targetLanguage: String = "en",
    val engine: TranslationEngine = TranslationEngine.ONLINE_FREE,
    val browser: BrowserSettings = BrowserSettings(),
    val filterStatus: FilterStatus = FilterStatus()
)

class TransLensViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("translens", Context.MODE_PRIVATE)
    private val dao = TransLensDatabase.getDatabase(application).dao()
    private val ocrEngine = OcrEngine()
    private val translator = OfflineTranslationService()
    val adBlocker = AdBlocker(application)

    private val _uiState = MutableStateFlow(
        TransLensUiState(
            homeUrl = prefs.getString("last_url", null) ?: DEFAULT_URL,
            liveEnabled = prefs.getBoolean("live", false),
            targetLanguage = prefs.getString("target", null) ?: "en",
            engine = if (prefs.getBoolean("online", true)) TranslationEngine.ONLINE_FREE else TranslationEngine.OFFLINE,
            browser = BrowserSettings(
                adBlock = prefs.getBoolean("ad_block", true),
                popupBlock = prefs.getBoolean("popup_block", true),
                searchEngine = runCatching { SearchEngine.valueOf(prefs.getString("search", "")!!) }.getOrDefault(SearchEngine.GOOGLE),
                darkPages = prefs.getBoolean("dark_pages", false),
                textZoom = prefs.getInt("text_zoom", 100),
                desktopMode = prefs.getBoolean("desktop", false),
                enabledLists = prefs.getStringSet("lists", null) ?: setOf("adguard_dns"),
                customLists = prefs.getString("custom_lists", "")!!.split('\n').filter { it.isNotBlank() }
            )
        )
    )
    val uiState: StateFlow<TransLensUiState> = _uiState.asStateFlow()

    val bookmarks = dao.getBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = dao.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val scriptCache = mutableMapOf<FrameSource, OcrScriptMode>()
    private val framesSinceAuto = mutableMapOf<FrameSource, Int>()
    private val failedDownloads = mutableSetOf<String>()
    private val prefetching = mutableSetOf<String>()
    private var imageJob: Job? = null
    private var statusJob: Job? = null
    private var lastHost: String? = null

    init {
        refreshFilters(force = false)
    }

    // ---- browser settings & ad blocking --------------------------------------------------------

    fun updateBrowser(change: (BrowserSettings) -> BrowserSettings) {
        val b = change(_uiState.value.browser)
        _uiState.update { it.copy(browser = b) }
        prefs.edit()
            .putBoolean("ad_block", b.adBlock)
            .putBoolean("popup_block", b.popupBlock)
            .putString("search", b.searchEngine.name)
            .putBoolean("dark_pages", b.darkPages)
            .putInt("text_zoom", b.textZoom)
            .putBoolean("desktop", b.desktopMode)
            .putStringSet("lists", b.enabledLists)
            .putString("custom_lists", b.customLists.joinToString("\n"))
            .apply()
    }

    fun allFilterLists(): List<FilterList> =
        AdBlocker.BUILT_IN + _uiState.value.browser.customLists.map { AdBlocker.custom(it) }

    private fun enabledFilterLists(): List<FilterList> {
        val b = _uiState.value.browser
        return AdBlocker.BUILT_IN.filter { it.id in b.enabledLists } + b.customLists.map { AdBlocker.custom(it) }
    }

    fun setFilterListEnabled(list: FilterList, enabled: Boolean) {
        updateBrowser { b -> b.copy(enabledLists = if (enabled) b.enabledLists + list.id else b.enabledLists - list.id) }
        refreshFilters(force = false)
    }

    fun addCustomFilterList(url: String) {
        val clean = url.trim()
        if (!clean.startsWith("http")) {
            showStatus("Enter a full link starting with https://")
            return
        }
        updateBrowser { b -> b.copy(customLists = (b.customLists + clean).distinct()) }
        refreshFilters(force = false)
    }

    fun removeCustomFilterList(url: String) {
        adBlocker.delete(AdBlocker.custom(url))
        updateBrowser { b -> b.copy(customLists = b.customLists - url) }
        refreshFilters(force = false)
    }

    /** Loads cached lists right away, then downloads missing / week-old ones. */
    fun refreshFilters(force: Boolean) {
        viewModelScope.launch {
            val lists = enabledFilterLists()
            _uiState.update { it.copy(filterStatus = it.filterStatus.copy(updating = true)) }
            adBlocker.reload(lists)
            publishFilterStatus(updating = true)
            var changed = false
            var failed = false
            for (list in lists) {
                if (!force && !adBlocker.needsUpdate(list)) continue
                if (adBlocker.download(list)) changed = true else failed = true
            }
            if (changed) adBlocker.reload(lists)
            publishFilterStatus(updating = false)
            if (force) showStatus(if (failed) "Some filter lists couldn't be updated" else "Filter lists updated")
        }
    }

    private fun publishFilterStatus(updating: Boolean) {
        val last = enabledFilterLists().maxOfOrNull { adBlocker.lastUpdated(it) } ?: 0L
        _uiState.update { it.copy(filterStatus = FilterStatus(adBlocker.rules.size, updating, last)) }
    }

    fun clearBrowsingData() {
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        clearHistory()
        showStatus("Browsing data cleared")
    }

    fun openSettings() = _uiState.update { it.copy(screen = Screen.SETTINGS) }

    // ---- tabs ----------------------------------------------------------------------------------

    fun savedTabs(): String? = prefs.getString("tabs", null)

    fun saveTabs(json: String) = prefs.edit().putString("tabs", json).apply()

    /** Long-pressed image on a page: fetch it (with the page's cookies) and translate it. */
    fun translateImageUrl(url: String) {
        viewModelScope.launch {
            showStatus("Loading image…", sticky = true)
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    if (url.startsWith("data:")) {
                        val bytes = android.util.Base64.decode(url.substringAfter(","), android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else {
                        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        conn.setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(getApplication()))
                        CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
                        conn.connectTimeout = 10_000
                        conn.readTimeout = 20_000
                        try {
                            conn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
                        } finally {
                            conn.disconnect()
                        }
                    }
                }.getOrNull()
            }
            clearStatus()
            if (bitmap == null) showStatus("Couldn't load that image") else translateImage(bitmap)
        }
    }

    // ---- settings ----------------------------------------------------------------------------

    fun toggleLive() {
        val live = !_uiState.value.liveEnabled
        _uiState.update { it.copy(liveEnabled = live) }
        prefs.edit().putBoolean("live", live).apply()
        showStatus(if (live) "Live translation on" else "Live translation off")
    }

    fun setTargetLanguage(code: String) {
        _uiState.update { it.copy(targetLanguage = code) }
        prefs.edit().putString("target", code).apply()
        _uiState.value.imageBitmap?.let { if (_uiState.value.screen == Screen.IMAGE) translateImage(it) }
    }

    fun setOnline(online: Boolean) {
        _uiState.update { it.copy(engine = if (online) TranslationEngine.ONLINE_FREE else TranslationEngine.OFFLINE) }
        prefs.edit().putBoolean("online", online).apply()
        failedDownloads.clear()
    }

    // ---- screens -----------------------------------------------------------------------------

    fun openCamera() = _uiState.update { it.copy(screen = Screen.CAMERA) }

    fun closeScreen() {
        imageJob?.cancel()
        _uiState.update { it.copy(screen = Screen.BROWSER, imageBitmap = null, imageBlocks = emptyList(), imageBusy = false) }
    }

    /** Photo from the gallery: OCR + translate once. */
    fun translateImage(bitmap: Bitmap) {
        imageJob?.cancel()
        imageJob = viewModelScope.launch {
            val image = withContext(Dispatchers.Default) { prepareBitmap(bitmap) }
            _uiState.update { it.copy(screen = Screen.IMAGE, imageBitmap = image, imageBlocks = emptyList(), imageBusy = true) }
            try {
                val blocks = translateFrame(image, FrameSource.IMAGE)
                _uiState.update { it.copy(imageBlocks = blocks, imageBusy = false) }
                if (blocks.isEmpty()) showStatus("No text found")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(imageBusy = false) }
                showStatus("Couldn't translate this image")
            }
        }
    }

    /** Camera shutter: keep the frame that is already translated. */
    fun freezeFrame(bitmap: Bitmap, blocks: List<DetectedTextBlock>) {
        imageJob?.cancel()
        _uiState.update { it.copy(screen = Screen.IMAGE, imageBitmap = bitmap, imageBlocks = blocks, imageBusy = false) }
    }

    // ---- browser -----------------------------------------------------------------------------

    fun onPageVisited(url: String, title: String) {
        prefs.edit().putString("last_url", url).apply()
        val host = runCatching { Uri.parse(url).host }.getOrNull()
        if (host != lastHost) {
            // New site, maybe a new language: re-detect the script on the next frame
            scriptCache.remove(FrameSource.BROWSER)
            lastHost = host
        }
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertHistory(BrowsingHistoryItem(url = url, title = title.ifEmpty { url }))
        }
    }

    fun addBookmark(url: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertBookmark(BookmarkItem(url = url, title = title.ifEmpty { url }))
        }
        showStatus("Bookmarked")
    }

    fun deleteBookmark(bookmark: BookmarkItem) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteBookmark(bookmark) }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) { dao.clearHistory() }
    }

    // ---- pipeline ----------------------------------------------------------------------------

    /** OCR + translate one frame. Normalized boxes refer to [bitmap]. */
    suspend fun translateFrame(bitmap: Bitmap, source: FrameSource): List<DetectedTextBlock> {
        val ocr = recognize(bitmap, source)
        if (ocr.blocks.isEmpty()) return emptyList()
        return translateBlocks(ocr.blocks, ocr.script)
    }

    private suspend fun recognize(bitmap: Bitmap, source: FrameSource): OcrResult {
        val cached = scriptCache[source]
        val count = framesSinceAuto[source] ?: 0
        // Live frames reuse the last detected script (one recognizer instead of five), with a
        // full re-detect every few frames in case the language changed.
        if (cached != null && count < 4) {
            framesSinceAuto[source] = count + 1
            val result = ocrEngine.recognizeText(bitmap, cached)
            if (result.blocks.isNotEmpty()) return result
        }
        framesSinceAuto[source] = 0
        val result = ocrEngine.recognizeText(bitmap, OcrScriptMode.AUTO)
        if (result.blocks.isNotEmpty()) scriptCache[source] = result.script
        return result
    }

    private suspend fun translateBlocks(
        blocks: List<DetectedTextBlock>,
        script: OcrScriptMode
    ): List<DetectedTextBlock> {
        val state = _uiState.value
        val target = OfflineTranslationService.toTranslateLanguage(state.targetLanguage) ?: "en"
        val online = state.engine == TranslationEngine.ONLINE_FREE

        val pageLang = translator.detectPageLanguage(blocks.map { it.originalText }, script)
        val langs = blocks.map { translator.detectLanguage(it.originalText, script, pageLang) }
        val out = blocks.toMutableList()

        for ((src, indices) in blocks.indices.groupBy { langs[it] }) {
            val texts = indices.map { blocks[it].originalText }
            val translated = when (src) {
                target -> texts
                else -> translateGroup(texts, src, target, online)
            }
            indices.forEachIndexed { k, i ->
                out[i] = blocks[i].copy(
                    translatedText = translated[k],
                    detectedLanguage = src ?: "und",
                    isTranslating = false
                )
            }
        }
        return out
    }

    private suspend fun translateGroup(texts: List<String>, src: String?, target: String, online: Boolean): List<String> {
        if (online) {
            translator.translateBatch(texts, src ?: "auto", target, online = true)?.let { result ->
                src?.let { prefetchOfflinePack(it, target) }
                return result
            }
            showStatus("No internet, using offline translation")
        }
        if (src == null || !ensureOfflinePacks(src, target)) return texts
        return translator.translateBatch(texts, src, target, online = false) ?: texts
    }

    private suspend fun ensureOfflinePacks(src: String, target: String): Boolean {
        for (lang in listOf(src, target)) {
            if (translator.isModelReady(lang)) continue
            if (lang in failedDownloads) return false
            showStatus("Downloading ${displayName(lang)} for offline use…", sticky = true)
            try {
                translator.downloadModel(lang)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failedDownloads += lang
                showStatus("${displayName(lang)} isn't available offline yet. Connect to the internet once.")
                return false
            }
        }
        clearStatus()
        return true
    }

    /** Quietly fetches offline packs on Wi-Fi so translation keeps working without internet. */
    private fun prefetchOfflinePack(src: String, target: String) {
        for (lang in listOf(src, target)) {
            if (!prefetching.add(lang)) continue
            viewModelScope.launch {
                runCatching { translator.downloadModel(lang, wifiOnly = true) }
            }
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private fun showStatus(message: String, sticky: Boolean = false) {
        statusJob?.cancel()
        _uiState.update { it.copy(status = message) }
        if (!sticky) {
            statusJob = viewModelScope.launch {
                delay(2500)
                _uiState.update { it.copy(status = null) }
            }
        }
    }

    private fun clearStatus() {
        statusJob?.cancel()
        _uiState.update { it.copy(status = null) }
    }

    /** Converts to a readable software bitmap and caps the size so OCR stays fast and memory-safe. */
    private fun prepareBitmap(source: Bitmap): Bitmap {
        var bitmap = source
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val longest = max(bitmap.width, bitmap.height)
        if (longest > MAX_IMAGE_SIDE) {
            val scale = MAX_IMAGE_SIDE.toFloat() / longest
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        }
        return bitmap
    }

    override fun onCleared() {
        ocrEngine.close()
        translator.close()
    }
}
