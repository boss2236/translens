package com.example

import com.example.data.adblock.FilterRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterRulesTest {

    private val rules = FilterRules.Builder().addLines(
        """
        ! comment
        # another comment
        ||doubleclick.net^
        ||ads.example.org^${'$'}third-party
        ||tracker.example.org^${'$'}script
        ||*.wildcard.com^
        @@||safe.doubleclick.net^
        0.0.0.0 popads.net
        127.0.0.1	exoclick.com # trailing comment
        0.0.0.0 localhost
        plain-ads.io
        /^regex/
        example.com##.banner
        """.trimIndent().lineSequence()
    ).build()

    @Test
    fun `blocks listed domains and their subdomains`() {
        assertTrue(rules.isBlocked("doubleclick.net"))
        assertTrue(rules.isBlocked("stats.g.doubleclick.net"))
        assertTrue(rules.isBlocked("ADS.example.org"))
        assertTrue(rules.isBlocked("popads.net"))
        assertTrue(rules.isBlocked("c.exoclick.com"))
        assertTrue(rules.isBlocked("plain-ads.io"))
    }

    @Test
    fun `does not block unrelated or parent domains`() {
        assertFalse(rules.isBlocked("example.org"))
        assertFalse(rules.isBlocked("notdoubleclick.net"))
        assertFalse(rules.isBlocked("google.com"))
        assertFalse(rules.isBlocked("localhost"))
        assertFalse(rules.isBlocked(null))
    }

    @Test
    fun `exceptions win`() {
        assertFalse(rules.isBlocked("safe.doubleclick.net"))
        assertFalse(rules.isBlocked("cdn.safe.doubleclick.net"))
    }

    @Test
    fun `skips rules a domain matcher cannot honour`() {
        // $script option, wildcards, regex and cosmetic rules are ignored
        assertFalse(rules.isBlocked("tracker.example.org"))
        assertFalse(rules.isBlocked("x.wildcard.com"))
        assertEquals(5, rules.size)
    }
}
