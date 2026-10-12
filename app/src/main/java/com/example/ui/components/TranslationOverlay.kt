package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DetectedTextBlock
import kotlin.math.max

private const val LINE_HEIGHT_RATIO = 1.12f
private const val MIN_FONT_SP = 7f
private const val MAX_FONT_SP = 48f
private val PAD_H = 3.dp
private val PAD_V = 1.dp

/**
 * Paints each translation over its original text, Google Lens style: a patch in the sampled
 * background color with the translation fitted inside. The modifier must size this to exactly the
 * image area the blocks' normalized boxes refer to.
 */
@Composable
fun TranslationOverlay(
    blocks: List<DetectedTextBlock>,
    modifier: Modifier = Modifier,
    onBlockClick: ((DetectedTextBlock) -> Unit)? = null
) {
    BoxWithConstraints(modifier) {
        val areaW = constraints.maxWidth.toFloat()
        val areaH = constraints.maxHeight.toFloat()
        for (block in blocks) {
            // Nothing to cover when the text is already in the target language
            if (block.isTranslating || block.translatedText.isBlank() || block.translatedText == block.originalText) continue
            key(block.id) {
                TranslatedPatch(
                    block = block,
                    leftPx = block.boundingBox.left * areaW,
                    topPx = block.boundingBox.top * areaH,
                    widthPx = max((block.boundingBox.right - block.boundingBox.left) * areaW, 16f),
                    heightPx = max((block.boundingBox.bottom - block.boundingBox.top) * areaH, 12f),
                    onClick = onBlockClick
                )
            }
        }
    }
}

@Composable
private fun TranslatedPatch(
    block: DetectedTextBlock,
    leftPx: Float,
    topPx: Float,
    widthPx: Float,
    heightPx: Float,
    onClick: ((DetectedTextBlock) -> Unit)?
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val baseStyle = LocalTextStyle.current
    // Text in an oval bubble must stay inside the largest inscribed rectangle (~70% of each side)
    val padH = if (block.isBubble) widthPx * 0.3f else with(density) { PAD_H.toPx() } * 2
    val padV = if (block.isBubble) heightPx * 0.3f else with(density) { PAD_V.toPx() } * 2
    val text = block.translatedText

    val fit = remember(text, widthPx, heightPx, density, baseStyle) {
        fitText(measurer, text, widthPx - padH, heightPx - padV, density, baseStyle)
    }
    // Grow (centered) when the translation can't fit the original box even at the minimum size
    val boxW = max(widthPx, fit.requiredWidthPx + padH)
    val boxH = max(heightPx, fit.requiredHeightPx + padV)
    val x = leftPx - (boxW - widthPx) / 2f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(x = with(density) { x.toDp() }, y = with(density) { topPx.toDp() })
            .size(width = with(density) { boxW.toDp() }, height = with(density) { boxH.toDp() })
            .clip(if (block.isBubble) OvalShape else RoundedCornerShape(2.dp))
            .background(Color(block.backgroundColor))
            .then(if (onClick != null) Modifier.clickable { onClick(block) } else Modifier)
            .padding(
                horizontal = with(density) { (padH / 2f).toDp() },
                vertical = with(density) { (padV / 2f).toDp() }
            )
    ) {
        Text(
            text = text,
            color = Color(block.textColor),
            fontSize = fit.fontSize,
            fontWeight = FontWeight.Medium,
            lineHeight = fit.fontSize * LINE_HEIGHT_RATIO,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Clip
        )
    }
}

private object OvalShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) })
}

private data class FittedText(
    val fontSize: TextUnit,
    val requiredHeightPx: Float,
    val requiredWidthPx: Float = 0f
)

/** Largest font that fits the box without breaking any word across lines. */
private fun fitText(
    measurer: TextMeasurer,
    text: String,
    maxWidthPx: Float,
    maxHeightPx: Float,
    density: Density,
    baseStyle: TextStyle
): FittedText {
    if (text.isEmpty() || maxWidthPx <= 1f) return FittedText(MIN_FONT_SP.sp, 0f)

    fun style(sp: Float) = baseStyle.merge(
        TextStyle(
            fontSize = sp.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = (sp * LINE_HEIGHT_RATIO).sp,
            textAlign = TextAlign.Center
        )
    )

    fun measure(sp: Float, widthPx: Float = maxWidthPx) = measurer.measure(
        text = AnnotatedString(text),
        style = style(sp),
        constraints = Constraints(maxWidth = widthPx.toInt().coerceAtLeast(1)),
        density = density
    )

    fun fits(sp: Float): Boolean {
        val result = measure(sp)
        if (result.didOverflowWidth || result.size.height > maxHeightPx) return false
        // Compose silently breaks a too-long word across lines ("Toda/y"); treat that as not fitting
        for (line in 0 until result.lineCount - 1) {
            val end = result.getLineEnd(line)
            if (end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()) {
                return false
            }
        }
        return true
    }

    var lo = MIN_FONT_SP
    var hi = with(density) { maxHeightPx.toSp().value }.coerceIn(MIN_FONT_SP, MAX_FONT_SP)
    if (fits(hi)) return FittedText(hi.sp, measure(hi).size.height.toFloat())
    if (!fits(lo)) {
        // Even the smallest font overflows: widen so no word is split
        val wordSp = with(density) { maxHeightPx.toSp().value }.coerceIn(MIN_FONT_SP, 12f)
        val wordWidth = text.split(' ').filter { it.isNotEmpty() }.maxOfOrNull { word ->
            measurer.measure(AnnotatedString(word), style = style(wordSp), density = density).size.width.toFloat()
        } ?: 0f
        val width = max(maxWidthPx, wordWidth + 1f)
        return FittedText(wordSp.sp, measure(wordSp, width).size.height.toFloat(), width)
    }
    repeat(7) {
        val mid = (lo + hi) / 2f
        if (fits(mid)) lo = mid else hi = mid
    }
    return FittedText(lo.sp, measure(lo).size.height.toFloat())
}
