package com.example.smartpantry.storage.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {

    @Query("SELECT * FROM ingredient WHERE isPublish = 1 OR userId = :userId ORDER BY id DESC")
    fun observeForUser(userId: String): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredient ORDER BY id DESC")
    fun observeAll(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredient WHERE isPublish = 1 OR userId = :userId ORDER BY id DESC")
    suspend fun getAllForUser(userId: String): List<IngredientEntity>

    @Query("SELECT * FROM ingredient ORDER BY id DESC")
    suspend fun getAll(): List<IngredientEntity>

    @Query("SELECT * FROM ingredient WHERE id = :id")
    suspend fun getById(id: Long): IngredientEntity?

    @Query("SELECT * FROM ingredient WHERE ingredientName LIKE '%' || :query || '%'")
    suspend fun searchByName(query: String): List<IngredientEntity>

    @Query("SELECT * FROM ingredient WHERE category = :category")
    suspend fun getByCategory(category: String): List<IngredientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: IngredientEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<IngredientEntity>)

    @Delete
    suspend fun delete(entity: IngredientEntity)

    @Query("DELETE FROM ingredient WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ingredient WHERE (isPublish IS NULL OR isPublish = 0) AND userId != :activeUserId")
    suspend fun deleteOtherUsersPrivate(activeUserId: String)

    @Query("DELETE FROM ingredient")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(entities: List<IngredientEntity>) {
        clear()
        insertAll(entities)
    }
}