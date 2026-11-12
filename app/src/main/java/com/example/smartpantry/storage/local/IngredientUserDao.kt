package com.example.smartpantry.storage.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientUserDao {
    @Query("SELECT * FROM ingredient_user ORDER BY expiredDate ASC")
    fun observeAll(): Flow<List<IngredientUserEntity>>

    @Query("SELECT * FROM ingredient_user WHERE IngredientUser_Id = :id")
    suspend fun getById(id: Long): IngredientUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: IngredientUserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<IngredientUserEntity>)

    @Query("DELETE FROM ingredient_user WHERE IngredientUser_Id = :id")
    suspend fun deleteById(id: Long)
}