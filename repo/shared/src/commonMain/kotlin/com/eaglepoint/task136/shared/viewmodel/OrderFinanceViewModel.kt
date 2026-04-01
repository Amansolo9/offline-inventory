package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.platform.getDeviceFingerprint
import com.eaglepoint.task136.shared.db.CartDao
import com.eaglepoint.task136.shared.db.CartItemEntity
import com.eaglepoint.task136.shared.rbac.AbacPolicyEvaluator
import com.eaglepoint.task136.shared.rbac.AccessContext
import com.eaglepoint.task136.shared.rbac.Action
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.ResourceType
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.orders.OrderStateMachine
import com.eaglepoint.task136.shared.platform.NotificationGateway
import com.eaglepoint.task136.shared.platform.ReceiptGateway
import com.eaglepoint.task136.shared.platform.ReceiptLineItem
import com.eaglepoint.task136.shared.security.DeviceBindingService
import com.eaglepoint.task136.shared.services.ValidationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CartItem(
    val id: String,
    val label: String,
    val quantity: Int,
    val unitPrice: Double,
)

data class InvoiceDraft(
    val id: String,
    val subtotal: Double,
    val tax: Double,
    val total: Double,
    val orderId: String? = null,
)

data class OrderFinanceState(
    val cart: List<CartItem> = emptyList(),
    val invoices: List<InvoiceDraft> = emptyList(),
    val refunds: List<String> = emptyList(),
    val note: String? = null,
)

class OrderFinanceViewModel(
    private val abac: AbacPolicyEvaluator,
    private val permissionEvaluator: PermissionEvaluator,
    private val validationService: ValidationService,
    private val deviceBindingService: DeviceBindingService,
    private val cartDao: CartDao,
    private val stateMachine: OrderStateMachine? = null,
    private val notificationGateway: NotificationGateway,
    private val receiptGateway: ReceiptGateway,
    private val deviceFingerprint: String = getDeviceFingerprint(),
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(OrderFinanceState())
    val state: StateFlow<OrderFinanceState> = _state.asStateFlow()

    private suspend fun resolveDeviceTrust(userId: String): Boolean {
        return deviceBindingService.isDeviceTrusted(userId, deviceFingerprint)
    }

    fun addDemoItem(role: Role, actorId: String) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(note = "Add item denied for role")
            return
        }
        val unitPrice = 49.99
        val priceError = validationService.validatePrice(unitPrice)
        if (priceError != null) {
            _state.value = _state.value.copy(note = priceError)
            return
        }
        val itemId = "item-${_state.value.cart.size + 1}"
        val next = CartItem(
            id = itemId,
            label = "Service Package ${_state.value.cart.size + 1}",
            quantity = 1,
            unitPrice = unitPrice,
        )
        _state.value = _state.value.copy(cart = _state.value.cart + next, note = null)

        scope.launch(Dispatchers.IO) {
            cartDao.upsert(
                CartItemEntity(
                    id = itemId,
                    userId = actorId,
                    resourceId = "res-1",
                    label = next.label,
                    quantity = next.quantity,
                    unitPrice = next.unitPrice,
                ),
            )
        }
    }

    fun splitFirstItem(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(note = "Split denied for role")
            return
        }
        val first = _state.value.cart.firstOrNull() ?: return
        if (first.quantity < 2) {
            _state.value = _state.value.copy(note = "Split requires quantity >= 2")
            return
        }
        val left = first.copy(id = "${first.id}-a", quantity = first.quantity / 2)
        val right = first.copy(id = "${first.id}-b", quantity = first.quantity - left.quantity)
        _state.value = _state.value.copy(cart = listOf(left, right) + _state.value.cart.drop(1), note = "Order split")
        scope.launch(Dispatchers.IO) {
            cartDao.deleteById(first.id)
            cartDao.upsert(CartItemEntity(id = left.id, userId = "", resourceId = "", label = left.label, quantity = left.quantity, unitPrice = left.unitPrice))
            cartDao.upsert(CartItemEntity(id = right.id, userId = "", resourceId = "", label = right.label, quantity = right.quantity, unitPrice = right.unitPrice))
        }
    }

    fun mergeFirstTwoItems(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(note = "Merge denied for role")
            return
        }
        val cart = _state.value.cart
        if (cart.size < 2) return
        val merged = cart[0].copy(
            id = "${cart[0].id}+${cart[1].id}",
            quantity = cart[0].quantity + cart[1].quantity,
            unitPrice = cart[0].unitPrice,
        )
        _state.value = _state.value.copy(cart = listOf(merged) + cart.drop(2), note = "Orders merged")
        scope.launch(Dispatchers.IO) {
            cartDao.deleteById(cart[0].id)
            cartDao.deleteById(cart[1].id)
            cartDao.upsert(CartItemEntity(id = merged.id, userId = "", resourceId = "", label = merged.label, quantity = merged.quantity, unitPrice = merged.unitPrice))
        }
    }

    fun generateInvoice(role: Role, actorId: String) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Order, "*", Action.Write)) {
            _state.value = _state.value.copy(note = "Invoice denied for role")
            return
        }
        if (_state.value.cart.isEmpty()) return
        val subtotal = _state.value.cart.sumOf { it.unitPrice * it.quantity }
        val priceError = validationService.validatePrice(subtotal)
        if (priceError != null) {
            _state.value = _state.value.copy(note = priceError)
            return
        }

        scope.launch(Dispatchers.IO) {
            val trusted = resolveDeviceTrust(actorId)
            val context = AccessContext(requesterId = actorId, ownerId = actorId, isDelegate = false, deviceTrusted = trusted)
            val tax = if (abac.canReadInvoiceTaxField(role, context)) subtotal * 0.12 else 0.0
            val invoice = InvoiceDraft(
                id = "inv-${_state.value.invoices.size + 1}",
                subtotal = subtotal,
                tax = tax,
                total = subtotal + tax,
            )
            _state.value = _state.value.copy(invoices = _state.value.invoices + invoice, note = "Invoice generated")

            try {
                notificationGateway.scheduleInvoiceReady(invoice.id, invoice.total)
            } catch (_: Exception) { /* notification scheduling is best-effort */ }

            try {
                val receiptItems = _state.value.cart.map {
                    ReceiptLineItem(label = it.label, amount = it.quantity * it.unitPrice)
                }
                receiptGateway.shareReceipt(invoice.id, actorId, receiptItems, invoice.total)
            } catch (_: Exception) { /* receipt generation is best-effort */ }
        }
    }

    fun refundLatest(role: Role, actorId: String) {
        val latest = _state.value.invoices.lastOrNull() ?: return
        scope.launch(Dispatchers.IO) {
            val trusted = resolveDeviceTrust(actorId)
            val context = AccessContext(requesterId = actorId, ownerId = actorId, isDelegate = false, deviceTrusted = trusted)
            if (!abac.canIssueRefund(role, context)) {
                _state.value = _state.value.copy(note = "Refund denied for role")
                return@launch
            }
            _state.value = _state.value.copy(refunds = _state.value.refunds + "Refunded ${latest.id}", note = "Refund completed")

            if (latest.orderId != null && stateMachine != null) {
                stateMachine.requestRefund(latest.orderId)
                stateMachine.completeRefund(latest.orderId)
            }
        }
    }

    fun clearSessionState() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        _state.value = OrderFinanceState()
    }
}
