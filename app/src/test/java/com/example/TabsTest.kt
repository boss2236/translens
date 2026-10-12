package com.example

import com.example.ui.screens.BrowserController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TabsTest {

    private fun controller() = BrowserController().apply { newTab("https://a.com") }

    @Test
    fun `new tabs open next to their parent and become active`() {
        val c = controller()
        val a = c.active!!
        c.newTab("https://z.com")
        c.select(a.id)
        val child = c.newTab("https://b.com", parent = a)
        assertEquals(listOf("https://a.com", "https://b.com", "https://z.com"), c.tabs.map { it.url })
        assertEquals(child.id, c.activeId)
    }

    @Test
    fun `closing the active tab returns to its parent, and the last tab is replaced`() {
        val c = controller()
        val a = c.active!!
        val b = c.newTab("https://b.com", parent = a)
        c.close(b.id)
        assertEquals(a.id, c.activeId)
        c.close(a.id)
        assertEquals(1, c.tabs.size) // never left with zero tabs
    }

    @Test
    fun `groups keep their tabs together and vanish when empty`() {
        val c = controller()
        val a = c.active!!
        val b = c.newTab("https://b.com")
        val d = c.newTab("https://d.com")
        val g = c.createGroup(listOf(a.id), "Manga")
        c.addToGroup(d.id, g.id)
        assertEquals(listOf(a.id, d.id, b.id), c.tabs.map { it.id }) // d moved next to a
        assertEquals(2, c.tabsIn(g.id).size)
        c.removeFromGroup(a.id)
        c.removeFromGroup(d.id)
        assertNull(c.group(g.id))
        assertTrue(c.groups.isEmpty())
    }

    @Test
    fun `tabs and groups survive a restart`() {
        val c = controller()
        val b = c.newTab("https://b.com")
        val g = c.createGroup(listOf(b.id), "Reading")
        c.recolorGroup(g.id, 3)
        val json = c.toJson()

        val restored = BrowserController()
        assertTrue(restored.restore(json))
        assertEquals(c.tabs.map { it.url }, restored.tabs.map { it.url })
        assertEquals(b.id, restored.activeId)
        assertEquals("Reading", restored.groups.single().name)
        assertEquals(3, restored.groups.single().colorIndex)
        assertEquals(g.id, restored.tabs.last().groupId)
        assertFalse(BrowserController().restore("not json"))
    }

    @Test
    fun `closing a group closes its tabs only`() {
        val c = controller()
        val b = c.newTab("https://b.com")
        val d = c.newTab("https://d.com")
        val g = c.createGroup(listOf(b.id, d.id), "")
        assertEquals("Group 1", g.name)
        c.closeGroup(g.id)
        assertEquals(listOf("https://a.com"), c.tabs.map { it.url })
        assertTrue(c.groups.isEmpty())
    }
}
