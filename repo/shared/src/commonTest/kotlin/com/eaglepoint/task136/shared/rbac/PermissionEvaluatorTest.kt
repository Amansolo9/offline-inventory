package com.eaglepoint.task136.shared.rbac

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionEvaluatorTest {
    private val evaluator = PermissionEvaluator(defaultRules())

    @Test
    fun `admin has full access to all resource types`() {
        ResourceType.entries.forEach { resType ->
            assertTrue(evaluator.canAccess(Role.Admin, resType, "*", Action.Read), "Admin should read $resType")
            assertTrue(evaluator.canAccess(Role.Admin, resType, "*", Action.Write), "Admin should write $resType")
        }
    }

    @Test
    fun `viewer can only read orders and resources`() {
        assertTrue(evaluator.canAccess(Role.Viewer, ResourceType.Order, "*", Action.Read))
        assertTrue(evaluator.canAccess(Role.Viewer, ResourceType.Resource, "*", Action.Read))
        assertFalse(evaluator.canAccess(Role.Viewer, ResourceType.Order, "*", Action.Write))
        assertFalse(evaluator.canAccess(Role.Viewer, ResourceType.Resource, "*", Action.Write))
    }

    @Test
    fun `companion can read and write orders but not approve`() {
        assertTrue(evaluator.canAccess(Role.Companion, ResourceType.Order, "*", Action.Read))
        assertTrue(evaluator.canAccess(Role.Companion, ResourceType.Order, "*", Action.Write))
        assertFalse(evaluator.canAccess(Role.Companion, ResourceType.Order, "*", Action.Approve))
    }

    @Test
    fun `companion can only read resources`() {
        assertTrue(evaluator.canAccess(Role.Companion, ResourceType.Resource, "*", Action.Read))
        assertFalse(evaluator.canAccess(Role.Companion, ResourceType.Resource, "*", Action.Write))
    }

    @Test
    fun `supervisor can approve orders`() {
        assertTrue(evaluator.canAccess(Role.Supervisor, ResourceType.Order, "*", Action.Approve))
    }

    @Test
    fun `operator cannot approve orders`() {
        assertFalse(evaluator.canAccess(Role.Operator, ResourceType.Order, "*", Action.Approve))
    }

    @Test
    fun `supervisor can read masked PII`() {
        assertTrue(evaluator.canAccess(Role.Supervisor, ResourceType.User, "maskedPII", Action.Read))
    }

    @Test
    fun `viewer cannot access user data`() {
        assertFalse(evaluator.canAccess(Role.Viewer, ResourceType.User, "maskedPII", Action.Read))
    }
}
