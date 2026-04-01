# Task-136 Design Document

## Overview

Task-136 is an offline-first Android operations application built with Kotlin Multiplatform (KMP) and Jetpack Compose. It manages resource booking, order lifecycle, meeting scheduling, and financial reconciliation -- all functioning without network connectivity.

## Architecture

### Module Structure

```
repo/
  shared/                        # KMP shared business logic
    src/commonMain/              # Platform-agnostic code
      db/                        # Room entities, DAOs, database
      rbac/                      # RBAC + ABAC access control
      security/                  # Auth, password hashing, device binding
      orders/                    # OrderStateMachine, BookingUseCase
      governance/                # Reconciliation, rule hit observer
      services/                  # ValidationService
      viewmodel/                 # Shared ViewModels
      platform/                  # Expect/actual platform abstractions
      di/                        # Koin DI modules
      repository/                # Guarded data access
    src/androidMain/             # Android-specific implementations
    src/commonTest/              # Shared unit tests
  composeApp/                    # Compose UI application
    src/commonMain/              # Shared UI composables
      ui/                        # Screen composables
      ui/theme/                  # Material 3 theme
      ui/navigation/             # Navigation state + router
      di/                        # App-level DI
    src/androidMain/             # Android entry point
```

### Technology Stack

| Layer         | Technology                                        |
|---------------|---------------------------------------------------|
| UI            | Jetpack Compose (Material 3)                      |
| Navigation    | State-based enum router (no external dependency)   |
| DI            | Koin 3.5.6                                         |
| Database      | Room KMP 2.7.0-alpha09                             |
| Encryption    | SQLCipher 4.5.4 (Android)                          |
| Async         | Kotlin Coroutines + Flow                           |
| DateTime      | kotlinx-datetime 0.6.1                             |
| Serialization | kotlinx-serialization-json 1.7.3                   |
| Build         | Gradle 8.8, Kotlin 2.0.21                          |

## Data Model

### Entity Relationship

```
UserEntity (users)
  |
  +--< OrderEntity (orders)         -- userId FK
  |      +--< OrderLineItemEntity   -- orderId FK
  |
  +--< CartItemEntity (cart_items)   -- userId FK
  |
  +--< DeviceBindingEntity          -- userId FK
  |
  +--< MeetingEntity (meetings)      -- organizerId FK
         +--< MeetingAttendeeEntity  -- meetingId FK

ResourceEntity (resources)
  |
  +--< OrderEntity                   -- resourceId FK

RuleHitMetricEntity (rule_hits)      -- standalone governance
DailyLedgerEntity (daily_ledger)     -- standalone finance
DiscrepancyTicketEntity              -- standalone audit
```

### Database Schema (v3)

| Entity                  | Table               | Key Indexes                                                   |
|------------------------|--------------------|-----------------------------------------------------------------|
| UserEntity             | users              | (role, isActive)                                                |
| ResourceEntity         | resources          | (category, availableUnits)                                      |
| CartItemEntity         | cart_items         | (userId)                                                        |
| OrderEntity            | orders             | (resourceId, startTime), (state, expiresAt), (userId, state)   |
| OrderLineItemEntity    | order_line_items   | (orderId)                                                       |
| DeviceBindingEntity    | device_bindings    | (userId)                                                        |
| MeetingEntity          | meetings           | (organizerId, startTime)                                        |
| MeetingAttendeeEntity  | meeting_attendees  | (meetingId)                                                     |
| RuleHitMetricEntity    | rule_hits          | (createdAt, resolved)                                           |
| DailyLedgerEntity      | daily_ledger       | (businessDate) UNIQUE                                           |

## Security Architecture

### Authentication

- Local-only authentication with PBKDF2WithHmacSHA256 (120,000 iterations)
- Password policy: minimum 10 characters, at least 1 digit
- Rate limiting: 5 failed attempts triggers 15-minute lockout
- Session expiry: 30 minutes with touch-to-extend
- Demo accounts seeded in `LocalAuthService`; production deployments should integrate with `UserDao` for persistent credential management

### RBAC (Role-Based Access Control)

Enforced via `PermissionEvaluator` across all ViewModels.

| Role       | Resources   | Orders            | Users       | Wallet/Analytics/Ledger |
|------------|-------------|-------------------|-------------|--------------------------|
| Admin      | CRUD        | CRUD + Approve    | CRUD        | Full access              |
| Supervisor | Read, Write | Read, Write, Approve | maskedPII Read | -                   |
| Operator   | Read        | Read, Write       | maskedPII Read | -                   |
| Viewer     | Read        | Read              | -           | -                        |
| Companion  | Read        | Read, Write       | -           | -                        |

### ABAC (Attribute-Based Access Control)

Enforced via `AbacPolicyEvaluator` for sensitive operations:
- **Device trust**: All ABAC policies require `deviceTrusted = true`, resolved at runtime via `DeviceBindingService`
- **Invoice tax field**: Admin only on trusted device
- **Refund issuance**: Admin or Supervisor on trusted device
- **Attendee read**: Admin/Supervisor unrestricted; Operator/Companion self-or-delegate; Viewer self-only

### Device Binding

- Each account limited to 2 devices via `DeviceBindingService`
- Device fingerprint tracked in `DeviceBindingEntity`
- Admin can reset bindings for any user
- Untrusted devices are blocked from ABAC-gated operations

