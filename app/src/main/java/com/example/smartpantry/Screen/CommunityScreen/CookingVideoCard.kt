package com.example.smartpantry.community

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable internal fun CookingVideoCard(url:String){
 if(url.isBlank())return
 val context=LocalContext.current;val c=MaterialTheme.colorScheme
 var error by remember{mutableStateOf(false)}
 Surface(shape=RoundedCornerShape(16.dp),color=c.surfaceContainerLow){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
  Icon(Icons.Default.VideoLibrary,null,tint=c.primary)
  Column(Modifier.weight(1f)){Text("Cooking video",fontWeight=FontWeight.Bold);Text("Opens the author's video link",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}
  IconButton(onClick={if(validRecipeVideo(url))try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:Exception){error=true}else error=true}){Icon(Icons.Default.PlayCircle,"Open cooking video",tint=c.primary)}
 }}
 if(error)AlertDialog(onDismissRequest={error=false},title={Text("Cannot open video")},text={Text("This video link is invalid or no app can open it. Ask the recipe author to check the link.")},confirmButton={TextButton(onClick={error=false}){Text("OK")}})
}
