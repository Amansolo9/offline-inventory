package com.eaglepoint.task136.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.shared.viewmodel.InvoiceDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class InvoiceListAdapterTest {
    private lateinit var context: Context
    private lateinit var parent: ViewGroup

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        parent = FrameLayout(context)
    }

    @Test
    fun `empty list has zero item count`() {
        val adapter = InvoiceListAdapter()
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `submitList with invoices updates item count`() {
        val adapter = InvoiceListAdapter()
        adapter.submitList(listOf(
            InvoiceDraft(id = "inv-1", subtotal = 100.0, tax = 12.0, total = 112.0, orderId = "ord-1"),
            InvoiceDraft(id = "inv-2", subtotal = 50.0, tax = 6.0, total = 56.0, orderId = "ord-2"),
        ))
        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `onBindViewHolder displays invoice id and total`() {
        val adapter = InvoiceListAdapter()
        adapter.submitList(listOf(
            InvoiceDraft(id = "inv-123", subtotal = 100.0, tax = 12.0, total = 112.0, orderId = "ord-1"),
        ))
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        val text1 = holder.itemView.findViewById<TextView>(android.R.id.text1)
        val text2 = holder.itemView.findViewById<TextView>(android.R.id.text2)
        assertEquals("inv-123", text1.text.toString())
        assertTrue(text2.text.toString().contains("112.00"))
    }

    @Test
    fun `click callback is invoked with invoice id`() {
        var clickedId: String? = null
        val adapter = InvoiceListAdapter { id -> clickedId = id }
        adapter.submitList(listOf(
            InvoiceDraft(id = "inv-target", subtotal = 10.0, tax = 1.0, total = 11.0),
        ))
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        holder.itemView.performClick()
        assertEquals("inv-target", clickedId)
    }

    @Test
    fun `onCreateViewHolder returns valid ViewHolder`() {
        val adapter = InvoiceListAdapter()
        val holder = adapter.onCreateViewHolder(parent, 0)
        assertNotNull(holder)
    }

    @Test
    fun `InvoiceDraft equality holds for DiffUtil content comparison`() {
        val a = InvoiceDraft(id = "inv-1", subtotal = 100.0, tax = 12.0, total = 112.0, orderId = "ord-1")
        val b = InvoiceDraft(id = "inv-1", subtotal = 100.0, tax = 12.0, total = 112.0, orderId = "ord-1")
        val c = InvoiceDraft(id = "inv-1", subtotal = 200.0, tax = 24.0, total = 224.0, orderId = "ord-1")
        assertEquals(a, b)
        assertTrue(a != c)
    }
}
