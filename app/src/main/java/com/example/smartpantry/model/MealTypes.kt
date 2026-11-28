package com.example.smartpantry.community

import kotlinx.serialization.json.JsonObject

internal val recipeMealTypes=listOf("Breakfast","Lunch","Dinner","Snack / Tea time")
internal fun validMealTypes(values:List<String>)=values.size<=4 && values.distinct().size==values.size && values.all{it in recipeMealTypes}
internal fun currentMealType(hour:Int):String? {
 require(hour in 0..23)
 return when(hour){in 5..10->"Breakfast";in 11..14->"Lunch";in 15..17->"Snack / Tea time";in 18..21->"Dinner";else->null}
}
internal fun rescueMealHeading(slot:String?,actualMeals:List<String>):String = if(slot!=null && slot in actualMeals)when(slot){
 "Snack / Tea time"->"Tea-time Rescue Idea"
 else->"$slot Rescue Idea"
}else "Your Next Rescue Meal"
/** Urgent stock first, earliest expiry next; meal preference never conceals a more urgent recipe. */
internal fun homeRescueCandidates(rows:List<JsonObject>,slot:String?):List<JsonObject>{
 val order=compareByDescending<JsonObject>{it.flag("useSoon")}
  .thenBy{if(it.flag("useSoon"))it.str("soonDate","9999-12-31")else ""}
  .thenByDescending{slot!=null && slot in it.words("mealTypes")}
  .thenByDescending{it.long("soonCount")}
  .thenByDescending{it.long("covered").toDouble()/it.long("required").coerceAtLeast(1)}
  .thenByDescending{it.long("recipeId")}
 val sorted=rows.distinctBy{it.long("recipeId")}.sortedWith(order)
 val first=sorted.filter{it.flag("useSoon")}.take(5)+sorted.filterNot{it.flag("useSoon")}.take(5)
 val ids=first.map{it.long("recipeId")}.toSet()
 return (first+sorted.filterNot{it.long("recipeId") in ids}.take(10-first.size)).sortedWith(order)
}
