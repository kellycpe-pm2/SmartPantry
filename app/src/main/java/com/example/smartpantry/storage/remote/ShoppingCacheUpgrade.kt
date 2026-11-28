package com.example.smartpantry.storage.remote

import kotlinx.serialization.json.*

/** Converts only shopping cache JSON. Unresolved legacy IDs survive offline; never guess by name. */
internal fun upgradeShoppingCache(value:JsonElement,mapping:Map<Long,Long>,names:Map<Long,JsonObject>):JsonElement = when(value){
 is JsonArray -> JsonArray(value.map{upgradeShoppingCache(it,mapping,names)})
 is JsonObject -> {
  val old=(value["legacy_custom_id"] ?: value["custom_ingredient_id"])?.jsonPrimitive?.longOrNull
  val definition=names[old]
  val target=mapping[old]
  buildJsonObject {
   value.forEach{(k,v)->if(k!="custom_ingredient_id" && k!="legacy_custom_id")put(k,upgradeShoppingCache(v,mapping,names))}
   if(old!=null){
    if(target!=null)put("ingredient_id",target)else put("legacy_custom_id",old)
    if(value["ingredient_name"]?.jsonPrimitive?.contentOrNull.isNullOrBlank())definition?.get("name")?.let{put("ingredient_name",it)}
    // An explicit empty photo means the user removed it. Only fill missing/null photos.
    if(value["photo_path"]==null || value["photo_path"]==JsonNull)definition?.get("image_path")?.let{put("photo_path",it)}
   }
  }
 }
 else -> value
}
