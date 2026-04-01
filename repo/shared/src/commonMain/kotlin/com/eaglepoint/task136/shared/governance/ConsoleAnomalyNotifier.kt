package com.eaglepoint.task136.shared.governance

class ConsoleAnomalyNotifier : AnomalyNotifier {
    override suspend fun notify(ruleName: String, value: Double, detail: String) {
        val sanitized = detail.replace(Regex("[0-9]"), "*")
        println("[ANOMALY] rule=$ruleName detail=$sanitized")
    }
}
