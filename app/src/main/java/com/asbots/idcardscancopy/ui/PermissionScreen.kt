package com.asbots.idcardscancopy.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.asbots.idcardscancopy.R
import com.asbots.idcardscancopy.ui.theme.Amber
import com.asbots.idcardscancopy.ui.theme.OnAmber

/** One permission plus the reason we show to the user. */
data class AppPermission(
    val permission: String,
    val icon: String,
    val title: String,
    val reason: String
)

/** Only the permissions this device really needs. */
fun requiredPermissions(): List<AppPermission> = buildList {
    add(
        AppPermission(
            Manifest.permission.CAMERA, "◉", "Camera",
            "To photograph the front and back of your ID card. Photos stay on your phone."
        )
    )
    // Android 10+ saves to Downloads without any permission, so only ask on Android 8 and 9.
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        add(
            AppPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE, "▤", "Storage",
                "To save your finished PDF into the Downloads folder."
            )
        )
    }
}

fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

fun hasAllPermissions(context: Context): Boolean =
    requiredPermissions().all { isGranted(context, it.permission) }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Shown after the splash whenever a required permission is missing.
 * onAllGranted: every permission is allowed, go to Home.
 * onSkip: user chose "Not now", go to Home for this session only (screen returns next launch).
 */
@Composable
fun PermissionScreen(onAllGranted: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val permissions = remember { requiredPermissions() }

    var wasDenied by remember { mutableStateOf(false) }
    // true when Android will no longer show the permission dialog; only Settings can fix it
    var blocked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasAllPermissions(context)) {
            onAllGranted()
        } else {
            wasDenied = true
            val activity = context.findActivity()
            blocked = activity != null && permissions.any {
                !isGranted(context, it.permission) &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(activity, it.permission)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(R.drawable.ic_app_logo), null, Modifier.size(30.dp))
            Text("ID Card Scan & Copy", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(8.dp))
        Text("Before we start", style = MaterialTheme.typography.headlineMedium)
        Text(
            "We only ask for what the app needs to work. Nothing is uploaded; everything stays on your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        permissions.forEach {
            PermissionRow(it, isGranted(context, it.permission))
        }

        // Reassurance card: things that need no permission
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                "No permission needed to pick photos from your gallery" +
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) " or to save PDFs to Downloads." else ".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(14.dp)
            )
        }

        if (wasDenied) {
            Text(
                if (blocked) "Permission is turned off. Open Settings, tap Permissions, and allow it."
                else "Permission was not allowed. You can still pick photos from your gallery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(6.dp))
        Button(
            onClick = {
                if (blocked) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                } else {
                    launcher.launch(permissions.map { it.permission }.toTypedArray())
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber)
        ) {
            Text(if (blocked) "Open Settings" else "Allow permissions", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PermissionRow(item: AppPermission, granted: Boolean) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) { Text(item.icon, color = MaterialTheme.colorScheme.surface, fontSize = 20.sp) }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (granted) {
                Text("✓", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}