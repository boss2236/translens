package com.example.data.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import com.example.data.await
import com.example.data.model.DetectedTextBlock
import com.example.data.model.OcrScriptMode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class OcrResult(
    val blocks: List<DetectedTextBlock>,
    /** Script whose recognizer produced [blocks]; used as a language hint for translation. */
    val script: OcrScriptMode
)

class OcrEngine {

    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val japaneseRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    private val chineseRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private val koreanRecognizer by lazy {
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }

    private val devanagariRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    private fun recognizerFor(script: OcrScriptMode): TextRecognizer = when (script) {
        OcrScriptMode.JAPANESE -> japaneseRecognizer
        OcrScriptMode.CHINESE -> chineseRecognizer
        OcrScriptMode.KOREAN -> koreanRecognizer
        OcrScriptMode.DEVANAGARI -> devanagariRecognizer
        OcrScriptMode.LATIN, OcrScriptMode.AUTO -> latinRecognizer
    }

    suspend fun recognizeText(
        bitmap: Bitmap,
        scriptMode: OcrScriptMode = OcrScriptMode.AUTO
    ): OcrResult = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)

        val (visionText, script) = if (scriptMode == OcrScriptMode.AUTO) {
            recognizeAuto(image)
        } else {
            val text = runCatching { recognizerFor(scriptMode).process(image).await() }
                .getOrElse { latinRecognizer.process(image).await() }
            text to scriptMode
        }

        OcrResult(processVisionText(visionText, bitmap, script, recognizerFor(script)), script)
    }

    /**
     * Every ML Kit recognizer also reads Latin, but only the matching one reads its own script
     * correctly (the Latin model turns Hangul/Kanji into garbage letters). So run them all and
     * keep the result whose native-script output is strongest.
     */
    private suspend fun recognizeAuto(image: InputImage): Pair<Text, OcrScriptMode> = coroutineScope {
        val candidates = listOf(
            OcrScriptMode.LATIN,
            OcrScriptMode.JAPANESE,
            OcrScriptMode.CHINESE,
            OcrScriptMode.KOREAN,
            OcrScriptMode.DEVANAGARI
        ).map { script ->
            async { script to runCatching { recognizerFor(script).process(image).await() }.getOrNull() }
        }.map { it.await() }

        val results = candidates.mapNotNull { (script, text) -> text?.let { script to it } }.toMap()
        val latin = results[OcrScriptMode.LATIN]
        val latinLetters = latin?.text?.count { it.isLetter() } ?: 0

        val nativeScores = buildMap<OcrScriptMode, Int> {
            results[OcrScriptMode.KOREAN]?.let { put(OcrScriptMode.KOREAN, it.text.count { isHangul(it) }) }
            results[OcrScriptMode.JAPANESE]?.let { text ->
                val kana = text.text.count { isKana(it) }
                // Without kana it's indistinguishable from Chinese; let the Chinese model win then.
                put(OcrScriptMode.JAPANESE, if (kana >= 2) kana + text.text.count { isHan(it) } else 0)
            }
            results[OcrScriptMode.CHINESE]?.let { put(OcrScriptMode.CHINESE, it.text.count { isHan(it) }) }
            results[OcrScriptMode.DEVANAGARI]?.let { put(OcrScriptMode.DEVANAGARI, it.text.count { isDevanagari(it) }) }
        }

        val best = nativeScores.maxByOrNull { it.value }
        // A CJK/Devanagari glyph carries far more information than a Latin letter, so weight it up;
        // this stops a stray mis-read glyph from hijacking an otherwise Latin page.
        if (best != null && best.value >= 2 && best.value * 4 >= latinLetters) {
            results.getValue(best.key) to best.key
        } else if (latin != null) {
            latin to OcrScriptMode.LATIN
        } else {
            val fallback = results.entries.firstOrNull()
                ?: throw IllegalStateException("Text recognition failed")
            fallback.value to fallback.key
        }
    }

    private suspend fun processVisionText(
        visionText: Text,
        bitmap: Bitmap,
        script: OcrScriptMode,
        recognizer: TextRecognizer
    ): List<DetectedTextBlock> {
        val results = mutableListOf<DetectedTextBlock>()
        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()

        if (imgW <= 0 || imgH <= 0) return emptyList()

        val verticalLines = mutableListOf<Text.Line>()
        val cjk = script == OcrScriptMode.JAPANESE || script == OcrScriptMode.CHINESE || script == OcrScriptMode.KOREAN

        for (block in visionText.textBlocks) {
            val lines = block.lines.filter { it.boundingBox != null && it.text.isNotBlank() }
            if (lines.isEmpty()) continue

            // Manga / vertical CJK: collect columns and regroup them per speech bubble below
            if (cjk && lines.any { isVerticalLine(it) }) {
                verticalLines += lines
                continue
            }

            // Menus/lists are separate items; paragraphs and speech bubbles are one sentence that
            // must be translated as a whole, otherwise each line gets translated out of context.
            for (group in groupLines(lines)) {
                val box = Rect(group.first().boundingBox!!)
                group.drop(1).forEach { box.union(it.boundingBox!!) }
                val text = joinLines(group.map { it.text.trim() }, script)
                addBlock(results, bitmap, text, box, inflate = max(2, group.minOf { it.boundingBox!!.height() } / 8))
            }
        }

        for (bubble in findBubbles(bitmap, clusterColumns(verticalLines))) {
            var lines: List<Pair<String, Rect>> = bubble.lines.map { it.text to Rect(it.boundingBox!!) }
            // The full-page pass often drops short columns (「いい」) inside a bubble; a second
            // pass on just the bubble recovers them.
            bubble.interior?.let { area ->
                val reread = rereadArea(bitmap, area, bubble.textBox, recognizer)
                if (reread.sumOf { (t, _) -> t.count { isCjk(it) } } > lines.sumOf { (t, _) -> t.count { isCjk(it) } }) {
                    lines = reread
                }
            }
            // Vertical text reads top-to-bottom, columns right-to-left
            val ordered = lines.sortedByDescending { it.second.centerX() }
            val box = Rect(ordered.first().second)
            ordered.drop(1).forEach { box.union(it.second) }
            // ML Kit reads some vertical kana as "/"; slashes don't occur in vertical manga text
            val text = ordered.joinToString("") { it.first.trim() }.replace("/", "")
            val colW = ordered.minOf { it.second.width() }
            // English is horizontal: fill the whole bubble interior (drawn as an oval), or widen
            // moderately when there's no clear bubble.
            addBlock(
                results, bitmap, text, box,
                inflate = max(2, colW / 8),
                widenTo = if (bubble.interior == null) max(box.width(), (box.height() * 0.6f).toInt()) else 0,
                bubble = bubble.interior
            )
        }

        return results
    }

    private fun addBlock(
        results: MutableList<DetectedTextBlock>,
        bitmap: Bitmap,
        text: String,
        textBox: Rect,
        inflate: Int,
        widenTo: Int = 0,
        bubble: Rect? = null
    ) {
        if (text.isBlank() || !text.any { it.isLetter() }) return
        // Colors come from just around the glyphs, before the patch is padded/widened
        var (bgColor, textColor) = sampleColors(bitmap, textBox)
        if (bubble != null) bgColor = dominantColor(bitmap, textBox).toLong() and 0xFFFFFFFFL
        val extraW = max(0, widenTo - textBox.width()) / 2
        // OCR boxes hug the glyphs; pad a little so the patch fully covers the original
        val box = Rect(
            (textBox.left - inflate - extraW).coerceAtLeast(0),
            (textBox.top - inflate).coerceAtLeast(0),
            (textBox.right + inflate + extraW).coerceAtMost(bitmap.width),
            (textBox.bottom + inflate).coerceAtMost(bitmap.height)
        )
        if (bubble != null) box.set(Rect(bubble).apply { inset(2, 2) })
        results.add(
            DetectedTextBlock(
                originalText = text,
                translatedText = "",
                detectedLanguage = "auto",
                boundingBox = normalizeBox(box, bitmap.width.toFloat(), bitmap.height.toFloat()),
                originalPixelBox = RectF(box),
                backgroundColor = bgColor,
                textColor = textColor,
                isTranslating = true,
                isBubble = bubble != null
            )
        )
    }

    private class Bubble(val lines: MutableList<Text.Line>, val textBox: Rect, var interior: Rect?)

    /** Finds the speech bubble around each column cluster; clusters in the same bubble merge. */
    private fun findBubbles(bitmap: Bitmap, clusters: List<List<Text.Line>>): List<Bubble> {
        val bubbles = clusters.map { cluster ->
            val box = Rect(cluster.first().boundingBox!!)
            cluster.drop(1).forEach { box.union(it.boundingBox!!) }
            Bubble(cluster.toMutableList(), box, bubbleInterior(bitmap, box))
        }.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            loop@ for (i in bubbles.indices) for (j in i + 1 until bubbles.size) {
                val a = bubbles[i]
                val b = bubbles[j]
                val sameBubble = a.interior?.contains(b.textBox.centerX(), b.textBox.centerY()) == true ||
                    b.interior?.contains(a.textBox.centerX(), a.textBox.centerY()) == true
                if (sameBubble) {
                    a.lines += b.lines
                    a.textBox.union(b.textBox)
                    a.interior = when {
                        a.interior == null -> b.interior
                        b.interior == null -> a.interior
                        else -> Rect(a.interior).apply { union(b.interior!!) }
                    }
                    bubbles.removeAt(j)
                    merged = true
                    break@loop
                }
            }
        }
        return bubbles
    }

    /**
     * Flood-fills the bubble's background outwards from the text (stepping over glyphs) and
     * returns its bounds. Returns null when the fill escapes, i.e. there's no closed bubble.
     */
    private fun bubbleInterior(bitmap: Bitmap, box: Rect): Rect? {
        // Between the glyphs is the bubble's own color (the ring outside the text can spill onto
        // the page, which would make the fill leak)
        val bg = dominantColor(bitmap, box)
        val bgR = Color.red(bg)
        val bgG = Color.green(bg)
        val bgB = Color.blue(bg)
        val step = max(2, min(box.width(), box.height()) / 12)
        val limit = Rect(
            (box.left - box.width() * 3 - box.height() * 3 / 2).coerceAtLeast(0),
            (box.top - box.height()).coerceAtLeast(0),
            (box.right + box.width() * 3 + box.height() * 3 / 2).coerceAtMost(bitmap.width - 1),
            (box.bottom + box.height()).coerceAtMost(bitmap.height - 1)
        )
        val cols = limit.width() / step + 1
        val rows = limit.height() / step + 1
        val seen = BooleanArray(cols * rows)
        fun isBackground(gx: Int, gy: Int): Boolean {
            val x = limit.left + gx * step
            val y = limit.top + gy * step
            return colorDistance(bitmap.getPixel(x, y), bgR, bgG, bgB) < 40
        }

        // Seed: background just outside the text, plus the gaps inside the text box
        val queue = ArrayDeque<Int>()
        for (gy in ((box.top - limit.top) / step)..((box.bottom - limit.top) / step)) {
            for (gx in ((box.left - limit.left) / step)..((box.right - limit.left) / step)) {
                if (gx in 0 until cols && gy in 0 until rows && isBackground(gx, gy)) {
                    seen[gy * cols + gx] = true
                    queue.add(gy * cols + gx)
                }
            }
        }
        if (queue.isEmpty()) return null

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            val gx = cell % cols
            val gy = cell / cols
            // Reaching the search limit means we leaked out of (or there is no) bubble
            if (gx == 0 || gy == 0 || gx == cols - 1 || gy == rows - 1) return null
            minX = min(minX, gx); maxX = max(maxX, gx)
            minY = min(minY, gy); maxY = max(maxY, gy)
            for ((nx, ny) in listOf(gx - 1 to gy, gx + 1 to gy, gx to gy - 1, gx to gy + 1)) {
                val n = ny * cols + nx
                if (!seen[n] && isBackground(nx, ny)) {
                    seen[n] = true
                    queue.add(n)
                }
            }
        }
        return Rect(
            limit.left + minX * step,
            limit.top + minY * step,
            limit.left + maxX * step,
            limit.top + maxY * step
        )
    }

    /** Most common (quantized) color inside [box]: the background between glyphs. */
    private fun dominantColor(bitmap: Bitmap, box: Rect): Int {
        val counts = HashMap<Int, Int>()
        val samples = HashMap<Int, Int>()
        for (iy in 0 until 16) for (ix in 0 until 16) {
            val x = (box.left + box.width() * (ix + 0.5f) / 16).toInt().coerceIn(0, bitmap.width - 1)
            val y = (box.top + box.height() * (iy + 0.5f) / 16).toInt().coerceIn(0, bitmap.height - 1)
            val c = bitmap.getPixel(x, y)
            val key = ((Color.red(c) shr 4) shl 8) or ((Color.green(c) shr 4) shl 4) or (Color.blue(c) shr 4)
            counts[key] = (counts[key] ?: 0) + 1
            samples[key] = c
        }
        return samples.getValue(counts.maxByOrNull { it.value }!!.key)
    }

    private suspend fun rereadArea(bitmap: Bitmap, area: Rect, textBox: Rect, recognizer: TextRecognizer): List<Pair<String, Rect>> {
        val r = Rect(area).apply { intersect(0, 0, bitmap.width, bitmap.height) }
        if (r.width() < 8 || r.height() < 8) return emptyList()
        val crop = Bitmap.createBitmap(bitmap, r.left, r.top, r.width(), r.height()).copy(Bitmap.Config.ARGB_8888, true)
        // Blank out everything outside the oval so bits of the bubble outline aren't read as kana
        val canvas = android.graphics.Canvas(crop)
        val outside = android.graphics.Path().apply {
            fillType = android.graphics.Path.FillType.INVERSE_EVEN_ODD
            addOval(android.graphics.RectF(0f, 0f, r.width().toFloat(), r.height().toFloat()), android.graphics.Path.Direction.CW)
        }
        canvas.drawPath(outside, android.graphics.Paint().apply { color = dominantColor(bitmap, textBox) })
        val text = runCatching { recognizer.process(InputImage.fromBitmap(crop, 0)).await() }.getOrNull()
            ?: return emptyList()
        return text.textBlocks.flatMap { it.lines }
            .filter { it.boundingBox != null && it.text.isNotBlank() }
            .map { line -> line.text to Rect(line.boundingBox!!).apply { offset(r.left, r.top) } }
    }

    private fun isVerticalLine(line: Text.Line): Boolean {
        val box = line.boundingBox ?: return false
        return line.text.trim().length >= 2 && box.height() > box.width() * 1.3f
    }

    /** Groups neighbouring vertical columns (same bubble) together. */
    private fun clusterColumns(columns: List<Text.Line>): List<List<Text.Line>> {
        val parent = IntArray(columns.size) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) x = parent[x]
            return x
        }
        for (i in columns.indices) {
            for (j in i + 1 until columns.size) {
                val a = columns[i].boundingBox!!
                val b = columns[j].boundingBox!!
                val colW = min(a.width(), b.width())
                val gapX = max(a.left, b.left) - min(a.right, b.right)
                val overlapY = min(a.bottom, b.bottom) - max(a.top, b.top)
                // Allow one missed column in between (OCR sometimes drops short ones)
                if (gapX <= colW * 2.2f && overlapY > min(a.height(), b.height()) * 0.3f) {
                    parent[find(i)] = find(j)
                }
            }
        }
        return columns.indices.groupBy { find(it) }.values.map { idx -> idx.map { columns[it] } }
    }

    /** Splits a block's lines wherever there is a big vertical gap or a jump in font size. */
    private fun groupLines(lines: List<Text.Line>): List<List<Text.Line>> {
        val groups = mutableListOf(mutableListOf(lines.first()))
        for (i in 1 until lines.size) {
            val prev = lines[i - 1].boundingBox!!
            val cur = lines[i].boundingBox!!
            val lineH = max(1, min(prev.height(), cur.height()))
            val gap = cur.top - prev.bottom
            val sizeRatio = max(prev.height(), cur.height()).toFloat() / lineH
            if (gap > lineH * 0.7f || sizeRatio > 1.6f) {
                groups.add(mutableListOf(lines[i]))
            } else {
                groups.last().add(lines[i])
            }
        }
        return groups
    }

    private fun joinLines(lines: List<String>, script: OcrScriptMode): String {
        val noSpaces = script == OcrScriptMode.JAPANESE || script == OcrScriptMode.CHINESE
        val sb = StringBuilder()
        for (line in lines) {
            if (sb.isEmpty()) {
                sb.append(line)
                continue
            }
            val last = sb.last()
            when {
                // Re-join words hyphenated across a line break: "transla-" + "tion"
                last == '-' && sb.length > 1 && sb[sb.length - 2].isLetter() && line.firstOrNull()?.isLowerCase() == true ->
                    sb.setLength(sb.length - 1)
                noSpaces && (isCjk(last) || line.firstOrNull()?.let { isCjk(it) } == true) -> Unit
                else -> sb.append(' ')
            }
            sb.append(line)
        }
        return sb.toString()
    }

    private fun normalizeBox(rect: Rect, imgW: Float, imgH: Float): RectF {
        val left = (rect.left.toFloat() / imgW).coerceIn(0f, 1f)
        val top = (rect.top.toFloat() / imgH).coerceIn(0f, 1f)
        val right = (rect.right.toFloat() / imgW).coerceIn(0f, 1f)
        val bottom = (rect.bottom.toFloat() / imgH).coerceIn(0f, 1f)
        return RectF(left, top, right, bottom)
    }

    /**
     * Google Lens style patch colors: background = median of a ring of pixels just outside the
     * text box (robust against neighbouring text), text = the pixels inside the box that differ
     * most from that background (keeps the original ink color).
     */
    private fun sampleColors(bitmap: Bitmap, box: Rect): Pair<Long, Long> {
        try {
            val w = bitmap.width
            val h = bitmap.height
            val pad = max(2, box.height() / 8)
            val outer = Rect(
                (box.left - pad).coerceAtLeast(0),
                (box.top - pad).coerceAtLeast(0),
                (box.right + pad).coerceAtMost(w - 1),
                (box.bottom + pad).coerceAtMost(h - 1)
            )

            val ring = mutableListOf<Int>()
            val steps = 24
            for (i in 0..steps) {
                val x = outer.left + (outer.width() * i / steps)
                val y = outer.top + (outer.height() * i / steps)
                ring += bitmap.getPixel(x.coerceIn(0, w - 1), outer.top)
                ring += bitmap.getPixel(x.coerceIn(0, w - 1), outer.bottom)
                ring += bitmap.getPixel(outer.left, y.coerceIn(0, h - 1))
                ring += bitmap.getPixel(outer.right, y.coerceIn(0, h - 1))
            }
            if (ring.isEmpty()) return DEFAULT_COLORS

            val bgR = ring.map { Color.red(it) }.sorted()[ring.size / 2]
            val bgG = ring.map { Color.green(it) }.sorted()[ring.size / 2]
            val bgB = ring.map { Color.blue(it) }.sorted()[ring.size / 2]

            val inside = mutableListOf<Int>()
            val gx = 24
            val gy = 8
            for (iy in 0 until gy) {
                for (ix in 0 until gx) {
                    val x = (box.left + box.width() * (ix + 0.5f) / gx).toInt().coerceIn(0, w - 1)
                    val y = (box.top + box.height() * (iy + 0.5f) / gy).toInt().coerceIn(0, h - 1)
                    inside += bitmap.getPixel(x, y)
                }
            }
            val byDistance = inside.sortedByDescending { colorDistance(it, bgR, bgG, bgB) }
            val ink = byDistance.take(max(1, byDistance.size / 6))
            var inkR = ink.sumOf { Color.red(it) } / ink.size
            var inkG = ink.sumOf { Color.green(it) } / ink.size
            var inkB = ink.sumOf { Color.blue(it) } / ink.size

            val bgLum = luminance(bgR, bgG, bgB)
            if (abs(luminance(inkR, inkG, inkB) - bgLum) < 90) {
                // Not enough contrast to be readable: fall back to black or white
                val v = if (bgLum > 128) 0x11 else 0xF9
                inkR = v; inkG = v; inkB = v
            }

            return Pair(argb(0xFF, bgR, bgG, bgB), argb(0xFF, inkR, inkG, inkB))
        } catch (_: Exception) {
            return DEFAULT_COLORS
        }
    }

    private fun colorDistance(c: Int, r: Int, g: Int, b: Int): Double {
        val dr = (Color.red(c) - r).toDouble()
        val dg = (Color.green(c) - g).toDouble()
        val db = (Color.blue(c) - b).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    private fun luminance(r: Int, g: Int, b: Int) = 0.2126 * r + 0.7152 * g + 0.0722 * b

    private fun argb(a: Int, r: Int, g: Int, b: Int): Long =
        (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()

    fun close() {
        listOf(latinRecognizer, japaneseRecognizer, chineseRecognizer, koreanRecognizer, devanagariRecognizer)
            .forEach { runCatching { it.close() } }
    }

    companion object {
        private val DEFAULT_COLORS = Pair(0xFF111827L, 0xFFFFFFFFL)

        fun isHangul(c: Char) = c in '가'..'힯' || c in 'ᄀ'..'ᇿ' || c in '㄰'..'㆏'
        fun isKana(c: Char) = c in '぀'..'ヿ' || c in 'ㇰ'..'ㇿ'
        fun isHan(c: Char) = c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿'
        fun isDevanagari(c: Char) = c in 'ऀ'..'ॿ'
        fun isCjk(c: Char) = isHan(c) || isKana(c) || c in '　'..'〿' || c in '＀'..'￯'
    }
}
