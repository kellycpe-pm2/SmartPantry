package com.example.smartpantry.Screen.RecipeUI

import android.content.Intent
import android.graphics.Paint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.smartpantry.model.Recipe
import com.example.smartpantry.viewmodel.RecipeDetailIngredient
import com.example.smartpantry.viewmodel.recipe_vm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: Long,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    recipeVm: recipe_vm
) {
    val context = LocalContext.current
    val recipes by recipeVm.recipes.collectAsState()
    val recipe = recipes.firstOrNull { it.recipeId == recipeId }
    var ingredients by remember(recipeId) { mutableStateOf<List<RecipeDetailIngredient>>(emptyList()) }
    var loading by remember(recipeId) { mutableStateOf(true) }
    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var exportMime by remember { mutableStateOf("application/pdf") }
    var exportName by remember { mutableStateOf("recipe.pdf") }
    var selectedForUse by remember { mutableStateOf<RecipeDetailIngredient?>(null) }
    var useAmount by remember { mutableStateOf("") }
    var showUseRecipeConfirm by remember { mutableStateOf(false) }
    var usingStock by remember { mutableStateOf(false) }
    var useMessage by remember { mutableStateOf<String?>(null) }
    var useMessageIsError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(recipeId) {
        loading = true
        ingredients = recipeVm.getRecipeDetailIngredients(recipeId)
        loading = false
    }

    val pdfSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val bytes = exportBytes
        if (uri != null && bytes != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        }
    }
    val textSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        val bytes = exportBytes
        if (uri != null && bytes != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        }
    }

    fun recipeText(r: Recipe, list: List<RecipeDetailIngredient>): String = buildString {
        appendLine(r.recipeName)
        appendLine(r.recipeCategory ?: "Recipe")
        if (r.isVegan) appendLine("Vegan")
        appendLine()
        r.description?.takeIf { it.isNotBlank() }?.let {
            appendLine("INSTRUCTIONS")
            appendLine(it)
            appendLine()
        }
        appendLine("INGREDIENTS")
        list.forEach {
            val status = if (it.isAvailable) "Available" else "Missing"
            appendLine("- ${it.ingredientName}: ${it.requiredQuantity} ${it.requiredUnit} ($status)")
            appendLine("  Pantry: ${it.availableQuantity} ${it.availableUnit}")
            it.description?.takeIf(String::isNotBlank)?.let { detail -> appendLine("  $detail") }
        }
        appendLine()
        appendLine("Created with SmartPantry")
    }

    fun shareText(r: Recipe, list: List<RecipeDetailIngredient>) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "SmartPantry recipe: ${r.recipeName}")
            putExtra(Intent.EXTRA_TEXT, recipeText(r, list))
        }
        context.startActivity(Intent.createChooser(send, "Share recipe"))
    }

    fun shareRecipeImage(r: Recipe, list: List<RecipeDetailIngredient>) {
        scope.launch {
            val bitmap = withContext(Dispatchers.Default) { buildRecipeShareImage(r, list) }
            val file = File(context.cacheDir, "smartpantry_recipe_${r.recipeId ?: "share"}.png")
            withContext(Dispatchers.IO) {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_SUBJECT, "SmartPantry recipe: ${r.recipeName}")
                putExtra(Intent.EXTRA_TEXT, "${r.recipeName}\nShared with SmartPantry")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share recipe image"))
        }
    }

    fun sharePdf(r: Recipe, list: List<RecipeDetailIngredient>) {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { buildRecipePdf(r, list) }
            val file = File(context.cacheDir, "shared_recipe_${r.recipeId ?: "recipe"}.pdf")
            withContext(Dispatchers.IO) { file.writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, "SmartPantry recipe: ${r.recipeName}")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share recipe PDF"))
        }
    }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Recipe Details", fontWeight = FontWeight.Bold, color = scheme.primary)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = scheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit recipe", tint = scheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.background)
            )
        }
    ) { padding ->
        if (recipe == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val availableCount = ingredients.count { it.isAvailable }
            val percent = if (ingredients.isEmpty()) 0 else (availableCount * 100 / ingredients.size)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    val imageModel: Any? = recipe.localRecipeImage?.takeIf { File(it).exists() }?.let(::File)
                        ?: recipe.recipeImage
                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = recipe.recipeName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                        )
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(160.dp).background(scheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🍽️", fontSize = 54.sp)
                        }
                    }
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(recipe.recipeName, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = scheme.onBackground)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            recipe.recipeCategory?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                            if (recipe.isVegan) AssistChip(onClick = {}, label = { Text("🌱 Vegan") })
                        }
                        recipe.description?.takeIf { it.isNotBlank() }?.let {
                            Text("Instructions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(it, color = scheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Pantry availability", fontWeight = FontWeight.Bold)
                                Text("$percent%", color = scheme.primary, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = percent / 100f,
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                                color = scheme.primary
                            )
                            Text("$availableCount of ${ingredients.size} ingredients available", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                            if (ingredients.isNotEmpty() && availableCount < ingredients.size) {
                                Text("${ingredients.size - availableCount} ingredient(s) missing or insufficient", color = scheme.error, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            } else if (ingredients.isNotEmpty()) {
                                Text("You have everything needed to cook this recipe.", color = Color(0xFF278653), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item {
                    Text("Ingredients", modifier = Modifier.padding(horizontal = 20.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (loading) {
                    item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else if (ingredients.isEmpty()) {
                    item { Text("No ingredients have been added to this recipe yet.", modifier = Modifier.padding(horizontal = 20.dp), color = scheme.onSurfaceVariant) }
                } else {
                    items(ingredients, key = { it.ingredientId }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = scheme.surface)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    if (item.isAvailable) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (item.isAvailable) Color(0xFF278653) else scheme.error
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(item.ingredientName, fontWeight = FontWeight.SemiBold)
                                    Text("Required: ${item.requiredQuantity} ${item.requiredUnit}".trim(), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                                    Text("In pantry: ${item.availableQuantity} ${item.availableUnit}".trim(), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                                    item.category?.let { Text(it, fontSize = 11.sp, color = scheme.onSurfaceVariant) }
                                    item.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = scheme.onSurfaceVariant) }
                                    item.expiryDate?.let {
                                        val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
                                        Text("Nearest expiry: $date", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            selectedForUse = item
                                            useAmount = item.requiredQuantity.toString()
                                            useMessage = null
                                        },
                                        enabled = item.availableQuantity > 0.0,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Use this ingredient", fontSize = 12.sp)
                                    }
                                }
                                Surface(
                                    color = if (item.isAvailable) Color(0xFFE4F4EA) else scheme.errorContainer,
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        if (item.isAvailable) "Available" else "Missing",
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        color = if (item.isAvailable) Color(0xFF216B43) else scheme.onErrorContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = { showUseRecipeConfirm = true },
                        enabled = !loading && ingredients.isNotEmpty() && ingredients.all { it.isAvailable },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text("Use this recipe – deduct all ingredients")
                    }
                    useMessage?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = if (useMessageIsError) scheme.error else Color(0xFF278653),
                            fontSize = 13.sp
                        )
                    }
                }
                item {
                    Button(
                        onClick = { shareRecipeImage(recipe, ingredients) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share Recipe Image")
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { shareText(recipe, ingredients) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val bytes = withContext(Dispatchers.IO) { buildRecipePdf(recipe, ingredients) }
                                    exportBytes = bytes
                                    pdfSaveLauncher.launch("${recipe.recipeName.replace(Regex("[^A-Za-z0-9_-]"), "_")}.pdf")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save PDF")
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val bytes = withContext(Dispatchers.IO) { recipeText(recipe, ingredients).toByteArray(Charsets.UTF_8) }
                                    exportBytes = bytes
                                    textSaveLauncher.launch("${recipe.recipeName.replace(Regex("[^A-Za-z0-9_-]"), "_")}.txt")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save TXT")
                        }
                        OutlinedButton(
                            onClick = { sharePdf(recipe, ingredients) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share PDF")
                        }
                    }
                    Text(
                        "Sharing options depend on which apps are installed and support the selected format.",
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = scheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    selectedForUse?.let { selected ->
        AlertDialog(
            onDismissRequest = { if (!usingStock) selectedForUse = null },
            title = { Text("Use ${selected.ingredientName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Available: ${selected.availableQuantity} ${selected.availableUnit}")
                    Text("Recipe requirement: ${selected.requiredQuantity} ${selected.requiredUnit}")
                    OutlinedTextField(
                        value = useAmount,
                        onValueChange = { useAmount = it },
                        label = { Text("Amount to use (${selected.availableUnit})") },
                        singleLine = true,
                        enabled = !usingStock
                    )
                    useMessage?.let {
                        Text(it, color = if (useMessageIsError) scheme.error else Color(0xFF278653))
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !usingStock && (useAmount.toDoubleOrNull()?.let { it > 0.0 } == true),
                    onClick = {
                        val amount = useAmount.toDoubleOrNull() ?: return@Button
                        usingStock = true
                        scope.launch {
                            val result = recipeVm.useIngredientStock(
                                selected.ingredientId,
                                amount,
                                selected.availableUnit
                            )
                            usingStock = false
                            result.onSuccess {
                                useMessage = it
                                useMessageIsError = false
                                selectedForUse = null
                                ingredients = recipeVm.getRecipeDetailIngredients(recipeId)
                            }.onFailure {
                                useMessage = it.message ?: "Unable to use this ingredient."
                                useMessageIsError = true
                            }
                        }
                    }
                ) { Text(if (usingStock) "Updating..." else "Confirm use") }
            },
            dismissButton = {
                TextButton(onClick = { selectedForUse = null }, enabled = !usingStock) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUseRecipeConfirm && recipe != null) {
        AlertDialog(
            onDismissRequest = { if (!usingStock) showUseRecipeConfirm = false },
            title = { Text("Use this recipe?") },
            text = {
                Text("SmartPantry will deduct the required quantity of every ingredient from your pantry. This action cannot be automatically undone.")
            },
            confirmButton = {
                Button(
                    enabled = !usingStock,
                    onClick = {
                        usingStock = true
                        scope.launch {
                            val result = recipeVm.useWholeRecipe(recipeId)
                            usingStock = false
                            showUseRecipeConfirm = false
                            result.onSuccess {
                                useMessage = it
                                useMessageIsError = false
                                ingredients = recipeVm.getRecipeDetailIngredients(recipeId)
                            }.onFailure {
                                useMessage = it.message ?: "Unable to use this recipe."
                                useMessageIsError = true
                            }
                        }
                    }
                ) { Text(if (usingStock) "Updating..." else "Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showUseRecipeConfirm = false }, enabled = !usingStock) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Creates a shareable recipe poster locally; no network/image-generation service is required. */
private fun buildRecipeShareImage(
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
    val shown = ingredients.take(12)
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

private fun wrapCanvasText(text: String, paint: Paint, maxWidth: Float): List<String> {
    val words = text.split(Regex("\\s+"))
    val lines = mutableListOf<String>()
    var line = ""
    for (word in words) {
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (paint.measureText(candidate) <= maxWidth || line.isEmpty()) {
            line = candidate
        } else {
            lines += line
            line = word
        }
    }
    if (line.isNotEmpty()) lines += line
    return lines
}

private fun buildRecipePdf(
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
    for ((line, paint) in lines) {
        if (y > pageHeight - 55f) {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = 58f
            canvas.drawText("SmartPantry", margin, 30f, mutedPaint)
        }
        if (line.isNotEmpty()) canvas.drawText(line.take(95), margin, y, paint)
        y += if (paint === titlePaint) 34f else if (paint === headingPaint) 24f else 18f
    }
    pdf.finishPage(page)
    val output = ByteArrayOutputStream()
    pdf.writeTo(output)
    pdf.close()
    return output.toByteArray()
}
