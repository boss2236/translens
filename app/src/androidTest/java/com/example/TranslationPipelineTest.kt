package com.example

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.model.OcrScriptMode
import com.example.data.ocr.OcrEngine
import com.example.data.translation.OfflineTranslationService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real OCR -> language detection -> translation pipeline on sample signs. */
@RunWith(AndroidJUnit4::class)
class TranslationPipelineTest {

    private data class Case(val file: String, val script: OcrScriptMode, val lang: String, val expectWords: List<String>)

    private val cases = listOf(
        Case("japanese", OcrScriptMode.JAPANESE, "ja", listOf("ramen", "station")),
        Case("chinese", OcrScriptMode.CHINESE, "zh", listOf("welcome", "smok")),
        Case("korean", OcrScriptMode.KOREAN, "ko", listOf("welcome", "menu", "left")),
        Case("spanish", OcrScriptMode.LATIN, "es", listOf("welcome", "smok", "right")),
        Case("french", OcrScriptMode.LATIN, "fr", listOf("sunday", "exit")),
        Case("hindi", OcrScriptMode.DEVANAGARI, "hi", listOf("welcome", "smok")),
        Case("manga_vertical", OcrScriptMode.JAPANESE, "ja", listOf("already dead"))
    )

    @Test
    fun translatesSignsToEnglish() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val ocr = OcrEngine()
        val translator = OfflineTranslationService()
        val failures = mutableListOf<String>()

        for (case in cases) {
            val bitmap = ctx.assets.open("ocr/${case.file}.png").use { BitmapFactory.decodeStream(it) }
            val result = ocr.recognizeText(bitmap, OcrScriptMode.AUTO)
            val texts = result.blocks.map { it.originalText }
            val pageLang = translator.detectPageLanguage(texts, result.script)
            translator.downloadModel(case.lang)

            val english = result.blocks.map { block ->
                val src = translator.detectLanguage(block.originalText, result.script, pageLang)
                    ?: case.lang
                val t = translator.translateBatch(listOf(block.originalText), src, "en", online = false)!!.single()
                Log.i(TAG, "[${case.file}] script=${result.script} lang=$src '${block.originalText}' -> '$t'")
                t
            }.joinToString(" | ")

            Log.i(TAG, "[${case.file}] RESULT: $english")
            if (result.script != case.script) failures += "${case.file}: script ${result.script} != ${case.script}"
            if (pageLang != case.lang) failures += "${case.file}: language $pageLang != ${case.lang}"
            val missing = case.expectWords.filterNot { english.contains(it, ignoreCase = true) }
            if (missing.isNotEmpty()) failures += "${case.file}: missing $missing in '$english'"
        }

        failures.forEach { Log.e(TAG, "FAIL $it") }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertEquals(0, failures.size)
    }

    @Test
    fun freeOnlineEngineTranslates() = runBlocking {
        val translator = OfflineTranslationService()
        val samples = mapOf("zh" to "请勿吸烟", "ja" to "駅はこちらです", "ko" to "환영합니다", "es" to "Prohibido fumar")
        for ((lang, text) in samples) {
            val r = translator.translateBatch(listOf(text, "出口"), lang, "en", online = true)
            Log.i(TAG, "[online] $lang '$text' -> '$r'")
            assertTrue("online $lang failed", r != null && r[0] != text)
        }
    }

    companion object {
        private const val TAG = "PipelineTest"
    }
}
