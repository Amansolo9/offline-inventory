# Task-136 Offline Operations App

Android-first offline operations app with shared KMP business logic. UI built with Android Views (Fragments + RecyclerView + DiffUtil) for the Android target, with Compose retained as a fallback for non-Android KMP targets.

## Features

### Core Ordering
- **Shopping cart** with persistent `CartItemEntity` backed by Room, add/remove items per user.
- **Order line items** (`OrderLineItemEntity`) for multi-item orders with notes and tags.
- **Split/merge** operations on orders for partial fulfillment — transactional DB-level split and merge in `OrderStateMachine`, persisted to `CartDao`.
- **Order state machine** with 14 states: Draft, PendingTender, Confirmed, Cancelled, Expired, PartiallyFulfilled, ReturnRequested, Returned, ExchangeRequested, Exchanged, RefundRequested, Refunded, AwaitingDelivery, Delivered.
- **Order cancellation** with inventory restock for PendingTender and Confirmed orders.
- **Payment method** recording: Cash, InternalWallet, ExternalTender per order.
- Conflict-free slot search with strict 10-minute buffer handling (`BookingUseCase`).
- Floating-point precision in split arithmetic (rounded to 2 decimal places).

### Return / Exchange / Refund Workflows
- Full post-purchase lifecycle with state guards enforcing valid transitions.
- Inventory automatically restocked on completed returns, exchanges, and refunds.
- Null-role callers blocked from completion operations (fail-closed).
- Companion role explicitly blocked from refund requests at both ViewModel and state machine levels.

### Delivery Confirmation
- Orders transition through AwaitingDelivery → InTransit → Delivered with signed delivery slip.
- `DeliveryState` enum tracks: None, AwaitingDelivery, InTransit, Delivered, DeliveryConfirmed.
- `markInTransit()` transition for tracking in-flight deliveries.

### Meeting Reservations
- Full lifecycle: Draft → PendingApproval → Approved/Denied → CheckedIn/NoShow.
- **Attendee management** with `MeetingAttendeeEntity` and RSVP tracking.
- **Agenda** text field and **attachment path** for document references.
- **Conflict detection** via `BookingUseCase.overlaps()` before meeting creation.
- **Auto no-show** — background coroutine marks no-show 10 minutes after meeting start if not checked in.
- **Meeting notifications** scheduled on submission and approval with quiet-hours enforcement (9 PM – 7 AM).
- Supervisor/Admin can approve or deny meeting requests.

### Employee Learning
- **Course catalog** with `CourseEntity` (title, description, category, duration).
- **Enrollment tracking** with `EnrollmentEntity` (status, progress percentage, completion).
- `LearningViewModel` with RBAC-guarded enrollment and progress updates.
- Demo courses seeded on first launch (Safety, Equipment, Customer Service, Data Privacy, Leadership).
- Android Views: `LearningFragment` with `CourseRecyclerAdapter` + DiffUtil.

### Security & Access Control
- **RBAC** via `PermissionEvaluator` — enforced in all ViewModels and as defense-in-depth in the state machine.
- **ABAC** via `AbacPolicyEvaluator` — attribute-based policies for invoice tax fields, refund issuance, and attendee reads. Device trust resolved at runtime via `DeviceBindingService`.
- **Device binding** — accounts limited to 2 devices, enforced at login. Admin can reset bindings. Rejection message shown on login if limit exceeded.
- **Password hashing** — PBKDF2WithHmacSHA256 with 120,000 iterations. Cryptographically random salts via `java.security.SecureRandom`.
- **Constant-time hash comparison** via `MessageDigest.isEqual()` to prevent timing attacks.
- **5 roles**: Admin, Supervisor, Operator, Viewer, Companion (delegated access).
- **Companion delegation** — orders attributed to the delegating user via `AuthPrincipal.delegateForUserId`.
- **Session management** — 30-minute idle timeout with touch-to-extend, 8-hour absolute session cap. All ViewModel scopes cancelled on logout.
- **Rate limiting** — 5-attempt lockout with 15-minute cooldown, persisted to Room (survives app restart).
- **Debug-only demo seeding** — gated by `BuildConfig.DEBUG`, never active in release builds.
- **Log redaction** — `AppLogger` strips passwords, hashes, fingerprints, wallet refs, and PII before output.

### Canary Rollout & Feature Flags
- `CanaryConfig` with role targeting, device-group targeting, and percentage-based rollout.
- `CanaryEvaluator.resolveFormVersion()` gates form layout versions per user context.
- Integrates with `DynamicFormEngine` versioned data dictionary.

