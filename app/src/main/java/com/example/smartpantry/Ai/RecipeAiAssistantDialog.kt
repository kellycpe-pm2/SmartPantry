package com.example.smartpantry.Ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartpantry.community.*
import kotlinx.serialization.json.JsonObject

/** Mirrors the team's IngredientAiAssistantDialog without modifying that component. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecipeAiAssistantDialog(draft:RecipeDraft,catalog:List<JsonObject>,onDismiss:()->Unit,onApply:(RecipeDraft)->Unit){
 val vm:RecipeAiViewModel=viewModel(key="recipe-ai-${draft.recipeId}")
 val state by vm.state.collectAsState()
 val c=MaterialTheme.colorScheme
 var tab by rememberSaveable{mutableStateOf("fill")}
 var name by rememberSaveable{mutableStateOf(draft.title)}
 var amount by rememberSaveable{mutableStateOf("")}
 var diet by rememberSaveable{mutableStateOf(draft.dietary.ifBlank{"Unspecified"})}
 var question by rememberSaveable{mutableStateOf("")}
 var confirm by remember{mutableStateOf(false)}
 DisposableEffect(Unit){vm.reset();onDispose{vm.reset()}}
 val close={vm.reset();onDismiss()}
 ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),containerColor=c.surface){
  Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=16.dp).navigationBarsPadding().imePadding(),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
    Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
     Surface(shape=RoundedCornerShape(14.dp),modifier=Modifier.size(40.dp)){Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF235F62),Color(0xFF83D1D1)))),contentAlignment=Alignment.Center){Icon(Icons.Default.AutoAwesome,null,tint=Color.White,modifier=Modifier.size(20.dp))}}
     Column{Text("SmartPantry AI · ${name.ifBlank{"Recipe"}}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text("Recipe drafting & cooking assistant",style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)}
    }
    IconButton(onClick=close){Icon(Icons.Default.Close,"Close Recipe AI")}
   }
   Surface(shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surfaceContainerLow){Row(Modifier.fillMaxWidth().padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
    listOf("fill" to "Auto-fill","chat" to "Ask AI").forEach{(key,label)->Surface(Modifier.weight(1f).clickable(enabled=!state.loading){tab=key;vm.clearError()},shape=RoundedCornerShape(12.dp),color=if(tab==key)Color(0xFF83D1D1) else c.surfaceContainerLow){Box(Modifier.heightIn(min=44.dp).padding(8.dp),contentAlignment=Alignment.Center){Text(label,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelLarge,color=if(tab==key)c.onSurface else c.onSurfaceVariant)}}}
   }}
   state.error?.let{error->Surface(shape=RoundedCornerShape(14.dp),color=c.errorContainer,modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Default.ErrorOutline,null,tint=c.onErrorContainer,modifier=Modifier.size(18.dp));Text(error,color=c.onErrorContainer,style=MaterialTheme.typography.bodySmall)}}}
   Column(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
    if(tab=="fill"){
     OutlinedTextField(name,{name=it.replace('\n',' ').replace('\r',' ').take(150);vm.clearError()},Modifier.fillMaxWidth(),label={Text("Dish name")},placeholder={Text("e.g. Nasi Goreng")},singleLine=true,enabled=!state.loading)
     OutlinedTextField(amount,{amount=it.replace('\n',' ').replace('\r',' ').take(120);vm.clearError()},Modifier.fillMaxWidth(),label={Text("Approximate quantity (optional)")},placeholder={Text("e.g. 500 g cooked rice or 1 plate")},supportingText={Text("Leave blank for AI-suggested quantities. Review them before saving.")},singleLine=true,enabled=!state.loading)
     Text("Dietary preference",style=MaterialTheme.typography.labelLarge)
     Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
      listOf("Unspecified" to "No preference","Vegetarian" to "Vegetarian","Vegan" to "Vegan","Halal" to "Halal").forEach{(value,label)->FilterChip(selected=diet==value,onClick={diet=value},enabled=!state.loading,label={Text(label)})}
     }
     if(state.preview==null && !state.loading)Surface(shape=RoundedCornerShape(18.dp),color=c.surfaceVariant.copy(alpha=0.45f),border=BorderStroke(1.dp,c.outlineVariant)){
      Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Ready to auto-fill",fontWeight=FontWeight.Bold);Text("Generate a recipe, check the preview, then apply it to your form. Nothing is saved or published automatically.",style=MaterialTheme.typography.bodySmall)}
     }
     state.preview?.let{preview->
      Surface(shape=RoundedCornerShape(16.dp),color=c.surfaceContainerLow,modifier=Modifier.fillMaxWidth()){
       Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Generated preview",style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold,color=c.primary)
        Text(preview.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        Text(preview.description,style=MaterialTheme.typography.bodySmall)
        Text("Ingredients",fontWeight=FontWeight.Bold)
        preview.ingredients.forEach{Text("${it.name} · ${it.quantity} ${it.unit}",style=MaterialTheme.typography.bodySmall)}
        preview.unresolved.forEach{Text("${it.name} · ${it.quantity} ${it.unit} · Needs matching",style=MaterialTheme.typography.bodySmall)}
        if(preview.unresolved.isNotEmpty())Text("After applying, match these ingredients to your ingredient list before saving.",style=MaterialTheme.typography.bodySmall)
        Text("Cooking steps",fontWeight=FontWeight.Bold)
        preview.steps.forEachIndexed{i,step->Text("${i+1}. $step",style=MaterialTheme.typography.bodySmall)}
       }
      }
     }
    }else{
     Surface(shape=RoundedCornerShape(16.dp),color=c.surfaceVariant.copy(alpha=0.45f),modifier=Modifier.fillMaxWidth()){
      Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
       Text("Ask about this recipe",fontWeight=FontWeight.SemiBold)
       Text("Answers use the current form or AI preview. Pantry stock is not checked in chat. Answers do not change your recipe.",style=MaterialTheme.typography.bodySmall)
       Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf("What can replace eggs?","How can I reduce leftovers?","How can I make this vegetarian?").forEach{prompt->SuggestionChip(onClick={question=prompt},enabled=!state.loading,label={Text(prompt,style=MaterialTheme.typography.labelMedium)})}
       }
      }
     }
     state.messages.forEach{(role,text)->val user=role=="user"
      Box(Modifier.fillMaxWidth(),contentAlignment=if(user)Alignment.CenterEnd else Alignment.CenterStart){
       Surface(shape=RoundedCornerShape(topStart=18.dp,topEnd=18.dp,bottomStart=if(user)18.dp else 4.dp,bottomEnd=if(user)4.dp else 18.dp),color=if(user)c.primary else c.surfaceVariant,modifier=Modifier.fillMaxWidth(0.86f)){
        Column(Modifier.padding(12.dp)){Text(if(user)"You" else "SmartPantry AI",style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,color=if(user)c.onPrimary else c.primary);Spacer(Modifier.height(3.dp));Text(text,style=MaterialTheme.typography.bodySmall,color=if(user)c.onPrimary else c.onSurfaceVariant)}
       }
      }
     }
    }
    if(state.loading)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp);Text(if(tab=="fill")"Generating your recipe…" else "SmartPantry AI is thinking…",style=MaterialTheme.typography.bodySmall)}
   }
   if(state.loading)OutlinedButton(onClick={vm.cancel()},modifier=Modifier.fillMaxWidth()){Text("Cancel request")}
   else if(tab=="fill")Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
    OutlinedButton(onClick={vm.generate(name,amount,diet,catalog)},modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(12.dp)){
     Icon(Icons.Default.Refresh,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(if(state.preview==null)"Generate" else "Regenerate")
    }
    state.preview?.let{preview->Button(onClick={if(draft.title.isNotBlank()||draft.description.isNotBlank()||draft.image.isNotBlank()||draft.ingredients.isNotEmpty()||draft.unresolved.isNotEmpty()||draft.steps.any{it.isNotBlank()})confirm=true else onApply(preview)},modifier=Modifier.weight(1.2f).heightIn(min=48.dp),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Check,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Apply to recipe")}}
   }else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
    OutlinedTextField(question,{question=it.replace(Regex("\\n{3,}"),"\n\n").take(500);vm.clearError()},Modifier.weight(1f),placeholder={Text("Ask about ingredients or cooking…")},maxLines=3,shape=RoundedCornerShape(14.dp))
    FilledIconButton(onClick={vm.ask(question,state.preview?:draft)},enabled=question.isNotBlank(),modifier=Modifier.size(48.dp),shape=CircleShape){Icon(Icons.AutoMirrored.Filled.Send,"Send recipe question")}
   }
   Text(if(tab=="fill")"AI quantities and dietary suggestions need your review. Halal suggestions are not certification." else "Chat is temporary and uses your Recipe AI quota. Closing this sheet clears the conversation.",style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
  }
 }
 if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Replace the current form?")},text={Text("Apply this AI preview to your recipe form? Your saved recipe stays unchanged until you save. Review the photo and all details afterwards.")},confirmButton={TextButton(onClick={confirm=false;state.preview?.let(onApply)}){Text("Apply draft")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
}
