package com.example.data.translation

import android.util.LruCache
import com.example.data.await
import com.example.data.model.OcrScriptMode
import com.example.data.ocr.OcrEngine
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Two engines:
 *  - online: Google Translate's free web endpoint (no key, best quality, needs internet)
 *  - offline: ML Kit on-device models, ~30 MB per language, downloaded once
 */
class OfflineTranslationService {

    private val languageIdentifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.3f).build()
    )
    private val modelManager = RemoteModelManager.getInstance()
    private val translators = mutableMapOf<String, Translator>()
    private val readyModels = java.util.Collections.synchronizedSet(mutableSetOf(TranslateLanguage.ENGLISH))
    private val memoryCache = LruCache<String, String>(4000)
    private val onlineLimiter = Semaphore(3)

    /** Dominant language of the whole image, used for short or ambiguous blocks. */
    suspend fun detectPageLanguage(texts: List<String>, script: OcrScriptMode): String? {
        when (script) {
            OcrScriptMode.KOREAN -> return TranslateLanguage.KOREAN
            OcrScriptMode.JAPANESE -> return TranslateLanguage.JAPANESE
            OcrScriptMode.CHINESE -> return TranslateLanguage.CHINESE
            else -> Unit
        }
        val sample = texts.joinToString("\n").take(3000)
        if (sample.isBlank()) return null
        val tag = runCatching { languageIdentifier.identifyLanguage(sample).await() }.getOrNull()
        return tag?.let { toTranslateLanguage(it) }
    }

    suspend fun detectLanguage(text: String, script: OcrScriptMode, pageLanguage: String?): String? {
        // Script alone settles CJK, and the language-ID model is unreliable on a few CJK glyphs.
        if (text.any { OcrEngine.isHangul(it) }) return TranslateLanguage.KOREAN
        if (text.any { OcrEngine.isKana(it) }) return TranslateLanguage.JAPANESE
        if (text.any { OcrEngine.isHan(it) }) {
            return if (script == OcrScriptMode.JAPANESE) TranslateLanguage.JAPANESE else TranslateLanguage.CHINESE
        }

        val candidates = runCatching { languageIdentifier.identifyPossibleLanguages(text).await() }
            .getOrNull().orEmpty()
        val best = candidates.firstOrNull { toTranslateLanguage(it.languageTag) != null }
            ?: return pageLanguage
        val bestLang = toTranslateLanguage(best.languageTag)
        val letters = text.count { it.isLetter() }

        return when {
            // Short snippets ("Menu", "Paris") are a coin flip for language ID; trust the page.
            pageLanguage != null && letters < 15 && best.confidence < 0.9f -> pageLanguage
            best.confidence >= 0.5f -> bestLang
            else -> pageLanguage ?: bestLang
        }
    }

    suspend fun isModelReady(language: String): Boolean {
        if (language in readyModels) return true
        val ready = runCatching {
            modelManager.isModelDownloaded(TranslateRemoteModel.Builder(language).build()).await()
        }.getOrDefault(false)
        if (ready) readyModels += language
        return ready
    }

    /** One-time download of a language pack. Throws if it can't be downloaded. */
    suspend fun downloadModel(language: String, wifiOnly: Boolean = false) {
        if (isModelReady(language)) return
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        modelManager.download(TranslateRemoteModel.Builder(language).build(), conditions).await()
        readyModels += language
    }

    /**
     * Translates [texts] in one go. Returns null if the engine failed (e.g. no internet), so the
     * caller can fall back to the other engine.
     */
    suspend fun translateBatch(
        texts: List<String>,
        source: String,
        target: String,
        online: Boolean
    ): List<String>? = withContext(Dispatchers.IO) {
        val results = arrayOfNulls<String>(texts.size)
        val misses = mutableListOf<Int>()
        texts.forEachIndexed { i, text ->
            val cached = memoryCache.get(cacheKey(online, source, target, text))
            if (cached != null) results[i] = cached else misses += i
        }
        if (misses.isEmpty()) return@withContext results.map { it!! }

        try {
            val missTexts = misses.map { texts[it] }
            val translated = if (online) {
                translateOnlineBatch(missTexts, source, target)
            } else {
                val translator = translatorFor(source, target)
                coroutineScope {
                    missTexts.map { async { translator.translate(it).await() } }.awaitAll()
                }
            }
            misses.forEachIndexed { k, i ->
                val value = translated[k].ifBlank { texts[i] }
                results[i] = value
                memoryCache.put(cacheKey(online, source, target, texts[i]), value)
            }
            results.map { it!! }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun cacheKey(online: Boolean, source: String, target: String, text: String) =
        "${if (online) "web" else "ml"}|$source|$target|$text"

    /** Sends many texts per request (newline separated); falls back to one-by-one if splitting fails. */
    private suspend fun translateOnlineBatch(texts: List<String>, source: String, target: String): List<String> {
        val out = mutableListOf<String>()
        var chunk = mutableListOf<String>()
        var chunkChars = 0
        suspend fun flush() {
            if (chunk.isEmpty()) return
            val parts = onlineLimiter.withPermit { translateOnline(chunk.joinToString("\n"), source, target) }
                .split("\n")
            if (parts.size == chunk.size) {
                out += parts.map { it.trim() }
            } else {
                out += chunk.map { text -> onlineLimiter.withPermit { translateOnline(text, source, target).trim() } }
            }
            chunk = mutableListOf()
            chunkChars = 0
        }
        for (text in texts) {
            val clean = text.replace('\n', ' ')
            if (chunkChars + clean.length > 1800) flush()
            chunk += clean
            chunkChars += clean.length + 1
        }
        flush()
        return out
    }

    private fun translateOnline(text: String, source: String, target: String): String {
        val conn = URL(
            "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t" +
                "&sl=${URLEncoder.encode(source, "UTF-8")}&tl=${URLEncoder.encode(target, "UTF-8")}"
        ).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            conn.outputStream.use { it.write("q=${URLEncoder.encode(text, "UTF-8")}".toByteArray()) }
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val segments = JSONArray(body).optJSONArray(0) ?: return ""
            return buildString {
                for (i in 0 until segments.length()) append(segments.optJSONArray(i)?.optString(0, "") ?: "")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun translatorFor(source: String, target: String): Translator = synchronized(translators) {
        translators.getOrPut("$source>$target") {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build()
            )
        }
    }

    fun close() {
        synchronized(translators) {
            translators.values.forEach { runCatching { it.close() } }
            translators.clear()
        }
        runCatching { languageIdentifier.close() }
    }

    companion object {
        /** Maps a BCP-47 tag (from language ID or the UI) to an ML Kit translate code, or null. */
        fun toTranslateLanguage(tag: String): String? {
            if (tag == "und" || tag.endsWith("-Latn")) return null // unknown / romanized text
            val normalized = when (val base = tag.substringBefore('-').lowercase()) {
                "iw" -> "he"
                "in" -> "id"
                "fil" -> "tl"
                else -> base
            }
            return TranslateLanguage.fromLanguageTag(normalized)
        }

        fun displayName(code: String): String =
            Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code.uppercase() }
    }
}
