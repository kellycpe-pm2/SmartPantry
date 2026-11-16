package com.example.smartpantry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ListItem(
    val id: Int,
    val name: String,
    val quantity: Int,
    val unit: String,
    val unitPriceSen: Int? = null,
    val imageRes: Int? = null,
    val purchased: Boolean = false,
    val photoUri: String? = null
)

private data class PantryBatch(
    val name: String,
    val quantity: Int,
    val unit: String,
    val expiry: LocalDate,
    val purchasedOn: LocalDate? = null,
    val note: String = "",
    val sourceItemId: Int? = null,
    val imageRes: Int? = null,
    val photoUri: String? = null
)

private enum class ShoppingPage { LIST, REVIEW, PANTRY }

private val shoppingUnits = listOf("g", "kg", "mL", "L", "pcs", "bag", "carton")
private val shopShape = RoundedCornerShape(18.dp)

private val sampleList = listOf(
    ListItem(1, "Eggs", 12, "pcs", 50, R.drawable.home_eggs),
    ListItem(2, "Tomatoes", 4, "pcs", 80, R.drawable.home_tomatoes),
    ListItem(3, "Milk", 2, "carton", 300, R.drawable.ingredient_milk),
    ListItem(4, "Rice", 1, "bag", imageRes = R.drawable.ingredient_rice, purchased = true)
)

private val shoppingIngredients = listOf(
    ListItem(0, "Eggs", 1, "pcs", imageRes = R.drawable.home_eggs),
    ListItem(0, "Tomatoes", 1, "pcs", imageRes = R.drawable.home_tomatoes),
    ListItem(0, "Milk", 1, "carton", imageRes = R.drawable.ingredient_milk),
    ListItem(0, "Rice", 1, "bag", imageRes = R.drawable.ingredient_rice),
    ListItem(0, "Orange", 1, "pcs", imageRes = R.drawable.ingredient_orange),
    ListItem(0, "Onion", 1, "pcs", imageRes = R.drawable.ingredient_onion)
)

