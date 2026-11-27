package com.example.smartpantry.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** One calendar appearance; callers keep their own valid date range and storage conversion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPantryDateDialog(title:String,initialDate:LocalDate,earliest:LocalDate?=null,latest:LocalDate?=null,onDismiss:()->Unit,onDate:(LocalDate)->Unit){
 fun date(millis:Long)=Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
 fun allowed(d:LocalDate)=(earliest==null||!d.isBefore(earliest))&&(latest==null||!d.isAfter(latest))
 val selected=initialDate.let{when{earliest!=null&&it<earliest->earliest;latest!=null&&it>latest->latest;else->it}}
 val state=rememberDatePickerState(initialSelectedDateMillis=selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),selectableDates=object:SelectableDates{
  override fun isSelectableDate(utcTimeMillis:Long)=allowed(date(utcTimeMillis))
 })
 DatePickerDialog(onDismissRequest=onDismiss,confirmButton={TextButton(onClick={state.selectedDateMillis?.let{if(allowed(date(it))){onDate(date(it));onDismiss()}}},enabled=state.selectedDateMillis?.let{allowed(date(it))}==true){Text("OK")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}}){DatePicker(state=state,title={Text(title,style=MaterialTheme.typography.labelLarge,modifier=androidx.compose.ui.Modifier.padding(start=24.dp,top=16.dp))},showModeToggle=false)}
}
