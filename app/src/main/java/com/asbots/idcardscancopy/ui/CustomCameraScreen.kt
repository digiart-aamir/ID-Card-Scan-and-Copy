package com.asbots.idcardscancopy.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import android.media.ExifInterface
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CustomCameraScreen(
    onImageCaptured: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = remember { CameraSelector.DEFAULT_BACK_CAMERA }
    var isCapturing by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // Ensure flash gets turned off if the composable leaves the screen unexpectedly 
    DisposableEffect(Unit) {
        onDispose {
            camera?.cameraControl?.enableTorch(false)
        }
    }

    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val scanLineAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLine"
    )
    
    val buttonScale by animateFloatAsState(
        targetValue = if (isCapturing) 0.85f else 1f, 
        label = "buttonScale"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                
                val executor = ContextCompat.getMainExecutor(ctx)
                
                // Bind CameraX
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    try {
                        cameraProvider.unbindAll()
                        val boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                        camera = boundCamera
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Use case binding failed", e)
                    }
                }, executor)
                
                previewView
            }
        )

        // Viewfinder Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val isPortrait = canvasHeight > canvasWidth

            // Calculate Viewfinder Rectangle (CR80 ID Card Ratio: ~1.58)
            val rectWidth: Float
            val rectHeight: Float
            
            if (isPortrait) {
                // Portrait orientation
                rectWidth = canvasWidth * 0.85f
                rectHeight = rectWidth * 1.58f
            } else {
                // Landscape orientation
                rectHeight = canvasHeight * 0.85f
                rectWidth = rectHeight * 1.58f
            }

            val left = (canvasWidth - rectWidth) / 2f
            val top = (canvasHeight - rectHeight) / 2f
            val right = left + rectWidth
            val bottom = top + rectHeight

            // Draw Semi-transparent Dark Background
            val backgroundPath = Path().apply {
                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
            }
            
            val holePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left, top, right, bottom, CornerRadius(16.dp.toPx(), 16.dp.toPx())
                    )
                )
            }
            
            // Subtract hole from background
            with(drawContext.canvas.nativeCanvas) {
                val checkPoint = saveLayer(null, null)
                drawPath(
                    path = backgroundPath,
                    color = Color.Black.copy(alpha = 0.75f) // Darker frosted feel
                )
                drawPath(
                    path = holePath,
                    color = Color.Transparent,
                    blendMode = BlendMode.Clear
                )
                restoreToCount(checkPoint)
            }

            // Draw Viewfinder Border Frame
            val cornerLength = 40.dp.toPx()
            val strokeWidth = 4.dp.toPx()
            val strokeColor = Color(0xFF00E5FF) // Cyber Cyan

            // Top-Left corner
            drawLine(strokeColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
            drawLine(strokeColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth)

            // Top-Right corner
            drawLine(strokeColor, Offset(right, top), Offset(right - cornerLength, top), strokeWidth)
            drawLine(strokeColor, Offset(right, top), Offset(right, top + cornerLength), strokeWidth)

            // Bottom-Left corner
            drawLine(strokeColor, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth)
            drawLine(strokeColor, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth)

            // Bottom-Right corner
            drawLine(strokeColor, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth)
            drawLine(strokeColor, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth)

            // Animated Scanner Line
            val scanLineY = top + (rectHeight * scanLineAnim)
            
            // Glowing line
            drawLine(
                color = strokeColor.copy(alpha = 0.8f),
                start = Offset(left, scanLineY),
                end = Offset(right, scanLineY),
                strokeWidth = 2.dp.toPx()
            )
            
            // Glow gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, strokeColor.copy(alpha = 0.3f), Color.Transparent),
                    startY = scanLineY - 30f,
                    endY = scanLineY + 30f
                ),
                topLeft = Offset(left, scanLineY - 30f),
                size = Size(rectWidth, 60f)
            )
        }

        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Close Button
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isFlashOn) {
                        camera?.cameraControl?.enableTorch(false)
                        isFlashOn = false
                    }
                    onCancel()
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Text("✕", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            // Flash Button
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    isFlashOn = !isFlashOn
                    camera?.cameraControl?.enableTorch(isFlashOn)
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isFlashOn) Color(0xFFFFC107).copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f))
            ) {
                Text(
                    text = if (isFlashOn) "⚡" else "☼",
                    color = if (isFlashOn) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
        }

        // Bottom Controls Layer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Guide Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("ℹ", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Position ID Card inside the frame",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Premium Shutter Button
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .scale(buttonScale) // Animates scale on click
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
                    .clickable(enabled = !isCapturing) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        isCapturing = true

                        takePhoto(
                            context = context,
                            imageCapture = imageCapture,
                            executor = ContextCompat.getMainExecutor(context),
                            onCaptureStarted = {
                                if (isFlashOn) {
                                    camera?.cameraControl?.enableTorch(false)
                                    isFlashOn = false
                                }
                            },
                            onImageCaptured = { uri ->
                                isCapturing = false
                                if (uri != null) {
                                    onImageCaptured(uri)
                                } else {
                                    Toast.makeText(context, "Failed to capture", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Inner button body
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .border(3.dp, Color.White, CircleShape)
                        .background(if (isCapturing) Color(0xFF00E5FF) else Color.White, CircleShape)
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(24.dp), 
                            color = Color.Black, 
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onCaptureStarted: () -> Unit,
    onImageCaptured: (Uri?) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                // Trigger instant flash off (runs on main thread via executor)
                onCaptureStarted()
                
                val rotationDegrees = image.imageInfo.rotationDegrees

                // Save file in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val photoFile = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
                        
                        val bytes = image.use { proxy ->
                            val buffer = proxy.planes[0].buffer
                            val array = ByteArray(buffer.remaining())
                            buffer.get(array)
                            array
                        }
                        
                        FileOutputStream(photoFile).use { it.write(bytes) }
                        
                        // Apply correct EXIF orientation based on rotationDegrees
                        val exif = ExifInterface(photoFile.absolutePath)
                        val orientation = when (rotationDegrees) {
                            90 -> ExifInterface.ORIENTATION_ROTATE_90
                            180 -> ExifInterface.ORIENTATION_ROTATE_180
                            270 -> ExifInterface.ORIENTATION_ROTATE_270
                            else -> ExifInterface.ORIENTATION_NORMAL
                        }
                        exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                        exif.saveAttributes()
                        
                        withContext(Dispatchers.Main) {
                            onImageCaptured(Uri.fromFile(photoFile))
                        }
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Photo save failed: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            onImageCaptured(null)
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onCaptureStarted()
                Log.e("CameraScreen", "Photo capture failed: ${exception.message}", exception)
                onImageCaptured(null)
            }
        }
    )
}