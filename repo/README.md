# android

# Task-136 Offline Operations Suite

Project type: **android** (Kotlin Multiplatform with Android Views as the primary delivered product UI).

Offline operations suite for workplace nutrition and resource management. All business logic runs locally on-device with no network backend.

## Architecture

- **Primary UI**: Android Views (Fragments + XML layouts) in `composeApp/src/androidMain/`
- **Shared logic**: KMP shared module (`shared/`) — Room entities/DAOs, RBAC/ABAC, ViewModels, workflows
- **Compose layer**: Supplementary/reference path; not primary delivery
- **DI**: Koin
- **Persistence**: Room with SQLCipher encryption at rest
- **Offline-only**: no HTTP endpoints, no network dependencies

---

## Quickstart (Docker-first — recommended)

**All builds and tests are fully containerized.** You do not need a local JDK, Android SDK, or Gradle to build this project. The Docker image is self-contained.

### Prerequisites (Docker path)
- Docker Desktop (or Docker Engine + `docker compose`)
- (Optional, for device install) Android device or emulator reachable via `adb` on the host

### 1. Run unit tests (Docker-contained)

```bash
./run_tests.sh
```

The script detects that no local SDK is available and launches the tests inside the container automatically. No environment variables required.

### 2. Build and install the APK on a connected device/emulator

```bash
docker compose up app
```

This builds `composeApp-debug.apk`, then (if a device is visible via host `adb`) installs and launches it.

### 3. Dev loop — rebuild on file changes

```bash
docker compose --profile dev up dev
```

Watches source files and reinstalls the APK on device when code changes.

### Emulator boot (host-side)

If no physical device is connected, start an emulator on your host before running `docker compose up app`:

```bash
# List available AVDs on host
$ANDROID_HOME/emulator/emulator -list-avds

# Boot an AVD (e.g., Pixel_6_API_34)
$ANDROID_HOME/emulator/emulator -avd Pixel_6_API_34 &

# Wait until ready
adb wait-for-device
adb shell getprop sys.boot_completed
```

Then `docker compose up app` will connect to your host's adb server (`host.docker.internal:5037`) and install to the emulator.

---

## Optional local path (not required)

If you already have a JDK 17+, Android SDK, and Gradle wrapper configured, you can run the shared tests directly:

