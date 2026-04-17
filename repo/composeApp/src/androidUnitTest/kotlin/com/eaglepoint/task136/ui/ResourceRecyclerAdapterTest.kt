package com.eaglepoint.task136.ui

import com.eaglepoint.task136.shared.db.ResourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ResourceRecyclerAdapterTest {

    private fun resource(id: String, name: String = "R", category: String = "Operations", allergens: String = "none") =
        ResourceEntity(id = id, name = name, category = category, availableUnits = 5, unitPrice = 1.0, allergens = allergens)

    @Test
    fun `empty adapter has zero items`() {
        val adapter = ResourceRecyclerAdapter()
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `submitList updates item count`() {
        val adapter = ResourceRecyclerAdapter()
        adapter.submitList(listOf(resource("r1"), resource("r2"), resource("r3")))
        assertEquals(3, adapter.itemCount)
    }

    @Test
    fun `DiffCallback areItemsTheSame compares by id`() {
        val callback = ResourceDiffCallback()
        val a = resource("r1", name = "Apple")
        val b = resource("r1", name = "Banana")
        val c = resource("r2", name = "Apple")
        assertTrue(callback.areItemsTheSame(a, b))
        assertFalse(callback.areItemsTheSame(a, c))
    }

    @Test
    fun `DiffCallback areContentsTheSame compares full equality`() {
        val callback = ResourceDiffCallback()
        val a = resource("r1", name = "Apple")
        val b = resource("r1", name = "Apple")
        val c = resource("r1", name = "Banana")
        assertTrue(callback.areContentsTheSame(a, b))
        assertFalse(callback.areContentsTheSame(a, c))
    }
}
