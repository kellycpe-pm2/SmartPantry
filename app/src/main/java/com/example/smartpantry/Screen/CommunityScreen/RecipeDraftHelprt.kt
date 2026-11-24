package com.example.smartpantry.Screen.CommunityScreenpackage

import com.example.smartpantry.community.DraftIngredient
import com.example.smartpantry.community.RecipeDraft
import com.example.smartpantry.community.long
import com.example.smartpantry.community.str
import kotlinx.serialization.json.JsonObject



/** Curated templates, explicitly labelled in the UI. No network model or invented AI result. */
internal object RecipeDraftHelper {
    data class Need(val names: List<String>, val quantity: String, val unit: String)
    data class Template(val title: String, val aliases: List<String>, val ingredients: List<Need>, val steps: List<String>, val vegan: Boolean = false)
    private fun need(q: String, unit: String, vararg names: String) = Need(names.toList(), q, unit)
    val templates = listOf(
        Template("Nasi Goreng", listOf("fried rice", "炒饭", "nasi goreng kampung"), listOf(need("300","g","White Rice","Rice"),need("2","pcs","Eggs","Egg"),need("50","g","Onion")), listOf("Use cooked rice. Chop the onion and beat the eggs.","In a non-stick pan, cook the onion with a splash of water until softened. Add the eggs and scramble until set.","Add the cooked rice. Stir-fry until piping hot throughout, then serve.")),
        Template("Tomato Egg Stir-fry", listOf("tomato eggs", "stir fried tomato and eggs", "番茄炒蛋", "西红柿炒鸡蛋"), listOf(need("250","g","Tomato","Tomatoes"),need("3","pcs","Eggs","Egg")), listOf("Cut the tomatoes into wedges and beat the eggs.","Scramble the eggs in a non-stick pan until set, then transfer to a plate.","Cook the tomatoes with a splash of water until juicy. Return the eggs, mix gently and heat through.")),
        Template("Ayam Kicap", listOf("soy sauce chicken", "酱油鸡"), listOf(need("400","g","Chicken Breast","Chicken Thigh"),need("60","g","Onion"),need("30","mL","Soy Sauce","Light Soy Sauce")), listOf("Cut the chicken into even pieces and slice the onion. Keep raw chicken separate from other food.","Place the chicken, onion, soy sauce and a little water in a pan. Bring to a simmer.","Cover and simmer until the chicken is fully cooked; use a food thermometer to check the centre. Uncover to reduce the sauce, then serve.")),
        Template("Sayur Campur", listOf("mixed vegetables", "mixed vegetable", "杂菜", "清炒杂菜"), listOf(need("120","g","Carrot"),need("150","g","Broccoli"),need("150","g","Cauliflower")), listOf("Wash the vegetables. Slice the carrot and cut the broccoli and cauliflower into small florets.","Steam or simmer the carrot until it starts to soften, then add the remaining vegetables.","Cook until tender but still crisp. Drain and serve."), true),
        Template("Onion Omelette", listOf("omelette", "omelet", "telur dadar", "洋葱煎蛋", "煎蛋"), listOf(need("3","pcs","Eggs","Egg"),need("40","g","Onion")), listOf("Finely chop the onion and beat the eggs.","Soften the onion with a splash of water in a non-stick pan. Let excess water evaporate.","Pour in the eggs and cook gently until fully set. Fold and serve.")),
        Template("Chicken Fried Rice", listOf("nasi goreng ayam", "鸡肉炒饭"), listOf(need("300","g","White Rice","Rice"),need("150","g","Chicken Breast"),need("2","pcs","Eggs","Egg")), listOf("Use cooked rice. Dice the chicken and beat the eggs; keep raw chicken separate.","Cook the chicken in a non-stick pan with a splash of water until fully cooked. Add the eggs and scramble until set.","Add the rice and stir until everything is piping hot throughout. Serve immediately.")),
        Template("Tomato Pasta", listOf("tomato spaghetti", "番茄意大利面", "番茄意面"), listOf(need("180","g","Spaghetti","Pasta"),need("300","g","Tomato","Tomatoes"),need("50","g","Onion")), listOf("Cook the pasta in water according to the packet instructions.","Chop the onion and tomatoes. Simmer them with a little water until soft and saucy.","Drain the pasta, combine with the sauce and heat through."), true),
        Template("Banana Oatmeal", listOf("banana oats", "porridge oats", "香蕉燕麦粥"), listOf(need("80","g","Oats","Rolled Oats"),need("250","mL","Fresh Milk","Milk"),need("1","pcs","Banana")), listOf("Slice the banana and measure the oats and milk.","Simmer the oats in the milk, stirring often, until cooked according to the packet instructions.","Stir in the banana slices and serve warm."))
    )
    fun key(value: String): String = value.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
    fun find(name: String): Template? = templates.firstOrNull { template -> (template.aliases + template.title).any { key(it) == key(name.trim()) } }
    fun create(name: String, catalog: List<JsonObject>): RecipeDraft {
        val template = find(name) ?: error("No built-in recipe matches this name. Try a suggestion, choose a published community recipe, or create your own recipe.")
        val missing = template.ingredients.filter { need -> catalog.none { row -> need.names.any { key(it) == key(row.str("name")) } } }
        require(missing.isEmpty()) { "Your ingredient catalogue is missing: ${missing.joinToString { it.names.first() }}. Add those ingredients first or edit a recipe manually." }
        return RecipeDraft(
            title = template.title, category = when (template.title) {
                "Tomato Pasta" -> "Western"; "Banana Oatmeal" -> "Other"; "Tomato Egg Stir-fry", "Onion Omelette" -> "Asian"; else -> "Malaysian"
            }, vegan = template.vegan,
            description = "Editable recipe template. Adjust quantities and cooking steps to your preference.",
            source = "Built-in recipe template · Review portions, ingredients and steps before saving.",
            ingredients = template.ingredients.map { need ->
                val row =
                    catalog.first { item -> need.names.any { key(it) == key(item.str("name")) } }
                DraftIngredient(
                    row.long("id"),
                    row.str("name"),
                    need.quantity,
                    need.unit,
                    row.str("image")
                )
            }, steps = template.steps
        )
    }
}