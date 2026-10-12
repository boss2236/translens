package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.TranslationEngine
import com.example.ui.components.decodePickedImage
import com.example.ui.screens.BrowserController
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.CameraScreen
import com.example.ui.screens.ImageScreen
import com.example.ui.screens.MenuSheet
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.Ink
import com.example.ui.theme.TransLensTheme
import com.example.ui.viewmodel.FrameSource
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.TransLensViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            TransLensTheme {
                TransLensApp()
            }
        }
    }
}

@Composable
fun TransLensApp(viewModel: TransLensViewModel = viewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val controller = remember { BrowserController() }
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runCatching { decodePickedImage(context, uri) }.getOrNull()?.let(viewModel::translateImage)
    }
    val pickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    BackHandler(enabled = ui.screen != Screen.BROWSER) { viewModel.closeScreen() }

    Box(Modifier.fillMaxSize().background(Ink)) {
        // The browser stays alive underneath so the page isn't reloaded after camera/photo
        BrowserScreen(
            controller = controller,
            startUrl = ui.homeUrl,
            browserSettings = ui.browser,
            adBlocker = viewModel.adBlocker,
            liveEnabled = ui.liveEnabled,
            status = if (ui.screen == Screen.BROWSER) ui.status else null,
            onToggleLive = viewModel::toggleLive,
            translateFrame = { viewModel.translateFrame(it, FrameSource.BROWSER) },
            onPageVisited = viewModel::onPageVisited,
            onOpenMenu = { menuOpen = true }
        )

        when (ui.screen) {
            Screen.IMAGE -> ui.imageBitmap?.let { bitmap ->
                ImageScreen(
                    bitmap = bitmap,
                    blocks = ui.imageBlocks,
                    busy = ui.imageBusy,
                    status = ui.status,
                    onClose = viewModel::closeScreen
                )
            }
            Screen.CAMERA -> CameraScreen(
                translateFrame = { viewModel.translateFrame(it, FrameSource.CAMERA) },
                onFreeze = viewModel::freezeFrame,
                onPickPhoto = pickPhoto,
                onClose = viewModel::closeScreen
            )
            Screen.SETTINGS -> SettingsScreen(
                browser = ui.browser,
                filterStatus = ui.filterStatus,
                filterLists = viewModel.allFilterLists(),
                lastUpdated = viewModel.adBlocker::lastUpdated,
                targetLanguage = ui.targetLanguage,
                online = ui.engine == TranslationEngine.ONLINE_FREE,
                onChange = viewModel::updateBrowser,
                onToggleList = viewModel::setFilterListEnabled,
                onAddList = viewModel::addCustomFilterList,
                onRemoveList = viewModel::removeCustomFilterList,
                onUpdateLists = { viewModel.refreshFilters(force = true) },
                onClearData = {
                    controller.webView?.clearCache(true)
                    controller.webView?.clearHistory()
                    viewModel.clearBrowsingData()
                },
                onSetLanguage = viewModel::setTargetLanguage,
                onSetOnline = viewModel::setOnline,
                onClose = viewModel::closeScreen
            )
            Screen.BROWSER -> Unit
        }

        if (menuOpen) {
            MenuSheet(
                blockedCount = controller.blockedCount,
                adBlock = ui.browser.adBlock,
                desktopMode = ui.browser.desktopMode,
                bookmarks = bookmarks,
                history = history,
                onForward = { controller.webView?.goForward() },
                onReload = { controller.webView?.reload() },
                onBookmark = { viewModel.addBookmark(controller.url, controller.title) },
                onShare = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, controller.title)
                        .putExtra(Intent.EXTRA_TEXT, controller.url)
                    context.startActivity(Intent.createChooser(send, null))
                },
                onFind = { controller.findMode = true },
                onSetDesktop = { on -> viewModel.updateBrowser { it.copy(desktopMode = on) } },
                onOpenUrl = { controller.load(it) },
                onCamera = viewModel::openCamera,
                onPickPhoto = pickPhoto,
                onSettings = viewModel::openSettings,
                onDeleteBookmark = viewModel::deleteBookmark,
                onClearHistory = viewModel::clearHistory,
                onDismiss = { menuOpen = false }
            )
        }
    }
}
