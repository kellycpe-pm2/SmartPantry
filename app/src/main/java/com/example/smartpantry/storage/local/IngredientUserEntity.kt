package com.example.smartpantry.storage.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.models.IngredientUser

@Entity(tableName = "ingredient_user")
data class IngredientUserEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "IngredientUser_Id") val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: String? = null,
    val quantity: Double? = null,
    @ColumnInfo(name = "quantity_unit") val quantityUnit: String? = null,
    @ColumnInfo(name = "location_storage") val locationStorage: String? = null,
    @ColumnInfo(name = "expiredDate") val expiredDate: Long,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "ingredientId") val ingredientId: Long? = null
)

fun IngredientUserEntity.toModel() = IngredientUser(
    id = id,
    createdAt = createdAt,
    quantity = quantity,
    quantityUnit = quantityUnit,
    locationStorage = locationStorage,
    expiredDate = expiredDate,
    userId = userId,
    ingredientId = ingredientId
)

fun IngredientUser.toEntity() = IngredientUserEntity(
    id = id ?: 0,
    createdAt = createdAt,
    quantity = quantity,
    quantityUnit = quantityUnit,
    locationStorage = locationStorage,
    expiredDate = expiredDate,
    userId = userId,
    ingredientId = ingredientId
)