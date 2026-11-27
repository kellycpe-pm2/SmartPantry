package com.example.smartpantry.Screen


import com.example.smartpantry.core.PurchaseReviewRules
import com.example.smartpantry.core.validShoppingQuantity
import com.example.smartpantry.core.shoppingQuantityHint
import com.example.smartpantry.core.measuredUnits
import com.example.smartpantry.core.userMessage
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import com.example.smartpantry.R
import com.example.smartpantry.community.SmartPantrySectionHeader
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.example.smartpantry.Screen.CommunityScreen.cleanDisplay
import com.example.smartpantry.Screen.CommunityScreen.cleanInput
import com.example.smartpantry.remote.CommunityRepository
import com.example.smartpantry.storage.remote.RemoteShoppingItem
import com.example.smartpantry.storage.remote.ShoppingRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
private data class ListItem(
    val id: Long,
    val name: String,
    val quantity: Double,
    val unit: String,
    val imageRes: Int? = null,
    val purchased: Boolean = false,
    val photoUri: String? = null,
    val ingredientId: Long? = null,
    val customIngredientId: Long? = null,
    val plannedQuantity: Double = quantity,
    val unitPrice: Double? = null,
    val handled: Boolean = false,
    val requestId: String = UUID.randomUUID().toString()
)

private val shoppingItemSaver = Saver<ListItem?, String>(save={Json.encodeToString(it)},restore={Json.decodeFromString<ListItem?>(it)})

private data class PantryBatch(
    val name: String,
    val quantity: Double,
    val unit: String,
    val expiry: LocalDate,
    val batchId: Long = 0L,
    val location: String = "Pantry",
    val purchasedOn: LocalDate? = null,
    val note: String = "",
    val sourceItemId: Long? = null,
    val ingredientId: Long? = null,
    val imageRes: Int? = null,
    val photoUri: String? = null
)

private enum class ShoppingPage { LIST, REVIEW, PANTRY }

private val shoppingUnits = listOf("g", "kg", "mL", "L", "pcs", "bag", "carton")
private val shopShape = RoundedCornerShape(20.dp)

private val catalogImages = mapOf(
    96L to R.drawable.home_eggs,
    9L to R.drawable.home_tomatoes,
    86L to R.drawable.ingredient_milk,
    100L to R.drawable.ingredient_rice,
    42L to R.drawable.ingredient_orange,
    13L to R.drawable.ingredient_onion
)

// Names provide stable fallbacks even when catalogue IDs change or remote URLs fail.
private fun ingredientImage(name: String, id: Long?): Int? = when(name.trim().lowercase(Locale.ROOT)) {
    "milk", "fresh milk", "whole milk", "full cream milk", "low fat milk" -> R.drawable.ingredient_milk
    "onion", "onions", "red onion", "yellow onion" -> R.drawable.ingredient_onion
    "egg", "eggs" -> R.drawable.home_eggs
    "tomato", "tomatoes" -> R.drawable.home_tomatoes
    "rice", "white rice" -> R.drawable.ingredient_rice
    "orange", "oranges" -> R.drawable.ingredient_orange
    else -> catalogImages[id]
}

private fun normalizedUnit(unit: String?): String? = when (unit?.trim()?.lowercase(Locale.ROOT)) {
    "piece", "pieces", "pc", "pcs" -> "pcs"
    "ml" -> "mL"
    "l" -> "L"
    "bags" -> "bag"
    "cartons" -> "carton"
    else -> unit?.trim()?.lowercase(Locale.ROOT)
}

private fun formatQuantity(quantity: Double): String =
    quantity.toBigDecimal().stripTrailingZeros().toPlainString()

private fun reviewAmount(quantity: Double, unit: String): String {
    val compact = quantity>0 && quantity<1 && unit in listOf("kg","L")
    val value=if(compact) quantity.toBigDecimal().multiply(1000.toBigDecimal()).stripTrailingZeros().toPlainString() else formatQuantity(quantity)
    return "$value ${if(compact) (if(unit=="kg") "g" else "mL") else unit}"
}

private fun parseQuantity(text: String, unit: String): Double? =
    text.replace(',', '.').toDoubleOrNull()?.takeIf {
        validShoppingQuantity(it, unit)
    }

private fun parseUnitPrice(value:String):Double? {
    val text=value.trim().replace(',','.')
    if(!text.matches(Regex("""[0-9]+(\.[0-9]{1,2})?""")))return null
    return text.toDoubleOrNull()?.takeIf{it.isFinite() && it>=0 && it<=9999999999.99}
}

