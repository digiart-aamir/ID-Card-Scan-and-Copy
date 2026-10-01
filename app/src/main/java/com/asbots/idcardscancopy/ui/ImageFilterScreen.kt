package com.asbots.idcardscancopy.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class FilterType(val title: String) {
    ORIGINAL("Original"),
    GRAYSCALE("Grayscale"),
    HIGH_CONTRAST("Sharp B/W"),
    ENHANCE("Enhance Color")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageFilterScreen(
    imageUri: Uri,
    onFilterSuccess: (filteredUri: Uri) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedFilter by remember { mutableStateOf(FilterType.ORIGINAL) }
    var isSaving by remember { mutableStateOf(false) }

    // Load the cropped image once
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val bmp = BitmapFactory.decodeStream(inputStream)
                originalBitmap = bmp
                displayedBitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Reactively apply the filter to the original bitmap when the selection changes
    LaunchedEffect(selectedFilter, originalBitmap) {
        val orig = originalBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val filtered = applyFilterToBitmap(orig, selectedFilter)
            withContext(Dispatchers.Main) {
                displayedBitmap = filtered
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apply Filter", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text("Back", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val bmpToSave = displayedBitmap ?: return@Button
                            isSaving = true
                            coroutineScope.launch {
                                val savedFile = withContext(Dispatchers.IO) {
                                    try {
                                        val file = File(context.cacheDir, "filtered_card_${System.currentTimeMillis()}.jpg")
                                        FileOutputStream(file).use { out ->
                                            bmpToSave.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                        }
                                        file
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        null
                                    }
                                }
                                isSaving = false
                                if (savedFile != null) {
                                    onFilterSuccess(Uri.fromFile(savedFile))
                                } else {
                                    Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = displayedBitmap != null && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Done", fontWeight = FontWeight.Bold)
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
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(FilterType.entries) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.title, fontWeight = FontWeight.Medium) }
                        )
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
            displayedBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Filtered Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
            } ?: CircularProgressIndicator(color = Color.White)
        }
    }
}

/**
 * Applies predefined ColorMatrix adjustments to a given Bitmap.
 */
private fun applyFilterToBitmap(bitmap: Bitmap, filterType: FilterType): Bitmap {
    if (filterType == FilterType.ORIGINAL) return bitmap

    val output = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint()
    val cm = ColorMatrix()

    when (filterType) {
        FilterType.GRAYSCALE -> {
            cm.setSaturation(0f)
        }
        FilterType.HIGH_CONTRAST -> {
            cm.setSaturation(0f)
            val scale = 1.5f
            val translate = (-0.5f * scale + 0.5f) * 255f
            val contrastMatrix = ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(contrastMatrix)
        }
        FilterType.ENHANCE -> {
            val scale = 1.2f
            val translate = (-0.5f * scale + 0.5f) * 255f
            cm.set(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            val satMatrix = ColorMatrix()
            satMatrix.setSaturation(1.3f)
            cm.postConcat(satMatrix)
        }
        else -> {}
    }

    paint.colorFilter = ColorMatrixColorFilter(cm)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    return output
}