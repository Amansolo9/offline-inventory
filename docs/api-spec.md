# Task-136 API Specification

This document defines the internal service APIs exposed by the shared module. Since this is an offline-first local application, these are Kotlin function-level APIs (not HTTP endpoints). They form the contract between the UI layer and business logic.

## Authentication API

### `LocalAuthService`

| Method | Signature | Returns | Description |
|--------|-----------|---------|-------------|
| `authenticate` | `(username: String, password: String) -> AuthPrincipal?` | AuthPrincipal or null | Validates credentials against stored PBKDF2 hashes. Returns principal with role and delegation info. |

### `SecurityRepository`

| Method | Signature | Returns | Description |
|--------|-----------|---------|-------------|
| `validatePassword` | `(password: String) -> PasswordPolicyResult` | PasswordPolicyResult | Checks minimum 10 chars + 1 digit. |
| `canAuthenticate` | `(userId: String, now: Instant) -> Boolean` | Boolean | Returns false if user is locked out (5 failures = 15 min lockout). |
| `recordFailure` | `(userId: String, now: Instant) -> Unit` | Unit | Increments failure count; triggers lockout at threshold. |
| `recordSuccess` | `(userId: String) -> Unit` | Unit | Clears failure count on successful auth. |

### `AuthViewModel`

| Method | Signature | Description |
|--------|-----------|-------------|
| `login` | `() -> Unit` | Validates password policy, checks lockout, authenticates, starts session. |
| `logout` | `(message: String?) -> Unit` | Clears session state. |
| `ensureSessionActive` | `() -> Unit` | Checks 30-minute expiry; triggers logout + ViewModel cleanup if expired. |
| `touchSession` | `() -> Unit` | Extends session by 30 minutes on user activity. |

**State:** `AuthUiState`
```kotlin
data class AuthUiState(
    val username: String,
    val password: String,
    val principal: AuthPrincipal?,
    val error: String?,
    val sessionExpiresAt: Instant?,
)
```

---

## RBAC / ABAC API

### `PermissionEvaluator`

| Method | Signature | Returns | Description |
|--------|-----------|---------|-------------|
| `canAccess` | `(role: Role, resource: ResourceType, field: String, action: Action) -> Boolean` | Boolean | Checks role against permission rules. |
| `guardOrNull` | `<T>(role, resource, field, action, valueProvider) -> T?` | T or null | Returns null if access denied, otherwise executes provider. |

**Roles:** `Admin`, `Supervisor`, `Operator`, `Viewer`, `Companion`
**Resources:** `Resource`, `Order`, `User`, `Wallet`, `Analytics`, `Ledger`
**Actions:** `Read`, `Write`, `Delete`, `Approve`

### `AbacPolicyEvaluator`

| Method | Signature | Returns | Description |
|--------|-----------|---------|-------------|
| `canReadAttendee` | `(role: Role, context: AccessContext) -> Boolean` | Boolean | Checks device trust + role-based attendee visibility. |
| `canReadInvoiceTaxField` | `(role: Role, context: AccessContext) -> Boolean` | Boolean | Admin only on trusted device. |
| `canIssueRefund` | `(role: Role, context: AccessContext) -> Boolean` | Boolean | Admin or Supervisor on trusted device. |

**AccessContext:**
```kotlin
data class AccessContext(
    val requesterId: String,
    val ownerId: String,
    val isDelegate: Boolean,
    val deviceTrusted: Boolean,  // resolved via DeviceBindingService
)
```

### `DeviceBindingService`

| Method | Signature | Returns | Description |
|--------|-----------|---------|-------------|
| `checkAndBindDevice` | `(userId: String, fingerprint: String) -> DeviceBindingResult` | Allowed or LimitExceeded | Binds device if under 2-device limit. |
| `isDeviceTrusted` | `(userId: String, fingerprint: String) -> Boolean` | Boolean | Checks if device is already bound. |
| `adminResetBindings` | `(adminRole: Role, userId: String) -> Boolean` | Boolean | Admin-only reset. |

---

## Order API

### `OrderStateMachine`