@RequiresApi(Build.VERSION_CODES.O)
private fun parseDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShoppingListScreen(onBack: () -> Unit, onInventoryChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember(context) { ShoppingRepository(context) }
    var storedIds by remember { mutableStateOf(emptySet<Long>()) }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(emptyList<ListItem>()) }
    var catalog by remember { mutableStateOf(emptyList<ListItem>()) }
    var batches by remember { mutableStateOf(emptyList<PantryBatch>()) }
    var offline by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    var pendingCount by remember { mutableStateOf(0) }
    var pantryReadFailed by remember { mutableStateOf(false) }
    var pantryKnown by remember { mutableStateOf(false) }
    var reviewVersion by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun mutate(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("ShoppingList", "Supabase operation failed", e)
                message = userMessage(e)
            } finally {
                offline = repository.offline
                pendingCount = repository.pendingCount
                syncStatus = repository.syncStatus
                busy = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun load(cacheOnly: Boolean = false, forceSync: Boolean = false) {
        if(!cacheOnly) pantryReadFailed=false
        try {
            if(!cacheOnly) { repository.beginRefresh(); repository.syncPending(force=forceSync) }
            val system = repository.catalog(cacheOnly).mapNotNull { ingredient ->
                val id = ingredient.ingredientId ?: return@mapNotNull null
                val unit = normalizedUnit(ingredient.unit)
                ListItem(0, ingredient.ingredientName, 1.0, unit.orEmpty(),
                    imageRes = ingredientImage(ingredient.ingredientName,id), ingredientId = id, photoUri=ingredient.image?.takeIf{it.isNotBlank()})
            }
            val custom = repository.customIngredients(cacheOnly).map {
                ListItem(0, it.name, 1.0, "pcs", customIngredientId = it.id, photoUri=it.imagePath?.takeIf{it.isNotBlank()})
            }
            val localCustom=repository.localIngredients().map { row -> ListItem(0,row.displayName.orEmpty(),1.0,row.unit,
                ingredientId=row.ingredientId,customIngredientId=row.customIngredientId,photoUri=row.photoPath,
                imageRes=ingredientImage(row.displayName.orEmpty(),row.ingredientId)) }
            val bySystemId = system.associateBy { it.ingredientId }
            val byCustomId = custom.associateBy { it.customIngredientId }
            val remoteItems = repository.items(cacheOnly).map { row ->
                val entry = if (row.ingredientId != null) bySystemId[row.ingredientId]
                else byCustomId[row.customIngredientId]
                (entry ?: ListItem(0, "Unavailable ingredient", 1.0, row.unit)).copy(
                    id = row.id, name=row.displayName ?: entry?.name ?: "Unavailable ingredient", quantity = row.purchaseQuantity, unit = row.unit,
                    purchased = row.isPurchased, ingredientId = row.ingredientId,
                    customIngredientId = row.customIngredientId, plannedQuantity = row.plannedQuantity,
                    unitPrice=row.unitPrice,handled=row.reviewHandled,photoUri=row.photoPath ?: entry?.photoUri,
                    imageRes=if(row.photoPath=="")null else ingredientImage(row.displayName ?: entry?.name.orEmpty(),row.ingredientId)
                )
            }
            catalog = system.sortedWith(compareBy({ it.ingredientId !in catalogImages.keys }, { it.name })) +
                    custom.filter{old->system.none{it.name.trim().equals(old.name.trim(),true)}}.sortedBy { it.name } +
                    remoteItems.filter { row -> (system+custom).none { it.name.trim().equals(row.name.trim(),true) } }.distinctBy { it.name.trim().lowercase() }.map { it.copy(id=0) }
            catalog=(catalog+localCustom.filter { local -> catalog.none{it.name.trim().equals(local.name.trim(),true)} }).sortedBy{it.name.lowercase()}
            // Read everything first. Never publish an empty interim stock snapshot.
            val nextStored = repository.storedIds(cacheOnly)
            val nextBatches = repository.pantry(cacheOnly).mapNotNull { row ->
                val id = row.ingredientId ?: return@mapNotNull null
                val quantity = row.quantity?.takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
                val unit = normalizedUnit(row.quantityUnit) ?: return@mapNotNull null
                val expiry = Instant.ofEpochMilli(row.expiredDate)
                    .atZone(ZoneId.of("Asia/Kuala_Lumpur")).toLocalDate()
                PantryBatch(bySystemId[id]?.name ?: "Ingredient #$id", quantity, unit, expiry,
                    batchId=row.id ?: 0L, ingredientId=id)
            }
            items = remoteItems
            storedIds = nextStored
            batches = nextBatches
            pantryKnown = repository.hasPantryCache
            offline = repository.offline
            pendingCount = repository.pendingCount
            ready = true
            reviewVersion++
        } catch(t: Exception) {
            if(!cacheOnly && t !is CancellationException) pantryReadFailed=true
            throw t
        }
    }

    LaunchedEffect(repository) { mutate { if(repository.hasCache)load(true);load() } }
    val lifecycleOwner=LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner,repository) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_RESUME && ready && !busy) mutate { load() } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Reconcile when the connection returns while this screen stays open.
    LaunchedEffect(offline,pendingCount) {
        if(offline || pendingCount>0) while(true) { delay(20000); if(!busy)mutate { load() } }
    }

    var page by rememberSaveable { mutableStateOf(ShoppingPage.LIST) }
    val itemSaver = Saver<ListItem?, String>(save={Json.encodeToString(it)},restore={Json.decodeFromString<ListItem?>(it)})
    var editingItem by rememberSaveable(stateSaver=itemSaver) { mutableStateOf<ListItem?>(null) }
    var selectedItem by rememberSaveable(stateSaver=itemSaver) { mutableStateOf<ListItem?>(null) }

    val toBuy = items.filterNot { it.purchased }
    val purchased = items.filter { it.purchased }

    val suggestions = buildReviewSuggestions(toBuy, batches)
    val reviewedIds = remember(suggestions,reviewVersion) {
        suggestions.filter { repository.reviewDecision(it.item.id)==it.fingerprint }.map { it.item.id }.toSet()
    }
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

    suspend fun saveItem(item: ListItem) {
        val photo = item.photoUri
        val saved = repository.save(RemoteShoppingItem(
            item.id,item.ingredientId,item.customIngredientId,item.plannedQuantity,
            item.quantity,item.unit,item.purchased,item.unitPrice,photo,item.handled
        ),item.name,item.requestId)
        val updated=item.copy(id=saved.id,ingredientId=saved.ingredientId,
            name=saved.displayName ?: item.name,quantity=saved.purchaseQuantity,plannedQuantity=saved.plannedQuantity,
            unit=saved.unit,purchased=saved.isPurchased,unitPrice=saved.unitPrice,handled=saved.reviewHandled,
            customIngredientId=saved.customIngredientId,photoUri=saved.photoPath ?: photo)
        items=(items.filterNot{it.id==item.id || it.id==updated.id}+updated).sortedBy{it.id}
        offline=repository.offline;pendingCount=repository.pendingCount
        // New private ingredients are immediately searchable next time.
        if(catalog.none{it.ingredientId==saved.ingredientId && saved.ingredientId!=null}) { catalog=catalog+updated.copy(id=0);if(saved.id>0)onInventoryChanged() }
        load(true)
    }

    fun removeItem(item: ListItem, after: () -> Unit = {}) = mutate {
        repository.remove(item.id)
        items = items.filterNot { it.id == item.id }
        after()
    }

    // LIST 的返回由 SmartPantryApp 处理，子页面则先回到上一层。
    BackHandler { if(!busy)goBack() }

    val title = when (page) {
        ShoppingPage.LIST -> "Shopping list"
        ShoppingPage.REVIEW -> "Purchase Review"
        ShoppingPage.PANTRY -> "Add to pantry"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.fillMaxSize()) {
            SmartPantrySectionHeader(title=title,showBack=true,onBack={if(!busy)goBack()})
            // Shared header, spacing and theme with Community.
            key(page) {
                PullToRefreshBox(isRefreshing=refreshing,onRefresh={if(!busy){refreshing=true;mutate{try{load(forceSync=true)}finally{refreshing=false}}}},modifier=Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().imePadding()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            message?.let {
                                Text(it, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { mutate { load() } }) { Text("Reload") }
                            }
                            if(offline || pendingCount>0) Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Icon(if(offline)Icons.Default.CloudOff else Icons.Default.Sync,null,Modifier.size(20.dp))
                                    Text(if(pendingCount>0) "$pendingCount change(s) saved on this device. Waiting to sync." else if(repository.hasCache) "Offline · Showing your saved list and ingredient catalogue." else "No connection. Connect once to load your list on this device.",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                                    TextButton(onClick={mutate{load(forceSync=true)}},enabled=!busy){Text("Sync")}
                                }
                            }
                            syncStatus?.let { status ->
                                Text(status,style=MaterialTheme.typography.bodySmall,color=if(pendingCount>0 && !offline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            when (page) {
                                ShoppingPage.LIST -> {
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                        ShoppingMetric(toBuy.size,"To buy",Modifier.weight(1f))
                                        ShoppingMetric(purchased.size,"Purchased",Modifier.weight(1f))
                                    }
                                    val reviewTone=if(pantryKnown && reviewCount>0)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                                    Surface(modifier=Modifier.fillMaxWidth().clickable(enabled=!busy){page=ShoppingPage.REVIEW;mutate{load()}},shape=RoundedCornerShape(16.dp),color=reviewTone) {
                                        Row(Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                            Icon(Icons.Default.ShoppingBasket,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(26.dp))
                                            Text(when {busy->"Checking pantry stock…";!pantryKnown->"Unable to check pantry stock";reviewCount>0->reviewCountLabel(reviewCount);else->"No purchase review"},Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
                                            Icon(Icons.Default.ChevronRight,"Open purchase review",Modifier.size(24.dp))
                                        }
                                    }
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("To buy", style = MaterialTheme.typography.titleLarge)
                                        Button(enabled = ready && !busy, onClick = { editingItem = ListItem(0, "", 1.0, "pcs") }) {
                                            Text("+ Add item")
                                        }
                                    }
                                    ShoppingPanel {
                                        if (ready && toBuy.isEmpty()) Text("Nothing to buy yet.")
                                        toBuy.forEachIndexed { index, item ->
                                            if (index > 0) HorizontalDivider()
                                            ShoppingRow(
                                                item = item,
                                                addedToPantry = item.id in storedIds,
                                                onToggle = {
                                                    mutate { saveItem(item.copy(purchased = it)) }
                                                },
                                                onEdit = { editingItem = item },
                                                onRemove = { removeItem(item) },
                                                onAddToPantry = {}
                                            )
                                        }
                                    }

                                    val priced=toBuy.filter{it.unitPrice!=null}
                                    if(priced.isNotEmpty()) Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                                        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                            Icon(Icons.Default.Calculate,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp))
                                            Text(String.format(Locale.US,"Est. RM %.2f · %d/%d items priced",priced.sumOf{it.quantity*(it.unitPrice?:0.0)},priced.size,toBuy.size),style=MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    Text("Purchased", style = MaterialTheme.typography.titleLarge)
                                    ShoppingPanel {
                                        if (ready && purchased.isEmpty()) Text("No purchased items yet.")
                                        purchased.forEachIndexed { index, item ->
                                            if (index > 0) HorizontalDivider()
                                            ShoppingRow(
                                                item = item,
                                                addedToPantry = item.id in storedIds,
                                                onToggle = {
                                                    mutate { saveItem(item.copy(purchased = it)) }
                                                },
                                                onEdit = { editingItem = item },
                                                onRemove = { removeItem(item) },
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
                                    stockKnown=pantryKnown, checking=busy, stale=offline || pantryReadFailed, lastUpdated=repository.pantryUpdatedAt,
                                    onEdit = { editingItem=it },
                                    onKeep = { id -> suggestions.firstOrNull { it.item.id==id }?.let {
                                        mutate { repository.rememberReview(id,it.fingerprint); reviewVersion++ }
                                    } },
                                    onChange = { item, quantity ->
                                        mutate {
                                            saveItem(item.copy(quantity = quantity,handled=false))
                                            buildReviewSuggestions(items.filterNot { it.purchased },batches).firstOrNull { it.item.id==item.id }?.let {
                                                repository.rememberReview(item.id,it.fingerprint); reviewVersion++
                                            }
                                        }
                                    },
                                    onRemove = { item -> removeItem(item) }
                                )

                                ShoppingPage.PANTRY -> selectedItem?.let { item ->
                                    PantryEditor(
                                        item = item,
                                        onCancel = ::goBack,
                                        busy=busy,
                                        onConfirm = { batch -> mutate {
                                            val storedItemId=repository.transfer(item.id,batch.name,batch.quantity,batch.unit,
                                                batch.purchasedOn!!.toString(),batch.expiry.toString(),cleanDisplay(batch.note),batch.photoUri,batch.location)
                                            storedIds=storedIds+storedItemId
                                            page=ShoppingPage.LIST
                                            onInventoryChanged()
                                            load(true)
                                        } }
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
        }
    }
    editingItem?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { if(!busy)editingItem = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (item.id == 0L) "Add item" else "Edit item",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                ShoppingEditor(
                    item = item,
                    catalog = catalog,
                    busy = busy,
                    error = message,
                    onSave = { edited ->
                        mutate {
                            saveItem(edited)
                            reviewVersion++
                            editingItem = null
                        }
                    },
                    onRemove = { removeItem(item) { editingItem = null } }
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
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Icon(if(label=="To buy")Icons.Default.ShoppingCart else Icons.Default.CheckCircle,null,tint=colors.primary,modifier=Modifier.size(20.dp))
            Text(count.toString(),style=MaterialTheme.typography.titleLarge,color=colors.primary,fontWeight=FontWeight.Bold)
            Text(label,style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant)
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
    // Explicitly removed photos stay removed; failed URLs fall back to bundled art.
    var photoLoaded by remember(item.photoUri) { mutableStateOf(false) }
    Box(Modifier.size(size.dp),contentAlignment=Alignment.Center) {
        if(!photoLoaded && item.photoUri!="" && item.imageRes!=null) Image(painterResource(item.imageRes),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
        else if(!photoLoaded) Icon(Icons.Default.Eco,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size((size*0.6).dp))
        if(!item.photoUri.isNullOrBlank()) AsyncImage(model=if(item.photoUri.startsWith("/"))java.io.File(item.photoUri) else item.photoUri,
            contentDescription=item.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit,
            onSuccess={photoLoaded=true},onError={photoLoaded=false})
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
            FoodThumbnail(item,48)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(item.name, fontWeight = FontWeight.Bold,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    "${formatQuantity(item.quantity)} ${item.unit}",
                    style = MaterialTheme.typography.bodySmall
                )

            }
            if(!item.purchased && item.unitPrice!=null) Text(String.format(Locale.US,"RM %.2f",item.quantity*item.unitPrice),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.purchased) {
                if (addedToPantry) {
                    AssistChip(onClick={},enabled=false,label={Text("Stored")},leadingIcon={Icon(Icons.Default.CheckCircle,null,Modifier.size(16.dp))})
                } else {
                    FilledTonalIconButton(
                        onClick = onAddToPantry,
                        modifier = Modifier.semantics { contentDescription = "Add ${item.name} to pantry" }
                    ) { ShoppingActionIcon(cart = true) }
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
    catalog: List<ListItem>,
    busy: Boolean,
    error: String?,
    onSave: (ListItem) -> Unit,
    onRemove: () -> Unit
) {
    var ingredient by rememberSaveable(item,stateSaver=shoppingItemSaver) {
        mutableStateOf(item.takeIf { it.name.isNotBlank() })
    }
    var quantityText by rememberSaveable { mutableStateOf(formatQuantity(item.quantity)) }
    var unit by rememberSaveable(item) {
        mutableStateOf(if (item.unit == "cartons") "carton" else item.unit)
    }
    var confirmRemove by remember { mutableStateOf(false) }
    val quantity = parseQuantity(quantityText, unit)
    val valid = ingredient != null && quantity != null && unit in shoppingUnits

    var priceText by rememberSaveable { mutableStateOf(item.unitPrice?.let{String.format(Locale.US,"%.2f",it)} ?: "") }
    val price=parseUnitPrice(priceText)
    val priceValid=priceText.isBlank() || price!=null
    ingredient?.let { selected ->
        ShoppingPhotoEditor(selected) { ingredient = selected.copy(photoUri = it) }
    }
    ShoppingIngredientPicker(catalog = catalog, selected = ingredient, onSelection = {
        ingredient = it
        if (it != null && item.id == 0L) unit = it.unit
    })
    ShoppingQuantityRow(quantityText, unit, { quantityText = it }, { unit = it })

    OutlinedTextField(value=priceText,onValueChange={if(it.length<=12)priceText=it},label={Text("Unit price (optional)")},prefix={Text("RM ")},suffix={Text("per $unit")},isError=!priceValid,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp))
    Text("Price is per selected unit, with up to 2 decimal places.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = valid && priceValid && !busy,
        onClick = {
            val selected = ingredient ?: return@Button
            val newQuantity = quantity ?: return@Button

            onSave(
                item.copy(
                    name = selected.name,
                    quantity = newQuantity,
                    unit = unit,
                    plannedQuantity = if (
                        item.id == 0L || item.quantity != newQuantity || item.unit != unit ||
                        item.ingredientId != selected.ingredientId ||
                        item.customIngredientId != selected.customIngredientId
                    ) newQuantity else item.plannedQuantity,
                    ingredientId = selected.ingredientId,
                    customIngredientId = selected.customIngredientId,
                    imageRes = selected.imageRes,
                    photoUri = selected.photoUri,
                    unitPrice=if(priceText.isBlank())null else price,
                    handled=if(!item.name.trim().equals(selected.name.trim(),true) || item.quantity!=newQuantity || item.unit!=unit || item.ingredientId!=selected.ingredientId || item.customIngredientId!=selected.customIngredientId) false else item.handled
                )
            )
        }
    ) {
        Text(if(busy) "Saving…" else if (item.id == 0L) "Add item" else "Save changes")
    }

    if (item.id != 0L) {
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
    val item: ListItem, val stock: Double, val buy: Double, val expiring: Double,
    val urgent: Double, val expired: Int, val batches: List<PantryBatch>,
    val differentUnit: Boolean, val possibleDuplicate: Boolean, val fingerprint: String
)
private val ReviewSuggestion.needsReview: Boolean
    get() = buy + 0.0000001 < item.quantity || differentUnit || possibleDuplicate || expiring>0 || expired>0
private fun reviewCountLabel(count: Int): String =
    "$count ${if (count == 1) "item" else "items"} worth reviewing"
private fun buildReviewSuggestions(items: List<ListItem>, batches: List<PantryBatch>): List<ReviewSuggestion> {
    val byId=items.associateBy { it.id }
    return PurchaseReviewRules.calculate(
        items.map { PurchaseReviewRules.Item(it.id,it.ingredientId,it.name,it.plannedQuantity,it.quantity,it.unit) },
        batches.map { PurchaseReviewRules.Batch(it.batchId,it.ingredientId,it.name,it.quantity,it.unit,it.expiry) },
        LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur"))
    ).map { r -> ReviewSuggestion(byId.getValue(r.item.id),r.stock,r.buy,r.soon,r.urgent,r.expired,
        r.batches.map { PantryBatch(it.name,it.quantity,it.unit,it.expiry,batchId=it.id,ingredientId=it.ingredientId) },
        r.manual,r.possibleDuplicate,r.fingerprint) }
}

@Composable
private fun ShoppingReview(
    suggestions: List<ReviewSuggestion>,
    handled: Set<Long>,
    stockKnown: Boolean, checking: Boolean, stale: Boolean, lastUpdated: Long,
    onEdit: (ListItem) -> Unit,
    onKeep: (Long) -> Unit,
    onChange: (ListItem, Double) -> Unit,
    onRemove: (ListItem) -> Unit
) {
    var showUnchanged by remember { mutableStateOf(false) }
    var removal by remember { mutableStateOf<ListItem?>(null) }
    var adjustment by remember { mutableStateOf<ReviewSuggestion?>(null) }
    val pending = suggestions.filter {
        it.item.id !in handled && it.needsReview
    }.sortedBy { it.buy == 0.0 }
    val unchanged = suggestions.filter { it !in pending && it.item.id !in handled }
    val colors = MaterialTheme.colorScheme
    val warningBackground=if(colors.background.luminance()<0.5f)Color(0xFF463719) else Color(0xFFFFF4D8)
    val warningText=if(colors.background.luminance()<0.5f)Color(0xFFFFE0A2) else Color(0xFF795410)

    Text("Compare your shopping list with usable pantry stock to avoid buying more than you need.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    if(!stockKnown) {
        Text(if(checking) "Checking your pantry…" else "Pantry stock is unavailable. Pull down to retry.")
        return
    }
    if(stale) {
        val stamp=if(lastUpdated>0) java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm",Locale.ENGLISH).withZone(ZoneId.of("Asia/Kuala_Lumpur")).format(Instant.ofEpochMilli(lastUpdated)) else "your last successful refresh"
        Text("Saved pantry snapshot from $stamp. Latest stock could not be confirmed. Check availability before changing your list.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
    }
    ShoppingPanel(tinted = true) {
        Text(if(checking) "Checking pantry…" else if (pending.isEmpty()) "No purchase review" else reviewCountLabel(pending.size),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    if(handled.isNotEmpty()) Text("${handled.size} reviewed for this list and pantry snapshot",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
    if (pending.isNotEmpty()) {
        Text("Suggested changes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
    pending.forEach { suggestion ->
        val item=suggestion.item
        key(item.id) {
            var showBatches by rememberSaveable { mutableStateOf(false) }
            Surface(shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,colors.outlineVariant),color=colors.surface) {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        FoodThumbnail(item,76)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Text(item.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                            Surface(shape=RoundedCornerShape(10.dp),color=colors.primaryContainer) {
                                Text(when {
                                    suggestion.differentUnit -> "Check package sizes and units"
                                    suggestion.buy==0.0 -> "You may be able to remove this item"
                                    suggestion.buy + 0.0000001 < item.quantity -> "Keep only ${reviewAmount(suggestion.buy,item.unit)} on your list"
                                    suggestion.possibleDuplicate -> "Check a possible duplicate ingredient"
                                    suggestion.expiring>0 -> "Use your pantry stock first"
                                    else -> "Expired stock is excluded"
                                },
                                    Modifier.padding(9.dp,6.dp),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,color=colors.onPrimaryContainer)
                            }
                            Text("Planned: ${reviewAmount(item.plannedQuantity,item.unit)} · Available: ${reviewAmount(suggestion.stock,item.unit)}",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
                            if(!suggestion.differentUnit) Text("Suggested list amount: ${reviewAmount(suggestion.buy,item.unit)}",style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold)
                            if(suggestion.stock>item.plannedQuantity) Text("${reviewAmount(suggestion.stock-item.plannedQuantity,item.unit)} more than this plan needs. Use existing stock to avoid waste.",style=MaterialTheme.typography.bodySmall,color=colors.primary)
                            if(suggestion.differentUnit) Text("Some pantry units cannot be converted. Check stock manually.",style=MaterialTheme.typography.bodySmall)
                            if(suggestion.possibleDuplicate) Text("A similar name has a different ingredient ID. It has not been counted.",style=MaterialTheme.typography.bodySmall)
                            if(suggestion.expired>0) Text("${suggestion.expired} expired batch(es) excluded.",style=MaterialTheme.typography.bodySmall,color=colors.error)
                        }
                    }
                    if(suggestion.expiring>0) Surface(shape=RoundedCornerShape(10.dp),color=if(suggestion.urgent>0)colors.errorContainer else warningBackground) {
                        Row(Modifier.padding(10.dp),horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.Top) {
                            val tone=if(suggestion.urgent>0)colors.onErrorContainer else warningText
                            Icon(if(suggestion.urgent>0)Icons.Default.WarningAmber else Icons.Default.Schedule,null,Modifier.size(18.dp),tint=tone)
                            Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                Text(if(suggestion.urgent>0) "Use first · stock expires today or tomorrow" else "Use first · stock expires within 3 days",style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold,color=tone)
                                Text("${reviewAmount(suggestion.expiring,item.unit)} expires within 3 days.${if(suggestion.urgent>0) " ${reviewAmount(suggestion.urgent,item.unit)} expires today or tomorrow." else ""} Reduce buying only if you can use it in time.",style=MaterialTheme.typography.bodySmall,color=tone)
                            }
                        }
                    }
                    if(suggestion.batches.isNotEmpty()) {
                        FilledTonalButton(onClick={showBatches=!showBatches},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.Inventory2,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp))
                            Text("${suggestion.batches.size} pantry batches",Modifier.weight(1f));ShoppingActionIcon(expanded=showBatches)
                        }
                        if(showBatches) suggestion.batches.forEach { batch ->
                            Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            if(batch.ingredientId!=item.ingredientId) Text("${batch.name} · different ingredient ID (not counted)",style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("${formatQuantity(batch.quantity)} ${batch.unit}",style=MaterialTheme.typography.bodySmall);Text(if(batch.expiry.isBefore(LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")))) "Expired · ${batch.expiry}" else "Expires ${batch.expiry}",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant) }
                            }
                        }
                    }
                    if(suggestion.differentUnit || suggestion.possibleDuplicate) {
                        OutlinedButton(onClick={onEdit(item)},enabled=!checking,modifier=Modifier.fillMaxWidth()) { Text("Edit quantity or ingredient") }
                    }
                    if(!suggestion.differentUnit && suggestion.buy + 0.0000001 < item.quantity) {
                        Button(onClick={if(suggestion.buy==0.0)removal=item else adjustment=suggestion},enabled=!checking,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) {
                            Text(if(suggestion.buy==0.0) "Remove from list" else "Change list to ${reviewAmount(suggestion.buy,item.unit)}")
                        }
                    }
                    OutlinedButton(onClick={onKeep(item.id)},enabled=!checking,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) { Text("Keep current amount") }
                }
            }
        }
    }
    if(pending.isEmpty() && !checking)Text("No changes are suggested based on your current shopping list and usable pantry stock.",style=MaterialTheme.typography.bodyMedium,color=colors.onSurfaceVariant)
    adjustment?.let { suggestion ->
        val item = suggestion.item
        AlertDialog(
            onDismissRequest = { adjustment = null },
            title = { Text("Buy fewer ${item.name.lowercase(Locale.ROOT)}?") },
            text = {
                Text("Reduce the shopping list from ${formatQuantity(item.quantity)} ${item.unit} to ${reviewAmount(suggestion.buy,item.unit)}. Your plan stays at ${formatQuantity(item.plannedQuantity)} ${item.unit}. Use the pantry stock before it expires to avoid waste.")
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
    busy: Boolean = false,
    onConfirm: (PantryBatch) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(item.name) }
    var photo by rememberSaveable { mutableStateOf(item.photoUri) }
    var quantityText by rememberSaveable { mutableStateOf(formatQuantity(item.quantity)) }
    var unit by rememberSaveable { mutableStateOf(item.unit) }
    var purchasedOnText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var location by rememberSaveable { mutableStateOf("Pantry") }
    var expiryText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    val quantity = parseQuantity(quantityText, unit)
    val purchasedOn = parseDate(purchasedOnText)
    val expiry = parseDate(expiryText)
    val validPurchaseDate = purchasedOn != null && !purchasedOn.isAfter(LocalDate.now())
    val validExpiry = expiry != null && purchasedOn != null && !expiry.isBefore(purchasedOn)
    val valid = name.isNotBlank() && quantity != null &&
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
        StorageLocationField(location) { location=it }
        OutlinedTextField(
            value = note,
            onValueChange = { input ->
                var breaks=0
                val normalized=cleanInput(input,150).map{character->if(character=='\n' && ++breaks>2)' ' else character}.joinToString("")
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
        enabled = valid && !busy,
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
                        location = location,
                        sourceItemId = item.id,
                        imageRes = item.imageRes,
                        photoUri = photo
                    )
                )
            }
        }
    ) {
        Text(if(busy) "Adding…" else "Confirm & add")
    }
    OutlinedButton(onClick = onCancel, enabled=!busy, modifier = Modifier.fillMaxWidth()) {
        Text("Cancel")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingQuantityRow(
    quantity: String,
    unit: String,
    onQuantity: (String) -> Unit,
    onUnit: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutlinedTextField(
                value = quantity,
                onValueChange = { if (it.length <= 12 && it.matches(if(unit in measuredUnits) Regex("[0-9]*([.,][0-9]{0,3})?") else Regex("[0-9]*"))) onQuantity(it) },
                modifier = Modifier.weight(1f),
                shape=RoundedCornerShape(12.dp),
                label = { Text("Quantity") },
                singleLine = true,
                isError = quantity.isNotEmpty() && parseQuantity(quantity, unit) == null,
                keyboardOptions = KeyboardOptions(keyboardType = if(unit in measuredUnits)KeyboardType.Decimal else KeyboardType.Number)
            )
            ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=it},modifier=Modifier.weight(1f)) {
                OutlinedTextField(value=unit,onValueChange={},readOnly=true,label={Text("Unit")},singleLine=true,
                    trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded=expanded)},shape=RoundedCornerShape(12.dp),
                    modifier=Modifier.menuAnchor(type = androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                ExposedDropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                    shoppingUnits.forEach { option -> DropdownMenuItem(text={Text(option)},onClick={expanded=false;onUnit(option)}) }
                }
            }
        }
        if(quantity.isNotEmpty() && parseQuantity(quantity,unit)==null) {
            Text(shoppingQuantityHint(unit),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingCalendar(label:String,date:LocalDate?,earliest:LocalDate?=null,latest:LocalDate?=null,onDate:(LocalDate)->Unit) {
    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    fun utc(day:LocalDate)=day.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    fun day(value:Long)=Instant.ofEpochMilli(value).atZone(java.time.ZoneOffset.UTC).toLocalDate()
    Column {
        Text(label,style=MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick={calendarOpen=true},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.CalendarMonth,null,Modifier.size(20.dp));Spacer(Modifier.width(10.dp))
            Text(date?.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy",Locale.ENGLISH)) ?: "Select date",Modifier.weight(1f))
            ShoppingActionIcon(expanded=calendarOpen)
        }
    }
    if(calendarOpen) {
        val picker=rememberDatePickerState(initialSelectedDateMillis=utc(date ?: LocalDate.now()),selectableDates=object:SelectableDates {
            override fun isSelectableDate(utcTimeMillis:Long):Boolean {
                val selected=day(utcTimeMillis)
                return (earliest==null||!selected.isBefore(earliest)) && (latest==null||!selected.isAfter(latest))
            }
        })
        DatePickerDialog(onDismissRequest={calendarOpen=false},confirmButton={
            TextButton(enabled=picker.selectedDateMillis!=null && picker.selectableDates.isSelectableDate(picker.selectedDateMillis ?: 0L),onClick={picker.selectedDateMillis?.let{onDate(day(it))};calendarOpen=false}) { Text("OK") }
        },dismissButton={TextButton(onClick={calendarOpen=false}){Text("Cancel")}}) { DatePicker(state=picker) }
    }
}

@Composable
private fun ShoppingPhotoEditor(item: ListItem, onPhotoChange: (String?) -> Unit) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val photoScope=rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null)photoScope.launch {
            try {
                val path= CommunityRepository().importImage(context,uri)
                onPhotoChange(Uri.fromFile(java.io.File(path)).toString());failed=false
            } catch(cancelled:CancellationException){throw cancelled} catch(error:Exception){failed=true}
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
                Icon(Icons.Default.PhotoLibrary,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp))
                Text(if (item.photoUri == null && item.imageRes == null) "Add photo" else "Change")
            }
        }
        if (item.photoUri != null || item.imageRes != null) {
            TextButton(onClick = { failed = false; onPhotoChange("") }) {
                Icon(Icons.Default.DeleteOutline,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text("Remove photo")
            }
        }
        if (failed) Text("Could not keep access to this photo.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ShoppingIngredientPicker(
    catalog: List<ListItem>,
    selected: ListItem?,
    onSelection: (ListItem?) -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf(selected?.name.orEmpty()) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var creatingCustom by rememberSaveable { mutableStateOf(false) }
    var customName by rememberSaveable { mutableStateOf("") }
    var customPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var photoError by rememberSaveable { mutableStateOf(false) }

    val photoScope=rememberCoroutineScope()
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null)photoScope.launch {
            try { customPhoto=Uri.fromFile(java.io.File(CommunityRepository().importImage(context,uri))).toString();photoError=false } catch(cancelled:CancellationException){throw cancelled} catch(error:Exception){photoError=true}
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
                    ShoppingActionIcon(expanded = expanded)
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
                        Icon(Icons.Default.PhotoLibrary,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp))
                        Text(if (customPhoto == null) "Add photo (optional)" else "Change photo")
                    }
                    if (customPhoto != null) {
                        ShoppingCustomPhoto(customPhoto, Modifier.size(56.dp))
                        TextButton(onClick = { customPhoto = null }) {
                            Icon(Icons.Default.DeleteOutline,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text("Remove photo")
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
                        onSelection(ListItem(0, name, 1.0, "pcs", photoUri = customPhoto))
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
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
        Icon(Icons.Default.Eco,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
        if(!photoUri.isNullOrBlank()) AsyncImage(model=photoUri,contentDescription=null,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize())
    }
}

@Composable
private fun ShoppingActionIcon(cart: Boolean = false, expanded: Boolean = false) {
    val tint = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val scale = size.width / 24f
        fun segment(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, Offset(x1 * scale, y1 * scale), Offset(x2 * scale, y2 * scale),
                strokeWidth = 1.8f * scale, cap = StrokeCap.Round)
        }
        if (cart) {
            segment(2f, 4f, 5f, 4f)
            segment(5f, 4f, 8f, 16f)
            segment(8f, 16f, 19f, 16f)
            segment(6f, 7f, 21f, 7f)
            segment(21f, 7f, 19f, 13f)
            segment(19f, 13f, 7.5f, 13f)
            drawCircle(tint, 1.3f * scale, Offset(9f * scale, 20f * scale))
            drawCircle(tint, 1.3f * scale, Offset(18f * scale, 20f * scale))
        } else {
            val outer = if (expanded) 15f else 9f
            val middle = if (expanded) 9f else 15f
            segment(6f, outer, 12f, middle)
            segment(12f, middle, 18f, outer)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StorageLocationField(value:String,onChange:(String)->Unit) {
 var expanded by remember { mutableStateOf(false) }
 ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=it}) {
  OutlinedTextField(value,{},Modifier.menuAnchor(type = androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),readOnly=true,label={Text("Storage location")},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded=expanded)})
  ExposedDropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {listOf("Pantry","Fridge","Freezer").forEach { location->DropdownMenuItem(text={Text(location)},onClick={onChange(location);expanded=false})} }
 }
}
