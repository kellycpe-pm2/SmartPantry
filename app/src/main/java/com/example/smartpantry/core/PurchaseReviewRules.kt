package com.example.smartpantry.core

import java.time.LocalDate
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ceil

/** Deterministic stock comparison. Never guesses the contents of bags or cartons. */
internal object PurchaseReviewRules {
    data class Item(val id: Long, val ingredientId: Long?, val name: String, val planned: Double, val buying: Double, val unit: String)
    data class Batch(val id: Long, val ingredientId: Long?, val name: String, val quantity: Double, val unit: String, val expiry: LocalDate)
    data class Result(val item: Item, val stock: Double, val buy: Double, val soon: Double,
        val urgent: Double, val expired: Int, val manual: Boolean, val possibleDuplicate: Boolean,
        val batches: List<Batch>, val fingerprint: String) {
        val needsReview get() = buy + 0.0000001 < item.buying || soon > 0 || manual || possibleDuplicate || expired > 0
    }
    fun unit(value: String): String = when(value.trim().lowercase(Locale.ROOT)) {
        "piece", "pieces", "pc", "pcs" -> "pcs"
        "bags" -> "bag"; "cartons" -> "carton"; "ml" -> "mL"; "l" -> "L"
        else -> value.trim().lowercase(Locale.ROOT)
    }
    private fun dimension(value: String) = when(unit(value)) { "kg", "g" -> "g"; "L", "mL" -> "mL"; else -> unit(value) }
    private fun scale(value: String) = if(unit(value) in listOf("kg", "L")) 1000.0 else 1.0
    fun convert(quantity: Double, from: String, to: String): Double? =
        if(dimension(from) == dimension(to) && unit(from).isNotBlank()) quantity * scale(from) / scale(to) else null
    private fun name(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    fun calculate(items: List<Item>, batches: List<Batch>, today: LocalDate = LocalDate.now()): List<Result> {
        val reserved = mutableMapOf<Pair<Long, String>, Double>()
        return items.sortedBy { it.id }.map { item ->
            val exact = batches.filter { item.ingredientId != null && it.ingredientId == item.ingredientId && it.quantity.isFinite() && it.quantity > 0 }
            val duplicates = batches.filter { name(it.name) == name(item.name) && it.ingredientId != item.ingredientId && it.quantity.isFinite() && it.quantity > 0 && !it.expiry.isBefore(today) }
            val live = exact.filter { !it.expiry.isBefore(today) }
            val comparable = live.mapNotNull { b -> convert(b.quantity,b.unit,item.unit)?.let { b.copy(quantity=it,unit=item.unit) } }.sortedWith(compareBy({it.expiry},{it.id}))
            val key = (item.ingredientId ?: Long.MIN_VALUE) to dimension(item.unit)
            var used = (reserved[key] ?: 0.0) / scale(item.unit)
            val allocated = comparable.mapNotNull { b ->
                val consumed = minOf(used,b.quantity); used -= consumed
                (b.quantity-consumed).takeIf { it > 0.0000001 }?.let { b.copy(quantity=it) }
            }
            val stock = allocated.sumOf { it.quantity }
            reserved[key] = (reserved[key] ?: 0.0) + minOf(item.planned,stock) * scale(item.unit)
            val missing = (item.planned-stock).coerceAtLeast(0.0)
            val rounded = if(unit(item.unit) in listOf("g","kg","mL","L")) ceil((missing-0.00000001).coerceAtLeast(0.0)*1000)/1000 else ceil((missing-0.00000001).coerceAtLeast(0.0))
            val manual = live.any { convert(it.quantity,it.unit,item.unit)==null }
            // An unknown package size prevents a numerical recommendation.
            val buy = if(manual) item.buying else minOf(item.buying,rounded)
            val signature = buildString {
                append(today);append('|');append(item);append('|');append(items.sortedBy { it.id })
                batches.filter { it.ingredientId==item.ingredientId || name(it.name)==name(item.name) }.sortedWith(compareBy({it.id},{it.expiry},{it.unit},{it.quantity})).forEach { append('|');append(it) }
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
            Result(item,stock,buy,allocated.filter { !it.expiry.isAfter(today.plusDays(3)) }.sumOf { it.quantity },
                allocated.filter { !it.expiry.isAfter(today.plusDays(1)) }.sumOf { it.quantity },exact.count { it.expiry.isBefore(today) },manual,duplicates.isNotEmpty(),(exact+duplicates).sortedBy { it.expiry },hash)
        }
    }
}
