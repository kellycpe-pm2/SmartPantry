package com.example.smartpantry.community

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import com.example.smartpantry.timers.RecipeTimers
import com.example.smartpantry.storage.remote.supabase
import io.github.jan.supabase.auth.auth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal fun validStepTimerSeconds(seconds:Int?)=seconds==null || seconds in 1..86400
internal fun formatStepDuration(seconds:Int):String {
 val s=seconds.coerceAtLeast(0)
 return if(s>=3600) "%02d:%02d:%02d".format(s/3600,s%3600/60,s%60) else "%02d:%02d".format(s/60,s%60)
}
@OptIn(ExperimentalMaterial3Api::class,ExperimentalFoundationApi::class)
@Composable internal fun AuthorStepsEditor(steps:List<String>,stepTimers:List<Int?>,enabled:Boolean,onChange:(List<String>,List<Int?>)->Unit){
 val c=MaterialTheme.colorScheme
 var focusNewStep by remember { mutableStateOf(false) }
 var editing by remember { mutableStateOf<Int?>(null) }
 var minutes by remember { mutableStateOf("3") };var seconds by remember { mutableStateOf("0") }
 var error by remember { mutableStateOf<String?>(null) }
 fun timers()=steps.indices.map{stepTimers.getOrNull(it)}
 fun move(i:Int,d:Int){val target=i+d;if(target !in steps.indices)return;val texts=steps.toMutableList();val times=timers().toMutableList();val text=texts.removeAt(i);val time=times.removeAt(i);texts.add(target,text);times.add(target,time);onChange(texts,times)}
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
 Text("Cooking steps",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
 steps.forEachIndexed { i,text ->
 var more by remember(i){mutableStateOf(false)}
 val bring=remember(i){BringIntoViewRequester()};val focus=remember(i){FocusRequester()}
 LaunchedEffect(steps.size,focusNewStep){if(focusNewStep && i==steps.lastIndex){delay(100);focus.requestFocus();bring.bringIntoView();focusNewStep=false}}
 Surface(modifier=Modifier.bringIntoViewRequester(bring),shape=RoundedCornerShape(14.dp),color=c.surfaceVariant.copy(alpha=.25f),border=BorderStroke(1.dp,c.outlineVariant)){
 Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(0.dp)){
 Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(6.dp)){
 Surface(modifier=Modifier.padding(top=12.dp),shape=CircleShape,color=c.primaryContainer){Text("${i+1}",Modifier.padding(horizontal=8.dp,vertical=4.dp),fontWeight=FontWeight.Bold,color=c.primary)}
 OutlinedTextField(text,{value->onChange(steps.mapIndexed{n,v->if(n==i)value.take(500)else v},timers())},Modifier.weight(1f).focusRequester(focus),enabled=enabled,placeholder={Text("Describe this cooking step")},maxLines=5,shape=RoundedCornerShape(12.dp))
 Box { IconButton(onClick={more=true},enabled=enabled,modifier=Modifier.size(40.dp)){Icon(Icons.Default.MoreVert,"Step ${i+1} options")}
 DropdownMenu(expanded=more,onDismissRequest={more=false}){
 DropdownMenuItem(text={Text("Move up")},enabled=i>0,onClick={more=false;move(i,-1)})
 DropdownMenuItem(text={Text("Move down")},enabled=i<steps.lastIndex,onClick={more=false;move(i,1)})
 DropdownMenuItem(text={Text("Remove step")},enabled=steps.size>1,onClick={more=false;onChange(steps.filterIndexed{n,_->n!=i},timers().filterIndexed{n,_->n!=i})})
 }} }
 Row(Modifier.padding(start=32.dp),verticalAlignment=Alignment.CenterVertically){
 TextButton(onClick={editing=i;val t=stepTimers.getOrNull(i)?.takeIf{it>0};minutes=(t?.div(60)?:3).toString();seconds=(t?.rem(60)?:0).toString();error=null},enabled=enabled,contentPadding=PaddingValues(4.dp),modifier=Modifier.heightIn(min=40.dp)){
 Icon(Icons.Default.Timer,null,Modifier.size(16.dp));Spacer(Modifier.width(4.dp));Text(stepTimers.getOrNull(i)?.takeIf{it>0}?.let{formatStepDuration(it)}?:"+ Add timer")}
 if(stepTimers.getOrNull(i)!=null)TextButton(onClick={onChange(steps,timers().mapIndexed{n,v->if(n==i)null else v})},enabled=enabled,contentPadding=PaddingValues(6.dp)){Text("Remove timer",color=c.error,style=MaterialTheme.typography.labelSmall)}
 }
 }}
 }
 OutlinedButton(onClick={focusNewStep=true;onChange(steps+"",timers()+listOf(null))},enabled=enabled&&steps.size<30,modifier=Modifier.fillMaxWidth().heightIn(min=44.dp),shape=RoundedCornerShape(12.dp)){Icon(Icons.Default.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Add step")}
 }
 editing?.takeIf{it in steps.indices}?.let { idx ->
 ModalBottomSheet(onDismissRequest={editing=null}) {Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 Text("Step ${idx+1} Timer",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
 Text("Optional duration · 1 second to 24 hours",style=MaterialTheme.typography.bodySmall)
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
 OutlinedTextField(minutes,{minutes=it;error=null},Modifier.weight(1f),label={Text("Minutes (0–1440)")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=error!=null)
 OutlinedTextField(seconds,{seconds=it;error=null},Modifier.weight(1f),label={Text("Seconds (0–59)")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=error!=null)
 }
 error?.let{Text(it,color=c.error,style=MaterialTheme.typography.bodySmall)}
 Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(30,60,180,300,600).forEach{t->TextButton(onClick={minutes=(t/60).toString();seconds=(t%60).toString();error=null},contentPadding=PaddingValues(6.dp)){Text(if(t<60)"30s" else "${t/60}m")}}}
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
 OutlinedButton(onClick={editing=null},modifier=Modifier.weight(1f)){Text("Cancel")}
 Button(onClick={val m=minutes.trim().toIntOrNull();val s=seconds.trim().toIntOrNull();error=when{m==null||m !in 0..1440->"Enter whole minutes from 0 to 1440.";s==null||s !in 0..59->"Enter whole seconds from 0 to 59.";m*60+s !in 1..86400->"Total duration must be 1 second to 24 hours.";else->null};if(error==null){onChange(steps,timers().mapIndexed{n,v->if(n==idx)m!!*60+s!! else v});editing=null}},modifier=Modifier.weight(1f)){Text("Confirm")}
 }
 }} }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ReaderStepsViewer(steps:List<String>,stepTimers:List<Int?>,sessionKey:String,visibleCount:Int=steps.size,onNotify:(String)->Unit={},recipeTitle:String=""){
 val c=MaterialTheme.colorScheme;val context=LocalContext.current
 val owner=supabase.auth.currentUserOrNull()?.id.orEmpty()
 var state by remember{mutableStateOf(RecipeTimers.read(context))}
 var tick by remember{mutableLongStateOf(android.os.SystemClock.elapsedRealtime())}
 var next by remember{mutableStateOf<Int?>(null)}
 var error by remember{mutableStateOf<String?>(null)}
 var needsAlarmAccess by remember{mutableStateOf(false)}
 var notificationsOn by remember{mutableStateOf(RecipeTimers.notificationsEnabled(context,owner))}
 fun refresh(){state=RecipeTimers.read(context)}
 fun act(block:()->Unit){try{block();error=null;refresh()}catch(t:Exception){error=t.message?:"Timer could not be updated. Try again.";needsAlarmAccess=!RecipeTimers.exactAllowed(context)}}
 fun start(i:Int){val t=stepTimers.getOrNull(i)?.takeIf{it in 1..86400}?:return;act{RecipeTimers.start(context,owner,sessionKey,recipeTitle,i,t)}}
 LaunchedEffect(sessionKey,owner){
  if(owner.isNotBlank())RecipeTimers.bindOwner(context,owner)
  while(true){
   tick=android.os.SystemClock.elapsedRealtime()
   val old=state
   state=RecipeTimers.read(context)
   if(state.status=="running" && state.remaining()==0){RecipeTimers.complete(context,state.token);state=RecipeTimers.read(context)}
   if(old.status!="finished" && state.status=="finished" && state.session==sessionKey)onNotify("Step ${state.step+1}: time’s up — check your food.")
   notificationsOn=RecipeTimers.notificationsEnabled(context,owner)
   delay(250)
  }
 }
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
 if(stepTimers.any{it!=null && it in 1..86400}){
  Text("Timers continue when you leave this page. Pause or reset to stop a reminder.",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
  if(!notificationsOn)Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),color=c.secondaryContainer){Column(Modifier.padding(10.dp)){
   Text("Timer notifications are off. Enable SmartPantry notifications and allow them in Android to receive sound and lock-screen alerts.",style=MaterialTheme.typography.bodySmall)
   TextButton(onClick={context.startActivity(Intent(context,com.example.smartpantry.MainActivity::class.java).setData(Uri.parse("smartpantry://notifications")).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))}){Text("Open notification settings")}
  }}
 }
 error?.let{Text(it,color=c.error,style=MaterialTheme.typography.bodySmall)}
 steps.forEachIndexed { i,text ->
 val isActive=state.owner==owner&&state.session==sessionKey&&state.step==i&&state.status!="ready"
 if(i<visibleCount||isActive){
 Surface(shape=RoundedCornerShape(16.dp),border=BorderStroke(if(isActive)2.dp else 1.dp,if(isActive)c.primary else c.outlineVariant),color=c.surface){Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
 Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Text("${i+1}",color=c.primary,fontWeight=FontWeight.Bold);Text(text,Modifier.weight(1f))}
 stepTimers.getOrNull(i)?.takeIf{it in 1..86400}?.let{authorTime->
 if(!isActive)OutlinedButton(onClick={if(state.status in listOf("running","paused"))next=i else start(i)}){Icon(Icons.Default.PlayArrow,null);Text("Start · ${formatStepDuration(authorTime)}")}
 else Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
 val left=state.remaining(tick)
 Text(when(state.status){"finished"->"Time’s up — check your food";"paused"->"Paused · ${formatStepDuration(left)}";else->formatStepDuration(left)},fontWeight=FontWeight.Bold,color=c.primary)
 if(state.status=="running")LinearProgressIndicator(progress={if(state.total>0)left.toFloat()/state.total else 0f},modifier=Modifier.fillMaxWidth())
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
 if(state.status!="finished")Button(onClick={act{if(state.status=="running")RecipeTimers.pause(context)else RecipeTimers.resume(context)}}){Text(if(state.status=="running")"Pause" else "Resume")}
 OutlinedButton(onClick={act{RecipeTimers.reset(context)}}){Text(if(state.status=="finished")"Dismiss" else "Reset")}
 if(state.status=="finished")Button(onClick={start(i)}){Text("Restart")}
 }
 }
 }
 }}
 }}
 }
 if(needsAlarmAccess)AlertDialog(onDismissRequest={needsAlarmAccess=false},title={Text("Allow timer reminders")},text={Text("Android needs Alarms & reminders access for this cooking timer. Allow SmartPantry on the next screen, return here and tap Start again.")},confirmButton={TextButton(onClick={needsAlarmAccess=false;try{context.startActivity(RecipeTimers.settingsIntent(context))}catch(_:Exception){error="Open Android Settings > Apps > Special app access > Alarms & reminders, and allow SmartPantry."}}){Text("Open settings")}},dismissButton={TextButton(onClick={needsAlarmAccess=false}){Text("Cancel")}})
 next?.let{i->ModalBottomSheet(onDismissRequest={next=null}){Column(Modifier.padding(20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 Text("Switch active step timer?",style=MaterialTheme.typography.titleLarge)
 Text("${state.title.ifBlank{"Your recipe"}} · Step ${state.step+1} is ${state.status} (${formatStepDuration(state.remaining())} left). Reset it and start Step ${i+1}?")
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={next=null}){Text("Keep current")};Button(onClick={start(i);next=null}){Text("Switch timer")}}
 }}}
}
