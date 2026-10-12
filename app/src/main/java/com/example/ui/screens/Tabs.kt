package com.example.ui.screens

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class BrowserTab(
    val id: String,
    url: String,
    title: String = "",
    groupId: String? = null,
    val parentId: String? = null
) {
    var url by mutableStateOf(url)
    var title by mutableStateOf(title)
    var groupId by mutableStateOf(groupId)
    var loading by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var canGoBack by mutableStateOf(false)
    var blockedCount by mutableIntStateOf(0)
    var thumbnail by mutableStateOf<ImageBitmap?>(null)
    var favicon by mutableStateOf<ImageBitmap?>(null)
    var webView: WebView? = null
    var lastActive: Long = System.currentTimeMillis()
}

data class TabGroup(val id: String, val name: String, val colorIndex: Int) {
    val color: Color get() = GROUP_COLORS[colorIndex % GROUP_COLORS.size]
}

val GROUP_COLORS = listOf(
    Color(0xFF8AB4F8), Color(0xFFF28B82), Color(0xFFFDD663), Color(0xFF81C995),
    Color(0xFFFF8BCB), Color(0xFFC58AF9), Color(0xFF78D9EC), Color(0xFFFCAD70)
)

/** Tabs, tab groups and the shared browser state the menu and screens act on. */
class BrowserController {
    val tabs = mutableStateListOf<BrowserTab>()
    val groups = mutableStateListOf<TabGroup>()
    var activeId by mutableStateOf("")
    var switcherOpen by mutableStateOf(false)
    var findMode by mutableStateOf(false)
    var findCurrent by mutableIntStateOf(0)
    var findTotal by mutableIntStateOf(0)
    /** Bumped to move focus into the address bar (new tab). */
    var focusUrlBar by mutableIntStateOf(0)

    var searchTemplate: String = "https://www.google.com/search?q=%s"
    var mobileUserAgent: String = ""
    var homeUrl: String = "https://www.google.com"
    /** Called whenever tabs change so they can be saved. */
    var onChanged: () -> Unit = {}
    /** Set by the browser: snapshot the page for the tab switcher. */
    var captureThumbnail: (BrowserTab) -> Unit = {}

    val active: BrowserTab? get() = tabs.firstOrNull { it.id == activeId }
    val webView: WebView? get() = active?.webView
    val url: String get() = active?.url.orEmpty()
    val title: String get() = active?.title.orEmpty()
    val blockedCount: Int get() = active?.blockedCount ?: 0

    fun load(input: String) {
        val tab = active ?: return
        val target = formatUrl(input, searchTemplate)
        tab.url = target
        tab.webView?.loadUrl(target)
        changed()
    }

    fun newTab(
        url: String = homeUrl,
        groupId: String? = null,
        parent: BrowserTab? = null,
        select: Boolean = true,
        focusAddressBar: Boolean = false
    ): BrowserTab {
        val tab = BrowserTab(newId(), formatUrl(url, searchTemplate), groupId = groupId, parentId = parent?.id)
        // Like Chrome: tabs opened from a page sit right after it
        val index = parent?.let { p -> tabs.indexOf(p) + 1 + tabs.count { it.parentId == p.id } } ?: tabs.size
        tabs.add(index.coerceIn(0, tabs.size), tab)
        if (select) select(tab.id)
        if (focusAddressBar) focusUrlBar++
        changed()
        return tab
    }

    fun select(id: String) {
        active?.let { captureThumbnail(it); it.lastActive = System.currentTimeMillis() }
        activeId = id
        active?.lastActive = System.currentTimeMillis()
        findMode = false
        switcherOpen = false
        trimWebViews()
        changed()
    }

    fun openSwitcher() {
        active?.let { captureThumbnail(it) }
        switcherOpen = true
    }

    fun close(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        val tab = tabs.removeAt(index)
        tab.webView?.destroy()
        tab.webView = null
        dropEmptyGroups()
        if (tabs.isEmpty()) {
            newTab()
        } else if (activeId == id) {
            // Back to the tab it was opened from, else a neighbour (same group first)
            val next = tabs.firstOrNull { it.id == tab.parentId }
                ?: tabs.getOrNull(index)?.takeIf { it.groupId == tab.groupId }
                ?: tabs.getOrNull(index - 1)?.takeIf { it.groupId == tab.groupId }
                ?: tabs[index.coerceAtMost(tabs.size - 1)]
            activeId = next.id
            findMode = false
        }
        changed()
    }

    fun closeAll() {
        tabs.forEach { it.webView?.destroy() }
        tabs.clear()
        groups.clear()
        newTab()
    }

