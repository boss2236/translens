package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.Muted
import com.example.ui.theme.OnInk
import com.example.ui.theme.Surface1
import com.example.ui.theme.Surface2

private sealed class Entry(val key: String) {
    class Tab(val tab: BrowserTab) : Entry(tab.id)
    class Group(val group: TabGroup, val tabs: List<BrowserTab>) : Entry("g_" + group.id)
}

/** Asks for a group name (and color); [tabId] is the tab to put in a new group. */
private class GroupEdit(val groupId: String?, val tabId: String?, val name: String, val color: Int)

@Composable
fun TabSwitcherScreen(controller: BrowserController) {
    var openGroupId by remember { mutableStateOf<String?>(null) }
    var actionsFor by remember { mutableStateOf<BrowserTab?>(null) }
    var edit by remember { mutableStateOf<GroupEdit?>(null) }
    var overflow by remember { mutableStateOf(false) }
    val openGroup = controller.group(openGroupId)
    if (openGroupId != null && openGroup == null) openGroupId = null

    BackHandler { if (openGroupId != null) openGroupId = null else controller.switcherOpen = false }

    // Groups appear as one card where their first tab sits
    val entries: List<Entry> = if (openGroup != null) {
        controller.tabsIn(openGroup.id).map { Entry.Tab(it) }
    } else {
        val seen = mutableSetOf<String>()
        controller.tabs.mapNotNull { tab ->
            val group = controller.group(tab.groupId)
            when {
                group == null -> Entry.Tab(tab)
                seen.add(group.id) -> Entry.Group(group, controller.tabsIn(group.id))
                else -> null
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)
        ) {
            IconButton(onClick = { if (openGroupId != null) openGroupId = null else controller.switcherOpen = false }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnInk)
            }
            if (openGroup != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { edit = GroupEdit(openGroup.id, null, openGroup.name, openGroup.colorIndex) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(openGroup.color))
                    Spacer(Modifier.width(10.dp))
                    Text(openGroup.name, color = OnInk, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                val n = controller.tabs.size
                Text(if (n == 1) "1 tab" else "$n tabs", color = OnInk, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
            }
            IconButton(onClick = { controller.newTab(groupId = openGroup?.id, parent = null, focusAddressBar = true) }) {
                Icon(Icons.Default.Add, "New tab", tint = OnInk)
            }
            Box {
                IconButton(onClick = { overflow = true }) { Icon(Icons.Default.MoreVert, "More", tint = OnInk) }
                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                    if (openGroup != null) {
                        DropdownMenuItem(text = { Text("Rename group") }, onClick = {
                            overflow = false
                            edit = GroupEdit(openGroup.id, null, openGroup.name, openGroup.colorIndex)
                        })
                        DropdownMenuItem(text = { Text("Ungroup") }, onClick = {
                            overflow = false
                            controller.ungroup(openGroup.id)
                        })
                        DropdownMenuItem(text = { Text("Close group") }, onClick = {
                            overflow = false
                            controller.closeGroup(openGroup.id)
                        })
                    } else {
                        DropdownMenuItem(text = { Text("Close all tabs") }, onClick = {
                            overflow = false
                            controller.closeAll()
                        })
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(entries, key = { it.key }) { entry ->
                when (entry) {
                    is Entry.Tab -> TabCard(
                        tab = entry.tab,
                        active = entry.tab.id == controller.activeId,
                        accent = controller.group(entry.tab.groupId)?.color ?: Accent,
                        onOpen = { controller.select(entry.tab.id) },
                        onClose = { controller.close(entry.tab.id) },
                        onLongPress = { actionsFor = entry.tab }
                    )
                    is Entry.Group -> GroupCard(
                        group = entry.group,
                        tabs = entry.tabs,
                        active = entry.tabs.any { it.id == controller.activeId },
                        onOpen = { openGroupId = entry.group.id }
                    )
                }
            }
        }
    }

    actionsFor?.let { tab ->
        TabActions(
            tab = tab,
            groups = controller.groups.filter { it.id != tab.groupId },
            inGroup = tab.groupId != null,
            onNewGroup = { edit = GroupEdit(null, tab.id, "", controller.groups.size) },
            onAddTo = { controller.addToGroup(tab.id, it.id) },
            onRemoveFromGroup = { controller.removeFromGroup(tab.id) },
            onClose = { controller.close(tab.id) },
            onDismiss = { actionsFor = null }
        )
    }

    edit?.let { e ->
        GroupDialog(
            initialName = e.name,
            initialColor = e.color,
            creating = e.groupId == null,
            onDone = { name, color ->
                if (e.groupId == null) {
                    val g = controller.createGroup(listOfNotNull(e.tabId), name)
                    controller.recolorGroup(g.id, color)
                } else {
                    controller.renameGroup(e.groupId, name)
                    controller.recolorGroup(e.groupId, color)
                }
                edit = null
            },
            onDismiss = { edit = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabCard(
    tab: BrowserTab,
    active: Boolean,
    accent: Color,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onLongPress: () -> Unit
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(2.dp, if (active) accent else Color.Transparent, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(38.dp).padding(start = 10.dp)) {
            TabIcon(tab, size = 16.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                tab.title.ifBlank { tab.url },
                color = OnInk,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, "Close tab", tint = Muted, modifier = Modifier.size(16.dp))
            }
        }
        Preview(tab, Modifier.fillMaxWidth().aspectRatio(0.78f))
    }
}

@Composable
private fun GroupCard(group: TabGroup, tabs: List<BrowserTab>, active: Boolean, onOpen: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(2.dp, if (active) group.color else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(group.color))
            Spacer(Modifier.width(8.dp))
            Text(group.name, color = OnInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${tabs.size}", color = Muted, fontSize = 12.sp)
        }
        // 2 x 2 previews of the group's tabs
        Column(
            Modifier.fillMaxWidth().aspectRatio(0.78f).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (row in 0..1) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0..1) {
                        val tab = tabs.getOrNull(row * 2 + col)
                        val cell = Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(8.dp))
                        if (tab != null) Preview(tab, cell) else Box(cell.background(Surface2.copy(alpha = 0.4f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun Preview(tab: BrowserTab, modifier: Modifier) {
    Box(modifier.background(Surface2), contentAlignment = Alignment.Center) {
        val thumb = tab.thumbnail
        if (thumb != null) {
            Image(thumb, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize())
        } else {
            TabIcon(tab, size = 32.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabActions(
    tab: BrowserTab,
    groups: List<TabGroup>,
    inGroup: Boolean,
    onNewGroup: () -> Unit,
    onAddTo: (TabGroup) -> Unit,
    onRemoveFromGroup: () -> Unit,
    onClose: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(tab.title.ifBlank { tab.url }, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            MenuRow(Icons.Default.CreateNewFolder, "Add to new group") { onDismiss(); onNewGroup() }
            for (group in groups) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onAddTo(group); onDismiss() }.padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(group.color))
                    }
                    Spacer(Modifier.width(20.dp))
                    Text("Add to \"${group.name}\"", color = OnInk, fontSize = 16.sp)
                }
            }
            if (inGroup) MenuRow(Icons.Default.FolderOff, "Remove from group") { onRemoveFromGroup(); onDismiss() }
            MenuRow(Icons.Default.Close, "Close tab") { onClose(); onDismiss() }
        }
    }
}

@Composable
private fun GroupDialog(
    initialName: String,
    initialColor: Int,
    creating: Boolean,
    onDone: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableIntStateOf(initialColor % GROUP_COLORS.size) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text(if (creating) "New group" else "Edit group", color = OnInk) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Group name", color = Muted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Muted.copy(alpha = 0.4f),
                        focusedTextColor = OnInk,
                        unfocusedTextColor = OnInk,
                        cursorColor = Accent
                    )
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GROUP_COLORS.forEachIndexed { i, c ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(26.dp).clip(CircleShape).background(c).clickable { color = i }
                        ) {
                            if (i == color) Icon(Icons.Default.Check, null, tint = Ink, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(name, color) }) { Text(if (creating) "Create" else "Save", color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}