### Data Governance
- **Allergen flag enforcement** — resources with blank allergens blocked from insertion.
- **Price validation (preventive)** — $0.01–$9,999.99 enforced before persistence.
- **Reconciliation** — daily ledger closure at 11 PM with order totals computed from real confirmed/delivered/refunded orders. Configurable settlement cycles via `SettlementConfig` (default: Friday 6 PM). Discrepancy tickets auto-generated when gross != net.
- Background governance observer (`RuleHitObserver`) monitors anomalies via Flow.
- **Stale order expiry** — expired PendingTender orders cleaned up on app startup with inventory restock.

### Receipt / Invoice
- Android: real PDF generation via `PdfDocument` API, shared via `FileProvider` intent.
- Desktop: receipt image generation via `BufferedImage`/`Graphics2D`.
- Invoice generation with ABAC-gated tax calculation.
- Notification scheduling with quiet-hours guard (9 PM–7 AM).
- Receipt button in order detail screen.

### Android Views (RecyclerView + DiffUtil)
- **Fragments**: `LoginFragment`, `DashboardFragment`, `CartFragment`, `OrderDetailFragment`, `LearningFragment`.
- **RecyclerView adapters with DiffUtil**: `ResourceRecyclerAdapter`, `CourseRecyclerAdapter` for 60fps scrolling with 5,000+ rows.
- **XML layouts**: Material Components with CardView, TextInputLayout, MaterialButton.
- **Fragment navigation** via `NavigationHost` interface with role-based screen access control.
- **Loading indicators** (horizontal ProgressBar) and **confirmation dialogs** (MaterialAlertDialog on logout).
- ViewModels emit `StateFlow` collected in Fragment `lifecycleScope`.

### Other
- Room KMP database (v5) with SQLCipher encryption on Android (Keystore-backed passphrase).
- Koin DI wiring across shared and app modules.
- Dynamic form renderer with versioned field definitions, PII masking, and canary rollout.
- Image bitmap LRU cache with 20 MB byte-budget cap.
- Structured logging via `AppLogger` with level filtering and sensitive data redaction.
- Composite indexes on orders: `[resourceId, startTime]`, `[state, expiresAt]`, `[userId, state]`, `[state, createdAt]`.

## Build

```bash
docker compose up --build
```

Builds the debug APK inside Docker. If a device is connected via host ADB, it auto-installs and launches the app. If no device is available, the build still succeeds and the APK is at `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

**Optional:** Start ADB on the host before building if you want auto-install:

```bash
adb start-server
adb devices          # verify your device shows "device" (authorized)
```

### Dev mode (continuous rebuild + auto-install)

```bash
docker compose --profile dev up --build dev
```

Edit code on the host, save, and it auto-rebuilds + pushes to device. Works without a device too (just rebuilds). Press `Ctrl+C` to stop.

### How ADB works from Docker

The container does **not** run its own ADB server. It connects to your host's ADB server at `host.docker.internal:5037`. Your device's USB authorization is handled by the host -- no conflicts.

## Run Tests

```bash
./run_tests.sh
```

Or directly:

```bash
./gradlew :shared:testDebugUnitTest
```

Runs all 166 unit tests across 16 test files using fakes and in-memory stores -- no emulator, no device, no APK build required.

## Local non-Docker path

```bash
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Windows PowerShell:

```powershell
.\gradlew.bat :composeApp:assembleDebug
adb install -r composeApp\build\outputs\apk\debug\composeApp-debug.apk
```

## Demo Accounts

Demo accounts are seeded automatically on first launch in debug builds only (`BuildConfig.DEBUG`).

| Username     | Password       | Role       | Notes |
|-------------|---------------|------------|-------|
| admin       | Admin1234!    | Admin      | Full access, can reset device bindings |
| supervisor  | Super1234!    | Supervisor | Can approve/deny meetings, complete returns/refunds |
| operator    | Oper12345!    | Operator   | Can create orders, check in to meetings |
| viewer      | Viewer1234    | Viewer     | Read-only access |
| companion   | Companion1!   | Companion  | Delegated access for operator, cannot approve or refund |

## Database Schema (v5)