    fun group(id: String?): TabGroup? = groups.firstOrNull { it.id == id }
    fun tabsIn(groupId: String): List<BrowserTab> = tabs.filter { it.groupId == groupId }

    fun createGroup(tabIds: List<String>, name: String): TabGroup {
        val group = TabGroup(newId(), name.ifBlank { "Group ${groups.size + 1}" }, nextColor())
        groups.add(group)
        tabs.filter { it.id in tabIds }.forEach { it.groupId = group.id }
        keepTogether(group.id)
        changed()
        return group
    }

    fun addToGroup(tabId: String, groupId: String) {
        tabs.firstOrNull { it.id == tabId }?.groupId = groupId
        keepTogether(groupId)
        dropEmptyGroups()
        changed()
    }

    fun removeFromGroup(tabId: String) {
        val tab = tabs.firstOrNull { it.id == tabId } ?: return
        val groupId = tab.groupId ?: return
        tab.groupId = null
        // Move it just after its old group so groups stay contiguous
        val lastMember = tabs.indexOfLast { it.groupId == groupId }
        if (lastMember >= 0) {
            tabs.remove(tab)
            tabs.add((lastMember).coerceAtMost(tabs.size), tab)
        }
        dropEmptyGroups()
        changed()
    }

    fun renameGroup(groupId: String, name: String) {
        val i = groups.indexOfFirst { it.id == groupId }
        if (i >= 0 && name.isNotBlank()) groups[i] = groups[i].copy(name = name.trim())
        changed()
    }

    fun recolorGroup(groupId: String, colorIndex: Int) {
        val i = groups.indexOfFirst { it.id == groupId }
        if (i >= 0) groups[i] = groups[i].copy(colorIndex = colorIndex)
        changed()
    }

    fun ungroup(groupId: String) {
        tabs.filter { it.groupId == groupId }.forEach { it.groupId = null }
        groups.removeAll { it.id == groupId }
        changed()
    }

    fun closeGroup(groupId: String) {
        tabsIn(groupId).map { it.id }.forEach { close(it) }
        groups.removeAll { it.id == groupId }
        changed()
    }

    /** Only a few pages stay loaded in memory; the rest reload when reopened. */
    private fun trimWebViews(keep: Int = 5) {
        tabs.filter { it.webView != null && it.id != activeId }
            .sortedByDescending { it.lastActive }
            .drop(keep - 1)
            .forEach {
                it.webView?.destroy()
                it.webView = null
            }
    }

    private fun keepTogether(groupId: String) {
        val members = tabs.filter { it.groupId == groupId }
        if (members.size < 2) return
        val first = tabs.indexOf(members.first())
        val rest = members.drop(1)
        tabs.removeAll(rest.toSet())
        tabs.addAll((first + 1).coerceAtMost(tabs.size), rest)
    }

    private fun dropEmptyGroups() {
        groups.removeAll { g -> tabs.none { it.groupId == g.id } }
    }

    private fun nextColor(): Int {
        val used = groups.map { it.colorIndex }.toSet()
        return GROUP_COLORS.indices.firstOrNull { it !in used } ?: (groups.size % GROUP_COLORS.size)
    }

    fun changed() = onChanged()

    fun toJson(): String = JSONObject()
        .put("active", activeId)
        .put("tabs", JSONArray().apply {
            tabs.forEach { t ->
                put(JSONObject().put("id", t.id).put("url", t.url).put("title", t.title).put("group", t.groupId ?: ""))
            }
        })
        .put("groups", JSONArray().apply {
            groups.forEach { g -> put(JSONObject().put("id", g.id).put("name", g.name).put("color", g.colorIndex)) }
        })
        .toString()

    /** Restores saved tabs (pages load lazily). Returns false if there was nothing to restore. */
    fun restore(json: String?): Boolean {
        if (json.isNullOrBlank()) return false
        return runCatching {
            val o = JSONObject(json)
            val g = o.getJSONArray("groups")
            for (i in 0 until g.length()) {
                val x = g.getJSONObject(i)
                groups.add(TabGroup(x.getString("id"), x.getString("name"), x.getInt("color")))
            }
            val t = o.getJSONArray("tabs")
            for (i in 0 until t.length()) {
                val x = t.getJSONObject(i)
                tabs.add(BrowserTab(x.getString("id"), x.getString("url"), x.optString("title"), x.optString("group").ifEmpty { null }))
            }
            dropEmptyGroups()
            activeId = o.optString("active").takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id
            tabs.isNotEmpty()
        }.getOrDefault(false)
    }

    private fun newId() = UUID.randomUUID().toString().take(8)
}
