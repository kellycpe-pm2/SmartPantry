package com.example.smartpantry.storage.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ingredient")
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ingredientName: String,
    val weight: Double? = null,
    val unit: String? = null,
    val image: String? = null,
    val localImagePath: String? = null,
    val description: String? = null,
    val category: String? = null,
    val minExpiredDuration: Short? = null,
    val maxExpiredDuration: Short? = null,
    val createAt: Long? = null,
    val updateAt: Long? = null,
    val isPublish: Boolean? = null,
    val userId: String? = null
)
