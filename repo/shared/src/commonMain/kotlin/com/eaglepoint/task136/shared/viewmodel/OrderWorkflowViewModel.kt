package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.db.OrderDao
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.db.ResourceDao
import com.eaglepoint.task136.shared.db.ResourceEntity
import com.eaglepoint.task136.shared.orders.BookingUseCase
import com.eaglepoint.task136.shared.orders.OrderState
import com.eaglepoint.task136.shared.orders.OrderStateMachine
import com.eaglepoint.task136.shared.orders.PaymentMethod
import com.eaglepoint.task136.shared.rbac.Action
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.ResourceType
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.services.ValidationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

data class OrderWorkflowState(
    val lastOrderId: String? = null,
    val lastOrderState: String? = null,
    val suggestedSlots: List<String> = emptyList(),
    val error: String? = null,
)

class OrderWorkflowViewModel(
    private val orderDao: OrderDao,
    private val resourceDao: ResourceDao,
    private val stateMachine: OrderStateMachine,
    private val bookingUseCase: BookingUseCase,
    private val permissionEvaluator: PermissionEvaluator,
    private val validationService: ValidationService,
    private val clock: Clock,
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(OrderWorkflowState())
    val state: StateFlow<OrderWorkflowState> = _state.asStateFlow()

    /**
     * Resolves the effective user ID for order attribution.
     * Companions act on behalf of their delegating user.
     */
    private fun resolveOrderUserId(actorId: String, delegateForUserId: String?): String {
        return delegateForUserId ?: actorId
    }

    fun createPendingTenderDemo(
        role: Role,
        actorId: String,
        delegateForUserId: String? = null,
        paymentMethod: PaymentMethod = PaymentMethod.Cash,
    ) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            ensureDemoResource()
            val effectiveUserId = resolveOrderUserId(actorId, delegateForUserId)
            val orderId = "ord-${clock.now().toEpochMilliseconds()}"
            val now = clock.now().toEpochMilliseconds()
            val totalPrice = 25.50
            val priceError = validationService.validatePrice(totalPrice)
            if (priceError != null) {
                _state.value = _state.value.copy(lastOrderState = priceError)
                return@launch
            }
            val order = OrderEntity(
                id = orderId,
                userId = effectiveUserId,
                resourceId = "res-1",
                state = OrderState.Draft.name,
                startTime = now,
                endTime = clock.now().plus(30.minutes).toEpochMilliseconds(),
                expiresAt = null,
                quantity = 1,
                totalPrice = totalPrice,
                createdAt = now,
                paymentMethod = paymentMethod.name,
            )
            orderDao.upsert(order)
            val transitionError = stateMachine.transitionToPendingTender(orderId, role)
            if (transitionError != null) {
                _state.value = _state.value.copy(lastOrderState = transitionError)
                return@launch
            }
            _state.value = _state.value.copy(lastOrderId = orderId, lastOrderState = OrderState.PendingTender.name)
        }
    }

    fun confirmLastOrder(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.confirm(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.Confirmed.name else "Confirm not allowed",
            )
        }
    }

    fun cancelOrder(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.cancel(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.Cancelled.name else "Cancel not allowed",
            )
        }
    }

    fun requestReturn(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.requestReturn(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.ReturnRequested.name else "Return not allowed",
            )
        }
    }

    fun completeReturn(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Approve)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.completeReturn(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.Returned.name else "Complete return not allowed",
            )
        }
    }

    fun requestExchange(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.requestExchange(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.ExchangeRequested.name else "Exchange not allowed",
            )
        }
    }

    fun requestRefund(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write) || role == Role.Companion) {
            _state.value = _state.value.copy(lastOrderState = "Refund denied for role")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.requestRefund(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.RefundRequested.name else "Refund not allowed",
            )
        }
    }

    fun completeRefund(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Approve)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.completeRefund(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.Refunded.name else "Complete refund not allowed",
            )
        }
    }

    fun markAwaitingDelivery(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.markAwaitingDelivery(orderId, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.AwaitingDelivery.name else "Cannot start delivery",
            )
        }
    }

    fun confirmDelivery(role: Role, signature: String) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(lastOrderState = "Denied")
            return
        }
        scope.launch(Dispatchers.IO) {
            val orderId = _state.value.lastOrderId ?: return@launch
            val success = stateMachine.confirmDelivery(orderId, signature, role)
            _state.value = _state.value.copy(
                lastOrderState = if (success) OrderState.Delivered.name else "Cannot confirm delivery",
            )
        }
    }

    fun suggestSlots(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Read)) {
            _state.value = _state.value.copy(suggestedSlots = emptyList())
            return
        }
        scope.launch(Dispatchers.IO) {
            ensureDemoResource()
            val slots = bookingUseCase.findThreeAvailableSlots(
                resourceId = "res-1",
                duration = 30.minutes,
            )
            val zone = TimeZone.currentSystemDefault()
            _state.value = _state.value.copy(
                suggestedSlots = slots.map {
                    val start = it.start.toLocalDateTime(zone)
                    "${start.date} ${start.time.hour.toString().padStart(2, '0')}:${start.time.minute.toString().padStart(2, '0')}"
                },
            )
        }
    }

    private suspend fun ensureDemoResource() {
        if (resourceDao.getById("res-1") != null) return
        resourceDao.upsert(
            ResourceEntity(
                id = "res-1",
                name = "Premium Hall",
                category = "Operations",
                availableUnits = 4,
                unitPrice = 25.50,
                allergens = "none",
            ),
        )
    }

    fun loadOrderById(orderId: String) {
        scope.launch(Dispatchers.IO) {
            val order = orderDao.getById(orderId)
            if (order != null) {
                _state.value = _state.value.copy(
                    lastOrderId = order.id,
                    lastOrderState = order.state,
                )
            }
        }
    }

    fun clearSessionState() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        _state.value = OrderWorkflowState()
    }
}
