package com.example.smartpantry.storage.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientUserDao {

    @Query("SELECT * FROM ingredient_user WHERE user_id = :userId ORDER BY expiredDate ASC")
    fun observeByUser(userId: String): Flow<List<IngredientUserEntity>>

    @Query("SELECT * FROM ingredient_user ORDER BY expiredDate ASC")
    fun observeAll(): Flow<List<IngredientUserEntity>>

    @Query("SELECT * FROM ingredient_user WHERE user_id = :userId ORDER BY expiredDate ASC")
    suspend fun getAllByUser(userId: String): List<IngredientUserEntity>

    @Query("SELECT * FROM ingredient_user ORDER BY expiredDate ASC")
    suspend fun getAll(): List<IngredientUserEntity>

    @Query("SELECT * FROM ingredient_user WHERE IngredientUser_Id = :id")
    suspend fun getById(id: Long): IngredientUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: IngredientUserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<IngredientUserEntity>)

    @Query("DELETE FROM ingredient_user WHERE IngredientUser_Id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ingredient_user WHERE user_id IS NOT NULL AND user_id != :activeUserId")
    suspend fun deleteOtherUsers(activeUserId: String)
}