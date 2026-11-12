# GreenGauge / Ecovia — Project Audit Report

**Project**: GreenGauge / Ecovia (`com.greengauge`)
**Platform**: Native Android (Kotlin, XML + Material Design, ViewBinding enabled)
**Backend**: Firebase Authentication & Firebase Cloud Firestore
**Build System**: Gradle 8.13 (Kotlin DSL), AGP 8.13.0, Kotlin 2.2.0, `compileSdk = 36`, `targetSdk = 36`, `minSdk = 27`

---

## Executive Summary

A full static and architectural audit of all Kotlin source files, XML layouts, drawables, menus, resource values, Gradle build scripts, `AndroidManifest.xml`, and `google-services.json` uncovered **critical runtime crashes**, **broken Firebase Authentication flows**, **non-idempotent/broken Firestore CRUD and seeding**, **inconsistent carbon calculation engines**, and **severe UI/theme contrast and layout defects**.

---

## 1. Critical Runtime Crashes & Compile / Resource Hazards

| Severity | File | Issue | Impact |
|---|---|---|---|
| **Critical** | `DashboardFragment.kt` & `fragment_dashboard.xml` | `tvWelcome` initialization is commented out (`// tvWelcome = view.findViewById(R.id.tvWelcome)`), `R.id.tvWelcome` does not exist in `fragment_dashboard.xml`, yet `updateUserDashboard()` sets `tvWelcome.text = ...`. | Immediate `UninitializedPropertyAccessException` crash whenever `currentUser` emits. |
| **Critical** | `DashboardFragment.kt` | `updateUserStreak()` compares `DateUtils.getCurrentDate()` (`today`) `!= yesterday` inside `currentUser.observe`, incrementing `streakDays` on every emission. | Infinite Firestore write loop, runaway `streakDays`, and repeated toast spam. |
| **Critical** | `res/xml/network_security_config.xml` | Hardcoded `<pin-set>` SSL pins on `googleapis.com`, `firestore.googleapis.com`, `firebase.googleapis.com`, and `identitytoolkit.googleapis.com`. | Breaks Firebase Auth & Firestore HTTPS connections whenever certificates rotate or differ from pinned hashes. |
| **High** | `AuthActivity.kt` | `firebaseAuthWithGoogle()` uses `it.idToken!!` without null safety and launches detached `CoroutineScope(Dispatchers.IO)`. | NullPointerException crash if `idToken` is missing; potential Activity leak. |
| **High** | `app/src/main/res/animation` | Empty 0-byte file placed directly under `res/` instead of `res/anim/`. | Invalid/unused resource file in `res/`. |
| **Medium** | `ExampleInstrumentedTest.kt` | Asserts `assertEquals("com.ecovia", appContext.packageName)`, whereas `applicationId` is `"com.greengauge"`. | Instrumented test fails unconditionally. |

---

## 2. Authentication Bugs (Phase 2)

1. **Duplicate Login Calls (`AuthActivity.kt` + `AppViewModel.kt`)**:
   - `AuthActivity.performLogin()` calls `auth.signInWithEmailAndPassword(email, password)`, then on success calls `viewModel.authenticateUser(email, password)`, which calls `FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)` a second time.
2. **Duplicate Registration Calls (`AuthActivity.kt` + `AppViewModel.kt`)**:
   - `AuthActivity.performRegistration()` calls `auth.createUserWithEmailAndPassword(email, password)`, then calls `viewModel.registerUser(email, password, name)`, which calls `createUserWithEmailAndPassword` again — failing with `ERROR_EMAIL_ALREADY_IN_USE` on the second call.
3. **Firebase UID Overwritten (`DatabaseHelper.kt`)**:
   - `DatabaseHelper.createUser(user)` discards `user.id` (the Firebase Auth UID) and replaces it with `EncryptionUtils.generateSecureId()` (a random UUID). User documents are stored at `users/<randomUUID>` while all reads query by `FirebaseAuth.currentUser.uid`, breaking profile retrieval and Firestore security rules.
4. **Google Sign-In Broken (`AuthActivity.kt` + `AppViewModel.kt`)**:
   - `firebaseAuthWithGoogle()` attempts to reuse `viewModel.authenticateUser(email, "")` or `viewModel.registerUser(email, "", name)`. Both fail `ValidationUtils.isValidPassword("")` (`MIN_PASSWORD_LENGTH = 8`) and attempt email/password auth on an already-authenticated Google user.
   - `app/google-services.json` has `"oauth_client": []` and `strings.xml` contains an API key placeholder (`AIzaSy...`) in `default_web_client_id` instead of a valid OAuth 2.0 Web Client ID (`*.apps.googleusercontent.com`).
