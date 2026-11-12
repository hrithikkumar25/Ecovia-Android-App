package com.greengauge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

class FakeAppRepository : AppRepository {
    var authUid: String? = null
    var authEmail: String? = null
    var authName: String? = null
    var authPhotoUrl: String? = null

    var nextError: String? = null
    var seedCount = 0

    val users = mutableMapOf<String, User>()
    val footprints = mutableMapOf<String, CarbonFootprint>()
    val trackingList = mutableListOf<TrackingData>()
    val products = mutableListOf<EcoProduct>()
    val reviews = mutableListOf<ProductReview>()
    val wishlist = mutableListOf<Wishlist>()
    val tips = mutableListOf<Tip>()
    val userTipStates = mutableMapOf<String, UserTipState>()
    val achievements = mutableListOf<Achievement>()
    val userAchievements = mutableListOf<UserAchievement>()
    val goals = mutableListOf<Goal>()
    val challenges = mutableListOf<Challenge>()
    val userChallenges = mutableListOf<UserChallenge>()
    val preferencesMap = mutableMapOf<String, UserPreferences>()

    private fun <T> checkError(): Result<T>? {
        val err = nextError
        if (err != null) {
            nextError = null
            return Result.failure(IllegalStateException(err))
        }
        return null
    }

    override fun currentAuthUserId(): String? = authUid
    override fun currentAuthUserEmail(): String? = authEmail
    override fun currentAuthUserName(): String? = authName
    override fun currentAuthUserPhotoUrl(): String? = authPhotoUrl

    override suspend fun signInWithEmail(email: String, password: String): Result<String> {
        checkError<String>()?.let { return it }
        if (password == "wrong") {
            return Result.failure(IllegalArgumentException("Invalid credentials"))
        }
        val uid = users.values.find { it.email == email }?.id ?: "uid_${email.hashCode()}"
        authUid = uid
        authEmail = email
        return Result.success(uid)
    }

    override suspend fun registerWithEmail(email: String, password: String, name: String): Result<String> {
        checkError<String>()?.let { return it }
        if (users.values.any { it.email == email }) {
            return Result.failure(IllegalStateException("Email already registered"))
        }
        val uid = "uid_${UUID.randomUUID().toString().take(8)}"
        authUid = uid
        authEmail = email
        authName = name
        return Result.success(uid)
    }

    override suspend fun signInWithGoogle(idToken: String): Result<AuthAccountInfo> {
        checkError<AuthAccountInfo>()?.let { return it }
        val uid = "google_uid_123"
        authUid = uid
        authEmail = "google@greengauge.app"
        authName = "Google Eco User"
        return Result.success(AuthAccountInfo(uid, authEmail!!, authName!!, ""))
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        checkError<Unit>()?.let { return it }
        return Result.success(Unit)
    }

    override suspend fun updateAuthDisplayName(name: String): Result<Unit> {
        authName = name
        return Result.success(Unit)
    }

    override fun signOut() {
        authUid = null
        authEmail = null
        authName = null
        authPhotoUrl = null
    }

    override suspend fun createUser(user: User): Result<User> {
        checkError<User>()?.let { return it }
        users[user.id] = user
        return Result.success(user)
    }

    override suspend fun getUser(userId: String): Result<User?> {
        checkError<User?>()?.let { return it }
        return Result.success(users[userId])
    }

    override suspend fun getUserByEmail(email: String): Result<User?> {
        checkError<User?>()?.let { return it }
        return Result.success(users.values.find { it.email == email })
    }

    override suspend fun updateUser(user: User): Result<Unit> {
        checkError<Unit>()?.let { return it }
        users[user.id] = user
        return Result.success(Unit)
    }

    override suspend fun incrementUserPoints(userId: String, points: Int): Result<User> {
        checkError<User>()?.let { return it }
        val existing = users[userId] ?: return Result.failure(IllegalStateException("User not found"))
        val newPoints = existing.totalPoints + points
        val newLevel = SustainabilityUtils.calculateLevel(newPoints)
        val updated = existing.copy(totalPoints = newPoints, level = newLevel)
        users[userId] = updated
        return Result.success(updated)
    }

    override suspend fun updateUserStreak(userId: String, streakDays: Int, lastActiveDate: String): Result<Unit> {
        val existing = users[userId] ?: return Result.success(Unit)
        users[userId] = existing.copy(streakDays = streakDays, lastActiveDate = lastActiveDate)
        return Result.success(Unit)
    }

    override suspend fun getLeaderboardUsers(limit: Int): Result<List<User>> {
        checkError<List<User>>()?.let { return it }
        return Result.success(users.values.sortedByDescending { it.totalPoints }.take(limit))
    }

    override suspend fun uploadProfileImage(userId: String, imageBytes: ByteArray): Result<String> {
        checkError<String>()?.let { return it }
        return Result.success("https://example.com/avatars/$userId.jpg")
    }

