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
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executor

@Composable
fun CustomCameraScreen(
    onImageCaptured: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = remember { CameraSelector.DEFAULT_BACK_CAMERA }
    var isCapturing by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // Ensure flash gets turned off if the composable leaves the screen unexpectedly 
    // (e.g. back button or system interruption)
    DisposableEffect(Unit) {
        onDispose {
            camera?.cameraControl?.enableTorch(false)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                // Portrait orientation: Taller rectangle to cover max screen
                rectWidth = canvasWidth * 0.85f
                rectHeight = rectWidth * 1.58f
            } else {
                // Landscape orientation: Wider rectangle
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
            
            // Subtract hole from background using BlendMode
            with(drawContext.canvas.nativeCanvas) {
                val checkPoint = saveLayer(null, null)
                drawPath(
                    path = backgroundPath,
                    color = Color.Black.copy(alpha = 0.6f)
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
            val strokeColor = Color(0xFF38BDF8)

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
        }

        // Top Cancel Button
        IconButton(
            onClick = {
                // Ensure torch is disabled before navigating away via Cancel
                if (isFlashOn) {
                    camera?.cameraControl?.enableTorch(false)
                    isFlashOn = false
                }
                onCancel()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .padding(top = 24.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Text("X", color = Color.White, fontWeight = FontWeight.Bold)
        }

        // Top Flash Button
        IconButton(
            onClick = {
                isFlashOn = !isFlashOn
                camera?.cameraControl?.enableTorch(isFlashOn)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .padding(top = 24.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Text(if (isFlashOn) "⚡" else "☼", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        // Guide Text
        Text(
            text = "Align ID Card within frame",
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 120.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        // Capture Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(80.dp)
                .border(4.dp, Color.White, CircleShape)
                .padding(8.dp)
                .background(if (isCapturing) Color.Gray else Color.White, CircleShape)
                .clickable(enabled = !isCapturing) {
                    isCapturing = true
                    
                    // Turn off flash visually FIRST, immediately when button is clicked
                    if (isFlashOn) {
                        camera?.cameraControl?.enableTorch(false)
                        isFlashOn = false
                    }

                    takePhoto(
                        context = context,
                        imageCapture = imageCapture,
                        executor = ContextCompat.getMainExecutor(context),
                        onImageCaptured = { uri ->
                            isCapturing = false
                            if (uri != null) {
                                onImageCaptured(uri)
                            } else {
                                Toast.makeText(context, "Failed to capture photo", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
        ) {
            if (isCapturing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            }
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onImageCaptured: (Uri?) -> Unit
) {
    val photoFile = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                onImageCaptured(savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraScreen", "Photo capture failed: ${exception.message}", exception)
                onImageCaptured(null)
            }
        }
    )
}