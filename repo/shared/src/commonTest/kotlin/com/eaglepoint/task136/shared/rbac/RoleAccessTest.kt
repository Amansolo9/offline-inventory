package com.eaglepoint.task136.shared.rbac

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoleAccessTest {
    private val evaluator = PermissionEvaluator(defaultRules())

    @Test
    fun `all roles can read resources`() {
        Role.entries.forEach { role ->
            assertTrue(evaluator.canAccess(role, ResourceType.Resource, "*", Action.Read), "$role should read Resources")
        }
    }

    @Test
    fun `only admin supervisor operator companion can write orders`() {
        assertTrue(evaluator.canAccess(Role.Admin, ResourceType.Order, "*", Action.Write))
        assertTrue(evaluator.canAccess(Role.Supervisor, ResourceType.Order, "*", Action.Write))
        assertTrue(evaluator.canAccess(Role.Operator, ResourceType.Order, "*", Action.Write))
        assertTrue(evaluator.canAccess(Role.Companion, ResourceType.Order, "*", Action.Write))
        assertFalse(evaluator.canAccess(Role.Viewer, ResourceType.Order, "*", Action.Write))
    }

    @Test
    fun `only admin and supervisor can approve`() {
        assertTrue(evaluator.canAccess(Role.Admin, ResourceType.Order, "*", Action.Approve))
        assertTrue(evaluator.canAccess(Role.Supervisor, ResourceType.Order, "*", Action.Approve))
        assertFalse(evaluator.canAccess(Role.Operator, ResourceType.Order, "*", Action.Approve))
        assertFalse(evaluator.canAccess(Role.Viewer, ResourceType.Order, "*", Action.Approve))
        assertFalse(evaluator.canAccess(Role.Companion, ResourceType.Order, "*", Action.Approve))
    }

    @Test
    fun `companion has no delete permission`() {
        ResourceType.entries.forEach { resType ->
            assertFalse(evaluator.canAccess(Role.Companion, resType, "*", Action.Delete), "Companion should not delete $resType")
        }
    }

    @Test
    fun `viewer has no write permission on anything`() {
        ResourceType.entries.forEach { resType ->
            assertFalse(evaluator.canAccess(Role.Viewer, resType, "*", Action.Write), "Viewer should not write $resType")
        }
    }

    @Test
    fun `role enum has exactly 5 roles`() {
        val expected = setOf("Admin", "Supervisor", "Operator", "Viewer", "Companion")
        val actual = Role.entries.map { it.name }.toSet()
        assertTrue(expected == actual)
    }
}