    override suspend fun initializeUserCollections(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun seedGlobalDataIfNeeded(): Result<Unit> {
        seedCount++
        return Result.success(Unit)
    }

    override suspend fun saveCarbonFootprint(carbonFootprint: CarbonFootprint): Result<CarbonFootprint> {
        checkError<CarbonFootprint>()?.let { return it }
        val id = carbonFootprint.id.ifBlank { "${carbonFootprint.userId}_${carbonFootprint.date}" }
        val saved = carbonFootprint.copy(id = id)
        footprints[id] = saved
        return Result.success(saved)
    }

    override suspend fun getCarbonFootprintsByUser(userId: String): Result<List<CarbonFootprint>> {
        checkError<List<CarbonFootprint>>()?.let { return it }
        return Result.success(footprints.values.filter { it.userId == userId }.sortedByDescending { it.date })
    }

    override suspend fun getCarbonFootprintsByDateRange(
        userId: String,
        startDate: String,
        endDate: String
    ): Result<List<CarbonFootprint>> {
        return Result.success(
            footprints.values.filter { it.userId == userId && it.date >= startDate && it.date <= endDate }
        )
    }

    override suspend fun saveTrackingData(trackingData: TrackingData): Result<TrackingData> {
        checkError<TrackingData>()?.let { return it }
        val id = trackingData.id.ifBlank { "track_${UUID.randomUUID().toString().take(8)}" }
        val saved = trackingData.copy(id = id)
        trackingList.add(0, saved)
        recalculateFootprint(saved.userId, saved.date)
        return Result.success(saved)
    }

    override suspend fun updateTrackingData(trackingData: TrackingData): Result<TrackingData> {
        checkError<TrackingData>()?.let { return it }
        val index = trackingList.indexOfFirst { it.id == trackingData.id }
        if (index >= 0) {
            trackingList[index] = trackingData
        }
        recalculateFootprint(trackingData.userId, trackingData.date)
        return Result.success(trackingData)
    }

    override suspend fun deleteTrackingData(trackingId: String, userId: String, date: String): Result<Unit> {
        checkError<Unit>()?.let { return it }
        trackingList.removeAll { it.id == trackingId }
        recalculateFootprint(userId, date)
        return Result.success(Unit)
    }

    private fun recalculateFootprint(userId: String, date: String) {
        val dayItems = trackingList.filter { it.userId == userId && it.date == date }
        val docId = "${userId}_$date"
        if (dayItems.isEmpty()) {
            footprints.remove(docId)
            return
        }
        val fp = CarbonFootprint(
            id = docId,
            userId = userId,
            date = date,
            transportationEmissions = dayItems.filter { it.category == "transportation" }.sumOf { it.emissions },
            energyEmissions = dayItems.filter { it.category == "energy" }.sumOf { it.emissions },
            dietEmissions = dayItems.filter { it.category == "diet" }.sumOf { it.emissions },
            wasteEmissions = dayItems.filter { it.category == "waste" }.sumOf { it.emissions },
            waterEmissions = dayItems.filter { it.category == "water" }.sumOf { it.emissions },
            shoppingEmissions = dayItems.filter { it.category == "shopping" }.sumOf { it.emissions },
            totalEmissions = dayItems.sumOf { it.emissions }
        )
        footprints[docId] = fp
    }

    override suspend fun getTrackingDataByUser(userId: String): Result<List<TrackingData>> {
        checkError<List<TrackingData>>()?.let { return it }
        return Result.success(trackingList.filter { it.userId == userId })
    }

    override suspend fun getTrackingDataByCategory(userId: String, category: String): Result<List<TrackingData>> {
        return Result.success(trackingList.filter { it.userId == userId && it.category == category })
    }

    override suspend fun getEcoProducts(): Result<List<EcoProduct>> {
        checkError<List<EcoProduct>>()?.let { return it }
        return Result.success(products.toList())
    }

    override suspend fun getEcoProductsByCategory(category: String): Result<List<EcoProduct>> {
        checkError<List<EcoProduct>>()?.let { return it }
        return Result.success(products.filter { it.category.equals(category, true) })
    }

    override suspend fun getRecommendedProducts(): Result<List<EcoProduct>> {
        return Result.success(products.filter { it.isRecommended })
    }

    override suspend fun saveProductReview(review: ProductReview): Result<String> {
        val id = review.id.ifBlank { "rev_${UUID.randomUUID().toString().take(8)}" }
        reviews.add(review.copy(id = id))
        return Result.success(id)
    }

    override suspend fun getProductReviews(productId: String): Result<List<ProductReview>> {
        return Result.success(reviews.filter { it.productId == productId })
    }

    override suspend fun addToWishlist(wishlist: Wishlist): Result<Wishlist> {
        checkError<Wishlist>()?.let { return it }
        val id = wishlist.id.ifBlank { "${wishlist.userId}_${wishlist.productId}" }
        val saved = wishlist.copy(id = id)
        this.wishlist.removeAll { it.id == id }
        this.wishlist.add(saved)
        return Result.success(saved)
    }

    override suspend fun getUserWishlist(userId: String): Result<List<Wishlist>> {
        return Result.success(wishlist.filter { it.userId == userId })
    }

    override suspend fun removeFromWishlist(wishlistId: String): Result<Unit> {
        checkError<Unit>()?.let { return it }
        wishlist.removeAll { it.id == wishlistId }
        return Result.success(Unit)
    }

    override suspend fun getTips(): Result<List<Tip>> {
        checkError<List<Tip>>()?.let { return it }
        return Result.success(tips.toList())
    }

    override suspend fun createTip(tip: Tip): Result<Tip> {
        checkError<Tip>()?.let { return it }
        val id = tip.id.ifBlank { "tip_${UUID.randomUUID().toString().take(8)}" }
        val saved = tip.copy(id = id)
        tips.add(0, saved)
        return Result.success(saved)
    }

    override suspend fun likeTip(tipId: String): Result<Int> {
        checkError<Int>()?.let { return it }
        val index = tips.indexOfFirst { it.id == tipId }
        if (index < 0) return Result.failure(IllegalStateException("Tip not found"))
        val updated = tips[index].copy(likes = tips[index].likes + 1)
        tips[index] = updated
        return Result.success(updated.likes)
    }

    override suspend fun getUserTipStates(userId: String): Result<List<UserTipState>> {
        return Result.success(userTipStates.values.filter { it.userId == userId })
    }

    override suspend fun saveUserTipState(state: UserTipState): Result<UserTipState> {
        checkError<UserTipState>()?.let { return it }
        val id = state.id.ifBlank { "${state.userId}_${state.tipId}" }
        val saved = state.copy(id = id)
        userTipStates[id] = saved
        return Result.success(saved)
    }

    override suspend fun getAchievements(): Result<List<Achievement>> = Result.success(achievements.toList())

    override suspend fun getUserAchievements(userId: String): Result<List<UserAchievement>> =
        Result.success(userAchievements.filter { it.userId == userId })

    override suspend fun saveUserAchievement(userAchievement: UserAchievement): Result<String> {
        val id = "${userAchievement.userId}_${userAchievement.achievementId}"
        userAchievements.add(userAchievement.copy(id = id))
        return Result.success(id)
    }

    override suspend fun saveGoal(goal: Goal): Result<Goal> {
        checkError<Goal>()?.let { return it }
        val id = goal.id.ifBlank { "goal_${UUID.randomUUID().toString().take(8)}" }
        val saved = goal.copy(id = id)
        goals.add(saved)
        return Result.success(saved)
    }

    override suspend fun getUserGoals(userId: String): Result<List<Goal>> =
        Result.success(goals.filter { it.userId == userId })

    override suspend fun updateGoal(goal: Goal): Result<Unit> {
        checkError<Unit>()?.let { return it }
        val idx = goals.indexOfFirst { it.id == goal.id }
        if (idx >= 0) goals[idx] = goal
        return Result.success(Unit)
    }

    override suspend fun deleteGoal(goalId: String): Result<Unit> {
        checkError<Unit>()?.let { return it }
        goals.removeAll { it.id == goalId }
        return Result.success(Unit)
    }

    override suspend fun getActiveChallenges(): Result<List<Challenge>> = Result.success(challenges.toList())

    override suspend fun getUserChallenges(userId: String): Result<List<UserChallenge>> =
        Result.success(userChallenges.filter { it.userId == userId })

    override suspend fun saveUserChallenge(userChallenge: UserChallenge): Result<String> {
        val id = userChallenge.id.ifBlank { "${userChallenge.userId}_${userChallenge.challengeId}" }
        userChallenges.add(userChallenge.copy(id = id))
        return Result.success(id)
    }

    override suspend fun updateUserChallenge(userChallenge: UserChallenge): Result<Unit> {
        val idx = userChallenges.indexOfFirst { it.id == userChallenge.id }
        if (idx >= 0) userChallenges[idx] = userChallenge
        return Result.success(Unit)
    }

    override suspend fun saveUserPreferences(preferences: UserPreferences): Result<String> {
        preferencesMap[preferences.userId] = preferences
        return Result.success(preferences.userId)
    }

    override suspend fun getUserPreferences(userId: String): Result<UserPreferences?> =
        Result.success(preferencesMap[userId])

    override suspend fun updateUserPreferences(preferences: UserPreferences): Result<Unit> {
        preferencesMap[preferences.userId] = preferences
        return Result.success(Unit)
    }
}