5. **Startup Session Hydration Missing (`MainActivity.kt` & `SplashActivity.kt`)**:
   - When an authenticated user launches the app, `MainActivity.loadInitialFragment()` calls `viewModel.initializeAppData()` but never loads `currentUser` (`users/{uid}`) or user-scoped collections, leaving `viewModel.currentUser.value == null` across all fragments.
6. **Double Navigation & Loading UI (`AuthActivity.kt` & `activity_auth.xml`)**:
   - Both `isAuthenticated` and `currentUser` observers call `navigateToMain()`.
   - `progressBar` in `activity_auth.xml` is never toggled during authentication or password reset.
   - `MainActivity.observeAuthenticationState()` risks premature redirect while `auth.currentUser != null` and `_isAuthenticated` initializes to `false`.

---

## 3. Firestore Database & Serialization Bugs (Phase 3)

1. **`createInitialTips()` Never Saves to Firestore**:
   - `MainActivity.createInitialTips()`, `DashboardFragment.createInitialTips()`, and `TipsFragment.createDefaultTips()` instantiate `listOf(Tip(...))` and discard the list without writing to Firestore.
2. **Non-Idempotent & Duplicate Seeding Across 5 Places**:
   - `MainActivity`, `DashboardFragment`, `TrackingFragment`, `ProductsFragment`, `TipsFragment`, and `ProfileFragment` independently run seeding routines.
   - `ProfileFragment.initializeUserCollections()` runs unconditionally on every view creation, creating duplicate welcome goals, empty footprints, and `user_preferences` documents.
   - `MainActivity` seeds products, challenges, and achievements with `.add(...)` (random IDs, unawaited) instead of deterministic document IDs.
3. **Broken Document IDs & Duplicate Documents**:
   - `DatabaseHelper.saveCarbonFootprint()` always generates a random ID instead of using a per-user/date or existing document ID, so updating today's footprint in `TrackingFragment` creates duplicate footprint records.
   - `DatabaseHelper.saveTrackingData()` always generates a random ID and overwrites `date = DateUtils.getCurrentDate()`, so `editTrackingData()` in `TrackingFragment` inserts a duplicate record instead of updating the existing document.
   - `DatabaseHelper.saveUserPreferences()`, `addToWishlist()`, `saveUserAchievement()`, and `saveUserChallenge()` use random UUIDs, allowing duplicate preferences, duplicate wishlist entries for the same product, and duplicate challenge enrollments.
4. **Missing CRUD Operations**:
   - Missing `updateTrackingData()`, `deleteTrackingData()`, `deleteGoal()`, `createTip()`, `toggleTipBookmark()`, `likeTip()`, `markTipCompleted()`, and real `getLeaderboardUsers()` queries.
5. **Composite Index Failures & Invalid Date Range Check**:
   - Almost all `DatabaseHelper` queries combine `.whereEqualTo(...)` with `.orderBy(...)` on a different field, which throws `FAILED_PRECONDITION` unless composite indexes are manually deployed.
   - `ValidationUtils.isValidDateRange(start, end)` requires `start < end`, which fails whenever `start == end` (e.g., every Monday for weekly range and the 1st of every month for monthly range).
   - `createInitialGoal()` and `createCarbonFootprint()` write raw maps without server timestamps, causing `orderBy("createdAt")` / `orderBy("timestamp")` queries to omit them.
6. **Silently Swallowed Errors**:
   - `DatabaseHelper.updateProductRating()` and `updateChallengeParticipantCount()` have empty `catch` blocks.
   - Nearly all `load*` methods in `AppViewModel` ignore `result.isFailure` instead of surfacing errors via `_error`.
7. **Inconsistent Carbon Calculators**:
   - `DatabaseHelper.saveTrackingData()` recalculates emissions using `CarbonCalculator` (which expects `"car"`, `"energy"`, `"meat"`, etc.), while `TrackingFragment` uses `EmissionUtils` (which uses `"Car"`, `"Electricity"`, `"Vegetables"`, `"Shopping"`, etc.). As a result, `DatabaseHelper.saveTrackingData()` overwrites calculated emissions with `0.0` for most categories.

---

## 4. Application Module Bugs (Phase 4)

