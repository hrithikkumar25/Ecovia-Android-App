package com.greengauge

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

data class AuthAccountInfo(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String
)

interface AppRepository {
    fun currentAuthUserId(): String?
    fun currentAuthUserEmail(): String?
    fun currentAuthUserName(): String?
    fun currentAuthUserPhotoUrl(): String?

    suspend fun signInWithEmail(email: String, password: String): Result<String>
    suspend fun registerWithEmail(email: String, password: String, name: String): Result<String>
    suspend fun signInWithGoogle(idToken: String): Result<AuthAccountInfo>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun updateAuthDisplayName(name: String): Result<Unit>
    fun signOut()

    suspend fun createUser(user: User): Result<User>
    suspend fun getUser(userId: String): Result<User?>
    suspend fun getUserByEmail(email: String): Result<User?>
    suspend fun updateUser(user: User): Result<Unit>
    suspend fun incrementUserPoints(userId: String, points: Int): Result<User>
    suspend fun updateUserStreak(userId: String, streakDays: Int, lastActiveDate: String): Result<Unit>
    suspend fun getLeaderboardUsers(limit: Int = 20): Result<List<User>>
    suspend fun uploadProfileImage(userId: String, imageBytes: ByteArray): Result<String>

    suspend fun initializeUserCollections(userId: String): Result<Unit>
    suspend fun seedGlobalDataIfNeeded(): Result<Unit>

    suspend fun saveCarbonFootprint(carbonFootprint: CarbonFootprint): Result<CarbonFootprint>
    suspend fun getCarbonFootprintsByUser(userId: String): Result<List<CarbonFootprint>>
    suspend fun getCarbonFootprintsByDateRange(userId: String, startDate: String, endDate: String): Result<List<CarbonFootprint>>

    suspend fun saveTrackingData(trackingData: TrackingData): Result<TrackingData>
    suspend fun updateTrackingData(trackingData: TrackingData): Result<TrackingData>
    suspend fun deleteTrackingData(trackingId: String, userId: String, date: String): Result<Unit>
    suspend fun getTrackingDataByUser(userId: String): Result<List<TrackingData>>
    suspend fun getTrackingDataByCategory(userId: String, category: String): Result<List<TrackingData>>

    suspend fun getEcoProducts(): Result<List<EcoProduct>>
    suspend fun getEcoProductsByCategory(category: String): Result<List<EcoProduct>>
    suspend fun getRecommendedProducts(): Result<List<EcoProduct>>
    suspend fun saveProductReview(review: ProductReview): Result<String>
    suspend fun getProductReviews(productId: String): Result<List<ProductReview>>

    suspend fun addToWishlist(wishlist: Wishlist): Result<Wishlist>
    suspend fun getUserWishlist(userId: String): Result<List<Wishlist>>
    suspend fun removeFromWishlist(wishlistId: String): Result<Unit>

    suspend fun getTips(): Result<List<Tip>>
    suspend fun createTip(tip: Tip): Result<Tip>
    suspend fun likeTip(tipId: String): Result<Int>
    suspend fun getUserTipStates(userId: String): Result<List<UserTipState>>
    suspend fun saveUserTipState(state: UserTipState): Result<UserTipState>

    suspend fun getAchievements(): Result<List<Achievement>>
    suspend fun getUserAchievements(userId: String): Result<List<UserAchievement>>
    suspend fun saveUserAchievement(userAchievement: UserAchievement): Result<String>

    suspend fun saveGoal(goal: Goal): Result<Goal>
    suspend fun getUserGoals(userId: String): Result<List<Goal>>
    suspend fun updateGoal(goal: Goal): Result<Unit>
    suspend fun deleteGoal(goalId: String): Result<Unit>

    suspend fun getActiveChallenges(): Result<List<Challenge>>
    suspend fun getUserChallenges(userId: String): Result<List<UserChallenge>>
    suspend fun saveUserChallenge(userChallenge: UserChallenge): Result<String>
    suspend fun updateUserChallenge(userChallenge: UserChallenge): Result<Unit>

