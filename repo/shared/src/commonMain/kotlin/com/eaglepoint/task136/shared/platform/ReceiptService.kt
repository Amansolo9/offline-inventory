package com.eaglepoint.task136.shared.platform

data class ReceiptLineItem(
    val label: String,
    val amount: Double,
)

data class ReceiptData(
    val receiptId: String,
    val customerName: String,
    val lineItems: List<ReceiptLineItem>,
    val total: Double,
)

expect class ReceiptService {
    suspend fun generateAndSharePdf(data: ReceiptData)
}

expect fun createReceiptService(): ReceiptService
