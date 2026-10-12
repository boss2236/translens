package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.BookmarkItem
import com.example.data.db.BrowsingHistoryItem
import com.example.data.model.SupportedLanguages
import com.example.ui.theme.Accent
import com.example.ui.theme.Hairline
import com.example.ui.theme.Ink
import com.example.ui.theme.Muted
import com.example.ui.theme.OnInk

private enum class MenuPage { MAIN, BOOKMARKS, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    blockedCount: Int,
    adBlock: Boolean,
    desktopMode: Boolean,
    bookmarks: List<BookmarkItem>,
    history: List<BrowsingHistoryItem>,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onFind: () -> Unit,
    onNewTab: () -> Unit,
    onSetDesktop: (Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCamera: () -> Unit,
    onPickPhoto: () -> Unit,
    onSettings: () -> Unit,
    onDeleteBookmark: (BookmarkItem) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    var page by remember { mutableStateOf(MenuPage.MAIN) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            when (page) {
                MenuPage.MAIN -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = { onForward(); onDismiss() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", tint = OnInk)
                        }
                        IconButton(onClick = { onReload(); onDismiss() }) {
                            Icon(Icons.Default.Refresh, "Reload", tint = OnInk)
                        }
                        IconButton(onClick = { onBookmark(); onDismiss() }) {
                            Icon(Icons.Default.BookmarkBorder, "Bookmark this page", tint = OnInk)
                        }
                        IconButton(onClick = { onShare(); onDismiss() }) {
                            Icon(Icons.Default.Share, "Share", tint = OnInk)
                        }
                    }
                    if (adBlock) {
                        Text(
                            text = if (blockedCount == 1) "1 ad blocked on this page" else "$blockedCount ads blocked on this page",
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                        )
                    }
                    MenuDivider()
                    MenuRow(Icons.Default.Add, "New tab") { onNewTab(); onDismiss() }
                    MenuRow(Icons.Default.Search, "Find in page") { onFind(); onDismiss() }
                    MenuRow(
                        Icons.Default.DesktopWindows,
                        "Desktop site",
                        trailing = { MenuSwitch(desktopMode, onSetDesktop) }
                    ) { onSetDesktop(!desktopMode) }
                    MenuDivider()
                    MenuRow(Icons.Default.PhotoCamera, "Camera") { onCamera(); onDismiss() }
                    MenuRow(Icons.Default.PhotoLibrary, "Translate a photo") { onPickPhoto(); onDismiss() }
                    MenuDivider()
                    MenuRow(Icons.Default.Bookmarks, "Bookmarks") { page = MenuPage.BOOKMARKS }
                    MenuRow(Icons.Default.History, "History") { page = MenuPage.HISTORY }
                    MenuRow(Icons.Default.Settings, "Settings") { onSettings(); onDismiss() }
                }

                MenuPage.BOOKMARKS -> {
                    SubHeader("Bookmarks") { page = MenuPage.MAIN }
                    if (bookmarks.isEmpty()) Empty("No bookmarks yet")
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(bookmarks, key = { it.id }) { item ->
                            LinkRow(item.title, item.url, onClick = { onOpenUrl(item.url); onDismiss() }) {
                                IconButton(onClick = { onDeleteBookmark(item) }) {
                                    Icon(Icons.Default.Close, "Remove", tint = Muted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                MenuPage.HISTORY -> {
                    SubHeader("History", action = if (history.isNotEmpty()) "Clear" else null, onAction = onClearHistory) {
                        page = MenuPage.MAIN
                    }
                    if (history.isEmpty()) Empty("Nothing here yet")
                    LazyColumn(Modifier.heightIn(max = 480.dp)) {
                        items(history, key = { it.id }) { item ->
                            LinkRow(item.title, item.url, onClick = { onOpenUrl(item.url); onDismiss() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MenuSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Ink)
    )
}

@Composable
internal fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = OnInk, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun SubHeader(title: String, action: String? = null, onAction: () -> Unit = {}, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnInk) }
        Text(title, color = OnInk, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                color = Accent,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onAction).padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
internal fun LinkRow(title: String, url: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = OnInk, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
internal fun Empty(text: String) {
    Text(text, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp))
}

@Composable
internal fun MenuDivider() {
    HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
}
