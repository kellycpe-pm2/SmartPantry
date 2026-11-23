package com.example.smartpantry.community

/** Preserve paragraphs, but never let repeated Enter create an unbounded blank block. */
internal fun cleanInput(value:String,limit:Int):String = value.replace("\r\n","\n").replace('\r','\n')
    .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u200B\\uFEFF]"),"")
    .replace(Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)+"),"\n\n").take(limit)
internal fun cleanDisplay(value:String):String = cleanInput(value,10000).lines().joinToString("\n"){it.trimEnd()}.trim()