private fun parseDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShoppingListScreen(onBack: () -> Unit) {
    // 四个子页面共用同一份清单和库存。切换子页面时，资料不会重置。
    var items by remember { mutableStateOf(sampleList) }
    var nextId by remember { mutableIntStateOf(5) }
    var batches by remember {
        val today = LocalDate.now()
        mutableStateOf(
            listOf(
                PantryBatch("Eggs", 6, "pcs", today.plusDays(1)),
                PantryBatch("Eggs", 12, "pcs", today.plusDays(7)),
                PantryBatch("Tomatoes", 3, "pcs", today.plusDays(4))
            )
        )
    }

    var page by remember { mutableStateOf(ShoppingPage.LIST) }
    var editingItem by remember { mutableStateOf<ListItem?>(null) }
    var reviewedIds by remember { mutableStateOf(emptySet<Int>()) }
    var selectedItem by remember { mutableStateOf<ListItem?>(null) }

    val toBuy = items.filterNot { it.purchased }
    val purchased = items.filter { it.purchased }

    val suggestions = buildReviewSuggestions(toBuy, batches)
    val reviewCount = suggestions.count { it.needsReview && it.item.id !in reviewedIds }

    fun goBack() {
        page = when (page) {
            ShoppingPage.LIST -> {
                onBack()
                ShoppingPage.LIST
            }
            ShoppingPage.REVIEW, ShoppingPage.PANTRY -> ShoppingPage.LIST
        }
    }

    fun replaceItem(updated: ListItem) {
        items = items.map { if (it.id == updated.id) updated else it }
    }

    // LIST 的返回由 SmartPantryApp 处理，子页面则先回到上一层。
    BackHandler(enabled = page != ShoppingPage.LIST) { goBack() }

    val title = when (page) {
        ShoppingPage.LIST -> "Shopping list"
        ShoppingPage.REVIEW -> "Purchase Review"
        ShoppingPage.PANTRY -> "Add to pantry"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // 每个画面单独从顶部开始滚动，并共用页首与外边距。
        key(page) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = ::goBack,
                            modifier = Modifier.semantics { contentDescription = "Back" }
                        ) {
                            Text("←")
                        }
                        Text(
                            if (page == ShoppingPage.REVIEW) "SmartPantry" else title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    when (page) {
                        ShoppingPage.LIST -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ShoppingMetric(toBuy.size, "To buy", Modifier.weight(1f))
                                ShoppingMetric(purchased.size, "Purchased", Modifier.weight(1f))
                            }
                            if (reviewCount > 0) {
                                ShoppingPanel(tinted = true) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(reviewCountLabel(reviewCount),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold)
                                            Text("Before you shop · Compare with pantry stock",
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = { page = ShoppingPage.REVIEW }) {
                                            Text("Review →")
                                        }
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("To buy", style = MaterialTheme.typography.titleLarge)
                                Button(onClick = { editingItem = ListItem(0, "", 1, "pcs") }) {
                                    Text("+ Add item")
                                }
                            }
                            ShoppingPanel {
                                if (toBuy.isEmpty()) Text("Nothing to buy yet.")
                                toBuy.forEachIndexed { index, item ->
                                    if (index > 0) HorizontalDivider()
                                    ShoppingRow(
                                        item = item,
                                        addedToPantry = false,
                                        onToggle = {
                                            replaceItem(item.copy(purchased = it))
                                        },
                                        onEdit = { editingItem = item },
                                        onRemove = { items = items.filterNot { it.id == item.id } },
                                        onAddToPantry = {}
                                    )
                                }
                            }

                            Text("Purchased", style = MaterialTheme.typography.titleLarge)
                            ShoppingPanel {
                                if (purchased.isEmpty()) Text("No purchased items yet.")
                                purchased.forEachIndexed { index, item ->
                                    if (index > 0) HorizontalDivider()
                                    val added = batches.any { it.sourceItemId == item.id }
                                    ShoppingRow(
                                        item = item,
                                        addedToPantry = added,
                                        onToggle = {
                                            replaceItem(item.copy(purchased = it))
                                        },
                                        onEdit = { editingItem = item },
                                        onRemove = { items = items.filterNot { it.id == item.id } },
                                        onAddToPantry = {
                                            selectedItem = item
                                            page = ShoppingPage.PANTRY
                                        }
                                    )
                                }
                            }
                        }

                        ShoppingPage.REVIEW -> ShoppingReview(
                            suggestions = suggestions,
                            handled = reviewedIds,
                            onKeep = { reviewedIds = reviewedIds + it },
                            onChange = { item, quantity ->
                                replaceItem(item.copy(quantity = quantity))
                                reviewedIds = reviewedIds + item.id
                            },
                            onRemove = { item -> items = items.filterNot { it.id == item.id } }
                        )

                        ShoppingPage.PANTRY -> selectedItem?.let { item ->
                            PantryEditor(
                                item = item,
                                onCancel = ::goBack,
                                onConfirm = { batch ->
                                    // 同一个已购项目只允许转入一次。
                                    if (items.any { it.id == item.id && it.purchased } &&
                                        batches.none { it.sourceItemId == item.id }
                                    ) {
                                        batches = batches + batch
                                    }
                                    page = ShoppingPage.LIST
                                }
                            )
                        }
                    }
                }
                if (page == ShoppingPage.REVIEW) {
                    Button(
                        onClick = ::goBack,
                        modifier = Modifier.padding(horizontal = 20.dp)
                            .padding(bottom = 12.dp).fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Done") }
                }
            }
        }
    }
    editingItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { editingItem = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (item.id == 0) "Add item" else "Edit item",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                ShoppingEditor(
                    item = item,
                    onSave = { edited ->
                        if (item.id == 0) items = items + edited.copy(id = nextId++)
                        else {
                            if (item.name != edited.name || item.quantity != edited.quantity ||
                                item.unit != edited.unit
                            ) reviewedIds = reviewedIds - item.id
                            replaceItem(edited)
                        }
                        editingItem = null
                    },
                    onRemove = {
                        items = items.filterNot { it.id == item.id }
                        editingItem = null
                    }
                )
            }
        }
    }

}

