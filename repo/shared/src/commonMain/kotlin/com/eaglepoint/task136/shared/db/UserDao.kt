package com.eaglepoint.task136.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countAll(): Int

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY fullName ASC")
    suspend fun getAllActive(): List<UserEntity>
}
