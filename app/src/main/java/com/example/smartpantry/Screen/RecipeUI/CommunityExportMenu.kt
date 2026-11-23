package com.example.smartpantry.Screen.RecipeUI

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.smartpantry.community.*
import com.example.smartpantry.model.Recipe
import com.example.smartpantry.viewmodel.RecipeDetailIngredient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID

/** Reuses the teammate's shared poster/PDF builders, without replacing their detail screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityExportMenu(data:JsonObject,onDismiss:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var pending by rememberSaveable{mutableStateOf<ByteArray?>(null)}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    val post=data["post"]!!.jsonObject
    val recipe=Recipe(recipeId=post.long("recipeId"),recipeName=post.str("title"),recipeCategory=post.str("category"),recipeImage=post.str("image"),
        description=buildString {
            appendLine("by ${post.str("author")}")
            if(post.str("caption").isNotBlank())appendLine(cleanDisplay(post.str("caption")))
            if(post.str("description").isNotBlank())appendLine(cleanDisplay(post.str("description")))
            appendLine("COOKING STEPS")
            data.words("steps").forEachIndexed{index,step->appendLine("${index+1}. ${cleanDisplay(step)}")}
        })
    val ingredients=data.rows("ingredients").map { row->RecipeDetailIngredient(row.long("id"),row.str("name"),null,null,row.str("image"),row.number("quantity"),row.str("unit"),row.number("stock"),row.str("unit"),null) }
    val plain=buildString {appendLine(recipe.recipeName);appendLine(recipe.description);appendLine("INGREDIENTS");ingredients.forEach {appendLine("• ${it.ingredientName}: ${quantityText(it.requiredQuantity)} ${it.requiredUnit}")};appendLine("Created with SmartPantry")}
    val fileName=recipe.recipeName.replace(Regex("[^\\p{L}\\p{N}_-]"),"_").take(80).ifBlank{"recipe"}
    fun work(block:suspend()->Unit){if(busy)return;busy=true;scope.launch{try{block()}catch(cancelled:CancellationException){throw cancelled}catch(problem:Exception){error=problem.message?:"Export failed. Retry."}finally{busy=false}}}
    fun writeResult(uri:android.net.Uri?){val bytes=pending;if(uri!=null&&bytes!=null)work{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(bytes)}?:throw java.io.IOException("Cannot write to this location.")};pending=null}}
    val savePdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){writeResult(it)}
    val saveTxt=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){writeResult(it)}
    fun shareFile(file:File,mime:String){
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type=mime;putExtra(Intent.EXTRA_STREAM,uri);putExtra(Intent.EXTRA_SUBJECT,recipe.recipeName);clipData=ClipData.newRawUri("Recipe",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share recipe"))
    }
    ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()}) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Share & export",style=MaterialTheme.typography.titleLarge)
            Text(recipe.recipeName,style=MaterialTheme.typography.bodyMedium)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(enabled=!busy,onClick={work{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,plain)},"Share recipe"))}},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Share,null);Text("Share recipe text")}
            OutlinedButton(enabled=!busy,onClick={work{
                val bitmap=withContext(Dispatchers.Default){buildRecipeShareImage(recipe,ingredients)}
                val file=File(context.cacheDir,"recipe_${UUID.randomUUID()}.png")
                try{withContext(Dispatchers.IO){file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}}finally{bitmap.recycle()}
                shareFile(file,"image/png")
            }},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Image,null);Text("Share recipe image")}
            OutlinedButton(enabled=!busy,onClick={pending=plain.toByteArray(Charsets.UTF_8);saveTxt.launch("$fileName.txt")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Description,null);Text("Save TXT")}
            OutlinedButton(enabled=!busy,onClick={work{pending=withContext(Dispatchers.Default){buildRecipePdf(recipe,ingredients)};savePdf.launch("$fileName.pdf")}},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.PictureAsPdf,null);Text("Save PDF")}
            OutlinedButton(enabled=!busy,onClick={work{val bytes=withContext(Dispatchers.Default){buildRecipePdf(recipe,ingredients)};val file=File(context.cacheDir,"recipe_${UUID.randomUUID()}.pdf");withContext(Dispatchers.IO){file.writeBytes(bytes)};shareFile(file,"application/pdf")}},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.PictureAsPdf,null);Text("Share PDF")}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
}