@Composable
private fun ShoppingMetric(count: Int, label: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall,
                color = colors.primary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun ShoppingPanel(
    tinted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shopShape,
        color = if (tinted) colors.secondaryContainer.copy(alpha = 0.48f) else colors.background,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f)),
        contentColor = if (tinted) colors.onSecondaryContainer else colors.onSurface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun FoodThumbnail(item: ListItem, size: Int = 38) {
    if (item.photoUri != null || item.imageRes == null) {
        ShoppingCustomPhoto(item.photoUri, Modifier.size(size.dp))
    } else {
        Image(
            painter = painterResource(item.imageRes),
            contentDescription = null,
            modifier = Modifier.size(size.dp)
        )
    }
}

@Composable
private fun ShoppingRow(
    item: ListItem,
    addedToPantry: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onAddToPantry: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.purchased,
                onCheckedChange = onToggle,
                enabled = !addedToPantry,
                modifier = Modifier.semantics {
                    contentDescription = "Purchased status for ${item.name}"
                }
            )
            FoodThumbnail(item)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(item.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${item.quantity} ${item.unit}",
                    style = MaterialTheme.typography.bodySmall
                )
                item.unitPriceSen?.let { sen ->
                    Text(
                        String.format(Locale.US, "RM %.2f / %s", sen / 100.0, item.unit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (item.purchased) {
                if (addedToPantry) {
                    Text("Stored", style = MaterialTheme.typography.labelSmall)
                } else {
                    TextButton(
                        onClick = onAddToPantry,
                        modifier = Modifier.semantics { contentDescription = "Add ${item.name} to pantry" }
                    ) { Text("+ Pantry", style = MaterialTheme.typography.labelMedium) }
                }
            }
            var menu by remember(item.id) { mutableStateOf(false) }
            var confirm by remember(item.id) { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.semantics {
                    contentDescription = "Item options"
                }) { Text("⋮") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!addedToPantry) {
                        DropdownMenuItem(text = { Text("Edit item") }, onClick = {
                            menu = false
                            onEdit()
                        })
                    }
                    DropdownMenuItem(text = { Text("Remove from list") }, onClick = {
                        menu = false
                        confirm = true
                    })
                }
            }
            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text("Remove ${item.name}?") },
                    confirmButton = {
                        TextButton(onClick = { confirm = false; onRemove() }) { Text("Remove") }
                    },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
                )
            }
        }

    }
}

@Composable
private fun ShoppingEditor(
    item: ListItem,
    onSave: (ListItem) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val customIngredients = remember(context) {
        context.getSharedPreferences("shopping_custom_ingredients", 0)
    }
    var ingredient by remember(item) {
        mutableStateOf(item.takeIf { it.name.isNotBlank() })
    }
    var quantityText by remember { mutableStateOf(item.quantity.toString()) }
    var unit by remember(item) {
        mutableStateOf(if (item.unit == "cartons") "carton" else item.unit)
    }
    var priceText by remember(item) {
        mutableStateOf(item.unitPriceSen?.let { String.format(Locale.US, "%.2f", it / 100.0) }.orEmpty())
    }
    val priceSen = priceText.replace(',', '.').toBigDecimalOrNull()
        ?.movePointRight(2)?.toInt()
    var confirmRemove by remember { mutableStateOf(false) }

    val quantity = quantityText.toIntOrNull()
    val valid = ingredient != null && quantity != null && quantity in 1..999 &&
            (priceText.isBlank() || priceSen != null)

    ingredient?.let { selected ->
        ShoppingPhotoEditor(selected) { ingredient = selected.copy(photoUri = it) }
    }
    ShoppingIngredientPicker(selected = ingredient, onSelection = {
        ingredient = it
        if (it != null && item.id == 0) unit = it.unit
    })
    ShoppingQuantityRow(quantityText, unit, { quantityText = it }, { unit = it })

    OutlinedTextField(
        value = priceText,
        onValueChange = { input ->
            if (input.matches(Regex("[0-9]{0,6}([.,][0-9]{0,2})?"))) priceText = input
        },
        label = { Text("Unit price (optional)") },
        prefix = { Text("RM ") },
        supportingText = { Text("Price per $unit") },
        isError = priceText.isNotBlank() && priceSen == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = valid,
        onClick = {
            val selected = ingredient ?: return@Button
            val newQuantity = quantity ?: return@Button

            if (selected.imageRes == null) {
                customIngredients.edit()
                    .putString(selected.name, selected.photoUri.orEmpty())
                    .apply()
            }

            onSave(
                item.copy(
                    name = selected.name,
                    quantity = newQuantity,
                    unit = unit,
                    unitPriceSen = priceSen,
                    imageRes = selected.imageRes,
                    photoUri = selected.photoUri
                )
            )
        }
    ) {
        Text(if (item.id == 0) "Add item" else "Save changes")
    }

    if (item.id != 0) {
        TextButton(onClick = { confirmRemove = true }) {
            Text("Remove item", color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${item.name}?") },
            text = { Text("This removes the item from your shopping list.") },
            confirmButton = { TextButton(onClick = onRemove) { Text("Remove") } },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            }
        )
    }
}

