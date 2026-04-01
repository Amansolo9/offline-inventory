package com.eaglepoint.task136.shared.governance

import com.eaglepoint.task136.shared.db.GovernanceDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

interface AnomalyNotifier {
    suspend fun notify(ruleName: String, value: Double, detail: String)
}

class RuleHitObserver(
    private val governanceDao: GovernanceDao,
    private val anomalyNotifier: AnomalyNotifier,
    private val scope: CoroutineScope,
) {
    fun start(): Job {
        return scope.launch(Dispatchers.IO) {
            governanceDao.observeOpenRuleHits().collectLatest { hits ->
                hits.forEach { hit ->
                    if (hit.valueObserved < 0.01 || hit.valueObserved > 9_999.99) {
                        anomalyNotifier.notify(
                            ruleName = hit.ruleName,
                            value = hit.valueObserved,
                            detail = "Price outside allowed range $0.01 to $9,999.99",
                        )
                    }
                }
            }
        }
    }
}