    suspend fun saveUserPreferences(preferences: UserPreferences): Result<String>
    suspend fun getUserPreferences(userId: String): Result<UserPreferences?>
    suspend fun updateUserPreferences(preferences: UserPreferences): Result<Unit>
}

class FirestoreRepository(
    private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() }
) : AppRepository {

    override fun currentAuthUserId(): String? = authProvider().currentUser?.uid
    override fun currentAuthUserEmail(): String? = authProvider().currentUser?.email
    override fun currentAuthUserName(): String? = authProvider().currentUser?.displayName
    override fun currentAuthUserPhotoUrl(): String? = authProvider().currentUser?.photoUrl?.toString()

    override suspend fun signInWithEmail(email: String, password: String): Result<String> {
        return try {
            val result = authProvider().signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return Result.failure(Exception("Authentication succeeded but user UID is missing"))
            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun registerWithEmail(email: String, password: String, name: String): Result<String> {
        return try {
            val result = authProvider().createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Registration succeeded but user is missing"))
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()
            Result.success(firebaseUser.uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<AuthAccountInfo> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = authProvider().signInWithCredential(credential).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Google sign-in failed to return a Firebase user"))
            Result.success(
                AuthAccountInfo(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    displayName = firebaseUser.displayName ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString() ?: ""
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            authProvider().sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateAuthDisplayName(name: String): Result<Unit> {
        return try {
            val user = authProvider().currentUser ?: return Result.failure(Exception("User not authenticated"))
            val updates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
            user.updateProfile(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun signOut() {
        authProvider().signOut()
    }

    override suspend fun createUser(user: User): Result<User> = DatabaseHelper.createUserWithResult(user)
    override suspend fun getUser(userId: String): Result<User?> = DatabaseHelper.getUser(userId)
    override suspend fun getUserByEmail(email: String): Result<User?> = DatabaseHelper.getUserByEmail(email)
    override suspend fun updateUser(user: User): Result<Unit> = DatabaseHelper.updateUser(user)
    override suspend fun incrementUserPoints(userId: String, points: Int): Result<User> = DatabaseHelper.incrementUserPointsAndReturn(userId, points)
    override suspend fun updateUserStreak(userId: String, streakDays: Int, lastActiveDate: String): Result<Unit> = DatabaseHelper.updateUserStreak(userId, streakDays, lastActiveDate)
    override suspend fun getLeaderboardUsers(limit: Int): Result<List<User>> = DatabaseHelper.getLeaderboardUsers(limit)
    override suspend fun uploadProfileImage(userId: String, imageBytes: ByteArray): Result<String> = DatabaseHelper.uploadProfileImage(userId, imageBytes)

    override suspend fun initializeUserCollections(userId: String): Result<Unit> = DatabaseHelper.initializeUserCollections(userId)
    override suspend fun seedGlobalDataIfNeeded(): Result<Unit> = DatabaseHelper.seedGlobalDataIfNeeded()

    override suspend fun saveCarbonFootprint(carbonFootprint: CarbonFootprint): Result<CarbonFootprint> = DatabaseHelper.saveOrUpdateCarbonFootprint(carbonFootprint)
    override suspend fun getCarbonFootprintsByUser(userId: String): Result<List<CarbonFootprint>> = DatabaseHelper.getCarbonFootprintsByUser(userId)
    override suspend fun getCarbonFootprintsByDateRange(userId: String, startDate: String, endDate: String): Result<List<CarbonFootprint>> = DatabaseHelper.getCarbonFootprintsByDateRange(userId, startDate, endDate)

    override suspend fun saveTrackingData(trackingData: TrackingData): Result<TrackingData> = DatabaseHelper.saveTrackingDataItem(trackingData)
    override suspend fun updateTrackingData(trackingData: TrackingData): Result<TrackingData> = DatabaseHelper.updateTrackingData(trackingData)
    override suspend fun deleteTrackingData(trackingId: String, userId: String, date: String): Result<Unit> = DatabaseHelper.deleteTrackingData(trackingId, userId, date)
    override suspend fun getTrackingDataByUser(userId: String): Result<List<TrackingData>> = DatabaseHelper.getTrackingDataByUser(userId)
    override suspend fun getTrackingDataByCategory(userId: String, category: String): Result<List<TrackingData>> = DatabaseHelper.getTrackingDataByCategory(userId, category)

    override suspend fun getEcoProducts(): Result<List<EcoProduct>> = DatabaseHelper.getEcoProducts()
    override suspend fun getEcoProductsByCategory(category: String): Result<List<EcoProduct>> = DatabaseHelper.getEcoProductsByCategory(category)
    override suspend fun getRecommendedProducts(): Result<List<EcoProduct>> = DatabaseHelper.getRecommendedProducts()
    override suspend fun saveProductReview(review: ProductReview): Result<String> = DatabaseHelper.saveProductReview(review)
    override suspend fun getProductReviews(productId: String): Result<List<ProductReview>> = DatabaseHelper.getProductReviews(productId)

    override suspend fun addToWishlist(wishlist: Wishlist): Result<Wishlist> = DatabaseHelper.addToWishlistItem(wishlist)
    override suspend fun getUserWishlist(userId: String): Result<List<Wishlist>> = DatabaseHelper.getUserWishlist(userId)
    override suspend fun removeFromWishlist(wishlistId: String): Result<Unit> = DatabaseHelper.removeFromWishlist(wishlistId)

    override suspend fun getTips(): Result<List<Tip>> = DatabaseHelper.getTips()
    override suspend fun createTip(tip: Tip): Result<Tip> = DatabaseHelper.createTip(tip)
    override suspend fun likeTip(tipId: String): Result<Int> = DatabaseHelper.likeTip(tipId)
    override suspend fun getUserTipStates(userId: String): Result<List<UserTipState>> = DatabaseHelper.getUserTipStates(userId)
    override suspend fun saveUserTipState(state: UserTipState): Result<UserTipState> = DatabaseHelper.saveUserTipState(state)

    override suspend fun getAchievements(): Result<List<Achievement>> = DatabaseHelper.getAchievements()
    override suspend fun getUserAchievements(userId: String): Result<List<UserAchievement>> = DatabaseHelper.getUserAchievements(userId)
    override suspend fun saveUserAchievement(userAchievement: UserAchievement): Result<String> = DatabaseHelper.saveUserAchievement(userAchievement)

    override suspend fun saveGoal(goal: Goal): Result<Goal> = DatabaseHelper.saveGoalItem(goal)
    override suspend fun getUserGoals(userId: String): Result<List<Goal>> = DatabaseHelper.getUserGoals(userId)
    override suspend fun updateGoal(goal: Goal): Result<Unit> = DatabaseHelper.updateGoal(goal)
    override suspend fun deleteGoal(goalId: String): Result<Unit> = DatabaseHelper.deleteGoal(goalId)

    override suspend fun getActiveChallenges(): Result<List<Challenge>> = DatabaseHelper.getActiveChallenges()
    override suspend fun getUserChallenges(userId: String): Result<List<UserChallenge>> = DatabaseHelper.getUserChallenges(userId)
    override suspend fun saveUserChallenge(userChallenge: UserChallenge): Result<String> = DatabaseHelper.saveUserChallenge(userChallenge)
    override suspend fun updateUserChallenge(userChallenge: UserChallenge): Result<Unit> = DatabaseHelper.updateUserChallenge(userChallenge)

    override suspend fun saveUserPreferences(preferences: UserPreferences): Result<String> = DatabaseHelper.saveUserPreferences(preferences)
    override suspend fun getUserPreferences(userId: String): Result<UserPreferences?> = DatabaseHelper.getUserPreferences(userId)
    override suspend fun updateUserPreferences(preferences: UserPreferences): Result<Unit> = DatabaseHelper.updateUserPreferences(preferences)
}

