package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DetectedTextBlock
import com.example.ui.components.TranslationOverlay
import com.example.ui.theme.Accent
import com.example.ui.theme.Muted
import com.example.ui.theme.OnInk
import com.example.ui.theme.Surface2
import kotlin.math.min

@Composable
fun ImageScreen(
    bitmap: Bitmap,
    blocks: List<DetectedTextBlock>,
    busy: Boolean,
    status: String?,
    onClose: () -> Unit
) {
    var showOriginal by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<DetectedTextBlock?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Swallow touches so they don't reach the browser underneath
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 56.dp, bottom = 72.dp)
        ) {
            val density = LocalDensity.current
            val areaW = constraints.maxWidth.toFloat()
            val areaH = constraints.maxHeight.toFloat()
            val scale = min(areaW / bitmap.width, areaH / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { ((areaW - w) / 2f).toDp() },
                        y = with(density) { ((areaH - h) / 2f).toDp() }
                    )
                    .size(width = with(density) { w.toDp() }, height = with(density) { h.toDp() })
            ) {
                Image(
                    bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                if (!showOriginal) {
                    TranslationOverlay(blocks, Modifier.fillMaxSize(), onBlockClick = { selected = it })
                }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = OnInk)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Surface2)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        showOriginal = true
                        waitForUpOrCancellation()
                        showOriginal = false
                    }
                }
                .padding(horizontal = 18.dp, vertical = 11.dp)
        ) {
            when {
                busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = Accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(status ?: "Translating…", color = OnInk, fontSize = 14.sp)
                }
                status != null -> Text(status, color = OnInk, fontSize = 14.sp)
                else -> Text(if (showOriginal) "Original" else "Hold to see original", color = OnInk, fontSize = 14.sp)
            }
        }
    }

    selected?.let { block -> BlockSheet(block, onDismiss = { selected = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockSheet(block: DetectedTextBlock, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(block.originalText, color = Muted, fontSize = 15.sp)
            Spacer(Modifier.height(12.dp))
            Text(block.translatedText, color = OnInk, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Translation", block.translatedText))
                onDismiss()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy translation", color = Accent)
            }
        }
    }
}
