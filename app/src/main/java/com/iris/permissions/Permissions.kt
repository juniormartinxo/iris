package com.iris.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

data class PermissionsState(
    val cameraGranted: Boolean,
    val micGranted: Boolean,
)

@Composable
fun rememberPermissionsState(): PermissionsState {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(checkGranted(context, Manifest.permission.CAMERA))
    }
    var micGranted by remember {
        mutableStateOf(checkGranted(context, Manifest.permission.RECORD_AUDIO))
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] ?: cameraGranted
        micGranted = result[Manifest.permission.RECORD_AUDIO] ?: micGranted
    }

    LaunchedEffect(Unit) {
        val toRequest = buildList {
            if (!cameraGranted) add(Manifest.permission.CAMERA)
            if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
        }
        if (toRequest.isNotEmpty()) launcher.launch(toRequest.toTypedArray())
    }

    return PermissionsState(cameraGranted, micGranted)
}

private fun checkGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
