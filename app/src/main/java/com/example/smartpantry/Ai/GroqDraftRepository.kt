package com.example.smartpantry.Aipackage

import com.example.smartpantry.Screen.CommunityScreen.cleanDisplay
import com.example.smartpantry.Screen.CommunityScreen.cleanInput
import com.example.smartpantry.community.*
import com.example.smartpantry.storage.remote.supabase
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Explicit AI-only endpoint. No automatic community/template fallback or automatic retries. */
internal class GroqDraftRepository {
 suspend fun create(name:String,catalog:List<JsonObject>,servings:Int=2,dietary:String="Unspecified"):RecipeDraft = suspendCancellableCoroutine {continuation->
  val owner=supabase.auth.currentUserOrNull()?.id
  val token=supabase.auth.currentSessionOrNull()?.accessToken
  if(owner==null || token==null){continuation.resumeWithException(IllegalStateException("Please sign in again."));return@suspendCancellableCoroutine}
  val requestId=UUID.randomUUID().toString()
  val connection=URL("https://sauxeqatfysygmkicpdf.supabase.co/functions/v1/recipe-draft").openConnection() as HttpURLConnection
  val worker=Thread({
   try{
    connection.requestMethod="POST";connection.connectTimeout=12000;connection.readTimeout=40000;connection.doOutput=true;connection.instanceFollowRedirects=false
    connection.setRequestProperty("Authorization","Bearer $token");connection.setRequestProperty("apikey",supabase.supabaseKey)
    connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("X-Request-ID",requestId)
    android.util.Log.i("RecipeAI","Request $requestId started")
    connection.outputStream.use{it.write(buildJsonObject{put("action","generate");put("clientVersion",2);put("name",name);put("servings",servings);put("dietary",dietary);put("requestId",requestId)}.toString().toByteArray(Charsets.UTF_8))}
    val status=connection.responseCode
    android.util.Log.i("RecipeAI","Request $requestId HTTP $status")
    val body=(if(status in 200..299)connection.inputStream else connection.errorStream)?.bufferedReader()?.use{it.readText().take(100000)}.orEmpty()
    val data=runCatching{Json.parseToJsonElement(body).jsonObject}.getOrNull()
    if(status !in 200..299){
     val fallback=when(status){401,403->"Please sign in again to generate a recipe.";404->"Recipe AI is not deployed. Check that recipe-draft exists in this Supabase project.";409->"This request is already being processed. Wait before generating again.";429->"The recipe generation limit has been reached. Try later or choose Templates.";422->"AI could not prepare a complete draft. Try another dish or create it manually.";else->"Recipe AI could not finish. Your current form is unchanged. Try again later."}
     val readable=data?.str("message").orEmpty().let{if(it.length in 1..400 && !Regex("https?://|gsk_|Bearer |PGRST|SQLSTATE|<html",RegexOption.IGNORE_CASE).containsMatchIn(it))it else fallback}
     throw IllegalStateException(readable)
    }
    requireNotNull(data){"The recipe response could not be read. Your current form is unchanged."}
    check(supabase.auth.currentUserOrNull()?.id==owner){"Please reopen the recipe page after switching accounts."}
    val resolved=mutableListOf<DraftIngredient>();val unresolved=mutableListOf<UnresolvedIngredient>()
    data.rows("ingredients").forEach{row->
     val known=catalog.firstOrNull{it.long("id")==row.long("id")}
     if(known==null){
      val ingredientName=cleanInput(row.str("name"),100,0)
      require(ingredientName.isNotBlank()){"AI returned an unavailable ingredient without a name. Try another dish."}
      unresolved+=UnresolvedIngredient(ingredientName,quantityText(row.number("quantity")),normalizedUnit(row.str("unit")))
     }else resolved+=DraftIngredient(known.long("id"),known.str("name"),quantityText(row.number("quantity")),normalizedUnit(row.str("unit")),known.str("image"))
    }
    data.rows("unresolved").forEach{row->unresolved+=UnresolvedIngredient(cleanInput(row.str("name"),100,0),quantityText(row.number("quantity")),normalizedUnit(row.str("unit")))}
    val value=RecipeDraft(title=cleanDisplay(data.str("title")).take(150),category=data.str("category","Malaysian"),description=cleanInput(data.str("description"),5000,8),vegan=data.flag("vegan"),
     cuisine=data.str("cuisine").ifBlank{data.str("category").takeIf{it in listOf("Malaysian","Malay","Chinese","Indian","Asian","Western")}.orEmpty()},dishType=data.str("dishType"),dietary=if(data.flag("vegan"))"Vegan" else data.str("dietary",dietary),
     source="AI-generated draft via Groq · $servings servings · Review before saving",ingredients=resolved,unresolved=unresolved,steps=data.words("steps").map{cleanInput(it,500,2)})
    value.problem(allowUnresolved=true)?.let{throw IllegalStateException("AI returned an incomplete draft: $it")}
    (resolved.map{it.quantity to it.unit}+unresolved.map{it.quantity to it.unit}).forEach{(text,unit)->val q=text.toDoubleOrNull();require(q!=null&&q.isFinite()&&q>0&&q<=1000000&&unit in recipeUnits && (unit !in listOf("pcs","bag","carton","bunch")||q%1==0.0)){"AI returned an invalid ingredient quantity. Try another dish."}}
    if(continuation.isActive)continuation.resume(value)
   }catch(t:Exception){if(continuation.isActive)continuation.resumeWithException(t)}finally{connection.disconnect()}
  },"SmartPantry-RecipeAI").apply{isDaemon=true}
  continuation.invokeOnCancellation{connection.disconnect();worker.interrupt();android.util.Log.i("RecipeAI","Request $requestId cancelled")}
  worker.start()
 }
}