| Method | Signature | Returns | Description |
|--------|-----------|---------|-------------|
| `transitionToPendingTender` | `(orderId: String) -> String?` | Error message or null | Validates price, reserves inventory, sets 30-min expiry. |
| `confirm` | `(orderId: String) -> Unit` | Unit | Finalizes order, clears expiry. |
| `requestReturn` | `(orderId: String) -> Boolean` | Boolean | Only from Confirmed or Delivered. |
| `completeReturn` | `(orderId: String) -> Boolean` | Boolean | Only from ReturnRequested; restocks. |
| `requestExchange` | `(orderId: String) -> Boolean` | Boolean | Only from Confirmed or Delivered. |
| `completeExchange` | `(orderId: String) -> Boolean` | Boolean | Only from ExchangeRequested. |
| `requestRefund` | `(orderId: String) -> Boolean` | Boolean | From Confirmed, Delivered, or Returned. |
| `completeRefund` | `(orderId: String) -> Boolean` | Boolean | Only from RefundRequested; restocks. |
| `markAwaitingDelivery` | `(orderId: String) -> Boolean` | Boolean | Only from Confirmed. |
| `confirmDelivery` | `(orderId: String, signature: String) -> Boolean` | Boolean | Only from AwaitingDelivery; stores signature. |
| `splitOrder` | `(orderId: String, splitQuantity: Int) -> Pair<String, String>?` | New order IDs or null | Splits into two orders with line items. |
| `mergeOrders` | `(orderId1: String, orderId2: String) -> String?` | Merged order ID or null | Same-user orders only. |
| `observeState` | `(orderId: String) -> Flow<OrderState>` | Flow | Reactive state observation. |

**OrderState enum:**
`Draft`, `PendingTender`, `Confirmed`, `Cancelled`, `Expired`, `PartiallyFulfilled`, `ReturnRequested`, `Returned`, `ExchangeRequested`, `Exchanged`, `RefundRequested`, `Refunded`, `AwaitingDelivery`, `Delivered`

**PaymentMethod enum:**
`Cash`, `InternalWallet`, `ExternalTender`

### `BookingUseCase`

| Method | Signature | Returns | Description |
|--------|-----------|---------|-------------|
| `findThreeAvailableSlots` | `(resourceId: String, duration: Duration, anchor: Instant) -> List<TimeWindow>` | Up to 3 TimeWindows | Scans 14-day horizon with 10-min buffer. |
| `overlaps` | `(existing: List<TimeWindow>, candidate: TimeWindow) -> Boolean` | Boolean | Checks for scheduling conflicts with buffer. |

### `OrderWorkflowViewModel`

All methods take `role: Role` as first parameter for RBAC enforcement.

| Method | Description |
|--------|-------------|
| `createPendingTenderDemo(role, actorId)` | Creates draft order, transitions to PendingTender. Validates price. Uses delegation context for Companion role. |
| `confirmLastOrder(role)` | Confirms last created order. |
| `requestReturn(role)` | Requests return on last order. |
| `completeReturn(role)` | Completes return (requires Approve permission). |
| `requestExchange(role)` | Requests exchange on last order. |
| `requestRefund(role)` | Requests refund on last order. |
| `completeRefund(role)` | Completes refund (requires Approve permission). |
| `markAwaitingDelivery(role)` | Starts delivery flow. |
| `confirmDelivery(role, signature)` | Confirms delivery with signed slip. |
| `suggestSlots(role)` | Finds 3 available booking slots. |

**State:** `OrderWorkflowState`
```kotlin
data class OrderWorkflowState(
    val lastOrderId: String?,
    val lastOrderState: String?,
    val suggestedSlots: List<String>,
)
```

---

## Cart & Invoice API

### `OrderFinanceViewModel`

| Method | Signature | Description |
|--------|-----------|-------------|
| `addDemoItem` | `(role: Role, actorId: String)` | Adds item to cart; persists via CartDao. Price validated. |
| `splitFirstItem` | `(role: Role)` | Splits first cart item (quantity >= 2). |
| `mergeFirstTwoItems` | `(role: Role)` | Merges first two cart items. |
| `generateInvoice` | `(role: Role, actorId: String)` | Creates invoice from cart. Resolves device trust for tax field ABAC. Schedules notification + shares receipt. |
| `refundLatest` | `(role: Role, actorId: String)` | Refunds last invoice. Resolves device trust for ABAC. Transitions order state if linked. |

**State:** `OrderFinanceState`
```kotlin
data class OrderFinanceState(
    val cart: List<CartItem>,
    val invoices: List<InvoiceDraft>,
    val refunds: List<String>,
    val note: String?,
)
```

---

## Meeting API

### `MeetingWorkflowViewModel`

| Method | Signature | Description |
|--------|-----------|-------------|
| `submitMeeting` | `(start: Instant, agenda: String, organizerId: String)` | Creates meeting, persists to DB, sets PendingApproval. |
| `addAttendee` | `(name: String)` | Adds attendee to current meeting. |
| `updateAgenda` | `(agenda: String)` | Updates meeting agenda. |
| `approve` | `(role: Role)` | Supervisor/Admin approve. |
| `deny` | `(role: Role)` | Supervisor/Admin deny. |
| `checkIn` | `(role: Role)` | Check in within +/- 10 min window. |
| `markNoShowIfDue` | `(role: Role, now: Instant)` | Mark no-show if > 10 min late. |

