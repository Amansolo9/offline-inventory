package com.eaglepoint.task136.shared.governance

import androidx.room.withTransaction
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.db.DailyLedgerEntity
import com.eaglepoint.task136.shared.db.DiscrepancyTicketEntity
import com.eaglepoint.task136.shared.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

private const val TAG = "ReconciliationService"

data class SettlementConfig(
    val settlementDay: DayOfWeek = DayOfWeek.FRIDAY,
    val settlementHour: Int = 18,
    val enableDailyClosure: Boolean = true,
)

class ReconciliationService(
    private val database: AppDatabase,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val config: SettlementConfig = SettlementConfig(),
) {
    private suspend fun computeLedgerTotals(businessDate: String): Pair<Double, Double> {
        val date = LocalDate.parse(businessDate)
        val startOfDay = date.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val endOfDay = startOfDay + 86_400_000L

        val gross = database.orderDao().sumGrossByDateRange(startOfDay, endOfDay)
        val refunds = database.orderDao().sumRefundsByDateRange(startOfDay, endOfDay)
        val net = gross - refunds
        return gross to net
    }

    suspend fun runDailyClosureIfDue() = withContext(Dispatchers.IO) {
        if (!config.enableDailyClosure) return@withContext
        val now = clock.now().toLocalDateTime(timeZone)
        if (now.time.hour < 23) return@withContext

        val businessDate = now.date.toString()
        database.withTransaction {
            val (gross, net) = computeLedgerTotals(businessDate)
            val ledger = database.governanceDao().getLedgerByDate(businessDate)
            if (ledger != null && !ledger.isClosed) {
                database.governanceDao().upsertLedger(
                    ledger.copy(
                        grossTotal = gross,
                        netTotal = net,
                        isClosed = true,
                        closedAt = clock.now().toEpochMilliseconds(),
                    ),
                )
                AppLogger.i(TAG, "Daily ledger closed for $businessDate (gross=$gross, net=$net)")
            } else if (ledger == null) {
                database.governanceDao().upsertLedger(
                    DailyLedgerEntity(
                        businessDate = businessDate,
                        grossTotal = gross,
                        netTotal = net,
                        closedAt = clock.now().toEpochMilliseconds(),
                        isClosed = true,
                    ),
                )
                AppLogger.i(TAG, "Daily ledger created and closed for $businessDate (gross=$gross, net=$net)")
            }
        }
    }

    suspend fun runSettlementIfDue() = withContext(Dispatchers.IO) {
        val now = clock.now().toLocalDateTime(timeZone)
        val isSettlementTime = now.date.dayOfWeek == config.settlementDay && now.time.hour >= config.settlementHour
        if (!isSettlementTime) return@withContext

        val businessDate = now.date.toString()
        database.withTransaction {
            val (gross, net) = computeLedgerTotals(businessDate)
            val ledger = database.governanceDao().getLedgerByDate(businessDate)
                ?: DailyLedgerEntity(businessDate = businessDate, grossTotal = gross, netTotal = net, closedAt = null, isClosed = false)

            if (!ledger.isClosed) {
                val updated = ledger.copy(
                    grossTotal = gross,
                    netTotal = net,
                    isClosed = true,
                    closedAt = clock.now().toEpochMilliseconds(),
                )
                database.governanceDao().upsertLedger(updated)

                val delta = gross - net
                if (delta != 0.0) {
                    database.governanceDao().createDiscrepancy(
                        DiscrepancyTicketEntity(
                            ledgerDate = businessDate,
                            reason = "Settlement mismatch (${config.settlementDay.name})",
                            amountDelta = delta,
                            createdAt = clock.now().toEpochMilliseconds(),
                        ),
                    )
                    AppLogger.w(TAG, "Discrepancy ticket created for $businessDate: delta=$delta")
                }
                AppLogger.i(TAG, "Settlement completed for $businessDate (gross=$gross, net=$net)")
            }
        }
    }

    @Deprecated("Use runSettlementIfDue()", replaceWith = ReplaceWith("runSettlementIfDue()"))
    suspend fun runFridaySettlementIfDue() = runSettlementIfDue()
}
