package com.example.smartpantry.ui.home
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable internal fun ContextAddButton(modifier:Modifier=Modifier,onRecipe:(()->Unit)?=null,onIngredient:(()->Unit)?=null,onPantry:(()->Unit)?=null) {
 var expanded by remember { mutableStateOf(false) }
 val count=listOfNotNull(onRecipe,onIngredient,onPantry).size
 Box(modifier) {
  FloatingActionButton(onClick={if(count==1)(onRecipe?:onIngredient?:onPantry)?.invoke() else expanded=!expanded},shape=CircleShape,containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary){Icon(Icons.Default.Add,if(count==1 && onRecipe!=null)"Create recipe" else "Add options")}
  DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
   onRecipe?.let { DropdownMenuItem(text={Text("Create recipe")},leadingIcon={Icon(Icons.Default.RestaurantMenu,null)},onClick={expanded=false;it()}) }
   onIngredient?.let { DropdownMenuItem(text={Text("Create ingredient")},leadingIcon={Icon(Icons.Default.Eco,null)},onClick={expanded=false;it()}) }
   onPantry?.let { DropdownMenuItem(text={Text("Add to pantry")},leadingIcon={Icon(Icons.Default.Inventory2,null)},onClick={expanded=false;it()}) }
  }
 }
}
