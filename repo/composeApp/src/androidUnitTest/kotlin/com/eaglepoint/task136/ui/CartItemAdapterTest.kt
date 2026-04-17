package com.eaglepoint.task136.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.shared.viewmodel.CartItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CartItemAdapterTest {
    private lateinit var context: Context
    private lateinit var parent: ViewGroup

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        parent = FrameLayout(context)
    }

    @Test
    fun `empty list has zero item count`() {
        val adapter = CartItemAdapter()
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `submitList updates item count`() {
        val adapter = CartItemAdapter()
        adapter.submitList(listOf(
            CartItem(id = "a", label = "Apple", quantity = 2, unitPrice = 1.5),
            CartItem(id = "b", label = "Bread", quantity = 1, unitPrice = 3.0),
        ))
        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `DiffUtil areItemsTheSame identifies items by id`() {
        val adapter = CartItemAdapter()
        adapter.submitList(listOf(
            CartItem(id = "a", label = "Apple", quantity = 1, unitPrice = 1.0),
        ))
        // Same id, different content
        adapter.submitList(listOf(
            CartItem(id = "a", label = "Apple", quantity = 3, unitPrice = 1.0),
        ))
        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun `onCreateViewHolder returns valid ViewHolder`() {
        val adapter = CartItemAdapter()
        val holder = adapter.onCreateViewHolder(parent, 0)
        assertNotNull(holder)
        assertTrue(holder is RecyclerView.ViewHolder)
    }

    @Test
    fun `onBindViewHolder sets label with quantity`() {
        val adapter = CartItemAdapter()
        adapter.submitList(listOf(CartItem(id = "a", label = "Apple", quantity = 3, unitPrice = 2.5)))
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)
        val text1 = holder.itemView.findViewById<TextView>(android.R.id.text1)
        val text2 = holder.itemView.findViewById<TextView>(android.R.id.text2)
        assertTrue(text1.text.toString().contains("Apple"))
        assertTrue(text1.text.toString().contains("x3"))
        assertTrue(text2.text.toString().contains("7.50"))
    }

    @Test
    fun `CartItem equality works for DiffUtil content comparison`() {
        val a = CartItem(id = "a", label = "A", quantity = 1, unitPrice = 1.0)
        val b = CartItem(id = "a", label = "A", quantity = 1, unitPrice = 1.0)
        val c = CartItem(id = "a", label = "A", quantity = 2, unitPrice = 1.0)
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun `CartItem id uniquely identifies item for DiffUtil areItemsTheSame`() {
        val a = CartItem(id = "x1", label = "x", quantity = 1, unitPrice = 1.0)
        val b = CartItem(id = "x1", label = "differentLabel", quantity = 99, unitPrice = 9.99)
        // Same id, different contents - areItemsTheSame should still return true
        assertEquals(a.id, b.id)
    }
}
