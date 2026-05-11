package com.iris.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

data class PermissionsState(
    val cameraGranted: Boolean,
    val micGranted: Boolean,
    val cameraPermanentlyDenied: Boolean,
    val micPermanentlyDenied: Boolean,
    val requestCamera: () -> Unit,
    val requestMic: () -> Unit,
    val openSettings: () -> Unit,
)

@Composable
fun rememberPermissionsState(): PermissionsState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var cameraGranted by remember {
        mutableStateOf(checkGranted(context, Manifest.permission.CAMERA))
    }
    var micGranted by remember {
        mutableStateOf(checkGranted(context, Manifest.permission.RECORD_AUDIO))
    }
    var cameraRequested by remember { mutableStateOf(false) }
    var micRequested by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result[Manifest.permission.CAMERA]?.let {
            cameraGranted = it
            cameraRequested = true
        }
        result[Manifest.permission.RECORD_AUDIO]?.let {
            micGranted = it
            micRequested = true
        }
    }

    LaunchedEffect(Unit) {
        val toRequest = buildList {
            if (!cameraGranted) add(Manifest.permission.CAMERA)
            if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
        }
        if (toRequest.isNotEmpty()) launcher.launch(toRequest.toTypedArray())
    }

    val cameraPermanentlyDenied = remember(cameraGranted, cameraRequested, activity) {
        isPermanentlyDenied(activity, Manifest.permission.CAMERA, cameraGranted, cameraRequested)
    }
    val micPermanentlyDenied = remember(micGranted, micRequested, activity) {
        isPermanentlyDenied(activity, Manifest.permission.RECORD_AUDIO, micGranted, micRequested)
    }

    return PermissionsState(
        cameraGranted = cameraGranted,
        micGranted = micGranted,
        cameraPermanentlyDenied = cameraPermanentlyDenied,
        micPermanentlyDenied = micPermanentlyDenied,
        requestCamera = { launcher.launch(arrayOf(Manifest.permission.CAMERA)) },
        requestMic = { launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
        openSettings = {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        },
    )
}

private fun isPermanentlyDenied(
    activity: Activity?,
    permission: String,
    granted: Boolean,
    requested: Boolean,
): Boolean = !granted && requested && activity != null &&
    !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

private fun checkGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
