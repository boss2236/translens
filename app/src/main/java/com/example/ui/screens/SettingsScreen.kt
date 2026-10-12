package com.example.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.adblock.AdBlocker
import com.example.data.adblock.FilterList
import com.example.data.model.SupportedLanguages
import com.example.ui.theme.Accent
import com.example.ui.theme.Hairline
import com.example.ui.theme.Ink
import com.example.ui.theme.Muted
import com.example.ui.theme.OnInk
import com.example.ui.theme.Surface2
import com.example.ui.viewmodel.BrowserSettings
import com.example.ui.viewmodel.FilterStatus
import com.example.ui.viewmodel.SearchEngine
import java.text.NumberFormat
import kotlin.math.roundToInt

private enum class SettingsPage(val title: String) {
    MAIN("Settings"),
    FILTERS("Filter lists"),
    SEARCH("Search engine"),
    LANGUAGE("Translate to")
}

@Composable
fun SettingsScreen(
    browser: BrowserSettings,
    filterStatus: FilterStatus,
    filterLists: List<FilterList>,
    lastUpdated: (FilterList) -> Long,
    targetLanguage: String,
    online: Boolean,
    onChange: ((BrowserSettings) -> BrowserSettings) -> Unit,
    onToggleList: (FilterList, Boolean) -> Unit,
    onAddList: (String) -> Unit,
    onRemoveList: (String) -> Unit,
    onUpdateLists: () -> Unit,
    onClearData: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetOnline: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    var page by remember { mutableStateOf(SettingsPage.MAIN) }
    var confirmClear by remember { mutableStateOf(false) }
    val back = { if (page == SettingsPage.MAIN) onClose() else page = SettingsPage.MAIN }
    BackHandler { back() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnInk) }
            Text(page.title, color = OnInk, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            when (page) {
                SettingsPage.MAIN -> {
                    Section("Ads & privacy")
                    MenuRow(
                        Icons.Default.Shield,
                        "Block ads",
                        subtitle = when {
                            !browser.adBlock -> "Off"
                            filterStatus.updating -> "Updating filter lists…"
                            else -> "${NumberFormat.getInstance().format(filterStatus.domains)} ad & tracker domains" +
                                (if (filterStatus.lastUpdated > 0) " · updated ${ago(filterStatus.lastUpdated)}" else "")
                        },
                        trailing = { MenuSwitch(browser.adBlock) { on -> onChange { it.copy(adBlock = on) } } }
                    ) { onChange { it.copy(adBlock = !it.adBlock) } }
                    MenuRow(
                        Icons.Default.Block,
                        "Block pop-ups",
                        subtitle = "Stops pages opening windows on their own",
                        trailing = { MenuSwitch(browser.popupBlock) { on -> onChange { it.copy(popupBlock = on) } } }
                    ) { onChange { it.copy(popupBlock = !it.popupBlock) } }
                    MenuRow(Icons.Default.FilterList, "Filter lists", subtitle = "AdGuard, AdAway or your own") {
                        page = SettingsPage.FILTERS
                    }
                    MenuRow(Icons.Default.DeleteOutline, "Clear browsing data", subtitle = "History, cookies and site data") {
                        confirmClear = true
                    }

                    Section("Browsing")
                    MenuRow(
                        Icons.Default.Search,
                        "Search engine",
                        trailing = { Text(browser.searchEngine.label, color = Muted, fontSize = 14.sp) }
                    ) { page = SettingsPage.SEARCH }
                    MenuRow(
                        Icons.Default.DarkMode,
                        "Dark websites",
                        subtitle = "Darken pages that only have a light theme",
                        trailing = { MenuSwitch(browser.darkPages) { on -> onChange { it.copy(darkPages = on) } } }
                    ) { onChange { it.copy(darkPages = !it.darkPages) } }
                    TextSizeRow(browser.textZoom) { zoom -> onChange { it.copy(textZoom = zoom) } }

                    Section("Translation")
                    MenuRow(
                        Icons.Default.Translate,
                        "Translate to",
                        trailing = { Text(SupportedLanguages.find(targetLanguage).name, color = Muted, fontSize = 14.sp) }
                    ) { page = SettingsPage.LANGUAGE }
                    MenuRow(
                        Icons.Default.Wifi,
                        "Online translation",
                        subtitle = if (online) "Best quality. Falls back to offline without internet" else "On-device only",
                        trailing = { MenuSwitch(online, onSetOnline) }
                    ) { onSetOnline(!online) }

                    Text(
                        "TransLens ${BuildConfig.VERSION_NAME}",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                SettingsPage.FILTERS -> FilterListsPage(
                    lists = filterLists,
                    enabled = browser.enabledLists,
                    updating = filterStatus.updating,
                    lastUpdated = lastUpdated,
                    onToggle = onToggleList,
                    onAdd = onAddList,
                    onRemove = onRemoveList,
                    onUpdate = onUpdateLists
                )

                SettingsPage.SEARCH -> SearchEngine.entries.forEach { engine ->
                    CheckRow(engine.label, selected = engine == browser.searchEngine) {
                        onChange { it.copy(searchEngine = engine) }
                        page = SettingsPage.MAIN
                    }
                }

                SettingsPage.LANGUAGE -> SupportedLanguages.ALL.forEach { lang ->
                    CheckRow(lang.name, detail = lang.nativeName, selected = lang.code == targetLanguage) {
                        onSetLanguage(lang.code)
                        page = SettingsPage.MAIN
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear browsing data?") },
            text = { Text("History, cookies and site data will be deleted. Bookmarks are kept.") },
            confirmButton = {
                TextButton(onClick = { onClearData(); confirmClear = false }) { Text("Clear", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = Muted) }
            },
            containerColor = Surface2
        )
    }
}

@Composable
private fun FilterListsPage(
    lists: List<FilterList>,
    enabled: Set<String>,
    updating: Boolean,
    lastUpdated: (FilterList) -> Long,
    onToggle: (FilterList, Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: () -> Unit
) {
    Text(
        "Ads are blocked by domain using the same public lists as AdGuard and AdAway. Lists refresh weekly.",
        color = Muted,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
    for (list in lists) {
        val updated = lastUpdated(list)
        val status = if (updated > 0) "Updated ${ago(updated)}" else if (list.builtIn && list.id !in enabled) "Off" else "Not downloaded yet"
        if (list.builtIn) {
            MenuRow(
                Icons.Default.FilterList,
                list.name,
                subtitle = status,
                trailing = { MenuSwitch(list.id in enabled) { onToggle(list, it) } }
            ) { onToggle(list, list.id !in enabled) }
        } else {
            MenuRow(
                Icons.Default.OpenInNew,
                list.name,
                subtitle = status,
                trailing = {
                    IconButton(onClick = { onRemove(list.url) }) {
                        Icon(Icons.Default.Close, "Remove", tint = Muted, modifier = Modifier.size(18.dp))
                    }
                }
            ) {}
        }
    }
    MenuRow(Icons.Default.Refresh, if (updating) "Updating…" else "Update now") { if (!updating) onUpdate() }
    MenuDivider()

    var url by remember { mutableStateOf("") }
    Text("Add a list", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp, end = 12.dp)) {
        BasicTextField(
            value = url,
            onValueChange = { url = it },
            singleLine = true,
            textStyle = TextStyle(color = OnInk, fontSize = 14.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank()) { onAdd(url); url = "" } }),
            modifier = Modifier.weight(1f).height(40.dp),
            decorationBox = { inner ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Surface2).padding(horizontal = 14.dp)
                ) {
                    if (url.isEmpty()) Text("https://… filter list link", color = Muted, fontSize = 14.sp)
                    inner()
                }
            }
        )
        IconButton(onClick = { if (url.isNotBlank()) { onAdd(url); url = "" } }) {
            Icon(Icons.Default.Add, "Add list", tint = Accent)
        }
    }
    Text(
        "Works with AdGuard / uBlock domain rules (||example.com^) and hosts files.",
        color = Muted,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun TextSizeRow(zoom: Int, onChange: (Int) -> Unit) {
    var value by remember(zoom) { mutableFloatStateOf(zoom.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FormatSize, null, tint = Muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(20.dp))
            Text("Text size", color = OnInk, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("${value.roundToInt()}%", color = Muted, fontSize = 14.sp)
        }
        Slider(
            value = value,
            onValueChange = { value = (it / 10).roundToInt() * 10f },
            onValueChangeFinished = { onChange(value.roundToInt()) },
            valueRange = 70f..170f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Hairline),
            modifier = Modifier.padding(start = 42.dp)
        )
    }
}

@Composable
private fun CheckRow(title: String, detail: String? = null, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(title, color = OnInk, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, color = Muted, fontSize = 14.sp)
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.Check, null, tint = Accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Accent, fontSize = 13.sp, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp))
}

private fun ago(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