**State:** `MeetingWorkflowState`
```kotlin
data class MeetingWorkflowState(
    val status: MeetingStatus,
    val meetingId: String?,
    val meetingStart: Instant?,
    val agenda: String,
    val attendees: List<AttendeeInfo>,
    val note: String?,
)
```

**MeetingStatus:** `Draft`, `PendingApproval`, `Approved`, `Denied`, `CheckedIn`, `NoShow`

---

## Governance API

### `ReconciliationService`

| Method | Signature | Description |
|--------|-----------|-------------|
| `runDailyClosureIfDue` | `() -> Unit` | Closes daily ledger at 11 PM if enabled. |
| `runSettlementIfDue` | `() -> Unit` | Runs configurable settlement (default: Friday 6 PM). Creates discrepancy ticket if gross != net. |

**Configuration:**
```kotlin
data class SettlementConfig(
    val settlementDay: DayOfWeek = FRIDAY,
    val settlementHour: Int = 18,
    val enableDailyClosure: Boolean = true,
)
```

### `RuleHitObserver`

| Method | Signature | Description |
|--------|-----------|-------------|
| `start` | `() -> Job` | Starts Flow collection, notifies on price anomalies ($0.01 - $9,999.99 range). |

---

## Validation API

### `ValidationService`

| Method | Signature | Returns | Description |
|--------|-----------|---------|-------------|
| `isWithinSupervisorWindow` | `(meetingStart: Instant, at: Instant) -> Boolean` | Boolean | True if within +/- 10 minutes. |
| `validatePrice` | `(price: Double) -> String?` | Error or null | Rejects prices outside $0.01 - $9,999.99. |
| `validateAllergens` | `(resource: ResourceEntity) -> String?` | Error or null | Rejects blank allergen flags. |
| `validateAllergenFlags` | `(allergens: String) -> Boolean` | Boolean | Returns true if non-blank. |

---

## Platform APIs

### `NotificationGateway`

| Method | Signature | Description |
|--------|-----------|-------------|
| `scheduleInvoiceReady` | `(invoiceId: String, total: Double)` | Schedules notification in 2 min with quiet hours (9 PM - 7 AM). |

### `ReceiptGateway`

| Method | Signature | Description |
|--------|-----------|-------------|
| `shareReceipt` | `(invoiceId: String, customerName: String, lineItems: List<ReceiptLineItem>, total: Double)` | Generates PDF (Android) or PNG (Desktop) and shares. |

---

## Data Access Objects

### OrderDao

| Query | Signature | Description |
|-------|-----------|-------------|
| `upsert` | `(order: OrderEntity)` | Insert or replace. |
| `update` | `(order: OrderEntity)` | Update existing. |
| `getById` | `(orderId: String) -> OrderEntity?` | Fetch by PK. |
| `observeById` | `(orderId: String) -> Flow<OrderEntity?>` | Reactive observation. |
| `getActiveByResource` | `(resourceId: String) -> List<OrderEntity>` | Non-cancelled orders for resource. |
| `deleteById` | `(orderId: String)` | Delete by PK. |
| `page` | `(limit: Int) -> List<OrderEntity>` | Recent orders, paginated. |

### MeetingDao

| Query | Signature | Description |
|-------|-----------|-------------|
| `upsert` | `(meeting: MeetingEntity)` | Insert or replace. |
| `update` | `(meeting: MeetingEntity)` | Update existing. |
| `getById` | `(id: String) -> MeetingEntity?` | Fetch by PK. |
| `observeById` | `(id: String) -> Flow<MeetingEntity?>` | Reactive observation. |
| `getByOrganizer` | `(userId: String, limit: Int) -> List<MeetingEntity>` | Meetings by organizer. |
| `page` | `(limit: Int) -> List<MeetingEntity>` | All meetings, paginated. |
| `upsertAttendee` | `(attendee: MeetingAttendeeEntity)` | Add/update attendee. |
| `getAttendees` | `(meetingId: String) -> List<MeetingAttendeeEntity>` | Attendees for meeting. |
| `removeAttendee` | `(id: String)` | Remove attendee by PK. |

### CartDao

| Query | Signature | Description |
|-------|-----------|-------------|
| `upsert` | `(item: CartItemEntity)` | Insert or replace. |
| `getByUser` | `(userId: String) -> List<CartItemEntity>` | Cart items for user. |
| `observeByUser` | `(userId: String) -> Flow<List<CartItemEntity>>` | Reactive cart observation. |
| `clearForUser` | `(userId: String)` | Empty user's cart. |
