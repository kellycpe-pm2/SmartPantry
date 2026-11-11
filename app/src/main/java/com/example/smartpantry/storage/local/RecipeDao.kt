package com.example.smartpantry.storage.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipe ORDER BY createAt DESC")
    fun observeAll(): Flow<List<RecipeEntity>>

    // 👇 ADDED: Needed for the background image uploader
    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun getById(id: Long): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecipeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RecipeEntity>)

    @Query("DELETE FROM recipe WHERE id = :id")
    suspend fun deleteById(id: Long)
}