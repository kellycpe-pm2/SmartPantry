package com.example.smartpantry.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartpantry.remote.CommunityRepository
import com.example.smartpantry.core.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

internal class DashboardViewModel(app:Application):AndroidViewModel(app) {
 private val repo=CommunityRepository()
 private val _data=MutableStateFlow<JsonObject>(buildJsonObject{});val data=_data.asStateFlow()
 private val _logs=MutableStateFlow<JsonObject>(buildJsonObject{});val logs=_logs.asStateFlow()
 private val _loading=MutableStateFlow(false);val loading=_loading.asStateFlow()
 private val _message=MutableStateFlow<String?>(null);val message=_message.asStateFlow()
 private val _recommendations=MutableStateFlow<List<JsonObject>>(emptyList());val recommendations=_recommendations.asStateFlow()
 private val _recommendationError=MutableStateFlow<String?>(null);val recommendationError=_recommendationError.asStateFlow()
 private var job:Job?=null
 fun refresh(start:String?=null,end:String?=null) {
  job?.cancel()
  job=viewModelScope.launch {
   _loading.value=true;_message.value=null
   try {
    val owner=repo.userId();val prefs=getApplication<Application>().getSharedPreferences("dashboard_$owner",0)
    val key=if(start==null)"home" else "logs_${start}_$end"
    val state=if(start==null)_data else _logs
    if(start==null){_recommendations.value=prefs.getString("recommendations",null)?.let{runCatching{Json.parseToJsonElement(it).jsonObject["posts"]?.jsonArray?.mapNotNull{r->r as? JsonObject}.orEmpty()}.getOrDefault(emptyList())}?:emptyList();_recommendationError.value=null}
    state.value=prefs.getString(key,null)?.let{Json.parseToJsonElement(it).jsonObject} ?: buildJsonObject{}
    try {
     val result=repo.v5(if(start==null)"dashboard" else "logs",buildJsonObject {if(start!=null){put("start",start);put("end",end)}})
     if(repo.userId()!=owner)error("Please reopen this page after switching accounts.")
     state.value=result;prefs.edit().putString(key,result.toString()).apply()
    } catch(t:Throwable) { if(t is CancellationException)throw t;_message.value=userMessage(t)+(if(state.value.isNotEmpty())" Showing the saved snapshot for this period; it may be out of date." else " Pull down to retry.") }
    if(start==null)try{
     val recommendations=repo.recommendations()
     if(repo.userId()==owner){_recommendations.value=recommendations["posts"]?.jsonArray?.mapNotNull{it as? JsonObject}.orEmpty();prefs.edit().putString("recommendations",recommendations.toString()).apply()}
    }catch(t:Throwable){if(t is CancellationException)throw t;_recommendationError.value=userMessage(t)+" Recommendations could not be refreshed."}
   } catch(t:Throwable){if(t is CancellationException)throw t;_message.value=userMessage(t)}
   finally {if(kotlin.coroutines.coroutineContext[Job]?.isActive==true)_loading.value=false}
  }
 }
}
