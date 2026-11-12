# Recommended Cloud Firestore Security Rules

These rules enforce strict per-user data isolation using Firebase Authentication (`request.auth.uid`), prevent cross-user tampering, and allow authenticated users to read global sustainability catalogs (`eco_products`, `tips`, `achievements`, `challenges`).

> **Never deploy `allow read, write: if true;` in production.**

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function isSignedIn() {
      return request.auth != null;
    }

    function isOwner(userId) {
      return isSignedIn() && request.auth.uid == userId;
    }

    function isValidUserDoc() {
      return request.resource.data.email is string
        && request.resource.data.name is string
        && request.resource.data.totalPoints is int
        && request.resource.data.level is int;
    }

    // Users collection: document ID MUST match Firebase Auth UID.
    // Authenticated users may read leaderboard summaries; only the owner can create/update/delete their own profile.
    match /users/{userId} {
      allow read: if isSignedIn();
      allow create, update: if isOwner(userId) && isValidUserDoc();
      allow delete: if isOwner(userId);
    }

    // Daily carbon footprints (deterministic ID: {uid}_{yyyy-MM-dd})
    match /carbon_footprints/{footprintId} {
      allow read: if isSignedIn() && resource.data.userId == request.auth.uid;
      allow create, update: if isSignedIn()
        && request.resource.data.userId == request.auth.uid
        && request.resource.data.totalEmissions is number;
      allow delete: if isSignedIn() && resource.data.userId == request.auth.uid;
    }

    // Individual carbon tracking entries
    match /tracking_data/{trackingId} {
      allow read: if isSignedIn() && resource.data.userId == request.auth.uid;
      allow create, update: if isSignedIn()
        && request.resource.data.userId == request.auth.uid
        && request.resource.data.value is number
        && request.resource.data.value > 0
        && request.resource.data.emissions is number;
      allow delete: if isSignedIn() && resource.data.userId == request.auth.uid;
    }

    // User goals
    match /goals/{goalId} {
      allow read: if isSignedIn() && resource.data.userId == request.auth.uid;
      allow create, update: if isSignedIn()
        && request.resource.data.userId == request.auth.uid
        && request.resource.data.targetValue is number
        && request.resource.data.targetValue > 0;
      allow delete: if isSignedIn() && resource.data.userId == request.auth.uid;
    }

    // User wishlist (deterministic ID: {uid}_{productId})
    match /wishlist/{wishlistId} {
      allow read: if isSignedIn() && resource.data.userId == request.auth.uid;
      allow create, update: if isSignedIn() && request.resource.data.userId == request.auth.uid;
      allow delete: if isSignedIn() && resource.data.userId == request.auth.uid;
    }

    // Per-user tip bookmarks, completions, and implementation notes ({uid}_{tipId})
    match /user_tip_states/{stateId} {
      allow read: if isSignedIn() && resource.data.userId == request.auth.uid;
      allow create, update: if isSignedIn() && request.resource.data.userId == request.auth.uid;
      allow delete: if isSignedIn() && resource.data.userId == request.auth.uid;
    }

    // User achievements & challenge participation
    match /user_achievements/{docId} {
      allow read: if isSignedIn() && resource.data.userId == request.auth.uid;
      allow create, update: if isSignedIn() && request.resource.data.userId == request.auth.uid;
      allow delete: if isSignedIn() && resource.data.userId == request.auth.uid;
    }

    match /user_challenges/{docId} {
      allow read: if isSignedIn() && resource.data.userId == request.auth.uid;
      allow create, update: if isSignedIn() && request.resource.data.userId == request.auth.uid;
      allow delete: if isSignedIn() && resource.data.userId == request.auth.uid;
    }

    // User preferences (document ID == Firebase Auth UID)
    match /user_preferences/{userId} {
      allow read, write: if isOwner(userId);
    }

    // Global catalogs (read by signed-in users; seeded idempotently with deterministic IDs)
    match /eco_products/{productId} {
      allow read: if isSignedIn();
      allow create: if isSignedIn();
      allow update, delete: if false;
    }

    match /product_reviews/{reviewId} {
      allow read: if isSignedIn();
      allow create: if isSignedIn() && request.resource.data.userId == request.auth.uid;
      allow update, delete: if isSignedIn() && resource.data.userId == request.auth.uid;
    }

    match /tips/{tipId} {
      allow read: if isSignedIn();
      allow create: if isSignedIn();
      // Allow authenticated users to increment likes counter only
      allow update: if isSignedIn()
        && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['likes']);
      allow delete: if false;
    }

    match /achievements/{achievementId} {
      allow read: if isSignedIn();
      allow create: if isSignedIn();
      allow update, delete: if false;
    }

    match /challenges/{challengeId} {
      allow read: if isSignedIn();
      allow create: if isSignedIn();
      allow update, delete: if false;
    }
  }
}
```

## Firebase Storage Rules (Profile Photos)

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /profile_images/{fileName} {
      allow read: if request.auth != null;
      allow write: if request.auth != null
        && fileName.matches(request.auth.uid + '\\.jpg')
        && request.resource.size < 5 * 1024 * 1024
        && request.resource.contentType.matches('image/.*');
    }
  }
}
```

