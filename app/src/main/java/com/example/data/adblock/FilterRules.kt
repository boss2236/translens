package com.example.data.adblock

/**
 * Domain-level ad/tracker rules parsed from common filter list formats:
 *  - Adblock / AdGuard:  ||ads.example.com^   and exceptions  @@||cdn.example.com^
 *  - hosts files:        0.0.0.0 ads.example.com   /   127.0.0.1 ads.example.com
 *  - plain domain lists: ads.example.com
 * Path, wildcard, regex and cosmetic rules are skipped (they need a full filtering engine).
 */
class FilterRules(
    private val blocked: Set<String> = emptySet(),
    private val allowed: Set<String> = emptySet()
) {
    val size: Int get() = blocked.size

    /** True if [host] or any of its parent domains is blocked (and not explicitly allowed). */
    fun isBlocked(host: String?): Boolean {
        if (host.isNullOrEmpty() || blocked.isEmpty()) return false
        var h = host.lowercase().trimEnd('.')
        var hit = false
        while (true) {
            if (h in allowed) return false
            if (!hit && h in blocked) hit = true
            val dot = h.indexOf('.')
            // Stop before the bare TLD ("com")
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) break
            h = h.substring(dot + 1)
        }
        if (h in allowed) return false
        return hit || h in blocked
    }

    class Builder {
        private val blocked = HashSet<String>(1 shl 16)
        private val allowed = HashSet<String>()

        fun addLines(lines: Sequence<String>): Builder {
            for (raw in lines) addLine(raw)
            return this
        }

        fun addLine(raw: String) {
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '!' || line[0] == '#' || line[0] == '[') return
            when {
                line.startsWith("@@||") -> domainRule(line.substring(4))?.let { allowed += it }
                line.startsWith("||") -> domainRule(line.substring(2))?.let { blocked += it }
                line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ") || line.startsWith("0.0.0.0\t") ||
                    line.startsWith("127.0.0.1\t") -> {
                    val host = line.split(' ', '\t').map { it.trim() }.filter { it.isNotEmpty() }.getOrNull(1)
                        ?.substringBefore('#')
                    if (host != null && isDomain(host) && host !in LOCAL_HOSTS) blocked += host.lowercase()
                }
                isDomain(line) -> blocked += line.lowercase()
            }
        }

        /** "ads.example.com^" or "ads.example.com^$third-party" -> "ads.example.com"; anything fancier -> null. */
        private fun domainRule(rule: String): String? {
            val body = rule.substringBefore('$')
            val options = rule.substringAfter('$', "")
            if (options.isNotEmpty() && options != "third-party" && options != "important") return null
            val domain = body.removeSuffix("^").removeSuffix("|")
            return if (isDomain(domain)) domain.lowercase() else null
        }

        fun build() = FilterRules(blocked, allowed)
    }

    companion object {
        private val LOCAL_HOSTS = setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "0.0.0.0")
        private val DOMAIN = Regex("""^[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)+$""")

        fun isDomain(s: String) = s.length in 3..253 && DOMAIN.matches(s)
    }
}
