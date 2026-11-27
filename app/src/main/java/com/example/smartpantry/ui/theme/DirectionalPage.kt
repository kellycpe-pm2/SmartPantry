package com.example.smartpantry.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal const val PAGE_MOTION_MS=220
@Composable internal fun DirectionalPage(targetState:String,modifier:Modifier=Modifier,content:@Composable (String)->Unit){
 fun depth(page:String)=when(page){"home","LIST"->0;"detail","personal_detail","REVIEW","PANTRY"->1;else->2}
 AnimatedContent(targetState=targetState,modifier=modifier,transitionSpec={
  val back=depth(targetState)<depth(initialState)
  (slideInHorizontally(tween(PAGE_MOTION_MS)){if(back)-it else it}+fadeIn(tween(PAGE_MOTION_MS))) togetherWith
   (slideOutHorizontally(tween(PAGE_MOTION_MS)){if(back)it else -it}+fadeOut(tween(PAGE_MOTION_MS)))
 },label="page-slide"){page->content(page)}
}
