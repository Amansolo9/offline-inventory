package com.eaglepoint.task136.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CartItemEntity)

    @Update
    suspend fun update(item: CartItemEntity)

    @Query("SELECT * FROM cart_items WHERE userId = :userId ORDER BY id ASC")
    suspend fun getByUser(userId: String): List<CartItemEntity>

    @Query("SELECT * FROM cart_items WHERE userId = :userId ORDER BY id ASC")
    fun observeByUser(userId: String): Flow<List<CartItemEntity>>

    @Query("SELECT * FROM cart_items WHERE id = :id")
    suspend fun getById(id: String): CartItemEntity?

    @Query("DELETE FROM cart_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cart_items WHERE userId = :userId")
    suspend fun clearForUser(userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CartItemEntity>)
}
