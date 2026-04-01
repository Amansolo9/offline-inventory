package com.eaglepoint.task136.shared.services

import com.eaglepoint.task136.shared.db.ResourceEntity
import com.eaglepoint.task136.shared.orders.MAX_PRICE
import com.eaglepoint.task136.shared.orders.MIN_PRICE
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class ValidationService(
    private val clock: Clock,
) {
    fun isWithinSupervisorWindow(meetingStart: Instant, at: Instant = clock.now()): Boolean {
        val diff = at - meetingStart
        return diff.inWholeMinutes in -10..10
    }

    fun validatePrice(price: Double): String? {
        if (price < MIN_PRICE) return "Price must be at least $$MIN_PRICE"
        if (price > MAX_PRICE) return "Price must not exceed $$MAX_PRICE"
        return null
    }

    fun validateAllergens(resource: ResourceEntity): String? {
        if (resource.allergens.isBlank()) {
            return "Allergen flags are required but missing for resource '${resource.name}'"
        }
        return null
    }

    fun validateAllergenFlags(allergens: String): Boolean {
        return allergens.isNotBlank()
    }
}
