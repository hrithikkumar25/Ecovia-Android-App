# GreenGauge (Ecovia) — Android Sustainability & Carbon Tracker

GreenGauge is a native Android application built with Kotlin, XML + Material Design, Firebase Authentication, and Cloud Firestore that helps users track their daily carbon footprint, discover eco-friendly products, adopt sustainability tips, and achieve personal green goals.

## Problem Statement

Individuals often want to lower their environmental impact but lack a unified mobile tool to quantify everyday emissions (transportation, energy, diet, waste, water, shopping), discover verified sustainable alternatives, and stay motivated through measurable goals and challenges.

## Features

- **Firebase Authentication**: Email/password registration and login, password reset, Google Sign-In support, persistent session routing, and clean sign-out.
- **Dashboard**: Real-time summary of total/weekly/monthly CO₂ emissions, sustainability score, level, streak days, category breakdown, 7-day trend chart (`MPAndroidChart`), daily eco tip, recent activities, and quick navigation.
- **Carbon Tracking**: Log activities across 6 categories (`Transportation`, `Energy`, `Diet`, `Waste`, `Water`, `Shopping`) manually or with GPS distance capture, view category-filtered history, edit entries, and delete entries with automatic daily footprint recalculation.
- **Eco Products**: Browse seeded eco-friendly products from Firestore, search, filter by category or wishlist, sort by rating/price/reviews, compare two products side-by-side, scan barcodes (`ZXing`), and find nearby eco stores via Google Maps.
- **Sustainability Tips**: Browse, search, and filter tips by category and difficulty; bookmark, like, share, create community tips, and mark tips implemented (with per-user state in `user_tip_states`).
- **User Profile**: View and edit profile details, upload profile photos (`Firebase Storage` + Android Photo Picker), track achievements, create/update/delete goals, join/update challenges, view the real-user Firestore leaderboard, and persist notification preferences.

## Technology Stack

- **Language**: Kotlin (`JVM 11`, `compileSdk 36`, `minSdk 27`)
- **UI**: Android XML Views, Material Components, ViewBinding, `MPAndroidChart`, `Glide`
- **Architecture**: MVVM (`AppViewModel`, `AppRepository`, `FirestoreRepository`, `DatabaseHelper`) + Kotlin Coroutines + LiveData
- **Backend**: Firebase Authentication, Cloud Firestore, Firebase Storage, Firebase Cloud Messaging, WorkManager
- **Scanning & Location**: ZXing Android Embedded, Google Play Services Location & Auth
- **Testing**: JUnit 4, `kotlinx-coroutines-test`, `androidx.arch.core:core-testing`

## Application Architecture

```text
Activities / Fragments (ViewBinding)
   └── AppViewModel (LiveData + viewModelScope coroutines)
          └── AppRepository (Interface for testability)
                 └── FirestoreRepository (FirebaseAuth + DatabaseHelper)
                        └── DatabaseHelper (Cloud Firestore + Firebase Storage)
```

## Folder Structure

```text
Greengauge/
├── app/
│   ├── google-services.json          # Firebase client configuration (replace with your project config)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/greengauge/
│       │   │   ├── AppRepository.kt
│       │   │   ├── AppViewModel.kt
│       │   │   ├── AuthActivity.kt
│       │   │   ├── DashboardFragment.kt
│       │   │   ├── DataModels.kt
│       │   │   ├── DatabaseHelper.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── ProductsFragment.kt
│       │   │   ├── ProfileFragment.kt
│       │   │   ├── SplashActivity.kt
│       │   │   ├── TipsFragment.kt
│       │   │   ├── TrackingFragment.kt
│       │   │   ├── UniversalAdapter.kt
│       │   │   └── Utils.kt
│       │   └── res/                  # Layouts, drawables, menus, values, xml
│       ├── test/java/com/greengauge/ # JVM unit tests
│       └── androidTest/              # Android instrumented tests
├── AUDIT_REPORT.md
├── FIRESTORE_RULES.md
├── TESTING.md
├── build.gradle.kts
├── gradle/libs.versions.toml
└── settings.gradle.kts
```

## Firebase Configuration Instructions

1. Create or open your Firebase project (`greengauge-7b8de` or your own project) in the [Firebase Console](https://console.firebase.google.com/).
2. Enable **Authentication** providers:
   - **Email/Password**
   - **Google** (add your debug/release SHA-1 fingerprint to the Android app in Project Settings, then download the updated `google-services.json` and update `default_web_client_id` in `app/src/main/res/values/strings.xml` with your OAuth 2.0 Web Client ID `*.apps.googleusercontent.com`).
3. Enable **Cloud Firestore** and deploy the security rules from [`FIRESTORE_RULES.md`](FIRESTORE_RULES.md).
4. Enable **Firebase Storage** and deploy the Storage rules from [`FIRESTORE_RULES.md`](FIRESTORE_RULES.md) if using profile photo uploads.
5. Place your project's `google-services.json` inside `app/`.

## Screenshots

| Dashboard | Carbon Tracking | Eco Products | Tips & Profile |
|---|---|---|---|
| *Add screenshot* | *Add screenshot* | *Add screenshot* | *Add screenshot* |

## Installation & Build Instructions

1. Clone the repository:
   ```bash
   git clone <your-repo-url>
   cd Greengauge
   ```
2. Open in **Android Studio** (or ensure `ANDROID_HOME` / `local.properties` points to your local Android SDK).
3. Run unit tests and build APKs:
   ```powershell
   .\gradlew.bat clean test :app:assembleDebug :app:assembleRelease
   ```
4. Generated APKs will be located at:
   - Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
   - Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk` (configure signing config for Play Store distribution)

## Future Improvements

- Migrate legacy `GoogleSignIn` client APIs to Android Credential Manager (`androidx.credentials`).
- Migrate ZXing `IntentIntegrator` to CameraX + ML Kit Barcode Scanning.
- Add offline Room caching synced with Firestore snapshots.
- Add push notification scheduling with Firebase Cloud Messaging topics.