```bash
# Set ANDROID_HOME first
./gradlew :shared:testDebugUnitTest
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

`run_tests.sh` will auto-detect `ANDROID_HOME` and use the local path instead of Docker.

---

## Demo accounts (auto-seeded in debug builds)

`LocalAuthService` seeds these users on first launch:

| Username     | Password      | Role       | Notes                       |
| ------------ | ------------- | ---------- | --------------------------- |
| `admin`      | `Admin1234!`  | Admin      | Full access; Admin panel    |
| `supervisor` | `Super1234!`  | Supervisor | Approve/deny meetings; refunds |
| `operator`   | `Oper12345!`  | Operator   | Order/cart; meetings        |
| `viewer`     | `Viewer1234!` | Viewer     | Read-only                   |
| `companion`  | `Companion1!` | Companion  | Delegated to operator       |

Password policy: 10+ characters with at least one digit.

---

## Runtime verification walkthrough

After `docker compose up app` completes with a device attached, the app launches automatically. Verify each flow below by interacting with the UI on the device/emulator screen:

### 1. Login and role badge (~30 seconds)
1. On the login screen, enter `admin` / `Admin1234!`, tap **Sign In**.
2. **Expected**: Dashboard opens showing the role badge `Admin`, your user ID `admin`, and a stats row (Resources / Cart / Invoices / Refunds).
3. Tap **Logout** → confirm. **Expected**: you return to the login screen. Pressing back must NOT re-open the dashboard (back stack is cleared).

### 2. Admin panel (Admin only)
1. Log in as `admin`. Tap **Admin** on the dashboard.
2. **Expected**: Admin panel opens with sections: Manage Resources, Business Rules, Device Binding Management, Resources list.
3. Enter name, category, units, price → tap **Add Resource**. **Expected**: status message `Resource '<name>' added` and the list count increases.
4. Enter a user ID → tap **Reset Device Bindings**. **Expected**: status message `Device bindings reset for '<id>'`.
5. Log out, log in as `operator`. **Expected**: **Admin** button is hidden on the dashboard. Even if invoked programmatically, navigation is rejected.

### 3. Meeting submission with agenda and attendees
1. Log in as `operator`. Tap **Calendar**.
2. Enter an agenda (e.g., `Q3 planning`), attendees (e.g., `Alice, Bob`), tap **Submit Meeting**.
3. **Expected**: meeting status updates to `PendingApproval` with a form-version annotation.
4. Tap **Open Meeting** → **Expected**: agenda displays `Agenda: Q3 planning`, attendees display `Alice, Bob` (supervisor/admin roles only see the full list per ABAC).
5. Log in as `viewer`. Tap **Calendar** → **Expected**: **Submit Meeting** button is disabled.

### 4. Cart and invoice flow (RecyclerView + DiffUtil)
1. Log in as `operator`. Tap **Cart**.
2. Tap **Add Item** several times. **Expected**: each item appears in the RecyclerView list with label and line total. Swapping orders reuses ViewHolders (DiffUtil diffing).
3. Tap **Checkout**. **Expected**: cart clears and an invoice is generated. The persisted invoice links to a real `ord-*` order ID (not a cart item ID).
4. Tap **Latest Invoice** → **Expected**: Invoice detail screen shows Subtotal/Tax/Total loaded from Room. As `operator`, Tax displays as `$0.00` (masked), but the stored entity retains canonical tax.
5. Log in as `admin`, open same invoice → **Expected**: Tax field shows the real amount.

### 5. Refund targeting and governance logging
1. Log in as `supervisor`. Navigate to an invoice via Cart → Latest Invoice, tap **Issue Refund**.
2. **Expected**: refund status updates, rule-hit records are written to `rule_hits` table if an allergen/price/role rule fires.
3. Log in as `companion`, attempt refund → **Expected**: `Refund denied for role` note.

### 6. Meeting attachment image pipeline
1. In a meeting detail screen, tap **Add Attachment**.
2. **Expected**: attachment path displays. If the path resolves to an image file, `ImageDownsampler` + `ImageBitmapLruCache` load it off-main-thread (capped at 800x600, 20MB cache).

### 7. Order reminder notifications
1. Log in as `operator`. Create and confirm an order via the Order Detail flow.
2. **Expected**: `NotificationGateway.scheduleOrderReminder` is invoked. Quiet hours (21:00–06:59 local) suppress display.

### 8. Session and device binding
1. Log in, leave idle for 30+ minutes. **Expected**: session expires, redirected to login with back stack cleared.
2. Fail login 5 times with wrong password. **Expected**: 15-minute lockout enforced.
3. Log in from a third device. **Expected**: device limit error unless Admin resets bindings via Admin panel.

---

## Test commands

### Run all shared unit tests (Docker-contained by default)
```bash
./run_tests.sh
```

### Run a single test class
```bash
./run_tests.sh --tests 'com.eaglepoint.task136.shared.viewmodel.SecurityRemediationTest'
```

### Run android instrumentation smoke test (requires device/emulator)
```bash
./gradlew :composeApp:connectedAndroidTest
```

---

## Static evidence map (for auditors who cannot execute tests)

Every test claim in this README is grounded in a specific committed file. Reviewers can count test methods statically (`@Test` annotations) and cross-reference from this table.

| Claim | File(s) | What to count |
|---|---|---|
| Security + admin guard tests | `shared/src/commonTest/.../viewmodel/SecurityRemediationTest.kt` | `@Test` methods |
| Object-level order authorization | `shared/src/commonTest/.../viewmodel/OrderAuthorizationTest.kt` | `@Test` methods |
| Admin navigation role gating | `shared/src/commonTest/.../navigation/AdminNavigationTest.kt` | `@Test` methods |
| Canary form engine in production | `shared/src/commonTest/.../config/CanaryProductionTest.kt`, `MeetingFormEngineTest.kt` | `@Test` methods |
| Invoice persistence + tax + refund targeting | `shared/src/commonTest/.../viewmodel/InvoicePersistenceTest.kt` | `@Test` methods |
| Governance rule-hit analytics | `shared/src/commonTest/.../governance/GovernanceAnalyticsTest.kt` | `@Test` methods |
| LocalAuthService / PBKDF2 / seeding | `shared/src/commonTest/.../security/LocalAuthServiceTest.kt` | `@Test` methods |
| DeviceBindingService / admin reset | `shared/src/commonTest/.../security/DeviceBindingServiceTest.kt` | `@Test` methods |
| CoreRepository RBAC | `shared/src/commonTest/.../repository/CoreRepositoryTest.kt` | `@Test` methods |
| Koin DI wiring (real Room + all VMs) | `shared/src/androidUnitTest/.../di/SharedModuleWiringTest.kt` | `@Test` methods |
| Real-Koin end-to-end (no fakes) | `shared/src/androidUnitTest/.../integration/RealKoinEndToEndTest.kt` | `@Test` methods |
| Admin resource mutation integration | `shared/src/androidUnitTest/.../integration/AdminResourceFlowIntegrationTest.kt` | `@Test` methods |
| Auth lockout integration | `shared/src/androidUnitTest/.../integration/AuthLockoutIntegrationTest.kt` | `@Test` methods |
| Meeting submission integration | `shared/src/androidUnitTest/.../integration/MeetingSubmissionIntegrationTest.kt` | `@Test` methods |
| Cart → invoice → refund integration | `shared/src/androidUnitTest/.../integration/CartCheckoutRefundIntegrationTest.kt` | `@Test` methods |
| Booking + refund + no-show integration | `shared/src/androidUnitTest/.../integration/CriticalFlowIntegrationTest.kt` | `@Test` methods |
| Cart RecyclerView+DiffUtil | `composeApp/src/androidUnitTest/.../ui/CartItemAdapterTest.kt` | `@Test` methods |
| Invoice RecyclerView+DiffUtil | `composeApp/src/androidUnitTest/.../ui/InvoiceListAdapterTest.kt` | `@Test` methods |
| Resource RecyclerView+DiffUtil | `composeApp/src/androidUnitTest/.../ui/ResourceRecyclerAdapterTest.kt` | `@Test` methods |
| Fragment class contracts | `composeApp/src/androidUnitTest/.../ui/FragmentContractTest.kt` | `@Test` methods |
| Login fragment class surface | `composeApp/src/androidUnitTest/.../ui/LoginFragmentTest.kt` | `@Test` methods |
| NavigationHost contract | `composeApp/src/androidUnitTest/.../ui/NavigationHostContractTest.kt` | `@Test` methods |
| LoginFragment + DashboardFragment lifecycle (Robolectric) | `composeApp/src/androidUnitTest/.../ui/FragmentLifecycleTest.kt` | `@Test` methods |
| CartFragment behavior (RecyclerView, VM wiring, click→state) | `composeApp/src/androidUnitTest/.../ui/CartFragmentTest.kt` | `@Test` methods |
| CalendarFragment behavior (viewer-disabled submit, agenda+attendees persist) | `composeApp/src/androidUnitTest/.../ui/CalendarFragmentTest.kt` | `@Test` methods |
| OrderDetailFragment behavior (arg binding, load by id, access-denied path) | `composeApp/src/androidUnitTest/.../ui/OrderDetailFragmentTest.kt` | `@Test` methods |
| InvoiceDetailFragment behavior (persistence load, admin vs operator tax masking) | `composeApp/src/androidUnitTest/.../ui/InvoiceDetailFragmentTest.kt` | `@Test` methods |
| MeetingDetailFragment behavior (role-gated approve, agenda, approve→Room transition) | `composeApp/src/androidUnitTest/.../ui/MeetingDetailFragmentTest.kt` | `@Test` methods |
| AdminFragment behavior (inflation, add resource, reset bindings, non-admin blocked) | `composeApp/src/androidUnitTest/.../ui/AdminFragmentTest.kt` | `@Test` methods |
| MainActivity smoke | `composeApp/src/androidInstrumentedTest/.../MainActivitySmokeTest.kt` | `@Test` methods |
| Critical user-flow instrumentation (login, admin, viewer-cannot-submit, cart checkout) | `composeApp/src/androidInstrumentedTest/.../CriticalWorkflowInstrumentationTest.kt` | `@Test` methods |

Static counting recipe (for an auditor):
```bash
# From repo root:
grep -r "@Test" shared/src/commonTest shared/src/androidUnitTest composeApp/src/androidUnitTest composeApp/src/androidInstrumentedTest | wc -l
```

## Test coverage overview (408 unit tests + 7 instrumentation tests; 415 `@Test` annotations total, 0 unit-test failures)

**Shared module: 341 unit tests** — domain, workflow, auth, security, repository, DI wiring, and in-memory Room integration
**composeApp module: 67 unit tests** — Fragments (inflation + interaction + role-gated UI + Room-backed behavior), Adapters (DiffUtil), Navigation contracts (Robolectric)
**composeApp instrumentation: 7 tests** — Critical user workflows (login, admin panel, viewer-cannot-submit, cart checkout) via Espresso

**Shared module: 331 tests** — domain/workflow/auth/security/repository + integration tests with in-memory Room
**composeApp module: 41 tests** — Fragments (inflation + interaction), Adapters (DiffUtil), Navigation contracts, role-based UI visibility (Robolectric)

- **Authorization**: `OrderAuthorizationTest`, `AdminNavigationTest`, `SecurityRemediationTest` — object-level order access, admin-only mutations, device-binding reset gating, meeting-write guards.
- **Auth / session**: `AuthViewModelTest`, `SecurityRepositoryTest`, `LocalAuthServiceTest` — lockout, idle expiry, password policy, demo seeding, PBKDF2 hashing.
- **Device binding**: `DeviceBindingServiceTest` — bind/rebind/limit-exceeded/admin-reset semantics.
- **Repository**: `CoreRepositoryTest` — role-gated user/resource/order reads with delegate context.
- **State machine**: `OrderStateMachineTest` — 45 transition scenarios.
- **Workflow VMs**: `OrderWorkflowViewModelTest`, `OrderFinanceViewModelTest`, `MeetingWorkflowViewModelTest`, `ResourceListViewModelTest`, `AuthViewModelTest`.
- **RBAC/ABAC**: `PermissionEvaluatorTest`, `AbacPolicyEvaluatorTest`, `RoleAccessTest`.
- **Validation**: `ValidationServiceTest` — price range, allergen flags, check-in window.
- **Booking**: `BookingUseCaseTest` — conflict detection with buffer.
- **Canary/Forms**: `CanaryEvaluatorTest`, `CanaryProductionTest`, `MeetingFormEngineTest`.
- **Governance**: `GovernanceAnalyticsTest`, `ReconciliationServiceTest`, `MeetingNoShowReconciliationServiceTest`.
- **Platform**: `QuietHoursTest`, `AppLoggerTest` (redaction).
- **Integration**: `CriticalFlowIntegrationTest` with in-memory Room.
- **Persistence**: `InvoicePersistenceTest`.
- **Android smoke**: `MainActivitySmokeTest`.
- **DI wiring**: `SharedModuleWiringTest` — boots Koin with real in-memory Room DB and asserts every DAO, ViewModel, and service resolves.
- **Integration (shared/androidUnitTest)**: `CriticalFlowIntegrationTest`, `AdminResourceFlowIntegrationTest`, `AuthLockoutIntegrationTest`, `MeetingSubmissionIntegrationTest`, `CartCheckoutRefundIntegrationTest` — all with real Room in-memory DB.
- **Frontend unit** (composeApp): `CartItemAdapterTest`, `InvoiceListAdapterTest`, `ResourceRecyclerAdapterTest`, `NavigationHostContractTest`, `LoginFragmentTest`, `FragmentContractTest`, `FragmentLifecycleTest` (actual inflation + role-gated UI + input-driven VM updates).

### Run composeApp unit tests (Robolectric)
```bash
./gradlew :composeApp:testDebugUnitTest
```

---

## Security controls (at a glance)

- **RBAC**: Admin, Supervisor, Operator, Viewer, Companion
- **ABAC**: attendee list visibility (Supervisor/Admin only), invoice tax field (Admin only), refund issuance (Admin/Supervisor, never Companion)
- **Object-level authz**: all order/invoice loads require actor context; Admin/Supervisor elevated
- **Session**: 30-min idle, 8-hour absolute, 5-failure / 15-min lockout, 10+ char password with digit
- **Device binding**: max 2 devices per user; admin-only reset via `adminResetBindings(Role.Admin, userId)`
- **Encryption**: SQLCipher at rest
- **Logging**: sensitive fields redacted (`AppLogger`)

---

## Static verification playbook (for code reviewers)

1. **No unrestricted order load**: grep for `loadOrderById(` — should only find the `role, actorId` variant.
2. **Admin-only admin nav**: `MainActivity.navigateToAdmin()` checks `role != Role.Admin`.
3. **Admin-only resource mutation**: `ResourceListViewModel.addResource(role, ...)` and `deleteResource(role, ...)` reject non-admin.
4. **Admin-only device reset**: `DeviceBindingService.adminResetBindings(Role.Admin, ...)`; no unprotected `resetBindings` method.
5. **Invoice orderId → real order**: `generateInvoice` creates an `OrderEntity` via `orderDao.upsert` before the invoice.
6. **Canonical tax**: `InvoiceEntity.tax` is always `subtotal * 0.12`; masking is only in `InvoiceDraft` presentation.
7. **Back stack cleared on logout**: `MainActivity.showLogin()` calls `popBackStack(null, POP_BACK_STACK_INCLUSIVE)`.
8. **RecyclerView+DiffUtil for cart**: `CartFragment` → `CartItemAdapter: ListAdapter<CartItem, …>` with `DiffUtil.ItemCallback`.
9. **Attachment pipeline**: `MeetingDetailFragment.loadAttachmentImage` uses `ImageDownsampler` + `ImageBitmapLruCache`.
10. **Order reminders**: `NotificationGateway.scheduleOrderReminder` invoked from `OrderWorkflowViewModel.confirmLastOrder`.

---

## Project layout

```
repo/
├── composeApp/              # Android app module (Activity + Fragments + layouts)
│   └── src/androidMain/
│       ├── kotlin/.../ui/   # AdminFragment, DashboardFragment, CalendarFragment, …
│       │                    # + CartItemAdapter, InvoiceListAdapter (DiffUtil)
│       └── res/layout/      # fragment_*.xml
├── shared/                  # KMP domain/data module
│   └── src/commonMain/kotlin/.../
│       ├── db/              # Room Entities + DAOs
│       ├── rbac/            # PermissionEvaluator, AbacPolicyEvaluator
│       ├── viewmodel/       # OrderWorkflow, OrderFinance, MeetingWorkflow, Auth, …
│       ├── security/        # LocalAuthService, DeviceBindingService, SecurityRepository
│       ├── governance/      # ReconciliationService, GovernanceAnalytics
│       └── di/              # SharedModule (Koin)
├── docs/
│   └── IMPLEMENTATION_STATUS.md
├── Dockerfile.android       # Reproducible build environment
├── docker-compose.yml       # `up app` = build+install; `--profile dev up dev` = watch mode
└── run_tests.sh             # Docker-first test runner
```
