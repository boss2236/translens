package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.model.DetectedTextBlock
import com.example.ui.components.TranslationOverlay
import com.example.ui.theme.Accent
import com.example.ui.theme.Ink
import com.example.ui.theme.Muted
import com.example.ui.theme.OnInk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

private class CameraFrame(val bitmap: Bitmap, val blocks: List<DetectedTextBlock>)

/** Live camera translation: frames are translated continuously and painted over the preview. */
@Composable
fun CameraScreen(
    translateFrame: suspend (Bitmap) -> List<DetectedTextBlock>,
    onFreeze: (Bitmap, List<DetectedTextBlock>) -> Unit,
    onPickPhoto: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val translate by rememberUpdatedState(translateFrame)
    val frames = remember { Channel<Bitmap>(Channel.CONFLATED) }
    val processing = remember { AtomicBoolean(false) }
    var latest by remember { mutableStateOf<CameraFrame?>(null) }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        for (bitmap in frames) {
            try {
                latest = CameraFrame(bitmap, translate(bitmap))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                processing.set(false)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        if (hasPermission) {
            val executor = remember { Executors.newSingleThreadExecutor() }
            DisposableEffect(Unit) { onDispose { executor.shutdown() } }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val viewW = constraints.maxWidth.toFloat()
                val viewH = constraints.maxHeight.toFloat()

                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val selector = ResolutionSelector.Builder()
                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                .setResolutionStrategy(
                                    ResolutionStrategy(Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                                )
                                .build()
                            val preview = Preview.Builder().setResolutionSelector(selector).build()
                                .also { it.surfaceProvider = previewView.surfaceProvider }
                            val analysis = ImageAnalysis.Builder()
                                .setResolutionSelector(selector)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()
                            analysis.setAnalyzer(executor) { proxy ->
                                // Only one frame in flight; drop the rest
                                if (!processing.compareAndSet(false, true)) {
                                    proxy.close()
                                    return@setAnalyzer
                                }
                                val rotation = proxy.imageInfo.rotationDegrees
                                val raw = proxy.toBitmap()
                                proxy.close()
                                val upright = if (rotation == 0) raw else Bitmap.createBitmap(
                                    raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true
                                )
                                frames.trySend(upright)
                            }
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                            } catch (_: Exception) {}
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Map frame coordinates onto the FILL_CENTER preview
                latest?.let { frame ->
                    val density = LocalDensity.current
                    val scale = max(viewW / frame.bitmap.width, viewH / frame.bitmap.height)
                    val w = frame.bitmap.width * scale
                    val h = frame.bitmap.height * scale
                    TranslationOverlay(
                        blocks = frame.blocks,
                        modifier = Modifier
                            .offset(
                                x = with(density) { ((viewW - w) / 2f).toDp() },
                                y = with(density) { ((viewH - h) / 2f).toDp() }
                            )
                            .size(width = with(density) { w.toDp() }, height = with(density) { h.toDp() })
                    )
                }
            }
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera access is needed to translate what you point at.", color = Muted, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera", color = Accent)
                }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = OnInk)
        }

        // Shutter freezes the current translated frame; gallery opens a photo instead
        IconButton(
            onClick = onPickPhoto,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 32.dp, bottom = 40.dp)
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = "Translate a photo", tint = OnInk)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .size(72.dp)
                .border(3.dp, OnInk, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(OnInk)
                .clickable(enabled = latest != null) { latest?.let { onFreeze(it.bitmap, it.blocks) } }
        )
    }
}
