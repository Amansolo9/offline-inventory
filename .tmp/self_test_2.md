# Task-136 Offline Operations Suite - Delivery Acceptance & Architecture Audit

---

## 1. Verdict

**Overall Conclusion: Partial Pass**

The project delivers a substantive, architecturally sound Kotlin Multiplatform Android application that addresses the majority of the prompt's requirements with real business logic, not stubs. Core flows for meetings, ordering, cart/invoice, RBAC/ABAC, authentication with lockout/session/device binding, booking conflict detection, order state machine, reconciliation, and learning modules are implemented end-to-end with persistence via Room. The codebase has 16 unit test files with 120+ assertions covering security, RBAC, order states, booking, reconciliation, and more. However, several explicit prompt requirements are missing or incomplete (notably: password policy not enforced during login, object-level authorization missing in OrderStateMachine, cart items not cleared after invoice generation, delegation identity not validated for Companion role), and certain features like split/merge at order-level (vs. cart-level) and full attachment handling are partial. The project is a strong deliverable but falls short of full acceptance due to these gaps, particularly the security-relevant ones.

---

## 2. Scope and Static Verification Boundary

### Reviewed
- All Kotlin source files in `shared/` and `composeApp/` (~100 files)
- All XML layouts, Gradle build files, `settings.gradle.kts`, `AndroidManifest.xml`
- Room entity definitions, DAOs, database schema (`schemas/*/6.json`)
- All 16 unit test files in `shared/src/commonTest/`
- 1 instrumented test file (`MainActivitySmokeTest.kt`)
- `README.md`, `run_tests.sh`, `data_dictionary_v1.json`
- DI wiring (`SharedModule.kt`, `AppDi.kt`, `DatabaseModule.kt`)

### Not Reviewed / Not Executed
- No project build was attempted
- No Docker execution
- No tests were run
- No emulator/device testing
- No runtime behavior verification
- Gradle build success cannot be confirmed statically (alpha-version Room KMP dependencies)

### Claims Requiring Manual Verification
- Whether the project compiles and builds successfully with the alpha Room KMP dependencies
- Whether `withTransaction` works correctly in the KMP context (alpha API)
- 60fps scrolling performance with 5000+ rows (runtime metric)
- SQLite query performance under 50ms on 100k records (runtime metric)
- PDF receipt generation and sharing via FileProvider (runtime Android test)

---

## 3. Repository / Requirement Mapping Summary

### Core Business Goal
Design an offline-first Android app unifying meeting reservations, meal/kit ordering, and employee learning with role-based access, local auth, order state machine, conflict detection, reconciliation, and data governance.

### Core Flows Mapped
| Prompt Requirement | Implementation Area | Status |
|---|---|---|
| Meeting reservations + approval/deny | `MeetingWorkflowViewModel`, `MeetingDao`, `BookingUseCase` | Implemented |
| Check-in within 10-min window, no-show | `ValidationService.isWithinSupervisorWindow`, `MeetingWorkflowViewModel.checkIn/markNoShowIfDue` | Implemented (deviation noted) |
| Attendee management + agenda | `MeetingWorkflowViewModel.addAttendee/updateAgenda`, `MeetingAttendeeEntity` | Implemented |
| Order state machine + transactions | `OrderStateMachine` with `withTransaction` | Implemented |
| Shopping cart + invoice | `OrderFinanceViewModel`, `CartDao` | Implemented |
| Split/merge for partial fulfillment | `OrderStateMachine.splitOrder/mergeOrders` + `OrderFinanceViewModel.splitFirstItem/mergeFirstTwoItems` | Implemented |
| Delivery confirmation + signature | `OrderStateMachine.confirmDelivery` with signature param | Implemented |
| Return/exchange/refund workflows | `OrderStateMachine` transitions | Implemented |
| Payment recording (cash/wallet/external) | `PaymentMethod` enum, `OrderEntity.paymentMethod` | Implemented |
| Receipt PDF + share | `ReceiptService.android.kt` with `PdfDocument` + `FileProvider` | Implemented |
| Auto-cancel unpaid after 30 min + restock | `OrderStateMachine.transitionToPendingTender` with `delay(30.minutes)` | Implemented |
| Local auth with password policy | `SecurityRepository.validatePassword` + `LocalAuthService` | Partial (see issues) |
| 5-attempt lockout for 15 min | `SecurityRepository.recordFailure/canAuthenticate` | Implemented |
| Session auto-expire 30 min idle | `AuthViewModel.ensureSessionActive` + `App.kt` polling | Implemented |
| Device binding (2 max, admin reset) | `DeviceBindingService` | Implemented |
| RBAC/ABAC per feature/record/field | `PermissionEvaluator` + `AbacPolicyEvaluator` | Implemented |
| Sensitive field encryption at rest | SQLCipher via `SecurePassphraseProvider` + `EncryptedRoomConfig` | Implemented |
| Log masking | `AppLogger.redact` with regex patterns | Implemented |
| Conflict detection + 3 slot suggestions | `BookingUseCase.findThreeAvailableSlots` with 10-min buffer | Implemented |
| Quiet hours (9PM-7AM) | `NotificationScheduler.scheduleWithQuietHours` | Implemented |
| Reconciliation + settlement cycles | `ReconciliationService` with Friday 6PM config | Implemented |
| Data dictionary / form engine | `DynamicFormEngine.kt` + `data_dictionary_v1.json` | Implemented (minimal) |
| Canary rollout | `CanaryConfig.kt` | Implemented |
| Employee learning | `LearningViewModel`, `LearningDao`, `CourseEntity`, `EnrollmentEntity` | Implemented |
| RecyclerView + DiffUtil | `ResourceRecyclerAdapter`, `CourseRecyclerAdapter` with `ListAdapter` + `DiffUtil.ItemCallback` | Implemented |
| Image LRU cache (20MB) | `ImageBitmapLruCache(maxBytes = 20 * 1024 * 1024)` | Implemented |
| Image downsampling | `ImageDownsampler.android.kt` with `inSampleSize` | Implemented |
| Composite indexes | `Entities.kt` with `resourceId+startTime`, `state+createdAt` etc. | Implemented |

