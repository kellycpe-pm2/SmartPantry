package com.example.smartpantry.storage.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredient ORDER BY createAt DESC")
    fun observeAll(): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredient WHERE id = :id")
    suspend fun getById(id: Long): IngredientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: IngredientEntity): Long

    @Delete
    suspend fun delete(entity: IngredientEntity)

    @Query("DELETE FROM ingredient")
    suspend fun clear()
}