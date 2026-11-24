package com.example.smartpantry.storage.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipe WHERE userId = :userId ORDER BY createAt DESC")
    fun observeByUser(userId: String): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe ORDER BY createAt DESC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun getById(id: Long): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecipeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RecipeEntity>)

    @Update
    suspend fun update(entity: RecipeEntity)

    @Query("DELETE FROM recipe WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recipe WHERE userId IS NOT NULL AND userId != :activeUserId")
    suspend fun deleteOtherUsers(activeUserId: String)

    @Transaction
    suspend fun replaceId(oldId: Long, newEntity: RecipeEntity) {
        deleteById(oldId)
        insert(newEntity)
    }
}