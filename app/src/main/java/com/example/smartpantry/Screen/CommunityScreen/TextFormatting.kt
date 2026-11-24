package com.example.smartpantry.Screen.CommunityScreen


/** One blank line per paragraph; cap line breaks as well as character count. */
internal fun cleanInput(value: String, limit: Int, maxBreaks: Int = 8): String {
    var breaks = 0
    val normalized = value.replace("\r\n", "\n").replace('\r', '\n')
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u200B\\uFEFF]"), "")
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)+"), "\n\n")
        .trimStart('\n', '\r')
    return normalized.map { if (it == '\n' && ++breaks > maxBreaks) ' ' else it }.joinToString("").take(limit)
}
internal fun cleanDisplay(value: String): String = cleanInput(value, 10000).lines().joinToString("\n") { it.trimEnd() }.trim()