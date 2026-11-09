package com.example.smartpantry.storage.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ingredient")
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ingredientName: String,
    val category: String?,
    val weight: Double?,
    val unit: String?,
    val description: String?,
    val image: String?,
    val minExpiredDuration: Short?,
    val maxExpiredDuration: Short?,
    val createAt: Long,
    val updateAt: Long,
)