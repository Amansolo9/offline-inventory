package com.eaglepoint.task136.shared.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.io.File
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

actual class ReceiptService {
    actual suspend fun generateAndSharePdf(data: ReceiptData) = withContext(Dispatchers.IO) {
        val width = 595
        val height = 842
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g: Graphics2D = image.createGraphics()

        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.color = Color.BLACK

        val titleFont = Font("SansSerif", Font.BOLD, 18)
        val bodyFont = Font("SansSerif", Font.PLAIN, 14)

        var y = 48
        g.font = titleFont
        g.drawString("Receipt #${data.receiptId}", 36, y)
        y += 32

        g.font = bodyFont
        g.drawString("Customer: ${data.customerName}", 36, y)
        y += 28

        data.lineItems.forEach { item ->
            g.drawString("${item.label}: ${"%.2f".format(item.amount)}", 36, y)
            y += 24
        }

        y += 16
        g.font = titleFont
        g.drawString("Total: ${"%.2f".format(data.total)}", 36, y)

        g.dispose()

        val outputDir = File(System.getProperty("java.io.tmpdir"), "task136-receipts")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "receipt-${data.receiptId}.png")
        ImageIO.write(image, "PNG", outputFile)

        println("[ReceiptService] Receipt saved to ${outputFile.absolutePath}")
    }
}

actual fun createReceiptService(): ReceiptService = ReceiptService()