- **Dashboard**: `tvWelcome` crash; `updateRecentActivities` and `updateRecentActivitiesFromTracking` overwrite/append inconsistently; quick-action buttons use `FragmentTransaction.replace` instead of syncing `BottomNavigationView`; `onBookmarkClick` and `onFavoriteClick` are no-ops; `chartProgress` does not sort chronologically or handle empty datasets cleanly.
- **Carbon Tracking**: `showAddTrackingDialog()` hardcodes transportation activities regardless of selected category; `editTrackingData()` creates duplicates instead of updating; delete activity does not exist; today's `CarbonFootprint` is not adjusted on edit/delete; GPS mode ignores location after fetching it and never updates distance.
- **Eco Products**: `recommendedProducts` observer overwrites the full `ecoProducts` list in `adapter` (often wiping out all products with an empty list); `UniversalAdapter.bindEcoProduct()` never updates `ivBookmark` icon for wishlist state; `showProductDetails()` always shows `"Add to Wishlist"` even when wishlisted; `loadProductReviews()` zeroes out review counts on all other products; `findNearbyStores()` displays hardcoded mock stores; camera permission is not checked before launching barcode scan.
- **Sustainability Tips**: `createTip()` shows a fake success toast without saving to Firestore; `onBookmarkClick()` shows a fake toast without persisting; `onFavoriteClick()` executes `tip.likes + 1` (discarded) without persisting; `showTipImplementationDialog()` awards 50 points repeatedly with no persistence; search clearing does not re-apply filters properly.
- **User Profile**: All 3 RecyclerViews share one adapter initially then recreate new adapters on every update; `btnChangePhoto` shows a placeholder dialog; `showLeaderboardDialog()` generates random mock `"EcoUser 1..9"` entries instead of querying Firestore; completing a goal or challenge awards **double points** (once in Fragment, once in `AppViewModel`).

---

## 5. UI / UX & XML Layout Defects (Phase 5)

1. **`themes.xml` & `values-v29/themes.xml`**: `colorOnSurface` and `colorOnBackground` are set to `#121212` on a `#121212` dark surface, causing black text on dark cards in `item_universal.xml` and Material components.
2. **`activity_auth.xml`**: `etEmail`, `etPassword`, `etConfirmPassword` have `android:textColor="#000000"` inside a dark card (`@drawable/card_background`), making typed input invisible; form lacks a `ScrollView` and clips on smaller screens or when the keyboard opens in Registration mode.
3. **`activity_main.xml`**: `BottomNavigationView` height is hardcoded to `100dp` with `backgroundTint="#8b8b8b"`; root uses `backgroundTint` without `background`.
4. **`fragment_dashboard.xml`**: Missing `@+id/tvWelcome`; uses `backgroundTint` without `background` on `ScrollView`; quick action button says `"Tip"`; missing empty states and loading indicator.
5. **`fragment_tracking.xml`**: `layout_weight="1"` inside vertical `LinearLayout` on `TextInputLayout`; missing empty state & loading indicator.
6. **`fragment_products.xml`**: `"Comp"` truncated button label; missing empty state & loading indicator.
7. **`fragment_tips.xml`**: `tabSelectedTextColor="#8b8b8b"` (grey when selected); `btnCreateTip` height `42dp` (< 48dp accessibility minimum); missing empty state & loading indicator.
8. **`fragment_profile.xml`**: `ivProfileImage` uses `@drawable/ic_profile` as background; missing empty states for achievements, goals, and challenges; no UI to join active challenges.
9. **`item_universal.xml`**: `ivBookmark` lacks default `src` (invisible for products); `tvBadge` overlaps `ivItemIcon`; `btnFavorite` uses `ic_menu_save`.
10. **`dialog_tip_implementation.xml`**: Missing dark background (`#2E2E2E`) while using `#FFFFFF` text.
11. **`dialog_create_tip.xml`**: Uses `#000000` background instead of `#2E2E2E`.

---

## 6. Gradle, Dependencies & Repository Hygiene

1. **Duplicate Dependency**: `implementation(libs.play.services.auth)` appears twice in `app/build.gradle.kts`.
2. **Unused Catalog Entry**: `play-services-auth-v2120` in `gradle/libs.versions.toml`.
3. **Missing Test Dependencies**: No `kotlinx-coroutines-test`, `androidx.arch.core:core-testing`, or unit test suites covering authentication, carbon math, ViewModel state, or Firestore helper behavior.
4. **Repository Hygiene**: `.DS_Store` files, `.idea/workspace.xml` / `.idea/deploymentTargetSelector.xml` / etc., and `local.properties` should not be committed; `.gitignore` needs hardening for Android, macOS, and Gradle artifacts.