### Companion Role + Delegation

- Companion role can book and order on behalf of a delegating user
- `AuthPrincipal.delegateForUserId` links companion to their delegating user
- ViewModels pass delegation context so orders/bookings are attributed correctly

## Order Lifecycle

### State Machine

```
Draft --> PendingTender --> Confirmed --> AwaitingDelivery --> Delivered
                |               |              |
                v               |              v
            Expired         +---+---+    ReturnRequested --> Returned
                            |       |    ExchangeRequested --> Exchanged
                        Cancelled   +--- RefundRequested --> Refunded
                                    |
                            PartiallyFulfilled (via split)
```

### Key Behaviors

- **PendingTender**: Reserves inventory, auto-expires after 30 minutes with restock
- **Price validation**: Enforced at transition time ($0.01 - $9,999.99)
- **Split/Merge**: Transactional operations that split or combine orders with line items
- **Delivery**: Tracks delivery state and stores signed delivery slip
- **Returns/Refunds**: Inventory automatically restocked on completion

### Payment Methods

Orders support payment method recording: Cash, InternalWallet, or ExternalTender. The `paymentMethod` field on `OrderEntity` tracks this.

## Meeting Lifecycle

```
Draft --> PendingApproval --> Approved --> CheckedIn
                |                |
                v                v
             Denied          NoShow (if >10 min late)
```

- Meetings persist to `MeetingEntity` with agenda and attendees
- Attendees tracked via `MeetingAttendeeEntity` with RSVP status
- Check-in requires +/- 10 minute window validation

## Navigation Architecture

State-based navigation using a sealed class `Screen` in `AppNavigation.kt`:
- `Screen.Dashboard` - Main overview with stats and action panels
- `Screen.Calendar` - Resource availability calendar with interactive slot selection
- `Screen.OrderDetail(orderId)` - Individual order details loaded by ID from DAO
- `Screen.Cart` - Shopping cart with checkout flow
- `Screen.InvoiceDetail(invoiceId)` - Invoice breakdown with receipt sharing
- `Screen.MeetingDetail(meetingId)` - Meeting details with attendee management

### Navigation Auth Guard

`AppNavigator.navigateTo(screen, role)` enforces per-route authorization:
- **Dashboard, Calendar**: All authenticated roles
- **OrderDetail, Cart, MeetingDetail**: All roles except Viewer
- **InvoiceDetail**: Admin, Supervisor, Operator only

Unauthorized navigation attempts are silently rejected. Additionally, `App.kt` performs a secondary guard in the `when` block, redirecting to Dashboard if a disallowed screen is somehow reached.

### State Isolation

On logout or session expiry, `clearSessionState()` is called on all ViewModels and the navigator resets to Dashboard. The `LaunchedEffect(authState.isAuthenticated)` in `App.kt` fires on every auth state change, ensuring cleanup runs on both manual logout and session timeout.

## Architectural Decisions

### Android Views with RecyclerView + DiffUtil

The UI layer uses traditional Android Views (XML layouts + Fragments) with RecyclerView + DiffUtil for all list rendering, as specified by the prompt. Key components:

- `ResourceRecyclerAdapter` extends `ListAdapter<ResourceEntity>` with `ResourceDiffCallback` implementing `DiffUtil.ItemCallback`
- `DashboardFragment` hosts the main RecyclerView with `LinearLayoutManager`, `setHasFixedSize(true)`, and `setItemViewCacheSize(20)` for 60fps with 5,000+ rows
- `LoginFragment` uses Material TextInputLayout/TextInputEditText for the login form
- `MainActivity` extends `AppCompatActivity` and manages Fragment transactions via `NavigationHost` interface
- ViewModels emit `StateFlow` collected in Fragment `lifecycleScope` for reactive UI updates

The Compose UI files in `commonMain` are retained as an alternative rendering path for future desktop/KMP support but are not the active Android rendering path.

### Repository Pattern

`CoreRepository` provides RBAC-guarded data access for User/Resource/Order entities. ViewModels additionally inject DAOs directly for performance-critical paths (e.g., batch resource seeding, order state transitions that require transactional access via `AppDatabase.withTransaction`). RBAC is enforced at both the ViewModel layer (via `PermissionEvaluator.canAccess()`) and the state machine layer (via optional `role` parameter) for defense-in-depth. The dual-path approach is intentional: CoreRepository for simple guarded reads, direct DAO for transactional writes.

### Password Salt Strategy

Each user gets a cryptographically random 32-character salt generated at account creation time, stored in `UserEntity.passwordSalt`. Combined with PBKDF2WithHmacSHA256 at 120,000 iterations, this prevents rainbow table attacks even if the database is compromised.

### Image LRU Cache

`ImageBitmapLruCache` enforces a 20 MB byte-size cap (not count-based). Each entry's size is estimated as `width * height * 4` bytes. The cache evicts LRU entries when the byte budget is exceeded.

## Governance & Reconciliation

- **Price anomaly observer**: `RuleHitObserver` monitors governance rule hits via Flow
- **Daily closure**: Ledger closes automatically at 11 PM (configurable)
- **Settlement**: Configurable day/time via `SettlementConfig` (default: Friday 6 PM)
- **Discrepancy tickets**: Auto-generated when gross != net at settlement

## Build & Deploy

See [README.md](../README.md) for Docker-based build instructions with dev mode (continuous rebuild + auto-install).
