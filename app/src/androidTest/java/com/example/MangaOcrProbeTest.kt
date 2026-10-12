package com.example

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.await
import com.example.data.model.OcrScriptMode
import com.example.data.ocr.OcrEngine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Logs how ML Kit reads vertical manga text (diagnostic, no assertions). */
@RunWith(AndroidJUnit4::class)
class MangaOcrProbeTest {
    @Test
    fun probeVerticalJapanese() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        val bitmap = ctx.assets.open("ocr/live_capture.png").use { BitmapFactory.decodeStream(it) }
        val raw = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            .process(InputImage.fromBitmap(bitmap, 0)).await()
        for (b in raw.textBlocks) {
            Log.i("MangaProbe", "BLOCK ${b.boundingBox} '${b.text.replace("\n", "⏎")}'")
            for (l in b.lines) Log.i("MangaProbe", "  LINE ${l.boundingBox} '${l.text}'")
        }
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        for ((name, r) in listOf("bubble1" to android.graphics.Rect(470, 200, 790, 560), "bubble2" to android.graphics.Rect(120, 620, 380, 990))) {
            for (scale in listOf(1f, 2f)) {
                val crop = android.graphics.Bitmap.createBitmap(bitmap, r.left, r.top, r.width(), r.height())
                val scaled = android.graphics.Bitmap.createScaledBitmap(crop, (r.width() * scale).toInt(), (r.height() * scale).toInt(), true)
                val t = recognizer.process(InputImage.fromBitmap(scaled, 0)).await()
                Log.i("MangaProbe", "CROP $name x$scale: " + t.textBlocks.flatMap { it.lines }.joinToString(" | ") { "'${it.text}' ${it.boundingBox}" })
            }
        }
        val result = OcrEngine().recognizeText(bitmap, OcrScriptMode.AUTO)
        result.blocks.forEach { Log.i("MangaProbe", "ENGINE ${result.script} '${it.originalText}'") }
    }
}
