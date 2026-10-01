package com.asbots.idcardscancopy

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.asbots.idcardscancopy.ui.PerspectiveCropScreen
import com.asbots.idcardscancopy.ui.CustomCameraScreen
import com.asbots.idcardscancopy.ui.ImageFilterScreen
import com.asbots.idcardscancopy.ui.theme.IDCardScanCopyTheme
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

enum class PdfAction { SAVE, SHARE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IDCardScanCopyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        IDCardScannerApp()
                    }
                }
            }
        }
    }
}

@Composable
fun IDCardScannerApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var frontImageUri by remember { mutableStateOf<Uri?>(null) }
    var rawFrontImageUri by remember { mutableStateOf<Uri?>(null) }
    var backImageUri by remember { mutableStateOf<Uri?>(null) }
    var rawBackImageUri by remember { mutableStateOf<Uri?>(null) }

    var pdfFile by remember { mutableStateOf<File?>(null) }
    var isGeneratingPdf by remember { mutableStateOf(false) }

    var isFrontSide by remember { mutableStateOf(true) }
    var showSourceDialog by remember { mutableStateOf(false) }

    // Rename Dialog state
    var showRenameDialog by remember { mutableStateOf(false) }
    var customFileName by remember { mutableStateOf("IDCardCopies") }
    var pendingAction by remember { mutableStateOf<PdfAction?>(null) }

    // Perspective Cropper state
    var showCropScreen by remember { mutableStateOf(false) }
    var rawImageToCropUri by remember { mutableStateOf<Uri?>(null) }

    // Image Filter state
    var showFilterScreen by remember { mutableStateOf(false) }
    var croppedImageToFilterUri by remember { mutableStateOf<Uri?>(null) }

    // Custom Camera State
    var showCameraScreen by remember { mutableStateOf(false) }

    // Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            if (isFrontSide) {
                rawFrontImageUri = uri
            } else {
                rawBackImageUri = uri
            }
            rawImageToCropUri = uri
            showCropScreen = true
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCameraScreen = true
        } else {
            Toast.makeText(context, "Camera permission required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    val openCamera = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showCameraScreen = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val openGallery = {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val startSelectionOrCrop = { front: Boolean ->
        isFrontSide = front
        val existingRawUri = if (front) rawFrontImageUri else rawBackImageUri
        val existingCroppedUri = if (front) frontImageUri else backImageUri
        if (existingRawUri != null || existingCroppedUri != null) {
            rawImageToCropUri = existingRawUri ?: existingCroppedUri
            showCropScreen = true
        } else {
            showSourceDialog = true
        }
    }

    if (showCameraScreen) {
        CustomCameraScreen(
            onImageCaptured = { uri: Uri ->
                showCameraScreen = false
                if (isFrontSide) {
                    rawFrontImageUri = uri
                } else {
                    rawBackImageUri = uri
                }
                rawImageToCropUri = uri
                showCropScreen = true
            },
            onCancel = {
                showCameraScreen = false
            }
        )
    } else if (showFilterScreen && croppedImageToFilterUri != null) {
        ImageFilterScreen(
            imageUri = croppedImageToFilterUri!!,
            onFilterSuccess = { finalUri: Uri ->
                if (isFrontSide) {
                    frontImageUri = finalUri
                } else {
                    backImageUri = finalUri
                }
                showFilterScreen = false
                croppedImageToFilterUri = null
            },
            onCancel = {
                showFilterScreen = false
                croppedImageToFilterUri = null
            }
        )
    } else if (showCropScreen && rawImageToCropUri != null) {
        PerspectiveCropScreen(
            imageUri = rawImageToCropUri!!,
            onCropSuccess = { croppedUri ->
                showCropScreen = false
                rawImageToCropUri = null
                croppedImageToFilterUri = croppedUri
                showFilterScreen = true
            },
            onCancel = {
                showCropScreen = false
                rawImageToCropUri = null
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Branding Header
            Text(
                text = "ID Card Scan & Copy",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            // Front Side Card Box
            CardSelectionView(
                title = "Front Side",
                uri = frontImageUri,
                onClick = { startSelectionOrCrop(true) },
                onClear = {
                    frontImageUri = null
                    rawFrontImageUri = null
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Back Side Card Box
            CardSelectionView(
                title = "Back Side",
                uri = backImageUri,
                onClick = { startSelectionOrCrop(false) },
                onClear = {
                    backImageUri = null
                    rawBackImageUri = null
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (frontImageUri == null || backImageUri == null) {
                        Toast.makeText(context, "Please scan both front and back", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isGeneratingPdf = true
                    coroutineScope.launch {
                        val file = generatePdf(context, frontImageUri, backImageUri)
                        pdfFile = file
                        isGeneratingPdf = false
                        if (file != null) {
                            Toast.makeText(context, "PDF Generated Successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isGeneratingPdf
            ) {
                Text(if (isGeneratingPdf) "Generating..." else "Generate 8 Copies PDF")
            }

            if (pdfFile != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { printPdf(context, pdfFile!!) }) {
                        Text("Print")
                    }
                    Button(onClick = {
                        customFileName = pdfFile!!.nameWithoutExtension
                        pendingAction = PdfAction.SHARE
                        showRenameDialog = true
                    }) {
                        Text("Share")
                    }
                    Button(onClick = {
                        customFileName = pdfFile!!.nameWithoutExtension
                        pendingAction = PdfAction.SAVE
                        showRenameDialog = true
                    }) {
                        Text("Save")
                    }
                }
            }
        }

        // Rename Dialog
        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename File", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = customFileName,
                        onValueChange = { customFileName = it },
                        label = { Text("File Name") },
                        singleLine = true,
                        suffix = { Text(".pdf") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRenameDialog = false
                            val finalName = if (customFileName.isNotBlank()) customFileName.trim() else "IDCardCopies"
                            val newFileName = "$finalName.pdf"
                            
                            // Rename the cached file so sharing/saving gets the correct name
                            val renamedFile = File(context.cacheDir, newFileName)
                            if (pdfFile?.absolutePath != renamedFile.absolutePath) {
                                pdfFile?.renameTo(renamedFile)
                                pdfFile = renamedFile
                            }

                            when (pendingAction) {
                                PdfAction.SAVE -> savePdfToDownloads(context, pdfFile!!, newFileName)
                                PdfAction.SHARE -> sharePdf(context, pdfFile!!)
                                else -> {}
                            }
                            pendingAction = null
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Source Dialog (Camera / Gallery)
        if (showSourceDialog) {
            AlertDialog(
                onDismissRequest = { showSourceDialog = false },
                title = { Text("Select Image Source", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        TextButton(
                            onClick = {
                                showSourceDialog = false
                                openCamera()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Take Photo with Camera", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                showSourceDialog = false
                                openGallery()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Choose from Gallery", fontSize = 16.sp)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showSourceDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun createTempImageUri(context: Context): Uri? {
    return try {
        val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun CardSelectionView(
    title: String,
    uri: Uri?,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            if (uri != null) {
                Row {
                    Text(
                        text = "Crop / Edit",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onClick() }
                            .padding(end = 12.dp, bottom = 8.dp)
                    )
                    Text(
                        text = "Clear",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onClear() }
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.LightGray.copy(alpha = 0.35f))
                .border(
                    width = 1.dp,
                    color = if (uri != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (uri != null) {
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (title.contains("Front", ignoreCase = true)) R.drawable.ic_card_front else R.drawable.ic_card_back
                        ),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tap to Scan/Crop $title", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }
            }
        }
    }
}

suspend fun generatePdf(context: Context, frontUri: Uri?, backUri: Uri?): File? {
    return withContext(Dispatchers.IO) {
        if (frontUri == null || backUri == null) return@withContext null
        var frontBitmap: Bitmap? = null
        var backBitmap: Bitmap? = null
        val document = PdfDocument()
        try {
            frontBitmap = getBitmapFromUri(context, frontUri)
            backBitmap = getBitmapFromUri(context, backUri)

            if (frontBitmap == null || backBitmap == null) return@withContext null

            // A4 page size in points (1 point = 1/72 inch): 595 x 842
            val pageWidth = 595
            val pageHeight = 842

            // Page 1: Front
            val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page1 = document.startPage(pageInfo1)
            draw8Copies(page1.canvas, frontBitmap, pageWidth.toFloat(), pageHeight.toFloat())
            document.finishPage(page1)

            // Page 2: Back
            val pageInfo2 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            val page2 = document.startPage(pageInfo2)
            draw8Copies(page2.canvas, backBitmap, pageWidth.toFloat(), pageHeight.toFloat())
            document.finishPage(page2)

            val pdfFile = File(context.cacheDir, "IDCardCopies.pdf")
            FileOutputStream(pdfFile).use { out ->
                document.writeTo(out)
            }
            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            document.close()
            frontBitmap?.recycle()
            backBitmap?.recycle()
        }
    }
}

private fun draw8Copies(canvas: Canvas, originalBitmap: Bitmap, pageWidth: Float, pageHeight: Float) {
    // CNIC dimensions: 3.375 x 2.125 inches (CR80 standard)
    // Convert to points (x 72) -> 243 x 153 points
    val targetWidth = 243f
    val targetHeight = 153f

    val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth.toInt(), targetHeight.toInt(), true)
    try {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val cols = 2
        val rows = 4

        // Calculate margins for equal spacing
        val totalAvailableWidthForMargins = pageWidth - (cols * targetWidth)
        val horizontalMargin = totalAvailableWidthForMargins / (cols + 1)

        val totalAvailableHeightForMargins = pageHeight - (rows * targetHeight)
        val verticalMargin = totalAvailableHeightForMargins / (rows + 1)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = horizontalMargin + col * (targetWidth + horizontalMargin)
                val y = verticalMargin + row * (targetHeight + verticalMargin)
                canvas.drawBitmap(scaledBitmap, x, y, paint)
            }
        }
    } finally {
        if (scaledBitmap != originalBitmap) {
            scaledBitmap.recycle()
        }
    }
}

private fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
                val size = info.size
                val maxDimension = 1200
                if (size.width > maxDimension || size.height > maxDimension) {
                    val sampleSize = maxOf(size.width / maxDimension, size.height / maxDimension)
                    if (sampleSize > 1) {
                        decoder.setTargetSampleSize(sampleSize)
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun printPdf(context: Context, pdfFile: File) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val printAdapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("IDCardCopies.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(2)
                .build()
            callback?.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            try {
                FileInputStream(pdfFile).use { input ->
                    FileOutputStream(destination?.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback?.onWriteFailed(e.message)
            }
        }
    }
    printManager.print("IDCardCopies", printAdapter, null)
}

fun sharePdf(context: Context, pdfFile: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ID Copies PDF"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error sharing file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun savePdfToDownloads(context: Context, pdfFile: File, fileName: String) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(pdfFile).use { input ->
                        input.copyTo(out)
                    }
                }
                Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to create file in Downloads", Toast.LENGTH_SHORT).show()
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val targetFile = File(downloadsDir, fileName)
            FileInputStream(pdfFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(context, "Saved to Downloads: ${targetFile.name}", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error saving file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
