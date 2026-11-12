# Testing Guide — GreenGauge / Ecovia

## Automated Test Suite

### Unit Tests (`app/src/test/java/com/greengauge/`)

1. **`CarbonCalculatorAndUtilsTest.kt`**
   - Transportation emission factors (`Car`, `Bus`, `Train`, `Subway`, `Taxi`, `Motorcycle`, `Walking`, `Bicycle`)
   - Energy (`Electricity`, `Gas`), Diet (`Meat`, `Vegetables`), Waste (`Recycling` negative credit, `Landfill`), Water (`Drinking`), Shopping (`Clothes`)
   - Zero and negative input clamping
   - `CarbonCalculator.calculateTotalEmissions` aggregation
   - `ValidationUtils` (email, password, name, date range)
   - `SustainabilityUtils` (level, progress percentage, goal completion, sustainability score, daily tip)

2. **`AppViewModelTest.kt`** (using `InstantTaskExecutorRule`, `MainDispatcherRule`, and `FakeAppRepository`)
   - Registration with Firebase UID preservation and initial points
   - Email/password login and error handling on invalid credentials
   - Google Sign-In flow (token -> UID -> user creation/load)
   - Password reset flow and validation
   - Sign out and full state reset
   - Profile updates & profile image upload persistence
   - Carbon tracking save, update, delete, and automatic dashboard total/weekly/monthly recalculation
   - Eco products loading, category filtering, and wishlist add/remove
   - Sustainability tips creation, per-user bookmark toggle, likes, and single-reward completion
   - Goal creation, progress update, single-reward completion, and deletion
   - Firestore error propagation to `error` LiveData

3. **`ExampleUnitTest.kt`**
   - Sanity arithmetic check

### Instrumented Tests (`app/src/androidTest/java/com/greengauge/ExampleInstrumentedTest.kt`)
- Verifies application package context (`com.greengauge`). Requires an Android emulator or connected physical device.

## Running Tests Locally

```powershell
# Run JVM unit tests
.\gradlew.bat test --no-daemon

# Clean and build Debug + Release APKs
.\gradlew.bat clean test :app:assembleDebug :app:assembleRelease --no-daemon
```

## Manual Device / Firebase Verification Checklist

Because live Firebase Auth, Cloud Firestore, and Firebase Storage require a configured Firebase project and Google Play Services on a device/emulator, verify the following on a physical Android device or Play-enabled emulator:

1. **Email/Password Registration & Sign-In**: Create a user, verify `users/{uid}` is created in Firestore with matching Auth UID.
2. **Google Sign-In**: Add SHA-1 fingerprint + Web Client ID in Firebase Console (`oauth_client` in `google-services.json` and `default_web_client_id` in `strings.xml`).
3. **Password Reset**: Tap "Forgot Password?" on `AuthActivity` and verify reset email delivery.
4. **Carbon Tracking**: Add, edit, and delete activities across categories; confirm `carbon_footprints/{uid}_{date}` updates in Firestore and Dashboard reflects the changes.
5. **Eco Products**: Search, filter by category, sort, compare two products, and toggle wishlist items.
6. **Sustainability Tips**: Filter by category/difficulty, search, bookmark, like, create a custom tip, and mark implemented.
7. **User Profile**: Edit display name, upload avatar photo (requires Firebase Storage enabled), create/update/delete goals, join/update challenges, view leaderboard, toggle notifications preference, and log out.

