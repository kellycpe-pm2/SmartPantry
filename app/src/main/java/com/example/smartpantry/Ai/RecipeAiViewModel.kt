package com.example.smartpantry.Ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.Aipackage.GroqDraftRepository
import com.example.smartpantry.community.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

internal data class RecipeAiState(
 val loading:Boolean=false,val error:String?=null,val preview:RecipeDraft?=null,
 val messages:List<Pair<String,String>> = emptyList()
)

/** UI requests -> state -> repository. Never saves recipes or changes stock. */
internal class RecipeAiViewModel:ViewModel(){
 private val repository=GroqDraftRepository()
 private val _state=MutableStateFlow(RecipeAiState());val state=_state.asStateFlow()
 private var job:Job?=null
 private fun message(t:Throwable):String = when(t){
  is java.net.UnknownHostException,is java.net.ConnectException -> "Cannot reach Recipe AI. Check your internet connection and try again. Your recipe is unchanged."
  is java.net.SocketTimeoutException -> "Recipe AI took too long to respond. Your recipe is unchanged. Try again later."
  is javax.net.ssl.SSLException -> "A secure connection to Recipe AI could not be established. Check your connection and device date."
  is IllegalArgumentException,is IllegalStateException -> t.message?.takeIf{it.length in 1..400 && !Regex("https?://|gsk_|Bearer |PGRST|SQLSTATE",RegexOption.IGNORE_CASE).containsMatchIn(it)} ?: "Recipe AI could not read this request. Your recipe is unchanged."
  else -> "Recipe AI could not complete this request. Your recipe is unchanged. Try again later."
 }
 fun clearError(){_state.value=_state.value.copy(error=null)}
 fun reset(){job?.cancel();_state.value=RecipeAiState()}
 fun cancel(){job?.cancel();_state.value=_state.value.copy(loading=false,error="Request cancelled. Your recipe is unchanged. A request already sent may count towards the AI limit.")}
 fun generate(name:String,amount:String,dietary:String,catalog:List<JsonObject>,mealTypes:List<String> = emptyList()){
  if(_state.value.loading)return
  val query=name.trim()
  if(query.length !in 1..150 || query.contains('\n')){_state.value=_state.value.copy(error="Enter a dish name with 1–150 characters on one line.");return}
  if(amount.length>120){_state.value=_state.value.copy(error="Keep the approximate quantity within 120 characters.");return}
  if(!validMealTypes(mealTypes)){_state.value=_state.value.copy(error="Choose supported meal types without duplicates.");return}
  if(catalog.isEmpty()){_state.value=_state.value.copy(error="The ingredient list is not loaded. Close AI, reconnect and reopen the recipe form before generating.");return}
  _state.value=_state.value.copy(loading=true,error=null)
  job=viewModelScope.launch{
   try{val preview=repository.create(query,catalog,amount.trim(),dietary,mealTypes);_state.value=_state.value.copy(preview=preview)}
   catch(t:CancellationException){throw t}catch(t:Exception){android.util.Log.e("RecipeAI","Generate failed",t);_state.value=_state.value.copy(error=message(t))}
   finally{_state.value=_state.value.copy(loading=false)}
  }
 }
 fun ask(question:String,draft:RecipeDraft){
  if(_state.value.loading)return
  val q=question.trim()
  if(q.isBlank()||q.length>500){_state.value=_state.value.copy(error="Enter a recipe question with 1–500 characters.");return}
  val history=_state.value.messages
  _state.value=_state.value.copy(loading=true,error=null)
  val context=buildJsonObject{
   put("mealTypes",JsonArray(draft.mealTypes.map{JsonPrimitive(it)}));put("title",draft.title.take(150));put("description",draft.description.take(1200))
   put("ingredients",buildJsonArray{draft.ingredients.take(30).forEach{add(buildJsonObject{put("name",it.name);put("quantity",it.quantity);put("unit",it.unit)})}})
   put("steps",buildJsonArray{draft.steps.take(12).forEach{add(it.take(500))}})
  }
  job=viewModelScope.launch{
   try{val answer=repository.chat(q,context,history);_state.value=_state.value.copy(messages=(history+listOf("user" to q,"assistant" to answer)).takeLast(12))}
   catch(t:CancellationException){throw t}catch(t:Exception){android.util.Log.e("RecipeAI","Chat failed",t);_state.value=_state.value.copy(error=message(t))}
   finally{_state.value=_state.value.copy(loading=false)}
  }
 }
}
