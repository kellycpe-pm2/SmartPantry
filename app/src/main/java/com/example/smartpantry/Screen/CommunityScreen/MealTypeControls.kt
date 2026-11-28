package com.example.smartpantry.community

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun MealTypeSelector(selected:List<String>,onChange:(List<String>)->Unit,enabled:Boolean=true,label:String="Meal type (optional)",hint:Boolean=true){
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)){
  Text(label,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
  FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
   recipeMealTypes.forEach{meal->FilterChip(selected=meal in selected,onClick={onChange(recipeMealTypes.filter{if(it==meal)it !in selected else it in selected})},enabled=enabled,label={Text(meal,style=MaterialTheme.typography.labelMedium)})}
  }
  if(hint)Text("Choose all meals this recipe suits. Leave blank if unsure.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
}
@Composable internal fun MealTypeSummary(meals:List<String>){
 if(meals.isNotEmpty())Text(meals.joinToString(" · "),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
}
