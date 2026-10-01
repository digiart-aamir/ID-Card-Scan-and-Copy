package com.asbots.idcardscancopy.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CropMode {
    PERSPECTIVE,      // Non-rectangular 4 corner points moving independently
    RECTANGLE_FREE,   // Axis-aligned rectangular crop box
    RECTANGLE_ID_RATIO // Fixed 85.6 : 53.98 ID card ratio
}

enum class CornerNode {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, INSIDE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerspectiveCropScreen(
    imageUri: Uri,
    onCropSuccess: (croppedUri: Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var cropMode by remember { mutableStateOf(CropMode.PERSPECTIVE) }
    var isCropping by remember { mutableStateOf(false) }

    // Normalized corner coordinates (0.0 .. 1.0) relative to image
    var tlNorm by remember { mutableStateOf(Offset(0.08f, 0.08f)) }
    var trNorm by remember { mutableStateOf(Offset(0.92f, 0.08f)) }
    var brNorm by remember { mutableStateOf(Offset(0.92f, 0.92f)) }
    var blNorm by remember { mutableStateOf(Offset(0.08f, 0.92f)) }

    var activeNode by remember { mutableStateOf(CornerNode.NONE) }

    // Load bitmap safely with EXIF rotation support
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val bitmap = loadCorrectlyOrientedBitmap(context, imageUri)
                loadedBitmap = bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val resetCorners = {
        tlNorm = Offset(0.08f, 0.08f)
        trNorm = Offset(0.92f, 0.08f)
        brNorm = Offset(0.92f, 0.92f)
        blNorm = Offset(0.08f, 0.92f)
    }

    // Get current rotated bitmap aspect ratio (width / height)
    val bmp = loadedBitmap
    val isRotatedOdd = (rotationDegrees / 90) % 2 != 0
    val currentBmpW = if (bmp != null) (if (isRotatedOdd) bmp.height.toFloat() else bmp.width.toFloat()) else 1f
    val currentBmpH = if (bmp != null) (if (isRotatedOdd) bmp.width.toFloat() else bmp.height.toFloat()) else 1f
    val imageAspect = currentBmpW / currentBmpH

    val alignToMode = { targetMode: CropMode ->
        val left = minOf(tlNorm.x, blNorm.x).coerceIn(0f, 0.8f)
        val right = maxOf(trNorm.x, brNorm.x).coerceIn(left + 0.1f, 1f)
        val top = minOf(tlNorm.y, trNorm.y).coerceIn(0f, 0.8f)
        val bottom = maxOf(blNorm.y, brNorm.y).coerceIn(top + 0.1f, 1f)

        when (targetMode) {
            CropMode.PERSPECTIVE -> {
                // Keep bounding rectangle or current corners
            }
            CropMode.RECTANGLE_FREE -> {
                tlNorm = Offset(left, top)
                trNorm = Offset(right, top)
                brNorm = Offset(right, bottom)
                blNorm = Offset(left, bottom)
            }
            CropMode.RECTANGLE_ID_RATIO -> {
                val targetAspect = 85.6f / 53.98f
                val normWidth = right - left
                var normHeight = normWidth * imageAspect / targetAspect
                if (top + normHeight > 1f) {
                    normHeight = 1f - top
                    val adjustedWidth = normHeight * targetAspect / imageAspect
                    trNorm = Offset(left + adjustedWidth, top)
                    brNorm = Offset(left + adjustedWidth, top + normHeight)
                    blNorm = Offset(left, top + normHeight)
                    tlNorm = Offset(left, top)
                } else {
                    trNorm = Offset(left + normWidth, top)
                    brNorm = Offset(left + normWidth, top + normHeight)
                    blNorm = Offset(left, top + normHeight)
                    tlNorm = Offset(left, top)
                }
            }
        }
    }

    // Helper to enforce mode constraints
    fun updateCorner(node: CornerNode, newNorm: Offset) {
        val clampedX = newNorm.x.coerceIn(0f, 1f)
        val clampedY = newNorm.y.coerceIn(0f, 1f)
        val minGap = 0.03f

        when (cropMode) {
            CropMode.PERSPECTIVE -> {
                when (node) {
                    CornerNode.TOP_LEFT -> {
                        val validX = clampedX.coerceAtMost(trNorm.x - minGap)
                        val validY = clampedY.coerceAtMost(blNorm.y - minGap)
                        tlNorm = Offset(validX, validY)
                    }
                    CornerNode.TOP_RIGHT -> {
                        val validX = clampedX.coerceAtLeast(tlNorm.x + minGap)
                        val validY = clampedY.coerceAtMost(brNorm.y - minGap)
                        trNorm = Offset(validX, validY)
                    }
                    CornerNode.BOTTOM_RIGHT -> {
                        val validX = clampedX.coerceAtLeast(blNorm.x + minGap)
                        val validY = clampedY.coerceAtLeast(trNorm.y + minGap)
                        brNorm = Offset(validX, validY)
                    }
                    CornerNode.BOTTOM_LEFT -> {
                        val validX = clampedX.coerceAtMost(brNorm.x - minGap)
                        val validY = clampedY.coerceAtLeast(tlNorm.y + minGap)
                        blNorm = Offset(validX, validY)
                    }
                    else -> {}
                }
            }
            CropMode.RECTANGLE_FREE -> {
                when (node) {
                    CornerNode.TOP_LEFT -> {
                        val newLeft = clampedX.coerceAtMost(trNorm.x - minGap)
                        val newTop = clampedY.coerceAtMost(blNorm.y - minGap)
                        tlNorm = Offset(newLeft, newTop)
                        blNorm = Offset(newLeft, blNorm.y)
                        trNorm = Offset(trNorm.x, newTop)
                    }
                    CornerNode.TOP_RIGHT -> {
                        val newRight = clampedX.coerceAtLeast(tlNorm.x + minGap)
                        val newTop = clampedY.coerceAtMost(brNorm.y - minGap)
                        trNorm = Offset(newRight, newTop)
                        brNorm = Offset(newRight, brNorm.y)
                        tlNorm = Offset(tlNorm.x, newTop)
                    }
                    CornerNode.BOTTOM_RIGHT -> {
                        val newRight = clampedX.coerceAtLeast(blNorm.x + minGap)
                        val newBottom = clampedY.coerceAtLeast(trNorm.y + minGap)
                        brNorm = Offset(newRight, newBottom)
                        trNorm = Offset(newRight, trNorm.y)
                        blNorm = Offset(blNorm.x, newBottom)
                    }
                    CornerNode.BOTTOM_LEFT -> {
                        val newLeft = clampedX.coerceAtMost(brNorm.x - minGap)
                        val newBottom = clampedY.coerceAtLeast(tlNorm.y + minGap)
                        blNorm = Offset(newLeft, newBottom)
                        tlNorm = Offset(newLeft, tlNorm.y)
                        brNorm = Offset(brNorm.x, newBottom)
                    }
                    else -> {}
                }
            }
            CropMode.RECTANGLE_ID_RATIO -> {
                val targetAspect = 85.6f / 53.98f
                when (node) {
                    CornerNode.TOP_LEFT, CornerNode.BOTTOM_LEFT -> {
                        val newLeft = clampedX.coerceAtMost(trNorm.x - 0.1f)
                        val normWidth = trNorm.x - newLeft
                        val normHeight = (normWidth * imageAspect / targetAspect).coerceIn(0.1f, 1f)
                        val top = tlNorm.y.coerceAtMost(1f - normHeight)

                        tlNorm = Offset(newLeft, top)
                        blNorm = Offset(newLeft, top + normHeight)
                        trNorm = Offset(trNorm.x, top)
                        brNorm = Offset(trNorm.x, top + normHeight)
                    }
                    CornerNode.TOP_RIGHT, CornerNode.BOTTOM_RIGHT -> {
                        val newRight = clampedX.coerceAtLeast(tlNorm.x + 0.1f)
                        val normWidth = newRight - tlNorm.x
                        val normHeight = (normWidth * imageAspect / targetAspect).coerceIn(0.1f, 1f)
                        val top = tlNorm.y.coerceAtMost(1f - normHeight)

                        trNorm = Offset(newRight, top)
                        brNorm = Offset(newRight, top + normHeight)
                        tlNorm = Offset(tlNorm.x, top)
                        blNorm = Offset(tlNorm.x, top + normHeight)
                    }
                    else -> {}
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crop ID Card Image", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val sourceBitmap = loadedBitmap ?: return@Button
                            isCropping = true
                            coroutineScope.launch {
                                val croppedFile = withContext(Dispatchers.IO) {
                                    processPerspectiveCrop(
                                        context = context,
                                        sourceBitmap = sourceBitmap,
                                        tlNorm = tlNorm,
                                        trNorm = trNorm,
                                        brNorm = brNorm,
                                        blNorm = blNorm,
                                        rotationDegrees = rotationDegrees,
                                        cropMode = cropMode
                                    )
                                }
                                isCropping = false
                                if (croppedFile != null) {
                                    onCropSuccess(Uri.fromFile(croppedFile))
                                } else {
                                    Toast.makeText(context, "Failed to crop image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = loadedBitmap != null && !isCropping
                    ) {
                        if (isCropping) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Crop", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = cropMode == CropMode.PERSPECTIVE,
                        onClick = {
                            cropMode = CropMode.PERSPECTIVE
                            alignToMode(CropMode.PERSPECTIVE)
                        },
                        label = { Text("4 Corner Free", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = cropMode == CropMode.RECTANGLE_FREE,
                        onClick = {
                            cropMode = CropMode.RECTANGLE_FREE
                            alignToMode(CropMode.RECTANGLE_FREE)
                        },
                        label = { Text("Rectangle Free", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = cropMode == CropMode.RECTANGLE_ID_RATIO,
                        onClick = {
                            cropMode = CropMode.RECTANGLE_ID_RATIO
                            alignToMode(CropMode.RECTANGLE_ID_RATIO)
                        },
                        label = { Text("Fixed ID Ratio", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Bar (Rotate & Reset)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            rotationDegrees = (rotationDegrees + 90) % 360
                            // Rotate corner points 90 deg clockwise around center (0.5, 0.5)
                            val newTl = Offset(1f - blNorm.y, blNorm.x)
                            val newTr = Offset(1f - tlNorm.y, tlNorm.x)
                            val newBr = Offset(1f - trNorm.y, trNorm.x)
                            val newBl = Offset(1f - brNorm.y, brNorm.x)

                            tlNorm = newTl
                            trNorm = newTr
                            brNorm = newBr
                            blNorm = newBl
                        }
                    ) {
                        Text("Rotate 90°", fontSize = 13.sp)
                    }

                    OutlinedButton(onClick = resetCorners) {
                        Text("Reset Corners", fontSize = 13.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = loadedBitmap
            if (bitmap != null) {
                var containerSize by remember { mutableStateOf(Size.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { containerSize = it.size.toSize() },
                    contentAlignment = Alignment.Center
                ) {
                    if (containerSize.width > 0 && containerSize.height > 0) {
                        // Calculate display scale and offset for ContentScale.Fit
                        val isRotated90 = (rotationDegrees / 90) % 2 != 0
                        val bmpW = if (isRotated90) bitmap.height.toFloat() else bitmap.width.toFloat()
                        val bmpH = if (isRotated90) bitmap.width.toFloat() else bitmap.height.toFloat()

                        val scale = minOf(containerSize.width / bmpW, containerSize.height / bmpH)
                        val displayW = bmpW * scale
                        val displayH = bmpH * scale

                        val leftOffset = (containerSize.width - displayW) / 2f
                        val topOffset = (containerSize.height - displayH) / 2f

                        // Map normalized corner coordinates to screen display coordinates
                        val pTl = Offset(leftOffset + tlNorm.x * displayW, topOffset + tlNorm.y * displayH)
                        val pTr = Offset(leftOffset + trNorm.x * displayW, topOffset + trNorm.y * displayH)
                        val pBr = Offset(leftOffset + brNorm.x * displayW, topOffset + brNorm.y * displayH)
                        val pBl = Offset(leftOffset + blNorm.x * displayW, topOffset + blNorm.y * displayH)

                        val touchRadiusPx = 130f

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(cropMode, displayW, displayH, rotationDegrees) {
                                    detectDragGestures(
                                        onDragStart = { touch ->
                                            // 🚨 FIX: Recalculate current screen points from latest State inside the lambda
                                            // This prevents stale captures of the local pTl/pTr variables which caused dragging to fail!
                                            val currPTl = Offset(leftOffset + tlNorm.x * displayW, topOffset + tlNorm.y * displayH)
                                            val currPTr = Offset(leftOffset + trNorm.x * displayW, topOffset + trNorm.y * displayH)
                                            val currPBr = Offset(leftOffset + brNorm.x * displayW, topOffset + brNorm.y * displayH)
                                            val currPBl = Offset(leftOffset + blNorm.x * displayW, topOffset + blNorm.y * displayH)

                                            activeNode = when {
                                                (touch - currPTl).getDistance() <= touchRadiusPx -> CornerNode.TOP_LEFT
                                                (touch - currPTr).getDistance() <= touchRadiusPx -> CornerNode.TOP_RIGHT
                                                (touch - currPBr).getDistance() <= touchRadiusPx -> CornerNode.BOTTOM_RIGHT
                                                (touch - currPBl).getDistance() <= touchRadiusPx -> CornerNode.BOTTOM_LEFT
                                                else -> {
                                                    if (isPointInsidePoly(touch, listOf(currPTl, currPTr, currPBr, currPBl))) {
                                                        CornerNode.INSIDE
                                                    } else {
                                                        CornerNode.NONE
                                                    }
                                                }
                                            }
                                        },
                                        onDragEnd = { activeNode = CornerNode.NONE },
                                        onDragCancel = { activeNode = CornerNode.NONE },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (activeNode == CornerNode.INSIDE) {
                                                val deltaNormX = dragAmount.x / displayW
                                                val deltaNormY = dragAmount.y / displayH

                                                val minX = minOf(tlNorm.x, trNorm.x, brNorm.x, blNorm.x)
                                                val maxX = maxOf(tlNorm.x, trNorm.x, brNorm.x, blNorm.x)
                                                val minY = minOf(tlNorm.y, trNorm.y, brNorm.y, blNorm.y)
                                                val maxY = maxOf(tlNorm.y, trNorm.y, brNorm.y, blNorm.y)

                                                val clampedDx = deltaNormX.coerceIn(-minX, 1f - maxX)
                                                val clampedDy = deltaNormY.coerceIn(-minY, 1f - maxY)

                                                val shift = Offset(clampedDx, clampedDy)
                                                tlNorm += shift
                                                trNorm += shift
                                                brNorm += shift
                                                blNorm += shift
                                            } else if (activeNode != CornerNode.NONE) {
                                                // 🚨 FIX: Read latest state for the active node instead of stale captured screen coordinates
                                                val currentNorm = when (activeNode) {
                                                    CornerNode.TOP_LEFT -> tlNorm
                                                    CornerNode.TOP_RIGHT -> trNorm
                                                    CornerNode.BOTTOM_RIGHT -> brNorm
                                                    CornerNode.BOTTOM_LEFT -> blNorm
                                                    else -> Offset.Zero
                                                }
                                                val newNormX = currentNorm.x + dragAmount.x / displayW
                                                val newNormY = currentNorm.y + dragAmount.y / displayH
                                                updateCorner(activeNode, Offset(newNormX, newNormY))
                                            }
                                        }
                                    )
                                }
                        ) {
                            // 1. Draw rotated bitmap scaled to display bounds
                            val drawMatrix = Matrix().apply {
                                postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
                                postRotate(rotationDegrees.toFloat())
                                val curRotW = if (isRotated90) bitmap.height else bitmap.width
                                val curRotH = if (isRotated90) bitmap.width else bitmap.height
                                postTranslate(curRotW / 2f, curRotH / 2f)
                                postScale(scale, scale)
                                postTranslate(leftOffset, topOffset)
                            }

                            drawContext.canvas.nativeCanvas.drawBitmap(
                                bitmap,
                                drawMatrix,
                                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
                            )

                            // 2. Draw Dimmed Mask outside the Polygon
                            val fullBoundsPath = Path().apply {
                                addRect(Rect(0f, 0f, size.width, size.height))
                            }
                            val polyPath = Path().apply {
                                moveTo(pTl.x, pTl.y)
                                lineTo(pTr.x, pTr.y)
                                lineTo(pBr.x, pBr.y)
                                lineTo(pBl.x, pBl.y)
                                close()
                            }

                            val overlayPath = Path.combine(PathOperation.Difference, fullBoundsPath, polyPath)
                            drawPath(overlayPath, color = Color.Black.copy(alpha = 0.65f))

                            // 3. Draw Connecting Polygon Outline & 3x3 Grid
                            drawPath(
                                path = polyPath,
                                color = Color(0xFF38BDF8),
                                style = Stroke(width = 3.dp.toPx())
                            )

                            // 3x3 Grid lines inside
                            for (i in 1..2) {
                                val frac = i / 3f
                                // Top to Bottom grid lines
                                val startTop = Offset(pTl.x + (pTr.x - pTl.x) * frac, pTl.y + (pTr.y - pTl.y) * frac)
                                val endBot = Offset(pBl.x + (pBr.x - pBl.x) * frac, pBl.y + (pBr.y - pBl.y) * frac)
                                drawLine(color = Color.White.copy(alpha = 0.35f), start = startTop, end = endBot, strokeWidth = 1.dp.toPx())

                                // Left to Right grid lines
                                val startLeft = Offset(pTl.x + (pBl.x - pTl.x) * frac, pTl.y + (pBl.y - pTl.y) * frac)
                                val endRight = Offset(pTr.x + (pBr.x - pTr.x) * frac, pTr.y + (pBr.y - pTr.y) * frac)
                                drawLine(color = Color.White.copy(alpha = 0.35f), start = startLeft, end = endRight, strokeWidth = 1.dp.toPx())
                            }

                            // 4. Draw Draggable 4 Corner Nodes / Dots
                            val nodes = listOf(
                                CornerNode.TOP_LEFT to pTl,
                                CornerNode.TOP_RIGHT to pTr,
                                CornerNode.BOTTOM_RIGHT to pBr,
                                CornerNode.BOTTOM_LEFT to pBl
                            )

                            for ((node, point) in nodes) {
                                val isActive = activeNode == node
                                val handleRadius = if (isActive) 22.dp.toPx() else 16.dp.toPx()
                                val innerRadius = if (isActive) 12.dp.toPx() else 8.dp.toPx()

                                // Outer accent ring
                                drawCircle(
                                    color = if (isActive) Color(0xFF38BDF8) else Color(0xFF2563EB),
                                    radius = handleRadius,
                                    center = point
                                )
                                // Inner white center dot
                                drawCircle(
                                    color = Color.White,
                                    radius = innerRadius,
                                    center = point
                                )
                                // Core dot indicator
                                drawCircle(
                                    color = Color(0xFF0284C7),
                                    radius = innerRadius * 0.5f,
                                    center = point
                                )
                            }
                        }

                        // 5. Magnifier / Loupe preview when actively dragging a corner
                        if (activeNode != CornerNode.NONE && activeNode != CornerNode.INSIDE) {
                            val activePoint = when (activeNode) {
                                CornerNode.TOP_LEFT -> pTl
                                CornerNode.TOP_RIGHT -> pTr
                                CornerNode.BOTTOM_RIGHT -> pBr
                                CornerNode.BOTTOM_LEFT -> pBl
                                else -> Offset.Zero
                            }

                            // Render magnified loupe in top screen corner
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .size(110.dp)
                                    .shadow(8.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                                    .border(3.dp, Color(0xFF38BDF8), CircleShape)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val loupeSize = size.width
                                    val loupeScale = 2.5f

                                    val drawMatrix = Matrix().apply {
                                        postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
                                        postRotate(rotationDegrees.toFloat())
                                        val curRotW = if (isRotated90) bitmap.height else bitmap.width
                                        val curRotH = if (isRotated90) bitmap.width else bitmap.height
                                        postTranslate(curRotW / 2f, curRotH / 2f)
                                        postScale(scale, scale)
                                        postTranslate(leftOffset, topOffset)
                                    }

                                    val loupeMatrix = Matrix().apply {
                                        postConcat(drawMatrix)
                                        postTranslate(-activePoint.x, -activePoint.y)
                                        postScale(loupeScale, loupeScale)
                                        postTranslate(loupeSize / 2f, loupeSize / 2f)
                                    }

                                    drawContext.canvas.nativeCanvas.drawBitmap(
                                        bitmap,
                                        loupeMatrix,
                                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                    )

                                    // Center Crosshair
                                    val center = Offset(loupeSize / 2f, loupeSize / 2f)
                                    drawLine(
                                        color = Color(0xFF38BDF8),
                                        start = Offset(center.x - 16.dp.toPx(), center.y),
                                        end = Offset(center.x + 16.dp.toPx(), center.y),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                    drawLine(
                                        color = Color(0xFF38BDF8),
                                        start = Offset(center.x, center.y - 16.dp.toPx()),
                                        end = Offset(center.x, center.y + 16.dp.toPx()),
                                        strokeWidth = 2.dp.toPx()
                                    )
                                    drawCircle(color = Color.White, radius = 3.dp.toPx(), center = center)
                                }
                            }
                        }
                    }
                }
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

private fun isPointInsidePoly(point: Offset, poly: List<Offset>): Boolean {
    var result = false
    var j = poly.size - 1
    for (i in poly.indices) {
        if ((poly[i].y > point.y) != (poly[j].y > point.y) &&
            (point.x < (poly[j].x - poly[i].x) * (point.y - poly[i].y) / (poly[j].y - poly[i].y) + poly[i].x)
        ) {
            result = !result
        }
        j = i
    }
    return result
}

private fun loadCorrectlyOrientedBitmap(context: Context, uri: Uri): Bitmap {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            val size = info.size
            val maxDimension = 2000
            if (size.width > maxDimension || size.height > maxDimension) {
                val sampleSize = maxOf(size.width / maxDimension, size.height / maxDimension)
                if (sampleSize > 1) {
                    decoder.setTargetSampleSize(sampleSize)
                }
            }
        }
    } else {
        @Suppress("DEPRECATION")
        val loaded = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        loaded.copy(Bitmap.Config.ARGB_8888, true)
    }

    // Check EXIF orientation
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val exif = inputStream?.use { ExifInterface(it) }
        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL

        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } else {
            bitmap
        }
    } catch (e: Exception) {
        bitmap
    }
}

private fun processPerspectiveCrop(
    context: Context,
    sourceBitmap: Bitmap,
    tlNorm: Offset,
    trNorm: Offset,
    brNorm: Offset,
    blNorm: Offset,
    rotationDegrees: Int,
    cropMode: CropMode
): File? {
    try {
        // Rotate source bitmap first if needed
        val rotatedBitmap = if (rotationDegrees % 360 != 0) {
            val rotMatrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, rotMatrix, true)
        } else {
            sourceBitmap
        }

        val bw = rotatedBitmap.width.toFloat()
        val bh = rotatedBitmap.height.toFloat()

        // Map normalized coordinates (0..1) to rotated bitmap pixel dimensions
        val pTlX = (tlNorm.x * bw).coerceIn(0f, bw)
        val pTlY = (tlNorm.y * bh).coerceIn(0f, bh)

        val pTrX = (trNorm.x * bw).coerceIn(0f, bw)
        val pTrY = (trNorm.y * bh).coerceIn(0f, bh)

        val pBrX = (brNorm.x * bw).coerceIn(0f, bw)
        val pBrY = (brNorm.y * bh).coerceIn(0f, bh)

        val pBlX = (blNorm.x * bw).coerceIn(0f, bw)
        val pBlY = (blNorm.y * bh).coerceIn(0f, bh)

        // Calculate target crop dimensions based on average edge lengths
        val topWidth = hypot((pTrX - pTlX).toDouble(), (pTrY - pTlY).toDouble()).toFloat()
        val bottomWidth = hypot((pBrX - pBlX).toDouble(), (pBrY - pBlY).toDouble()).toFloat()
        val leftHeight = hypot((pBlX - pTlX).toDouble(), (pBlY - pTlY).toDouble()).toFloat()
        val rightHeight = hypot((pBrX - pTrX).toDouble(), (pBrY - pTrY).toDouble()).toFloat()

        val avgWidth = (topWidth + bottomWidth) / 2f
        val avgHeight = (leftHeight + rightHeight) / 2f

        val targetWidth: Float
        val targetHeight: Float

        if (cropMode == CropMode.RECTANGLE_ID_RATIO) {
            val idAspect = 85.6f / 53.98f // ~1.5857
            targetWidth = maxOf(avgWidth, 800f)
            targetHeight = targetWidth / idAspect
        } else {
            targetWidth = avgWidth.coerceAtLeast(150f)
            targetHeight = avgHeight.coerceAtLeast(150f)
        }

        val dstW = targetWidth.toInt()
        val dstH = targetHeight.toInt()

        // Inverse Homography Matrix: Maps target coordinates (0..dstW, 0..dstH) to source coordinates in rotatedBitmap
        val dstToSrcMatrix = Matrix()
        val ok = dstToSrcMatrix.setPolyToPoly(
            floatArrayOf(
                0f, 0f,
                targetWidth, 0f,
                targetWidth, targetHeight,
                0f, targetHeight
            ),
            0,
            floatArrayOf(
                pTlX, pTlY,
                pTrX, pTrY,
                pBrX, pBrY,
                pBlX, pBlY
            ),
            0,
            4
        )

        if (!ok) {
            return null
        }

        val m = FloatArray(9)
        dstToSrcMatrix.getValues(m)
        val m00 = m[Matrix.MSCALE_X]; val m01 = m[Matrix.MSKEW_X]; val m02 = m[Matrix.MTRANS_X]
        val m10 = m[Matrix.MSKEW_Y]; val m11 = m[Matrix.MSCALE_Y]; val m12 = m[Matrix.MTRANS_Y]
        val m20 = m[Matrix.MPERSP_0]; val m21 = m[Matrix.MPERSP_1]; val m22 = m[Matrix.MPERSP_2]

        val srcW = rotatedBitmap.width
        val srcH = rotatedBitmap.height
        val srcPixels = IntArray(srcW * srcH)
        rotatedBitmap.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        val outPixels = IntArray(dstW * dstH)

        for (y in 0 until dstH) {
            val yf = y.toFloat()
            val rowOffset = y * dstW
            for (x in 0 until dstW) {
                val xf = x.toFloat()
                val denom = m20 * xf + m21 * yf + m22
                if (denom != 0f) {
                    val u = (m00 * xf + m01 * yf + m02) / denom
                    val v = (m10 * xf + m11 * yf + m12) / denom

                    val cu = u.coerceIn(0f, (srcW - 1).toFloat())
                    val cv = v.coerceIn(0f, (srcH - 1).toFloat())

                    val x0 = cu.toInt()
                    val y0 = cv.toInt()
                    val x1 = (x0 + 1).coerceAtMost(srcW - 1)
                    val y1 = (y0 + 1).coerceAtMost(srcH - 1)

                    val dx = cu - x0
                    val dy = cv - y0

                    val p00 = srcPixels[y0 * srcW + x0]
                    val p10 = srcPixels[y0 * srcW + x1]
                    val p01 = srcPixels[y1 * srcW + x0]
                    val p11 = srcPixels[y1 * srcW + x1]

                    val r00 = (p00 shr 16) and 0xFF; val g00 = (p00 shr 8) and 0xFF; val b00 = p00 and 0xFF; val a00 = (p00 ushr 24)
                    val r10 = (p10 shr 16) and 0xFF; val g10 = (p10 shr 8) and 0xFF; val b10 = p10 and 0xFF; val a10 = (p10 ushr 24)
                    val r01 = (p01 shr 16) and 0xFF; val g01 = (p01 shr 8) and 0xFF; val b01 = p01 and 0xFF; val a01 = (p01 ushr 24)
                    val r11 = (p11 shr 16) and 0xFF; val g11 = (p11 shr 8) and 0xFF; val b11 = p11 and 0xFF; val a11 = (p11 ushr 24)

                    val w00 = (1f - dx) * (1f - dy)
                    val w10 = dx * (1f - dy)
                    val w01 = (1f - dx) * dy
                    val w11 = dx * dy

                    val a = (a00 * w00 + a10 * w10 + a01 * w01 + a11 * w11).toInt().coerceIn(0, 255)
                    val r = (r00 * w00 + r10 * w10 + r01 * w01 + r11 * w11).toInt().coerceIn(0, 255)
                    val g = (g00 * w00 + g10 * w10 + g01 * w01 + g11 * w11).toInt().coerceIn(0, 255)
                    val b = (b00 * w00 + b10 * w10 + b01 * w01 + b11 * w11).toInt().coerceIn(0, 255)

                    outPixels[rowOffset + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        val outputBitmap = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        outputBitmap.setPixels(outPixels, 0, dstW, 0, 0, dstW, dstH)

        if (rotatedBitmap != sourceBitmap) {
            rotatedBitmap.recycle()
        }

        // Save output bitmap to cache file
        val file = File(context.cacheDir, "cropped_card_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            outputBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        outputBitmap.recycle()

        return file
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
