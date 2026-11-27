package com.example.smartpantry.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class IngredientChoice(val key:String,val name:String)
/** Shared look and interactions; callers own IDs, permissions and persistence. */
@Composable internal fun IngredientPickerField(query:String,onQuery:(String)->Unit,expanded:Boolean,onExpanded:(Boolean)->Unit,choices:List<IngredientChoice>,selectedKey:String?,enabled:Boolean=true,onSelect:(IngredientChoice)->Unit,onCustom:()->Unit,thumbnail:@Composable (IngredientChoice)->Unit){
 val c=MaterialTheme.colorScheme
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)){
  OutlinedTextField(query,{if(it.length<=50&&it.none{ch->ch=='\n'||ch=='\r'})onQuery(it)},Modifier.fillMaxWidth(),label={Text("Ingredient")},placeholder={Text("Search ingredients")},leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={IconButton(onClick={onExpanded(!expanded)},enabled=enabled){Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,if(expanded)"Hide ingredients" else "Show ingredients")}},singleLine=true,enabled=enabled,shape=RoundedCornerShape(12.dp))
  if(expanded)Surface(shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surface){Column(Modifier.fillMaxWidth().padding(8.dp)){
   val matches=choices.filter{it.name.contains(query.trim(),true)}.take(30)
   if(matches.isEmpty())Text("No matching ingredients",Modifier.padding(10.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
   else Column(Modifier.heightIn(max=180.dp).verticalScroll(rememberScrollState())){matches.forEach{choice->DropdownMenuItem(text={Text(choice.name,fontWeight=if(choice.key==selectedKey)FontWeight.Bold else FontWeight.Normal)},leadingIcon={thumbnail(choice)},trailingIcon={if(choice.key==selectedKey)Icon(Icons.Default.Check,"Selected",tint=c.primary)},onClick={onSelect(choice)},enabled=enabled)}}
   HorizontalDivider(color=c.outlineVariant)
   TextButton(onClick=onCustom,enabled=enabled,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Other · Add custom ingredient")}
  }}
 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CustomIngredientSheet(name:String,onName:(String)->Unit,unit:String,onUnit:(String)->Unit,units:List<String>,busy:Boolean,error:String?,explanation:String,onDismiss:()->Unit,onConfirm:()->Unit,extra:@Composable ()->Unit={}){
 val c=MaterialTheme.colorScheme
 ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=c.surface,shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp)){
  Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().heightIn(max=650.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){Text("Add custom ingredient",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);IconButton(onClick=onDismiss,enabled=!busy){Icon(Icons.Default.Close,"Close")}}
   OutlinedTextField(name,{if(it.length<=50&&it.none{ch->ch=='\n'||ch=='\r'})onName(it)},Modifier.fillMaxWidth(),label={Text("Ingredient name")},singleLine=true,enabled=!busy,shape=RoundedCornerShape(12.dp),supportingText={Text("${name.length}/50")})
   com.example.smartpantry.community.UnitDropdown("Unit",unit,units,!busy,onUnit)
   extra()
   Text(explanation,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
   error?.let{Text(it,color=c.error,style=MaterialTheme.typography.bodySmall)}
   Button(onClick=onConfirm,enabled=!busy&&name.trim().isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(12.dp)){Text(if(busy)"Saving…" else "Use ingredient")}
  }
 }
}

/** Identical photo actions in recipe and shopping custom ingredient forms. */
@Composable internal fun IngredientPhotoActions(image:String,busy:Boolean,onCamera:()->Unit,onPhotos:()->Unit,onRemove:()->Unit){
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)){
  Text("Photo (optional)",style=MaterialTheme.typography.labelLarge)
  if(image.isNotBlank())com.example.smartpantry.community.RecipePhoto(image,Modifier.size(64.dp),false)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
   OutlinedButton(onClick=onCamera,enabled=!busy){Icon(Icons.Default.PhotoCamera,null,Modifier.size(18.dp));Spacer(Modifier.width(5.dp));Text("Camera")}
   OutlinedButton(onClick=onPhotos,enabled=!busy){Icon(Icons.Default.PhotoLibrary,null,Modifier.size(18.dp));Spacer(Modifier.width(5.dp));Text("Photos")}
  }
  if(image.isNotBlank())TextButton(onClick=onRemove,enabled=!busy){Icon(Icons.Default.DeleteOutline,null,Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text("Remove photo")}
 }
}