---

## 4. Section-by-Section Review

### 4.1 Hard Gates

#### 4.1.1 Documentation and Static Verifiability
**Conclusion: Pass**

- `README.md` provides clear build/test/run instructions with both bash and PowerShell commands (`README.md:19-58`)
- Entry point documented: `MainActivity` (`README.md:41`)
- Test commands documented: `./run_tests.sh` and direct Gradle (`README.md:53-66`)
- Demo accounts with credentials listed (`README.md:70-76`)
- Static verification playbook provided (`README.md:79-106`)
- `settings.gradle.kts` correctly includes `:shared` and `:composeApp` (`settings.gradle.kts:19-20`)

#### 4.1.2 Material Deviation from Prompt
**Conclusion: Pass**

The implementation is centered on the described business goal: offline workplace nutrition and resource management. All major subsystems (meetings, orders, cart, learning, RBAC, reconciliation) address the prompt. The addition of a desktop target is supplementary, not a deviation. No part of the implementation is unrelated to the prompt.

### 4.2 Delivery Completeness

#### 4.2.1 Core Requirements Coverage
**Conclusion: Partial Pass**

Most requirements are implemented with real logic. Issues:

1. **Password policy not enforced during login flow**: `SecurityRepository.validatePassword()` exists (`SecurityRepository.kt:20-26`) but is never called in `AuthViewModel.login()` (`AuthViewModel.kt:57-98`). Users can log in with passwords that violate the policy.
2. **Demo passwords violate 10-char minimum**: `"Viewer1234"` is 10 chars (OK), but `"Companion1!"` is 11 chars (OK). All demo passwords actually meet the 10-char requirement upon re-check. However, the policy check is still not enforced at login.
3. **Scoped storage handling**: Prompt requires "Android 10+ scoped storage" compliance. The receipt PDF uses `cacheDir` (`ReceiptService.android.kt:39`) which is correct for scoped storage, but no other file operations demonstrate scoped storage handling for attachments.

#### 4.2.2 End-to-End Deliverable
**Conclusion: Pass**

- Complete KMP project structure with two modules
- Real Gradle build files with proper dependency declarations
- Android manifest with launcher activity, FileProvider, BroadcastReceiver
- XML layouts for all screens
- Both Android Views (Fragments) and Compose (Screens) UI paths
- Room database with 11 entities, 9 DAOs, schema versioning
- 16 unit test files + 1 instrumented test
- `README.md` with bootstrap instructions

### 4.3 Engineering and Architecture Quality

#### 4.3.1 Project Structure and Module Decomposition
**Conclusion: Pass**

- Clean separation: `:shared` (domain/data) and `:composeApp` (UI/Android)
- Logical package structure: `db/`, `rbac/`, `security/`, `orders/`, `governance/`, `viewmodel/`, `services/`, `platform/`, `config/`
- DI via Koin with proper module decomposition (`SharedModule.kt`, `DatabaseModule.kt`, `AppDi.kt`)
- Platform-specific implementations via `expect/actual` pattern (`NotificationScheduler`, `ReceiptService`, `ImageDownsampler`, `PasswordHashing`, `DeviceFingerprintProvider`)
- No redundant or unnecessary files observed

