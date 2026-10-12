package com.example.data.adblock

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class FilterList(val id: String, val name: String, val url: String, val builtIn: Boolean = true)

/**
 * Network-level ad blocker for the WebView: requests to listed ad / tracker / pop-up domains
 * are dropped before they load. Uses the same public filter lists as AdGuard and AdAway,
 * downloaded once and refreshed weekly.
 */
class AdBlocker(private val context: Context) {

    @Volatile
    var rules: FilterRules = FilterRules()
        private set

    private val dir = File(context.filesDir, "filters").apply { mkdirs() }

    fun isBlocked(host: String?): Boolean = rules.isBlocked(host)

    fun lastUpdated(list: FilterList): Long = file(list).takeIf { it.exists() }?.lastModified() ?: 0L

    fun needsUpdate(list: FilterList): Boolean =
        System.currentTimeMillis() - lastUpdated(list) > REFRESH_INTERVAL_MS

    /** Rebuilds the rule set from the built-in list plus every enabled, downloaded list. */
    suspend fun reload(enabled: List<FilterList>) = withContext(Dispatchers.IO) {
        val builder = FilterRules.Builder()
        context.assets.open("adblock/base.txt").bufferedReader().useLines { builder.addLines(it) }
        for (list in enabled) {
            val f = file(list)
            if (f.exists()) f.bufferedReader().useLines { builder.addLines(it) }
        }
        rules = builder.build()
    }

    /** Downloads a list. Returns false if it couldn't be fetched (the old copy is kept). */
    suspend fun download(list: FilterList): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(dir, "${list.id}.tmp")
        try {
            val conn = URL(list.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode != 200) return@withContext false
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
            tmp.renameTo(file(list))
        } catch (_: Exception) {
            tmp.delete()
            false
        }
    }

    fun delete(list: FilterList) {
        file(list).delete()
    }

    private fun file(list: FilterList) = File(dir, "${list.id}.txt")

    companion object {
        private const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

        val BUILT_IN = listOf(
            FilterList("adguard_dns", "AdGuard DNS filter", "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"),
            FilterList("adaway", "AdAway", "https://adaway.org/hosts.txt"),
            FilterList("stevenblack", "StevenBlack hosts", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
        )

        fun custom(url: String) = FilterList(
            id = "custom_" + url.hashCode().toUInt().toString(16),
            name = runCatching { URL(url).host }.getOrNull() ?: url,
            url = url,
            builtIn = false
        )

        /** Hides leftover ad slots that are filled by first-party scripts. Deliberately conservative. */
        const val COSMETIC_CSS =
            "ins.adsbygoogle,[id^='google_ads_'],[id^='div-gpt-ad'],[data-ad-slot],[data-ad-unit]," +
                "iframe[src*='doubleclick.net'],iframe[src*='googlesyndication'],amp-ad,amp-embed[type='taboola']," +
                "[id^='taboola-'],.trc_rbox_container,.OUTBRAIN,.adsbox,.ad-banner,.ad-container" +
                "{display:none!important}"
    }
}
