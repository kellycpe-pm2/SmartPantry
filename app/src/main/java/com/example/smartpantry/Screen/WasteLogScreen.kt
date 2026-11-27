package com.example.smartpantry.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartpantry.community.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.Instant

/** Temporary real-data log route until the team's full recording module is pushed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WasteLogScreen(onBack:()->Unit,initialWeek:Boolean=false) {
 val vm:DashboardViewModel=viewModel();val data by vm.logs.collectAsState();val loading by vm.loading.collectAsState();val message by vm.message.collectAsState()
 val today=LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur"))
 var period by rememberSaveable {mutableStateOf(if(initialWeek)"This week" else "7 days")}
 var start by rememberSaveable {mutableStateOf((if(initialWeek)today.with(java.time.DayOfWeek.MONDAY) else today.minusDays(6)).toString())}
 var end by rememberSaveable {mutableStateOf(today.toString())};var choosing by remember {mutableStateOf("")}
 var type by rememberSaveable {mutableStateOf("All")}
 val valid=LocalDate.parse(start)<=LocalDate.parse(end) && LocalDate.parse(end)<=today
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle,start,end) {val observer=LifecycleEventObserver{_,e->if(e==Lifecycle.Event.ON_RESUME && valid)vm.refresh(start,end)};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
 LaunchedEffect(start,end){if(valid)vm.refresh(start,end)}
 val c=MaterialTheme.colorScheme
 Column(Modifier.fillMaxSize()) {
  SmartPantrySectionHeader("Usage & waste log",true,onBack)
  PullToRefreshBox(isRefreshing=loading,onRefresh={if(valid)vm.refresh(start,end)},modifier=Modifier.fillMaxSize()) {
   LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,8.dp,18.dp,124.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
    item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)) {listOf("This week","7 days","30 days","Custom").forEach {p->FilterChip(selected=period==p,onClick={period=p;if(p!="Custom"){end=today.toString();start=when(p){"This week"->today.with(java.time.DayOfWeek.MONDAY);"30 days"->today.minusDays(29);else->today.minusDays(6)}.toString()}},label={Text(p,style=MaterialTheme.typography.labelMedium)})} } }
    item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={period="Custom";choosing="start"},modifier=Modifier.weight(1f)){Icon(Icons.Default.DateRange,null,Modifier.size(18.dp));Text(LocalDate.parse(start).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy",java.util.Locale.ENGLISH)))};OutlinedButton(onClick={period="Custom";choosing="end"},modifier=Modifier.weight(1f)){Icon(Icons.Default.DateRange,null,Modifier.size(18.dp));Text(LocalDate.parse(end).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy",java.util.Locale.ENGLISH)))}} }
    if(!valid)item{Text("Choose a start date on or before the end date. Future dates are not allowed.",color=c.error)}
    message?.let{item{Text(it,color=c.error,style=MaterialTheme.typography.bodySmall)}}
    if(valid){
     item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){listOf("Consumed","Wasted").forEach {label->Surface(Modifier.weight(1f),shape=RoundedCornerShape(16.dp),color=if(label=="Wasted")c.surfaceContainerLow else c.primaryContainer){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(label,fontWeight=FontWeight.Bold);Text(if(data.isEmpty())"—" else data.rows("amounts").filter{it.str("type")==label}.joinToString(" · "){displayAmount(it.number("quantity"),it.str("unit"))}.ifBlank{"No records"},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text(if(data.isEmpty())"Not loaded" else "${data.long(if(label=="Wasted")"wastedCount" else "consumedCount")} entries",style=MaterialTheme.typography.bodySmall)}}} } }
     item {Text("Recent entries",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("All","Consumed","Wasted").forEach{label->FilterChip(selected=type==label,onClick={type=label},label={Text(label)})}}}
     val entries=data.rows("entries").filter{type=="All" || it.str("type")==type}
     if(entries.isEmpty() && !loading)item{Text(if(data.isEmpty())"Records could not be loaded. Pull down to retry." else "No ${if(type=="All")"usage or waste" else type.lowercase()} records in this period.",color=c.onSurfaceVariant)}
     if(data.flag("limited"))item{Text("Showing the latest 300 entries. Summary cards include all records in this period. Choose a shorter period to find older entries.",style=MaterialTheme.typography.bodySmall)}
     items(entries,key={it.str("id")}){row->Surface(shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surface){Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Row{Text(row.str("name"),Modifier.weight(1f),fontWeight=FontWeight.Bold);Text(displayAmount(row.number("quantity"),row.str("unit")),fontWeight=FontWeight.SemiBold)};Text("${row.str("type")} · ${row.str("date")}",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant);if(row.str("note").isNotBlank())Text(row.str("note"),style=MaterialTheme.typography.bodySmall)}}}
    }
   }
  }
 }
 if(choosing.isNotBlank()) {
  val field=choosing;val selected=LocalDate.parse(if(field=="start")start else end)
  com.example.smartpantry.ui.theme.SmartPantryDateDialog(if(field=="start")"Start date" else "End date",selected,earliest=if(field=="end")LocalDate.parse(start) else null,latest=if(field=="start")LocalDate.parse(end).coerceAtMost(today) else today,onDismiss={choosing=""},onDate={d->if(field=="start")start=d.toString() else end=d.toString()})
 }
}
