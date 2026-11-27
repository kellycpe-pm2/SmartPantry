package com.example.smartpantry.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject

internal fun pantryMatchLabel(row:JsonObject):String = "${if(row.long("required")>0)(100*row.long("covered")/row.long("required")).coerceIn(0,100) else 0}% pantry match"
@Composable internal fun PantryImageCard(row:JsonObject,modifier:Modifier=Modifier,onClick:()->Unit){
 val c=MaterialTheme.colorScheme
 Surface(modifier.clickable(onClick=onClick),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,c.outlineVariant),color=c.surface){Column{
  Box{
   RecipePhoto(row.str("image"),Modifier.fillMaxWidth().height(118.dp))
   Surface(Modifier.align(Alignment.TopStart).padding(8.dp),shape=RoundedCornerShape(8.dp),color=Color.White.copy(alpha=.95f),shadowElevation=1.dp){
    Text(if(row.flag("useSoon"))"Use up ${row.long("soonCount").coerceAtLeast(1)} expiring ingredient${if(row.long("soonCount")==1L)"" else "s"}" else pantryMatchLabel(row),Modifier.padding(7.dp,5.dp),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,color=Color(0xFF235F62),maxLines=2,overflow=TextOverflow.Ellipsis)
   }
  }
  Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
   Text(row.str("title"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
   Text(row.str("cuisine").ifBlank{if(row.long("missing")==0L)"Ready with your pantry" else "Missing ${row.long("missing")} ingredient(s)"},style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant,maxLines=1)
  }
 }}
}
