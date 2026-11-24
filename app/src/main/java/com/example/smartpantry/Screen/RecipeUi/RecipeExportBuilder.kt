package com.example.smartpantry.Screen.RecipeUI

import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.smartpantry.Screen.CommunityScreen.cleanDisplay
import com.example.smartpantry.model.Recipe
import com.example.smartpantry.viewmodel.RecipeDetailIngredient
import java.io.ByteArrayOutputStream

/** Creates a shareable recipe poster locally; no network/image-generation service is required. */
internal fun buildRecipeShareImage(
    recipe: Recipe,
    ingredients: List<RecipeDetailIngredient>
): Bitmap {
    val width = 1080
    val height = 1440
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(241, 250, 248) }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)

    val dark = android.graphics.Color.rgb(30, 65, 61)
    val muted = android.graphics.Color.rgb(91, 116, 111)
    val green = android.graphics.Color.rgb(61, 139, 111)
    val white = android.graphics.Color.WHITE
    val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white }
    val header = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(211, 239, 231) }
    canvas.drawRoundRect(RectF(42f, 42f, 1038f, 1398f), 42f, 42f, panel)
    canvas.drawRoundRect(RectF(42f, 42f, 1038f, 380f), 42f, 42f, header)

    fun textPaint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val brandPaint = textPaint(34f, green, true)
    val titlePaint = textPaint(58f, dark, true)
    val subtitlePaint = textPaint(28f, muted)
    val headingPaint = textPaint(34f, dark, true)
    val itemPaint = textPaint(27f, dark)
    val statusPaint = textPaint(23f, green, true)
    val missingPaint = textPaint(23f, android.graphics.Color.rgb(183, 73, 65), true)

    canvas.drawText("SMARTPANTRY  •  COOK WITH WHAT YOU HAVE", 88f, 112f, brandPaint)
    val titleLines = wrapCanvasText(recipe.recipeName, titlePaint, 900f).take(2)
    var y = 205f
    titleLines.forEach { canvas.drawText(it, 88f, y, titlePaint); y += 68f }
    val category = buildString {
        append(recipe.recipeCategory?.takeIf { it.isNotBlank() } ?: "Recipe")
        if (recipe.isVegan) append("   •   Vegan 🌱")
    }
    canvas.drawText(category.take(58), 90f, 330f, subtitlePaint)

    var currentY = 450f
    canvas.drawText("INGREDIENT CHECKLIST", 88f, currentY, headingPaint)
    currentY += 54f
    val shown = ingredients.take(7)
    shown.forEach { ingredient ->
        val isAvailable = ingredient.isAvailable
        val line = "${ingredient.ingredientName}: ${ingredient.requiredQuantity} ${ingredient.requiredUnit}".trim()
        val linePaint = itemPaint
        wrapCanvasText(line, linePaint, 720f).take(1).forEach {
            canvas.drawText(it, 92f, currentY, linePaint)
        }
        val status = if (isAvailable) "AVAILABLE" else "MISSING"
        canvas.drawText(status, 790f, currentY, if (isAvailable) statusPaint else missingPaint)
        currentY += 48f
        val stock = "Pantry: ${ingredient.availableQuantity} ${ingredient.availableUnit}".trim()
        canvas.drawText(stock.take(70), 112f, currentY, subtitlePaint)
        currentY += 45f
        if (currentY > 1190f) return@forEach
    }
    if (ingredients.size > shown.size) {
        canvas.drawText("+ ${ingredients.size - shown.size} more ingredients", 92f, minOf(currentY + 8f, 1260f), subtitlePaint)
    }
    val available = ingredients.count { it.isAvailable }
    val summary = "$available / ${ingredients.size} ingredients available"
    canvas.drawRoundRect(RectF(82f, 1270f, 998f, 1345f), 24f, 24f, header)
    canvas.drawText(summary, 112f, 1318f, headingPaint)
    canvas.drawText("Made with SmartPantry", 88f, 1375f, textPaint(22f, muted))
    return bitmap
}

private fun wrapCanvasText(text:String,paint:Paint,maxWidth:Float):List<String> {
    val lines=mutableListOf<String>()
    for(paragraph in cleanDisplay(text).split('\n')) {
        if(paragraph.isEmpty()){lines+="";continue}
        var line=""
        for(character in paragraph) {
            if(line.isNotEmpty()&&paint.measureText(line+character)>maxWidth){lines+=line;line=""}
            line+=character
        }
        if(line.isNotEmpty())lines+=line
    }
    return lines.ifEmpty{listOf("")}
}

internal fun buildRecipePdf(
    recipe: Recipe,
    ingredients: List<RecipeDetailIngredient>
): ByteArray {
    val pdf = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 42f
    val titlePaint = Paint().apply { textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
    val headingPaint = Paint().apply { textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
    val bodyPaint = Paint().apply { textSize = 11f; isAntiAlias = true }
    val mutedPaint = Paint().apply { textSize = 9f; color = android.graphics.Color.DKGRAY; isAntiAlias = true }
    val lines = mutableListOf<Pair<String, Paint>>()
    lines += recipe.recipeName to titlePaint
    lines += (recipe.recipeCategory ?: "Recipe") to mutedPaint
    if (recipe.isVegan) lines += "Vegan" to mutedPaint
    lines += "" to bodyPaint
    recipe.description?.takeIf { it.isNotBlank() }?.let {
        lines += "INSTRUCTIONS" to headingPaint
        lines += it to bodyPaint
    }
    lines += "" to bodyPaint
    lines += "INGREDIENTS" to headingPaint
    ingredients.forEach {
        val status = if (it.isAvailable) "Available" else "Missing / insufficient"
        lines += "• ${it.ingredientName} — ${it.requiredQuantity} ${it.requiredUnit} ($status)" to bodyPaint
        lines += "  Pantry: ${it.availableQuantity} ${it.availableUnit}" to mutedPaint
        it.description?.takeIf(String::isNotBlank)?.let { desc -> lines += "  $desc" to mutedPaint }
    }
    lines += "" to bodyPaint
    lines += "Created with SmartPantry" to mutedPaint

    var pageNumber = 1
    var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas = page.canvas
    var y = 58f
    canvas.drawText("SmartPantry", margin, 30f, mutedPaint)
    for ((rawLine, paint) in lines) {
        for(line in wrapCanvasText(rawLine,paint,pageWidth-margin*2)) {
            if (y > pageHeight - 55f) {
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 58f
                canvas.drawText("SmartPantry", margin, 30f, mutedPaint)
            }
            if (line.isNotEmpty()) canvas.drawText(line, margin, y, paint)
            y += if (paint === titlePaint) 34f else if (paint === headingPaint) 24f else 18f
        }
    }
    pdf.finishPage(page)
    val output = ByteArrayOutputStream()
    pdf.writeTo(output)
    pdf.close()
    return output.toByteArray()
}