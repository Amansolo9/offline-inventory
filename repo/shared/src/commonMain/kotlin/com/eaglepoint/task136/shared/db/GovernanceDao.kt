package com.eaglepoint.task136.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GovernanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun logRuleHit(metric: RuleHitMetricEntity)

    @Query("SELECT * FROM rule_hits WHERE resolved = 0 ORDER BY createdAt DESC")
    fun observeOpenRuleHits(): Flow<List<RuleHitMetricEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLedger(entry: DailyLedgerEntity)

    @Query("SELECT * FROM daily_ledger WHERE businessDate = :businessDate LIMIT 1")
    suspend fun getLedgerByDate(businessDate: String): DailyLedgerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createDiscrepancy(ticket: DiscrepancyTicketEntity)
}