private data class ReviewSuggestion(
    val item: ListItem,
    val stock: Long,
    val buy: Int,
    val expiring: Long,
    val batches: List<PantryBatch>,
    val differentUnit: Boolean
)

private val ReviewSuggestion.needsReview: Boolean
    get() = buy < item.quantity || differentUnit

private fun reviewCountLabel(count: Int): String =
    "$count ${if (count == 1) "item" else "items"} worth reviewing"

private fun buildReviewSuggestions(
    items: List<ListItem>,
    batches: List<PantryBatch>
): List<ReviewSuggestion> {
    val today = LocalDate.now()
    val demand = mutableMapOf<Pair<String, String>, Long>()
    return items.map { item ->
        val key = item.name.trim().lowercase(Locale.ROOT) to item.unit
        val used = demand[key] ?: 0L
        demand[key] = used + item.quantity
        val matching = batches.filter {
            it.name.trim().equals(item.name.trim(), true) &&
                    it.quantity > 0 && !it.expiry.isBefore(today)
        }
        val comparable = matching.filter { it.unit == item.unit }.sortedBy { it.expiry }
        val stock = (comparable.sumOf { it.quantity.toLong() } - used).coerceAtLeast(0)
        val expiring = (comparable.filter { !it.expiry.isAfter(today.plusDays(1)) }
            .sumOf { it.quantity.toLong() } - used).coerceAtLeast(0)
        ReviewSuggestion(item, stock, (item.quantity - stock).coerceAtLeast(0).toInt(),
            expiring, comparable, comparable.isEmpty() && matching.isNotEmpty())
    }
}

