package com.eaglepoint.task136.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeviceBindingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(binding: DeviceBindingEntity)

    @Query("SELECT * FROM device_bindings WHERE userId = :userId ORDER BY boundAt ASC")
    suspend fun getByUserId(userId: String): List<DeviceBindingEntity>

    @Query("SELECT COUNT(*) FROM device_bindings WHERE userId = :userId")
    suspend fun countByUserId(userId: String): Int

    @Query("DELETE FROM device_bindings WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT * FROM device_bindings WHERE userId = :userId AND deviceFingerprint = :fingerprint LIMIT 1")
    suspend fun findByUserAndDevice(userId: String, fingerprint: String): DeviceBindingEntity?
}
