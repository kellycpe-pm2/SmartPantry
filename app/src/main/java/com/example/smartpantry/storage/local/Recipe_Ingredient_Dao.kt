package com.example.smartpantry.storage.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface Recipe_IngredientDao {

    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :recipeId")
    fun getIngredientsForRecipe(recipeId: Long): Flow<List<RecipeIngredientEntity>>

    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :recipeId")
    suspend fun getIngredientsForRecipeOnce(recipeId: Long): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_ingredient")
    suspend fun getAll(): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_ingredient WHERE id = :id")
    suspend fun getById(id: Long): RecipeIngredientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecipeIngredientEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RecipeIngredientEntity>)



    @Update
    suspend fun update(entity: RecipeIngredientEntity)

    @Query("DELETE FROM recipe_ingredient WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recipe_ingredient WHERE recipeId = :recipeId")
    suspend fun deleteByRecipe(recipeId: Long)

    @Transaction
    suspend fun rekeyIngredients(oldRecipeId: Long, newRecipeId: Long) {
        getIngredientsForRecipeOnce(oldRecipeId).forEach {
            update(it.copy(recipeId = newRecipeId))
        }
    }

    @Transaction
    suspend fun replaceId(oldId: Long, newEntity: RecipeIngredientEntity) {
        deleteById(oldId)
        insert(newEntity)
    }
}