package com.eaglepoint.task136.shared.config

import com.eaglepoint.task136.shared.rbac.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanaryEvaluatorTest {

    @Test
    fun `feature enabled for matching role`() {
        val manifest = CanaryManifest(listOf(
            CanaryConfig("form-v2", targetVersion = 2, enabledRoles = setOf("Admin", "Supervisor")),
        ))
        val evaluator = CanaryEvaluator(manifest)

        assertTrue(evaluator.isFeatureEnabled("form-v2", Role.Admin))
        assertTrue(evaluator.isFeatureEnabled("form-v2", Role.Supervisor))
    }

    @Test
    fun `feature disabled for non-matching role`() {
        val manifest = CanaryManifest(listOf(
            CanaryConfig("form-v2", targetVersion = 2, enabledRoles = setOf("Admin")),
        ))
        val evaluator = CanaryEvaluator(manifest)

        assertFalse(evaluator.isFeatureEnabled("form-v2", Role.Operator))
        assertFalse(evaluator.isFeatureEnabled("form-v2", Role.Viewer))
    }

    @Test
    fun `feature enabled for matching device group`() {
        val manifest = CanaryManifest(listOf(
            CanaryConfig("form-v2", targetVersion = 2, enabledDeviceGroups = setOf("beta-testers")),
        ))
        val evaluator = CanaryEvaluator(manifest)

        assertTrue(evaluator.isFeatureEnabled("form-v2", Role.Admin, deviceGroup = "beta-testers"))
        assertFalse(evaluator.isFeatureEnabled("form-v2", Role.Admin, deviceGroup = "production"))
    }

    @Test
    fun `feature with empty roles allows all roles`() {
        val manifest = CanaryManifest(listOf(
            CanaryConfig("form-v2", targetVersion = 2),
        ))
        val evaluator = CanaryEvaluator(manifest)

        Role.entries.forEach { role ->
            assertTrue(evaluator.isFeatureEnabled("form-v2", role), "$role should be enabled")
        }
    }

    @Test
    fun `unknown feature returns false`() {
        val evaluator = CanaryEvaluator(CanaryManifest())
        assertFalse(evaluator.isFeatureEnabled("nonexistent", Role.Admin))
    }

    @Test
    fun `rollout percentage gates by user hash`() {
        val manifest = CanaryManifest(listOf(
            CanaryConfig("form-v2", targetVersion = 2, rolloutPercentage = 50),
        ))
        val evaluator = CanaryEvaluator(manifest)

        // Test that different users get different results (at least some enabled, some disabled)
        val results = (1..100).map { evaluator.isFeatureEnabled("form-v2", Role.Admin, userId = "user-$it") }
        assertTrue(results.any { it }, "Some users should be enabled")
        assertTrue(results.any { !it }, "Some users should be disabled")
    }

    @Test
    fun `100 percent rollout enables all`() {
        val manifest = CanaryManifest(listOf(
            CanaryConfig("form-v2", targetVersion = 2, rolloutPercentage = 100),
        ))
        val evaluator = CanaryEvaluator(manifest)

        val allEnabled = (1..50).all { evaluator.isFeatureEnabled("form-v2", Role.Admin, userId = "user-$it") }
        assertTrue(allEnabled)
    }

    @Test
    fun `resolveFormVersion returns target when enabled`() {
        val manifest = CanaryManifest(listOf(
            CanaryConfig("booking-form", targetVersion = 3, enabledRoles = setOf("Admin")),
        ))
        val evaluator = CanaryEvaluator(manifest)

        assertEquals(3, evaluator.resolveFormVersion("booking-form", defaultVersion = 1, role = Role.Admin))
    }

    @Test
    fun `resolveFormVersion returns default when disabled`() {
        val manifest = CanaryManifest(listOf(
            CanaryConfig("booking-form", targetVersion = 3, enabledRoles = setOf("Admin")),
        ))
        val evaluator = CanaryEvaluator(manifest)

        assertEquals(1, evaluator.resolveFormVersion("booking-form", defaultVersion = 1, role = Role.Viewer))
    }

    @Test
    fun `resolveFormVersion returns default for unknown feature`() {
        val evaluator = CanaryEvaluator(CanaryManifest())
        assertEquals(1, evaluator.resolveFormVersion("unknown", defaultVersion = 1, role = Role.Admin))
    }

    @Test
    fun `combined role and device group gating`() {
        val manifest = CanaryManifest(listOf(
            CanaryConfig("new-ui", targetVersion = 2, enabledRoles = setOf("Admin"), enabledDeviceGroups = setOf("beta")),
        ))
        val evaluator = CanaryEvaluator(manifest)

        assertTrue(evaluator.isFeatureEnabled("new-ui", Role.Admin, deviceGroup = "beta"))
        assertFalse(evaluator.isFeatureEnabled("new-ui", Role.Admin, deviceGroup = "stable"))
        assertFalse(evaluator.isFeatureEnabled("new-ui", Role.Viewer, deviceGroup = "beta"))
    }
}