@Composable
private fun ShoppingReview(
    suggestions: List<ReviewSuggestion>,
    handled: Set<Int>,
    onKeep: (Int) -> Unit,
    onChange: (ListItem, Int) -> Unit,
    onRemove: (ListItem) -> Unit
) {
    var showUnchanged by remember { mutableStateOf(false) }
    var removal by remember { mutableStateOf<ListItem?>(null) }
    var adjustment by remember { mutableStateOf<ReviewSuggestion?>(null) }
    val pending = suggestions.filter {
        it.item.id !in handled && it.needsReview
    }.sortedBy { it.buy == 0 }
    val unchanged = suggestions.filter { it !in pending && it.item.id !in handled }
    val colors = MaterialTheme.colorScheme

    Text("Purchase Review", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("See what you can use from your pantry.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    ShoppingPanel(tinted = true) {
        Text(if (pending.isEmpty()) "All caught up" else reviewCountLabel(pending.size),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    if (pending.isNotEmpty()) {
        Text("Suggested changes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
    pending.forEach { suggestion ->
        val item = suggestion.item
        key(item.id) {
            var showBatches by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FoodThumbnail(item, 56)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            suggestion.differentUnit -> "Check pantry units"
                            suggestion.buy == 0 -> "You may be able to skip buying"
                            else -> "Buy ${suggestion.buy} instead of ${item.quantity}"
                        },
                        Modifier.background(colors.secondaryContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        color = colors.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (suggestion.differentUnit) "Stock uses a different unit."
                        else "${item.quantity} ${item.unit} planned · ${suggestion.stock} ${item.unit} in pantry",
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                    )
                    if (suggestion.expiring > 0) {
                        Text("● Use ${suggestion.expiring} ${item.unit} expiring by tomorrow first",
                            color = colors.tertiary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (suggestion.batches.isNotEmpty() && (suggestion.expiring > 0 || suggestion.buy == 0)) {
                        FilledTonalButton(
                            onClick = { showBatches = !showBatches },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            val count = suggestion.batches.size
                            Text("$count pantry ${if (count == 1) "batch" else "batches"}", Modifier.weight(1f))
                            Text(if (showBatches) "⌃" else "⌄")
                        }
                        if (showBatches) suggestion.batches.forEach {
                            Text("${it.quantity} ${it.unit} · ${it.expiry}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (!suggestion.differentUnit && suggestion.buy > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        adjustment = suggestion
                    }) {
                        val unit = if (suggestion.buy == 1 && item.unit == "pcs") "pc" else item.unit
                        Text("Change to ${suggestion.buy} $unit →")
                    }
                }
            }
            if (!suggestion.differentUnit && suggestion.buy == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { removal = item }, modifier = Modifier.weight(1f)) {
                        Text("Remove from list")
                    }
                    OutlinedButton(onClick = { onKeep(item.id) }, modifier = Modifier.weight(1f)) {
                        Text("Keep item")
                    }
                }
            } else if (suggestion.differentUnit) {
                TextButton(onClick = { onKeep(item.id) }) { Text("Keep item") }
            }
            HorizontalDivider()
        }
    }
    if (unchanged.isNotEmpty()) {
        FilledTonalButton(
            onClick = { showUnchanged = !showUnchanged },
            modifier = Modifier.fillMaxWidth(),
            shape = shopShape
        ) {
            Text("No changes needed · ${unchanged.size}", Modifier.weight(1f))
            Text(if (showUnchanged) "↑" else "↓")
        }
        if (showUnchanged) unchanged.forEach {
            Text("${it.item.name} · ${it.item.quantity} ${it.item.unit}")
        }
    }
    adjustment?.let { suggestion ->
        val item = suggestion.item
        AlertDialog(
            onDismissRequest = { adjustment = null },
            title = { Text("Buy fewer ${item.name.lowercase(Locale.ROOT)}?") },
            text = {
                Text("Change ${item.quantity} ${item.unit} to ${suggestion.buy} ${item.unit} on your shopping list.")
            },
            confirmButton = {
                Button(onClick = {
                    adjustment = null
                    onChange(item, suggestion.buy)
                }) { Text("Apply change") }
            },
            dismissButton = { TextButton(onClick = { adjustment = null }) { Text("Cancel") } }
        )
    }
    removal?.let { item ->
        AlertDialog(
            onDismissRequest = { removal = null },
            title = { Text("Remove ${item.name}?") },
            text = { Text("Check that you can use the stock before it expires.") },
            confirmButton = {
                TextButton(onClick = { removal = null; onRemove(item) }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removal = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PantryEditor(
    item: ListItem,
    onCancel: () -> Unit,
    onConfirm: (PantryBatch) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var photo by remember { mutableStateOf(item.photoUri) }
    var quantityText by remember { mutableStateOf(item.quantity.toString()) }
    var unit by remember { mutableStateOf(item.unit) }
    var purchasedOnText by remember { mutableStateOf(LocalDate.now().toString()) }
    var expiryText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val quantity = quantityText.toIntOrNull()
    val purchasedOn = parseDate(purchasedOnText)
    val expiry = parseDate(expiryText)
    val validPurchaseDate = purchasedOn != null && !purchasedOn.isAfter(LocalDate.now())
    val validExpiry = expiry != null && purchasedOn != null && !expiry.isBefore(purchasedOn)
    val valid = name.isNotBlank() && quantity != null && quantity in 1..999 &&
            validPurchaseDate && validExpiry

    ShoppingPhotoEditor(item.copy(name = name, photoUri = photo)) { photo = it }
    OutlinedTextField(
        value = name,
        onValueChange = { input ->
            if (input.length <= 50 && input.none { it == '\n' || it == '\r' }) name = input
        },
        label = { Text("Ingredient") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    ShoppingPanel {
        ShoppingQuantityRow(quantityText, unit, { quantityText = it }, { unit = it })
        ShoppingCalendar("Purchase date", purchasedOn, latest = LocalDate.now()) {
            purchasedOnText = it.toString()
            if (expiry?.isBefore(it) == true) expiryText = ""
        }
        ShoppingCalendar("Expiry date", expiry, earliest = purchasedOn) {
            expiryText = it.toString()
        }
        OutlinedTextField(
            value = note,
            onValueChange = { input ->
                val normalized = input.replace("\r\n", "\n").replace('\r', '\n')
                if (normalized.length <= 150 && normalized.count { it == '\n' } <= 2) {
                    note = normalized
                }
            },
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            supportingText = { Text("${note.length} / 150 · Max 3 lines") }
        )
    }

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = valid,
        onClick = {
            if (quantity != null && purchasedOn != null && expiry != null) {
                onConfirm(
                    PantryBatch(
                        name = name.trim(),
                        quantity = quantity,
                        unit = unit,
                        expiry = expiry,
                        purchasedOn = purchasedOn,
                        note = note.trim(),
                        sourceItemId = item.id,
                        imageRes = item.imageRes,
                        photoUri = photo
                    )
                )
            }
        }
    ) {
        Text("Confirm & add")
    }
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel")
    }
}

@Composable
private fun ShoppingQuantityRow(
    quantity: String,
    unit: String,
    onQuantity: (String) -> Unit,
    onUnit: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = quantity,
            onValueChange = { if (it.length <= 3 && it.all { digit -> digit in '0'..'9' }) onQuantity(it) },
            modifier = Modifier.weight(1f),
            label = { Text("Quantity") },
            singleLine = true,
            isError = quantity.isNotEmpty() && quantity.toIntOrNull() !in 1..999,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Unit · $unit ▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                shoppingUnits.forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = {
                        expanded = false
                        onUnit(it)
                    })
                }
            }
        }
    }
}

@Composable
private fun ShoppingCalendar(
    label: String,
    date: LocalDate?,
    earliest: LocalDate? = null,
    latest: LocalDate? = null,
    onDate: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    fun millis(day: LocalDate): Long =
        day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            onClick = {
                val initial = date ?: LocalDate.now()
                val dialog = android.app.DatePickerDialog(
                    context,
                    { _, year, month, day -> onDate(LocalDate.of(year, month + 1, day)) },
                    initial.year, initial.monthValue - 1, initial.dayOfMonth
                )
                earliest?.let { dialog.datePicker.minDate = millis(it) }
                latest?.let { dialog.datePicker.maxDate = millis(it) }
                dialog.show()
            }
        ) {
            Text(date?.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
                ?: "Select date", Modifier.weight(1f))
            Text("▾")
        }
    }
}

@Composable
private fun ShoppingPhotoEditor(item: ListItem, onPhotoChange: (String?) -> Unit) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                onPhotoChange(uri.toString())
                failed = false
            } catch (_: SecurityException) {
                failed = true
            }
        }
    }
    ShoppingPanel(tinted = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FoodThumbnail(item, 56)
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold)
                Text("Photo (optional)", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                Text(if (item.photoUri == null && item.imageRes == null) "Add photo" else "Change")
            }
        }
        if (item.photoUri != null) {
            TextButton(onClick = { failed = false; onPhotoChange(null) }) {
                Text(if (item.imageRes == null) "Remove photo" else "Restore default")
            }
        }
        if (failed) Text("Could not keep access to this photo.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ShoppingIngredientPicker(
    selected: ListItem?,
    onSelection: (ListItem?) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("shopping_custom_ingredients", 0)
    }
    val catalog = remember(preferences) {
        val saved = preferences.all.mapNotNull { (name, value) ->
            val photo = value as? String ?: return@mapNotNull null
            ListItem(0, name, 1, "pcs", photoUri = photo.ifBlank { null })
        }
        (shoppingIngredients + saved)
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    var query by remember { mutableStateOf(selected?.name.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    var creatingCustom by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customPhoto by remember { mutableStateOf<String?>(null) }
    var photoError by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            photoError = try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                customPhoto = uri.toString()
                false
            } catch (_: SecurityException) {
                true
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { input ->
                if (input.length <= 50 && input.none { it == '\n' || it == '\r' }) {
                    query = input
                    onSelection(null)
                    expanded = true
                }
            },
            label = { Text("Ingredient") },
            placeholder = { Text("Search ingredients") },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▴" else "▾")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        val matches = catalog.filter {
            it.name.startsWith(query.trim(), ignoreCase = true)
        }

        if (expanded) {
            ShoppingPanel {
                if (matches.isEmpty()) {
                    Text("No matching ingredients", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(Modifier.heightIn(max = 156.dp).verticalScroll(rememberScrollState())) {
                        matches.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(entry.name) },
                                leadingIcon = { FoodThumbnail(entry, 32) },
                                onClick = {
                                    query = entry.name
                                    onSelection(entry)
                                    expanded = false
                                }
                            )
                        }
                    }
                    HorizontalDivider()
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        customName = query.trim()
                        customPhoto = null
                        photoError = false
                        expanded = false
                        creatingCustom = true
                    }
                ) { Text("+ Other · Add custom ingredient") }
            }
        }
    }

    if (creatingCustom) {
        val name = customName.trim()
        val alreadyExists = catalog.any { it.name.equals(name, ignoreCase = true) }

        AlertDialog(
            onDismissRequest = { creatingCustom = false },
            title = { Text("Add custom ingredient") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { input ->
                            if (input.length <= 50 && input.none { it == '\n' || it == '\r' }) {
                                customName = input
                            }
                        },
                        label = { Text("Ingredient name") },
                        singleLine = true,
                        isError = alreadyExists,
                        supportingText = {
                            Text(
                                if (alreadyExists) "Already in your ingredient list"
                                else "${customName.length} / 50 characters"
                            )
                        }
                    )
                    TextButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) {
                        Text(if (customPhoto == null) "Add photo (optional)" else "Change photo")
                    }
                    if (customPhoto != null) {
                        ShoppingCustomPhoto(customPhoto, Modifier.size(56.dp))
                        TextButton(onClick = { customPhoto = null }) {
                            Text("Remove photo")
                        }
                    }
                    if (photoError) {
                        Text("Couldn't keep access to this photo. Choose another or continue without it.")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotEmpty() && !alreadyExists,
                    onClick = {
                        query = name
                        onSelection(ListItem(0, name, 1, "pcs", photoUri = customPhoto))
                        creatingCustom = false
                    }
                ) {
                    Text("Use ingredient")
                }
            },
            dismissButton = {
                TextButton(onClick = { creatingCustom = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ShoppingCustomPhoto(photoUri: String?, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = photoUri
    ) {
        value = null
        val uri = photoUri?.let(Uri::parse) ?: return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 512) {
                    sampleSize *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                }
            } catch (_: java.io.IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
    }

    val photo = bitmap
    Box(modifier, contentAlignment = Alignment.Center) {
        if (photo != null) {
            Image(photo, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Text("◇", color = MaterialTheme.colorScheme.primary)
        }
    }
}
