package com.example.smartpantry.hardware

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun rememberCameraCapture(onImagePicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnPicked by rememberUpdatedState(onImagePicked)
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val pending = pendingUri
        pendingUri = null
        if (success && pending != null) currentOnPicked(Uri.parse(pending))
    }
    return {
        val file = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingUri = uri.toString()
        launcher.launch(uri)
    }
}