#### 4.3.2 Maintainability and Extensibility
**Conclusion: Pass**

- State machine pattern for orders with clear state enum and guard conditions
- Permission system is data-driven via `PermissionRule` list, easily extensible
- ABAC evaluator with context-based decisions
- Form engine supports versioned layouts via JSON data dictionary
- Canary config supports rollout by role/device group
- Settlement config is parameterized (`SettlementConfig` data class)

### 4.4 Engineering Details and Professionalism

#### 4.4.1 Error Handling, Logging, Validation
**Conclusion: Partial Pass**

**Strengths:**
- Structured logging with `AppLogger` featuring severity levels and sensitive-data redaction (`AppLogger.kt:12-20`)
- Validation service for prices and allergen flags (`ValidationService.kt`)
- Order state machine validates price boundaries and state guards
- Comprehensive error messages in auth flow (lockout, device limit, invalid credentials)

**Weaknesses:**
- Receipt generation swallows exceptions silently with `catch (_: Exception)` (`ReceiptService.android.kt:59`)
- Notification scheduling swallows exceptions (`OrderFinanceViewModel.kt:202-203`)
- `println` used instead of `AppLogger` in receipt error handling (`ReceiptService.android.kt:60`)

#### 4.4.2 Real Product vs Demo
**Conclusion: Partial Pass**

- The project is structured as a real application with proper manifest, build configs, and Android lifecycle integration
- Demo seed data is controlled by `isDebug` flag (`LocalAuthService.kt:17-19`)
- However, `CartFragment` and the Compose `CartScreen` use `addDemoItem()` internally (hardcoded `"res-1"` resource, `$49.99` price), which couples demo behavior into production UI code

### 4.5 Prompt Understanding and Requirement Fit

#### 4.5.1 Business Goal Accuracy
**Conclusion: Partial Pass**

The core business objective is correctly implemented. Issues:

1. **Check-in window semantics**: Prompt says "check-in within a 10-minute window around the start time (otherwise the reservation is marked 'no-show')". The code uses `isWithinSupervisorWindow` which checks `diff.inWholeMinutes in -10..10` (`ValidationService.kt:13`), allowing check-in up to 10 minutes BEFORE start. The prompt implies the window is around start time for check-in, and no-show after 10 minutes past start. The auto-no-show timer at `start + 10 minutes` (`MeetingWorkflowViewModel.kt:269`) is correct, but the check-in window allowing 10 min early is reasonable.

2. **Companion refund restriction**: Prompt says "Companions... cannot approve or issue refunds". `OrderStateMachine.requestRefund` blocks Companion (`OrderStateMachine.kt:197`). `AbacPolicyEvaluator.canIssueRefund` also blocks Companion (`AbacPolicyEvaluator.kt:37`). However, `InvoiceDetailScreen.kt:117` enables the refund button for `Admin` or `Supervisor` string comparison, which is correct.

3. **Notes/tags on orders**: `OrderEntity` has `notes` and `tags` fields (`Entities.kt:77-78`), but there's no UI to set them.

### 4.6 Aesthetics

#### 4.6.1 Visual and Interaction Design
**Conclusion: Pass**

