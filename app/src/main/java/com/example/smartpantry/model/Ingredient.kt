package com.example.smartpantry.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Ingredient(
    @SerialName("ingredientId")
    val ingredientId: Long? = null,

    @SerialName("ingredientName")
    val ingredientName: String,

    @SerialName("weight")
    val weight: Double? = null,

    @SerialName("unit")
    val unit: String? = null,

    @SerialName("image")
    val image: String? = null,

    @SerialName("description")
    val description: String? = null,

    @SerialName("category")
    val category: String? = null,

    @SerialName("min_expired_duration")
    val minExpiredDuration: Short? = null,

    @SerialName("max_expired_duration")
    val maxExpiredDuration: Short? = null,

    @SerialName("create_at")
    val createAt: Long? = null,

    @SerialName("update_at")
    val updateAt: Long? = null,
)