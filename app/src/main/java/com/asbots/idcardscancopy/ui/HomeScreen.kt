package com.asbots.idcardscancopy.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.asbots.idcardscancopy.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import com.asbots.idcardscancopy.ui.theme.Amber
import com.asbots.idcardscancopy.ui.theme.OnAmber
import com.asbots.idcardscancopy.ui.theme.successColor
import java.io.File

/** Home screen: 3-step flow (front, back, generate). */
@Composable
fun HomeScreen(
    frontUri: Uri?,
    backUri: Uri?,
    isGenerating: Boolean,
    onOpenDrawer: () -> Unit = {},
    onSlotClick: (front: Boolean) -> Unit,
    onClear: (front: Boolean) -> Unit,
    onGenerate: () -> Unit
) {
    val bothReady = frontUri != null && backUri != null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Navigation Menu",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Text("ID Card Scan & Copy", style = MaterialTheme.typography.titleMedium)
            }
        }

        Text(
            if (bothReady) "Ready to generate" else "Copy your ID card in three steps",
            style = MaterialTheme.typography.headlineMedium
        )
        if (!bothReady) {
            Text(
                "Scan both sides. Get 8 copies per page, ready to print.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        StepSlot(1, "Front side", frontUri, { onSlotClick(true) }, { onClear(true) })
        StepSlot(2, "Back side", backUri, { onSlotClick(false) }, { onClear(false) })

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onGenerate,
            enabled = bothReady && !isGenerating,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Amber,
                contentColor = OnAmber,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(
                if (isGenerating) "Generating..." else "Generate 8 copies PDF",
                style = MaterialTheme.typography.labelLarge
            )
        }
        if (!bothReady) {
            Text(
                "Scan both sides to continue",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StepSlot(
    number: Int,
    title: String,
    uri: Uri?,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    val done = uri != null
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(if (done) successColor() else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) "✓" else number.toString(),
                color = if (done) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (done) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ActionPill("Crop / Edit", MaterialTheme.colorScheme.onSurface, onClick)
                        ActionPill("Clear", MaterialTheme.colorScheme.error, onClear)
                    }
                }
            }
            val shape = RoundedCornerShape(16.dp)
            val teal = MaterialTheme.colorScheme.secondary
            val line = MaterialTheme.colorScheme.outline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.586f)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .then(
                        if (done) Modifier.border(1.5.dp, teal, shape)
                        else Modifier.dashedBorder(line).cornerMarks(teal)
                    )
                    .clickable(onClickLabel = "Scan $title") { onClick() },
                contentAlignment = Alignment.Center
            ) {
                if (done) {
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "$title scan",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp)
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(if (number == 1) R.drawable.ic_card_front else R.drawable.ic_card_back),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text("Scan ${title.lowercase()}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Camera or gallery",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(text: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        )
    }
}

/** Bottom sheet: pick camera or gallery. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSheet(isFront: Boolean, onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (isFront) "Add front side" else "Add back side", style = MaterialTheme.typography.titleLarge)
            Text(
                "Choose how to bring in your card.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SourceOption("◉", MaterialTheme.colorScheme.secondary, "Take photo with camera", "Best for a flat card in good light", onCamera)
            SourceOption("▤", MaterialTheme.colorScheme.primary, "Choose from gallery", "Use a photo you already have", onGallery)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SourceOption(icon: String, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(tint),
            contentAlignment = Alignment.Center
        ) { Text(icon, color = MaterialTheme.colorScheme.surface, fontSize = 19.sp) }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Bottom sheet: rename the PDF before saving or sharing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameSheet(
    fileName: String,
    confirmLabel: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Rename file", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = fileName,
                onValueChange = onNameChange,
                label = { Text("File name") },
                singleLine = true,
                suffix = { Text(".pdf") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = CircleShape) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1.4f).height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber)
                ) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ---- drawing helpers ----

private fun Modifier.dashedBorder(color: Color, radius: Dp = 16.dp) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)))
    )
}

/** Crop-mark style corner brackets, a nod to print registration marks. */
private fun Modifier.cornerMarks(color: Color, inset: Dp = 10.dp, length: Dp = 16.dp) = drawBehind {
    val i = inset.toPx()
    val l = length.toPx()
    val sw = 3.dp.toPx()
    val w = size.width
    val h = size.height
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(x1, y1), Offset(x2, y2), sw, StrokeCap.Round)
    seg(i, i, i + l, i); seg(i, i, i, i + l)
    seg(w - i, i, w - i - l, i); seg(w - i, i, w - i, i + l)
    seg(i, h - i, i + l, h - i); seg(i, h - i, i, h - i - l)
    seg(w - i, h - i, w - i - l, h - i); seg(w - i, h - i, w - i, h - i - l)
}