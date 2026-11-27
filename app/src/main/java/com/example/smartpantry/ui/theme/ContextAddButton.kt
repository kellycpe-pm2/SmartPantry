package com.example.smartpantry.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** Non-modal popup: outside scroll gestures reach the page instead of being swallowed. */
@Composable internal fun ContextAddButton(modifier:Modifier=Modifier,onRecipe:(()->Unit)?=null,onIngredient:(()->Unit)?=null,onPantry:(()->Unit)?=null) {
 var expanded by remember { mutableStateOf(false) }
 val count=listOfNotNull(onRecipe,onIngredient,onPantry).size
 Box(modifier) {
  FloatingActionButton(onClick={if(count==1)(onRecipe?:onIngredient?:onPantry)?.invoke() else expanded=!expanded},shape=CircleShape,containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary){Icon(if(expanded)Icons.Default.Close else Icons.Default.Add,if(expanded)"Close add options" else "Add")}
  if(expanded) Popup(alignment=Alignment.BottomEnd,onDismissRequest={expanded=false},properties=PopupProperties(focusable=false,dismissOnBackPress=true,dismissOnClickOutside=true,clippingEnabled=true)) {
   Surface(Modifier.padding(bottom=64.dp).width(210.dp),shape=RoundedCornerShape(18.dp),shadowElevation=8.dp,color=MaterialTheme.colorScheme.surface) {
    Column {
     onRecipe?.let { DropdownMenuItem(text={Text("Create recipe")},leadingIcon={Icon(Icons.Default.RestaurantMenu,null)},onClick={expanded=false;it()}) }
     onIngredient?.let { DropdownMenuItem(text={Text("Create ingredient")},leadingIcon={Icon(Icons.Default.Eco,null)},onClick={expanded=false;it()}) }
     onPantry?.let { DropdownMenuItem(text={Text("Add to pantry")},leadingIcon={Icon(Icons.Default.Inventory2,null)},onClick={expanded=false;it()}) }
    }
   }
  }
 }
}
