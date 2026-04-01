package com.eaglepoint.task136.shared.platform

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual class ReceiptService {
    actual suspend fun generateAndSharePdf(data: ReceiptData) {
        try {
            val context = AndroidPlatformContext.require()

            withContext(Dispatchers.IO) {
                val document = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = document.startPage(pageInfo)

                val paint = Paint().apply { textSize = 14f }
                var y = 48f
                page.canvas.drawText("Receipt #${data.receiptId}", 36f, y, paint)
                y += 28f
                page.canvas.drawText("Customer: ${data.customerName}", 36f, y, paint)
                y += 28f

                data.lineItems.forEach { item ->
                    page.canvas.drawText("${item.label}: $${"%.2f".format(item.amount)}", 36f, y, paint)
                    y += 24f
                }

                y += 16f
                page.canvas.drawText("Total: $${"%.2f".format(data.total)}", 36f, y, paint)

                document.finishPage(page)

                val file = File(context.cacheDir, "receipt-${data.receiptId}.pdf")
                FileOutputStream(file).use { output -> document.writeTo(output) }
                document.close()
            }
            // PDF saved to cache. Share intent removed to prevent crashes
            // from background/application context. PDF can be accessed at:
            // context.cacheDir/receipt-{id}.pdf
        } catch (e: Exception) {
            println("[ReceiptService] Error generating PDF: ${e.message}")
        }
    }
}

actual fun createReceiptService(): ReceiptService = ReceiptService()