- Consistent color palette: Purple (#6C5CE7), Green (#00B894), Coral (#FF7675), Amber (#FDAA48), Blue (#74B9FF) used across all screens
- Login screen has gradient background, animated entry, card-based form with elevation
- Dashboard has header with role badge, stats row, navigation buttons, RecyclerView list
- Material Design components used consistently (MaterialButton, TextInputLayout, CardView, RecyclerView)
- Error states are visually distinct (coral/red containers with error text)
- Compose screens use Material3 with consistent theming (`Task136Theme`)
- Both Fragment-based (Android Views) and Compose paths share the same design language
- Action buttons use color-coded tonal buttons for different operations (Confirm=Green, Ship=Blue, Refund=Coral)
- Loading indicators present (ProgressBar in Fragment views)

---

## 5. Issues / Suggestions (Severity-Rated)

### Issue 1 - High: Password Policy Not Enforced at Login
**Severity: High**
**Conclusion:** `SecurityRepository.validatePassword()` is defined but never called during the `AuthViewModel.login()` flow.
**Evidence:** `AuthViewModel.kt:57-98` - no call to `validatePassword` anywhere in `login()`. `SecurityRepository.kt:20-26` defines the validation.
**Impact:** Users can authenticate with passwords that violate the minimum 10-character + 1-number policy. The policy is only useful for registration (which doesn't exist as a user-facing feature).
**Minimum Fix:** Add `securityRepository.validatePassword(snapshot.password)` check at the start of `login()` before calling `authService.authenticate()`, or validate at registration/password-change time.

### Issue 2 - Medium: Cart Not Cleared After Invoice Generation
**Severity: Medium**
**Conclusion:** `OrderFinanceViewModel.generateInvoice()` creates an invoice from cart items but never clears the cart afterward.
**Evidence:** `OrderFinanceViewModel.kt:171-212` - the cart state and `cartDao` entries are not cleared after invoice generation.
**Impact:** Users can generate duplicate invoices from the same cart items, leading to financial discrepancies.
**Minimum Fix:** After successful invoice generation, set `_state.value = _state.value.copy(cart = emptyList(), ...)` and call `cartDao.deleteAllForUser(ownerId)`.

### Issue 3 - Medium: Silent Exception Swallowing in Receipt/Notification Paths
**Severity: Medium**
**Conclusion:** Multiple catch blocks silently discard exceptions without logging through the proper `AppLogger`.
**Evidence:** `OrderFinanceViewModel.kt:202-203` (`catch (_: Exception) { /* notification scheduling is best-effort */ }`), `OrderFinanceViewModel.kt:208-209`, `ReceiptService.android.kt:59-60` (uses `println` instead of `AppLogger`).
**Impact:** Failures in receipt generation or notification scheduling will be invisible, making troubleshooting impossible.
**Minimum Fix:** Replace silent catches with `AppLogger.w(TAG, "Notification scheduling failed: ${e.message}")`.

### Issue 4 - Medium: Order-Level Split/Merge Uses In-Memory State, Not Actual Orders
**Severity: Medium**
**Conclusion:** `OrderFinanceViewModel.splitFirstItem/mergeFirstTwoItems` operates on cart items, not placed orders. `OrderStateMachine.splitOrder/mergeOrders` operates on real orders but is never called from any ViewModel or UI.
**Evidence:** `OrderFinanceViewModel.kt:129-168` (cart-level split/merge). `OrderStateMachine.kt:268-333` (order-level, unused from UI).
**Impact:** The prompt's "split/merge for partial fulfillment" requirement refers to order-level operations. The cart-level operations are useful but don't fulfill the requirement for partial fulfillment of placed orders.
**Minimum Fix:** Wire `OrderStateMachine.splitOrder/mergeOrders` to `OrderWorkflowViewModel` and expose them in the `OrderDetailScreen`.

### Issue 5 - Medium: Hardcoded Demo Resource in Calendar Fragment
**Severity: Medium**
**Conclusion:** `CalendarFragment.kt:49` hardcodes `resourceId = "res-1"` for meeting submission. Users cannot select which resource to book.
**Evidence:** `CalendarFragment.kt:49`
**Impact:** All meetings are booked against the same resource regardless of user selection. The prompt requires browsing a room/equipment calendar.
**Minimum Fix:** Add a resource selector (dropdown or list) before meeting submission, passing the selected resourceId.

### Issue 6 - Medium: No Admin Resource/Business-Rule Management UI
**Severity: Medium**
**Conclusion:** The prompt specifies "Admins manage resources, business rules, and access policies." No admin UI for CRUD on resources, rules, or policies exists.
**Evidence:** No admin fragment or screen found in `composeApp/src/androidMain/kotlin/.../ui/` or `composeApp/src/commonMain/kotlin/.../ui/`. `ResourceDao` has `upsert/upsertAll/update` methods but no UI calls them from an admin context.
**Impact:** Admin resource management must be done directly via database, not through the app.
**Minimum Fix:** Add an admin screen with resource CRUD, accessible only to Admin role.

### Issue 7 - High: Delegation Identity Not Validated for Companion Role
**Severity: High**
**Conclusion:** The `AbacPolicyEvaluator` checks `context.isDelegate` but never validates that the requesting Companion user is actually the authorized delegate for the target owner.
**Evidence:** `AbacPolicyEvaluator.kt:25` - `context.isDelegate` is a boolean passed by the caller. `MeetingDetailFragment.kt:52` passes `delegateFor != null` as `isDelegate`, but this only checks if the *logged-in* user has a `delegateForUserId` set — it doesn't verify the delegate relationship matches the specific owner being acted upon.
**Impact:** A Companion user with delegation for User A could potentially pass `isDelegate = true` when acting on User B's resources, bypassing the ownership check. The UI currently prevents this by using the auth state's `delegateForUserId`, but the business logic layer doesn't enforce it independently.
**Minimum Fix:** In `AbacPolicyEvaluator.canManageAttendee()`, verify that `context.requesterId`'s `delegateForUserId` matches `context.ownerId` rather than relying solely on a boolean flag.

### Issue 8 - Medium: Device Binding Checked After Authentication
**Severity: Medium**
**Conclusion:** In `AuthViewModel.login()`, device binding is checked *after* successful authentication, not during it.
**Evidence:** `AuthViewModel.kt:78-85` — `deviceBindingService.checkAndBindDevice()` is called only after `authService.authenticate()` returns a valid principal (line 71).
**Impact:** An attacker with valid credentials can authenticate on an unlimited number of devices before the binding check runs. The password hash comparison and credential validation complete before the device limit is enforced. While the login ultimately fails, the successful authentication step (including `securityRepository.recordSuccess()` at line 88 which resets the lockout counter) runs before the device check.
**Minimum Fix:** Move `recordSuccess()` to after the device binding check, or check device binding before authentication.

### Issue 9 - Low: ImageBitmapLruCache Not Thread-Safe
**Severity: Low**
**Conclusion:** `ImageBitmapLruCache` uses a plain `linkedMapOf` without synchronization.
**Evidence:** `ImageBitmapLruCache.kt:8` - `private val map = linkedMapOf<String, CacheEntry>()`; no mutex, lock, or synchronized block.
**Impact:** Concurrent get/put from multiple coroutines could cause data corruption or crashes. Currently likely called from UI thread only, but the expect/actual pattern with suspend functions in `ImageDownsampler` could lead to multi-threaded access.
**Minimum Fix:** Wrap access in a `Mutex` or use `Collections.synchronizedMap()`.

### Issue 10 - Low: Demo Seed Passwords in Source Code
**Severity: Low**
**Conclusion:** Demo credentials are hardcoded in `LocalAuthService.kt:22-27` and duplicated in `README.md:70-76`.
**Evidence:** `LocalAuthService.kt:22-27`, `README.md:70-76`
**Impact:** In debug-only builds (`enableDemoSeed = isDebug`), so production risk is low. The flag is controlled by DI at `AppDi.kt:15`.
**Minimum Fix:** Acceptable for debug builds. Consider moving to a separate test fixture.

### Issue 11 - Low: ViewModel Instances Are Koin Singletons
**Severity: Low**
**Conclusion:** All ViewModels are registered as `single` in Koin, not `factory` or `viewModel`.
**Evidence:** `SharedModule.kt:48-112` - all ViewModel registrations use `single { ... }`.
**Impact:** ViewModels survive configuration changes but also survive user logout/login across different accounts. While `clearSessionState()` is called on logout, any race condition or missed clear could leak state between users.
**Minimum Fix:** Consider using `factory` for ViewModels or ensuring `clearSessionState()` is comprehensive.

### Issue 12 - Low: Missing Schema Version 4
**Severity: Low**
**Conclusion:** Schema directory jumps from version 3 to 5 (no `4.json`).
**Evidence:** `shared/schemas/com.eaglepoint.task136.shared.db.AppDatabase/` contains 1.json, 2.json, 3.json, 5.json, 6.json. Version 4 is missing.
**Impact:** Migration path from version 3 to 4 would fail if anyone has a database at version 4. Since this is a new app, practical risk is low.
**Minimum Fix:** Ensure migration path is complete or document the gap.

### Issue 13 - Low: No Order Notes/Tags UI
**Severity: Low**
**Conclusion:** `OrderEntity` has `notes` and `tags` fields but no UI to set or view them.
**Evidence:** `Entities.kt:77-78`, no reference to notes/tags in any UI file.
**Impact:** Data model supports the prompt's "notes/tags" requirement but it's not user-accessible.
**Minimum Fix:** Add text fields for notes and tags in `OrderDetailScreen`/`OrderDetailFragment`.

---

## 6. Security Review Summary

### Authentication Entry Points
**Conclusion: Pass**
- Single entry: `AuthViewModel.login()` (`AuthViewModel.kt:57`)
- Validates username non-blank, checks lockout via `securityRepository.canAuthenticate()`, authenticates via `LocalAuthService.authenticate()`, checks device binding
- Password hashed with PBKDF2 (`PasswordHashing.kt` expect/actual with `PasswordHashing.android.kt`)
- Constant-time comparison used (`LocalAuthService.kt:52`)
- Lockout after 5 failures for 15 minutes (`SecurityRepository.kt:17-18`)
- Session idle timeout 30 min + absolute 8-hour limit (`AuthViewModel.kt:42-43`)

### Route-Level Authorization
**Conclusion: Pass**
- `AppNavigator.isScreenAllowed()` checks role before navigation (`AppNavigation.kt:42-48`)
- `App.kt:104,118,131,145` checks `isScreenAllowed` before rendering each screen, redirecting to Dashboard on failure
- Entire UI gated behind `authState.isAuthenticated` check (`App.kt:63`)

### Object-Level Authorization
**Conclusion: Partial Pass**
- `OrderDao` has `getByIdForActor()` and `getByIdForOwnerOrDelegate()` methods for owner-scoped queries (`OrderDao.kt`)
- `MeetingDao` has `getByIdForOrganizer()` for organizer-scoped access (`MeetingDao.kt`)
- `CartDao` has `deleteByIdForUser()` for user-scoped deletion
- **Gap:** `OrderStateMachine` uses `database.orderDao().getById(orderId)` without user filtering in most transitions (`OrderStateMachine.kt:84,126,137...`), meaning any authenticated user could transition any order if they know the ID

### Function-Level Authorization
**Conclusion: Pass**
- `PermissionEvaluator.canAccess()` enforced in ViewModels before operations (`OrderFinanceViewModel.kt:79`, `MeetingWorkflowViewModel.kt:206,225,236`)
- `AbacPolicyEvaluator` enforced for attendee read/write, invoice tax, refund (`AbacPolicyEvaluator.kt`)
- Order state machine checks `Role.Viewer` and `Role.Companion` restrictions per operation
- Device trust checked via `DeviceBindingService.isDeviceTrusted()` in ABAC contexts

### Tenant / User Data Isolation
**Conclusion: Partial Pass**
- Cart items are user-scoped via `userId` field and `deleteByIdForUser()` in `CartDao`
- Meetings scoped by `organizerId` for non-admin roles (`MeetingWorkflowViewModel.kt:161-163`)
- **Gap:** As noted above, `OrderStateMachine` doesn't scope by user. Order transitions are protected by role but not by ownership.

### Admin / Internal / Debug Protection
**Conclusion: Pass**
- Demo seed only runs when `isDebug = true` AND no users exist (`LocalAuthService.kt:19-20`)
- `isDebug` flag passed from `AppDi.kt:15` via `BuildConfig.DEBUG` (not directly visible but standard Android pattern)
- No debug endpoints, no exposed admin APIs
- Device binding reset restricted to Admin role only (`DeviceBindingService.kt:39`)

---

## 7. Tests and Logging Review

### Unit Tests
**Conclusion: Pass**
- 16 test files in `shared/src/commonTest/` covering all major business logic modules
- Test framework: `kotlin.test` with `kotlinx-coroutines-test`
- Tests use fake DAOs (in-memory implementations) and fixed/mutable clocks for deterministic behavior
- `OrderStateMachineTest`: 30+ tests covering all state transitions, role guards, inventory restock, expiry, nonexistent orders, null-role defense
- `AuthViewModelTest`: 8 tests covering valid/invalid credentials, session expiry, touch extends session, device limit, absolute session limit, logout
- `SecurityRepositoryTest`: password policy validation, 5-attempt lockout with expiry
- `AbacPolicyEvaluatorTest`: 22 tests covering all role/context combinations for attendee read/manage, invoice tax, refund, untrusted device blocking
- `PermissionEvaluatorTest`: 8 tests covering admin full access, viewer read-only, companion write-no-approve, supervisor approve
- `BookingUseCaseTest`: 10-minute buffer overlap, 3-slot recommendation

### API / Integration Tests
**Conclusion: Partial Pass**
- 1 instrumented test (`MainActivitySmokeTest.kt`) - minimal, only verifies activity launch
- No integration tests with real Room database
- Room transaction behavior (`withTransaction`) not tested (fake bypasses it)

### Logging Categories / Observability
**Conclusion: Pass**
- `AppLogger` supports DEBUG/INFO/WARN/ERROR levels (`AppLogger.kt:3`)
- Configurable minimum level and sink (`AppLogger.kt:6-9`)
- All state machine transitions logged at INFO level (`OrderStateMachine.kt:92,98,129,145...`)
- Reconciliation logged at INFO/WARN (`ReconciliationService.kt:60,106-107`)
- Anomaly alerts logged via `RuleHitObserver` with WARN level (`SharedModule.kt:103-104`)

### Sensitive-Data Leakage Risk
**Conclusion: Pass**
- `AppLogger.redact()` strips password, passwordHash, passwordSalt, fingerprint, walletRef, encryptedWalletRef, maskedPII patterns (`AppLogger.kt:12-19`)
- `AppLoggerTest` verifies redaction works (`AppLoggerTest.kt:10-39`)
- `MaskAllButLast4` visual transformation for sensitive UI fields (`MaskAllButLast4.kt:8-14`)
- Passwords cleared from state after login (`AuthViewModel.kt:92`)
- SQLCipher used for database encryption (`EncryptedRoomConfig.android.kt` + `SecurePassphraseProvider.kt`)
- **Minor concern:** `ReceiptService.android.kt:60` uses `println` with error message which could include exception details

---

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview

| Aspect | Detail |
|---|---|
| Unit test files | 16 files in `shared/src/commonTest/` |
| Instrumented test files | 1 file in `composeApp/src/androidInstrumentedTest/` |
| Test framework | `kotlin.test`, `kotlinx-coroutines-test`, Espresso (instrumented) |
| Test entry point | `./gradlew :shared:testDebugUnitTest` or `./run_tests.sh` |
| Documentation | `README.md:53-66` provides test commands |

### 8.2 Coverage Mapping Table

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion | Coverage | Gap | Min Test Addition |
|---|---|---|---|---|---|
| Password policy (10 char + number) | `SecurityRepositoryTest.kt:16-21` | `assertFalse("short")`, `assertFalse("longpassword")`, `assertTrue("longpassword1")` | Sufficient | - | - |
| 5-attempt lockout 15 min | `SecurityRepositoryTest.kt:24-49` | `repeat(5) recordFailure`, `assertFalse canAuthenticate`, advance 16 min, `assertTrue` | Sufficient | - | - |
| Valid credential login | `AuthViewModelTest.kt:46-54` | `assertTrue(isAuthenticated)` after correct credentials | Sufficient | - | - |
| Invalid credential rejection | `AuthViewModelTest.kt:57-67` | `assertFalse(isAuthenticated)`, `assertNotNull(error)` | Sufficient | - | - |
| Session idle timeout (30 min) | `AuthViewModelTest.kt:70-82` | Advance clock 31 min, `assertFalse(isAuthenticated)` | Sufficient | - | - |
| Session touch extends | `AuthViewModelTest.kt:110-125` | Touch at +25 min, check at +50 min, still authenticated | Sufficient | - | - |
| Absolute session limit | `AuthViewModelTest.kt:163-192` | 2-hour absolute, touch doesn't prevent expiry | Sufficient | - | - |
| Device binding limit | `AuthViewModelTest.kt:128-161` | 2 devices bound, new device blocked | Sufficient | - | - |
| Order state: confirm from PendingTender | `OrderStateMachineTest.kt:145-153` | `assertTrue(confirm)`, state="Confirmed" | Sufficient | - | - |
| Order state: cancel with restock | `OrderStateMachineTest.kt:90-99` | state="Cancelled", availableUnits restored | Sufficient | - | - |
| Order state: return flow | `OrderStateMachineTest.kt:197-265` | ReturnRequested from Confirmed/Delivered, completeReturn restocks | Sufficient | - | - |
| Order state: refund flow | `OrderStateMachineTest.kt:268-318` | RefundRequested from Confirmed/Returned, completeRefund restocks | Sufficient | - | - |
| Order state: exchange flow | `OrderStateMachineTest.kt:323-339` | ExchangeRequested from Confirmed | Sufficient | - | - |
| Order state: delivery flow | `OrderStateMachineTest.kt:343-364` | AwaitingDelivery -> Delivered with signature | Sufficient | - | - |
| Order state: expiry restock | `OrderStateMachineTest.kt:369-379` | Stale PendingTender cancelled, units restocked | Sufficient | - | - |
| Order state: Viewer blocked | `OrderStateMachineTest.kt:134-140,185-192,225-231` | `assertFalse` for Viewer on cancel/confirm/return | Sufficient | - | - |
| Companion cannot refund | `OrderStateMachineTest.kt:299-303` | `assertFalse(requestRefund("o1", Role.Companion))` | Sufficient | - | - |
| RBAC admin full access | `PermissionEvaluatorTest.kt:11-16` | All ResourceTypes, Read+Write | Sufficient | - | - |
| RBAC viewer read-only | `PermissionEvaluatorTest.kt:19-24` | Read=true, Write=false | Sufficient | - | - |
| RBAC companion no-approve | `PermissionEvaluatorTest.kt:27-31` | Read+Write=true, Approve=false | Sufficient | - | - |
| ABAC invoice tax admin-only | `AbacPolicyEvaluatorTest.kt:13-35` | Admin trusted=true, all others false | Sufficient | - | - |
| ABAC untrusted device blocks all | `AbacPolicyEvaluatorTest.kt:155-166` | All roles blocked on untrusted device | Sufficient | - | - |
| ABAC attendee read supervisor/admin only | `AbacPolicyEvaluatorTest.kt:76-129` | Admin/Supervisor=true, Operator/Companion/Viewer=false | Sufficient | - | - |
| Booking conflict detection + buffer | `BookingUseCaseTest.kt:17-38` | 5-min gap with 10-min buffer = overlap | Sufficient | - | - |
| Booking 3 slot suggestions | `BookingUseCaseTest.kt:40-72` | Returns exactly 3 slots | Sufficient | - | - |
| Meeting submit -> approve -> checkin | `MeetingWorkflowViewModelTest.kt:84-93` | Status ends at CheckedIn | Sufficient | - | - |
| Meeting deny | `MeetingWorkflowViewModelTest.kt:97-104` | Status=Denied | Sufficient | - | - |
| Price validation boundaries | `OrderStateMachineTest.kt:51-81` | min/max/zero/negative/normal prices | Sufficient | - | - |
| Log redaction | `AppLoggerTest.kt:10-44` | Password, hash, fingerprint, walletRef redacted | Sufficient | - | - |
| Log level filtering | `AppLoggerTest.kt:48-68` | WARN level filters DEBUG/INFO | Sufficient | - | - |
| Reconciliation/settlement | `ReconciliationServiceTest.kt` (file exists) | Cannot Confirm (not read in full) | Basically Covered | - | - |
| Canary config | `CanaryEvaluatorTest.kt` (file exists) | Cannot Confirm (not read in full) | Basically Covered | - | - |
| Allergen validation | `ValidationServiceTest.kt` (file exists) | Cannot Confirm (not read in full) | Basically Covered | - | - |
| Cart add/split/merge UI | No test | - | Missing | No UI test for cart operations | Add OrderFinanceViewModel unit tests |
| Invoice generation | No test | - | Missing | No test for invoice creation flow | Add test for `generateInvoice()` |
| Receipt PDF generation | No test | - | Missing | Platform-specific, hard to unit test | Add instrumented test |
| Object-level order authorization | No test | - | Missing | OrderStateMachine doesn't filter by user | Add test verifying user can't transition other's orders |
| No-show auto-marking | No test | - | Missing | Coroutine delay behavior not tested | Add test with advancing clock |
| Quiet hours notification suppression | `QuietHoursTest.kt` (file exists) | Cannot Confirm (not read in full) | Basically Covered | - | - |

### 8.3 Security Coverage Audit

| Security Dimension | Test Coverage | Assessment |
|---|---|---|
| Authentication | `AuthViewModelTest`: valid/invalid login, lockout (via `SecurityRepositoryTest`), blank username | Sufficient |
| Route authorization | `AppNavigator.isScreenAllowed` tested implicitly via navigation, `PermissionEvaluatorTest` covers role-resource matrix | Basically Covered |
| Object-level authorization | `OrderDao` has owner-scoped queries but `OrderStateMachine` doesn't use them; no test verifying cross-user access is blocked | **Insufficient** |
| Tenant/data isolation | Cart operations are user-scoped; meetings filtered by organizer for non-admin. No cross-user isolation test | Insufficient |
| Admin/internal protection | Device binding reset admin-only tested implicitly. Debug seed controlled by flag. No dedicated test | Basically Covered |

### 8.4 Final Coverage Judgment

**Conclusion: Partial Pass**

**Covered risks:** Authentication flow (valid/invalid/lockout/expiry/device-limit), all order state transitions with role guards, RBAC/ABAC policy enforcement for all roles, booking conflict detection with buffer, price validation boundaries, log redaction of sensitive data.

**Uncovered risks that could allow severe defects to remain undetected:**
1. **Object-level authorization on orders**: No test verifies that User A cannot transition User B's order. The `OrderStateMachine` uses unscoped `getById()`, so cross-user manipulation is possible and untested.
2. **Invoice/cart lifecycle**: No test for the generate-invoice or cart operations, meaning financial calculation errors could go undetected.
3. **Room transaction integrity**: All tests bypass `withTransaction` via fake, so actual database transaction behavior is untested.

---

## 9. Final Notes

This is a substantive, well-structured KMP Android application that demonstrates genuine domain modeling rather than scaffolding or placeholder code. The order state machine with 13 states and comprehensive transition guards, the RBAC/ABAC dual-layer authorization system, the booking conflict detection with configurable buffers, and the reconciliation/settlement engine all reflect thoughtful implementation aligned with the prompt.

The most material gap is the object-level authorization in the `OrderStateMachine`, which bypasses user scoping on all transitions. This is a security concern that would need to be addressed before production use. The second most material gap is the unused wiring of `OrderStateMachine.splitOrder/mergeOrders` from any UI, meaning the order-level partial fulfillment requirement is implemented but not accessible.

Test coverage is strong for pure business logic (state transitions, RBAC, validation, auth flow) but weak for integration scenarios (Room transactions, cross-user access, invoice lifecycle).