| Entity                  | Table               | Purpose                          |
|------------------------|--------------------|---------------------------------|
| `UserEntity`           | `users`            | Credentials, roles, lockout state, delegation |
| `ResourceEntity`       | `resources`        | Bookable resources with allergens |
| `CartItemEntity`       | `cart_items`       | Shopping cart per user            |
| `OrderEntity`          | `orders`           | Orders with delivery state, payment method, notes, tags |
| `OrderLineItemEntity`  | `order_line_items` | Line items per order              |
| `DeviceBindingEntity`  | `device_bindings`  | Device fingerprints per user (2 max) |
| `MeetingEntity`        | `meetings`         | Meeting requests with agenda and attachments |
| `MeetingAttendeeEntity`| `meeting_attendees` | Meeting attendees with RSVP      |
| `CourseEntity`         | `courses`          | Learning course catalog           |
| `EnrollmentEntity`     | `enrollments`      | User course enrollments and progress |
| `RuleHitMetricEntity`  | `rule_hits`        | Governance rule violations         |
| `DailyLedgerEntity`    | `daily_ledger`     | Financial reconciliation from real order data |
| `DiscrepancyTicketEntity` | `discrepancy_tickets` | Settlement mismatches        |

## Tests

```bash
./run_tests.sh
```

**166 tests across 16 test files:**

| Test File | Tests | Coverage |
|-----------|-------|----------|
| `OrderStateMachineTest` | 45 | State transitions, cancellation, confirm guard, return/refund/exchange flows, delivery, expiry, split guards, null-role rejection, inventory restock |
| `AbacPolicyEvaluatorTest` | 26 | All 5 roles x trusted/untrusted device for tax, refund, attendee policies |
| `ValidationServiceTest` | 12 | Price boundaries, allergen validation, check-in window |
| `CanaryEvaluatorTest` | 11 | Role/device-group/percentage rollout, version resolution |
| `ReconciliationServiceTest` | 11 | Settlement config, daily closure timing, discrepancy detection |
| `QuietHoursTest` | 9 | 9 PM–7 AM boundary validation |
| `AuthViewModelTest` | 9 | Login, logout, session expiry, absolute cap, device binding rejection |
| `PermissionEvaluatorTest` | 8 | Admin full access, viewer restrictions, companion permissions |
| `OrderWorkflowViewModelTest` | 7 | RBAC permissions, delegation resolution, initial state |
| `RoleAccessTest` | 6 | Cross-role write/approve/delete permissions |
| `AppLoggerTest` | 6 | Password/hash/fingerprint redaction, log level filtering |
| `OrderFinanceViewModelTest` | 5 | Cart operations, split guard, viewer denial |
| `ResourceListViewModelTest` | 5 | Loading, session clearing, allergen validation |
| `SecurityRepositoryTest` | 2 | Password policy, lockout persistence |
| `MeetingWorkflowViewModelTest` | 2 | Submit→approve→check-in, deny flow |
| `BookingUseCaseTest` | 2 | Overlap detection, slot finding |

## Project Structure

```
repo/
  shared/                           # KMP shared business logic
    src/commonMain/kotlin/
      db/                           # Room entities, DAOs (13 entities, 8 DAOs)
      rbac/                         # RBAC (PermissionEvaluator) + ABAC (AbacPolicyEvaluator)
      security/                     # Auth, PBKDF2, device binding, lockout
      orders/                       # OrderStateMachine (14 states), BookingUseCase
      governance/                   # ReconciliationService, RuleHitObserver
      services/                     # ValidationService
      config/                       # CanaryConfig, CanaryEvaluator
      logging/                      # AppLogger with redaction
      viewmodel/                    # 6 ViewModels (Auth, OrderWorkflow, OrderFinance, Meeting, Resource, Learning)
      platform/                     # Expect/actual for notifications, receipts, device fingerprint
      di/                           # Koin modules
    src/androidMain/kotlin/         # Android implementations (PBKDF2, SecureRandom, ReceiptService, etc.)
    src/commonTest/kotlin/          # 166 unit tests across 16 files
  composeApp/
    src/commonMain/kotlin/
      ui/                           # Compose screens (retained for KMP fallback)
      ui/navigation/                # AppNavigator with role-based screen access
      ui/form/                      # DynamicFormEngine with versioning
      media/                        # ImageBitmapLruCache (20 MB cap)
      di/                           # App-level Koin init
    src/androidMain/
      kotlin/.../ui/                # Android View Fragments + RecyclerView adapters
      res/layout/                   # XML layouts for all screens
      res/values/                   # Material Components theme
  docs/
    design.md                       # Architecture, data model, security, decisions
    api-spec.md                     # Full internal API specification
```
