package com.example.smartpantry.hardware
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
@Composable fun rememberCameraCapture(onImagePicked:(Uri)->Unit):()->Unit {
 val context=LocalContext.current;val callback by rememberUpdatedState(onImagePicked)
 var pending by rememberSaveable { mutableStateOf<String?>(null) }
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->
  val value=pending;pending=null;if(success && value!=null)callback(Uri.parse(value))
 }
 return {val file=File(context.cacheDir,"photo_${System.currentTimeMillis()}.jpg");val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file);pending=uri.toString();launcher.launch(uri)}
}
