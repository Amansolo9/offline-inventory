package com.eaglepoint.task136.shared.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eaglepoint.task136.shared.db.AppDatabase
import com.eaglepoint.task136.shared.db.ResourceEntity
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.services.ValidationService
import com.eaglepoint.task136.shared.viewmodel.ResourceListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class AdminResourceFlowIntegrationTest {
    private lateinit var db: AppDatabase
    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-04-01T10:00:00Z")
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() { db.close() }

    private fun vm() = ResourceListViewModel(
        resourceDao = db.resourceDao(),
        validationService = ValidationService(fixedClock),
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun admin_add_resource_returns_true_and_eventually_persists() = runBlocking {
        val resourceVm = vm()
        val ok = resourceVm.addResource(Role.Admin, "Widget", "Operations", 10, 5.99)
        assertTrue("admin add returns true for authorized role", ok)
        // Let async upsert settle
        repeat(10) {
            Thread.sleep(100)
            if (db.resourceDao().countAll() >= 1) return@repeat
        }
        assertTrue("admin add must persist to Room", db.resourceDao().countAll() >= 1)
    }

    @Test
    fun non_admin_cannot_mutate_resources_core_path_returns_false() {
        val resourceVm = vm()
        assertFalse(resourceVm.addResource(Role.Viewer, "Blocked", "Operations", 1, 1.0))
        assertFalse(resourceVm.addResource(Role.Operator, "Blocked2", "Operations", 1, 1.0))
        assertFalse(resourceVm.addResource(Role.Companion, "Blocked3", "Operations", 1, 1.0))
        assertFalse(resourceVm.addResource(Role.Supervisor, "Blocked4", "Operations", 1, 1.0))
    }

    @Test
    fun non_admin_delete_returns_false_without_mutation() = runBlocking {
        // Pre-seed directly via DAO
        db.resourceDao().upsert(ResourceEntity(
            id = "res-seed", name = "Seed", category = "Ops",
            availableUnits = 1, unitPrice = 1.0, allergens = "none",
        ))
        val resourceVm = vm()
        assertFalse(resourceVm.deleteResource(Role.Operator, "res-seed"))
        assertFalse(resourceVm.deleteResource(Role.Viewer, "res-seed"))
        assertFalse(resourceVm.deleteResource(Role.Companion, "res-seed"))
        // Resource must still exist
        assertNotNull(db.resourceDao().getById("res-seed"))
    }

    @Test
    fun admin_delete_returns_true() = runBlocking {
        db.resourceDao().upsert(ResourceEntity(
            id = "res-to-del", name = "ToDel", category = "Ops",
            availableUnits = 1, unitPrice = 1.0, allergens = "none",
        ))
        val resourceVm = vm()
        val ok = resourceVm.deleteResource(Role.Admin, "res-to-del")
        assertTrue(ok)
    }

    @Test
    fun resource_dao_roundtrip_preserves_all_fields() = runBlocking {
        val original = ResourceEntity(
            id = "res-rt", name = "RT", category = "Logistics",
            availableUnits = 42, unitPrice = 19.99, allergens = "gluten,dairy",
        )
        db.resourceDao().upsert(original)
        val loaded = db.resourceDao().getById("res-rt")
        assertNotNull(loaded)
        assertEquals("RT", loaded!!.name)
        assertEquals("Logistics", loaded.category)
        assertEquals(42, loaded.availableUnits)
        assertEquals(19.99, loaded.unitPrice, 0.001)
        assertEquals("gluten,dairy", loaded.allergens)
    }

    @Test
    fun resource_paging_returns_added_entries() = runBlocking {
        db.resourceDao().upsertAll(
            (1..5).map {
                ResourceEntity(
                    id = "res-p$it", name = "R$it", category = "Ops",
                    availableUnits = it, unitPrice = it * 1.0, allergens = "none",
                )
            },
        )
        val page = db.resourceDao().page(limit = 10, offset = 0)
        assertEquals(5, page.size)
    }

    @Test
    fun resource_deleteById_removes_from_room() = runBlocking {
        db.resourceDao().upsert(ResourceEntity(
            id = "res-del", name = "X", category = "Ops",
            availableUnits = 1, unitPrice = 1.0, allergens = "none",
        ))
        db.resourceDao().deleteById("res-del")
        assertNull(db.resourceDao().getById("res-del"))
    }
}
