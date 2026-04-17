package com.eaglepoint.task136.shared.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.db.CartItemEntity
import com.eaglepoint.task136.shared.db.InvoiceEntity
import com.eaglepoint.task136.shared.db.OrderEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CartCheckoutRefundIntegrationTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun invoice_with_linked_order_round_trips_through_room() = runBlocking {
        // Real order in Room
        val order = OrderEntity(
            id = "ord-42", userId = "admin", resourceId = "res-1",
            state = "Confirmed", startTime = 0L, endTime = 0L, expiresAt = null,
            quantity = 2, totalPrice = 20.0, createdAt = 0L,
        )
        db.orderDao().upsert(order)

        // Invoice links to the real order
        val invoice = InvoiceEntity(
            id = "inv-42", subtotal = 20.0, tax = 2.4, total = 22.4,
            orderId = "ord-42", ownerId = "admin", actorId = "admin", createdAt = 0L,
        )
        db.invoiceDao().upsert(invoice)

        val loadedInvoice = db.invoiceDao().getById("inv-42")
        assertNotNull(loadedInvoice)
        assertEquals("ord-42", loadedInvoice!!.orderId)
        // Canonical tax preserved on disk
        assertEquals(2.4, loadedInvoice.tax, 0.001)

        val loadedOrder = db.orderDao().getById(loadedInvoice.orderId!!)
        assertNotNull("linked order must exist", loadedOrder)
    }

    @Test
    fun invoice_without_linked_order_indicates_broken_reference() = runBlocking {
        val invoice = InvoiceEntity(
            id = "inv-orphan", subtotal = 10.0, tax = 1.2, total = 11.2,
            orderId = "ord-nonexistent", ownerId = "u", actorId = "u", createdAt = 0L,
        )
        db.invoiceDao().upsert(invoice)
        val loaded = db.invoiceDao().getById("inv-orphan")!!
        assertEquals("ord-nonexistent", loaded.orderId)
        // The linked order is NOT present — refund flow must detect this
        assertNull(db.orderDao().getById(loaded.orderId!!))
    }

    @Test
    fun cart_items_scoped_per_user_in_room() = runBlocking {
        db.cartDao().upsert(CartItemEntity("i1", "alice", "alice", "res-1", "A", 1, 5.0))
        db.cartDao().upsert(CartItemEntity("i2", "bob", "bob", "res-1", "B", 1, 5.0))
        db.cartDao().upsert(CartItemEntity("i3", "alice", "alice", "res-1", "C", 2, 5.0))

        val aliceCart = db.cartDao().getByUser("alice")
        val bobCart = db.cartDao().getByUser("bob")
        assertEquals(2, aliceCart.size)
        assertEquals(1, bobCart.size)
    }

    @Test
    fun cart_clearForUser_only_clears_one_user() = runBlocking {
        db.cartDao().upsert(CartItemEntity("i1", "alice", "alice", "res-1", "A", 1, 5.0))
        db.cartDao().upsert(CartItemEntity("i2", "bob", "bob", "res-1", "B", 1, 5.0))
        db.cartDao().clearForUser("alice")
        assertEquals(0, db.cartDao().getByUser("alice").size)
        assertEquals(1, db.cartDao().getByUser("bob").size)
    }

    @Test
    fun invoice_getByOwner_filters_results() = runBlocking {
        db.invoiceDao().upsert(InvoiceEntity(id = "a", subtotal = 10.0, tax = 1.2, total = 11.2, orderId = null, ownerId = "alice", actorId = "alice", createdAt = 1L))
        db.invoiceDao().upsert(InvoiceEntity(id = "b", subtotal = 20.0, tax = 2.4, total = 22.4, orderId = null, ownerId = "bob", actorId = "bob", createdAt = 2L))
        val aliceInvoices = db.invoiceDao().getByOwner("alice")
        assertEquals(1, aliceInvoices.size)
        assertEquals("a", aliceInvoices[0].id)
    }

    @Test
    fun invoice_getRecent_returns_newest_first() = runBlocking {
        db.invoiceDao().upsert(InvoiceEntity(id = "old", subtotal = 10.0, tax = 1.2, total = 11.2, orderId = null, ownerId = "u", actorId = "u", createdAt = 100L))
        db.invoiceDao().upsert(InvoiceEntity(id = "new", subtotal = 20.0, tax = 2.4, total = 22.4, orderId = null, ownerId = "u", actorId = "u", createdAt = 200L))
        val recent = db.invoiceDao().getRecent(10)
        assertEquals(2, recent.size)
        assertEquals("new", recent[0].id)
    }

    @Test
    fun order_entity_stores_notes_and_payment_method_canonically() = runBlocking {
        val order = OrderEntity(
            id = "ord-xyz", userId = "operator", resourceId = "res-1",
            state = "PendingTender", startTime = 0L, endTime = 0L, expiresAt = null,
            quantity = 1, totalPrice = 5.0, createdAt = 0L,
            paymentMethod = "Cash", notes = "delegated:operator",
        )
        db.orderDao().upsert(order)
        val loaded = db.orderDao().getById("ord-xyz")
        assertEquals("Cash", loaded!!.paymentMethod)
        assertEquals("delegated:operator", loaded.notes)
    }

    @Test
    fun order_getByIdForActor_enforces_owner_scoping() = runBlocking {
        val order = OrderEntity(
            id = "ord-owned", userId = "owner", resourceId = "res-1",
            state = "Confirmed", startTime = 0L, endTime = 0L, expiresAt = null,
            quantity = 1, totalPrice = 5.0, createdAt = 0L,
        )
        db.orderDao().upsert(order)
        assertNotNull(db.orderDao().getByIdForActor("ord-owned", "owner"))
        assertNull(db.orderDao().getByIdForActor("ord-owned", "someone-else"))
    }

    @Test
    fun cart_upsert_overwrites_existing_by_id() = runBlocking {
        db.cartDao().upsert(CartItemEntity("i1", "alice", "alice", "res-1", "A", 1, 5.0))
        db.cartDao().upsert(CartItemEntity("i1", "alice", "alice", "res-1", "A-updated", 5, 10.0))
        val items = db.cartDao().getByUser("alice")
        assertEquals(1, items.size)
        assertEquals("A-updated", items[0].label)
        assertEquals(5, items[0].quantity)
    }

    @Test
    fun invoice_entity_tax_field_is_stored_exactly_as_provided() = runBlocking {
        // Proves canonical tax storage at DAO level
        val canonical = 13.37
        db.invoiceDao().upsert(InvoiceEntity(id = "inv-tax", subtotal = 100.0, tax = canonical, total = 113.37, orderId = "ord-1", ownerId = "u", actorId = "u", createdAt = 0L))
        val loaded = db.invoiceDao().getById("inv-tax")!!
        assertEquals(canonical, loaded.tax, 0.0001)
    }
}
