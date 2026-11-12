package com.greengauge

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * Shared ViewModel coordinating authentication, Firestore persistence,
 * dashboard metrics, carbon tracking, eco-products, tips, and user profile state.
 */
class AppViewModel(
    private val repository: AppRepository
) : ViewModel() {

    constructor() : this(FirestoreRepository())

    // Authentication & user state
    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    private val _isAuthenticated = MutableLiveData<Boolean>()
    val isAuthenticated: LiveData<Boolean> = _isAuthenticated

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _actionMessage = MutableLiveData<String?>()
    val actionMessage: LiveData<String?> = _actionMessage

    private val _passwordResetSent = MutableLiveData(false)
    val passwordResetSent: LiveData<Boolean> = _passwordResetSent

    // Domain collections
    private val _carbonFootprints = MutableLiveData<List<CarbonFootprint>>(emptyList())
    val carbonFootprints: LiveData<List<CarbonFootprint>> = _carbonFootprints

    private val _trackingData = MutableLiveData<List<TrackingData>>(emptyList())
    val trackingData: LiveData<List<TrackingData>> = _trackingData

    private val _ecoProducts = MutableLiveData<List<EcoProduct>>(emptyList())
    val ecoProducts: LiveData<List<EcoProduct>> = _ecoProducts

    private val _recommendedProducts = MutableLiveData<List<EcoProduct>>(emptyList())
    val recommendedProducts: LiveData<List<EcoProduct>> = _recommendedProducts

    private val _productReviews = MutableLiveData<List<ProductReview>>(emptyList())
    val productReviews: LiveData<List<ProductReview>> = _productReviews

    private val _tips = MutableLiveData<List<Tip>>(emptyList())
    val tips: LiveData<List<Tip>> = _tips

    private val _userTipStates = MutableLiveData<List<UserTipState>>(emptyList())
    val userTipStates: LiveData<List<UserTipState>> = _userTipStates

    private val _achievements = MutableLiveData<List<Achievement>>(emptyList())
    val achievements: LiveData<List<Achievement>> = _achievements

    private val _userAchievements = MutableLiveData<List<UserAchievement>>(emptyList())
    val userAchievements: LiveData<List<UserAchievement>> = _userAchievements

    private val _goals = MutableLiveData<List<Goal>>(emptyList())
    val goals: LiveData<List<Goal>> = _goals

    private val _activeChallenges = MutableLiveData<List<Challenge>>(emptyList())
    val activeChallenges: LiveData<List<Challenge>> = _activeChallenges

    private val _userChallenges = MutableLiveData<List<UserChallenge>>(emptyList())
    val userChallenges: LiveData<List<UserChallenge>> = _userChallenges

    private val _wishlist = MutableLiveData<List<Wishlist>>(emptyList())
    val wishlist: LiveData<List<Wishlist>> = _wishlist

    private val _userPreferences = MutableLiveData<UserPreferences?>()
    val userPreferences: LiveData<UserPreferences?> = _userPreferences

    private val _leaderboardUsers = MutableLiveData<List<User>>(emptyList())
    val leaderboardUsers: LiveData<List<User>> = _leaderboardUsers

    // Dashboard metrics
    private val _totalEmissions = MutableLiveData(0.0)
    val totalEmissions: LiveData<Double> = _totalEmissions

    private val _weeklyEmissions = MutableLiveData(0.0)
    val weeklyEmissions: LiveData<Double> = _weeklyEmissions

    private val _monthlyEmissions = MutableLiveData(0.0)
    val monthlyEmissions: LiveData<Double> = _monthlyEmissions

    private val _sustainabilityScore = MutableLiveData(0)
    val sustainabilityScore: LiveData<Int> = _sustainabilityScore

    fun clearError() {
        _error.value = null
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun clearPasswordResetSent() {
        _passwordResetSent.value = false
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.seedGlobalDataIfNeeded()
                val uid = repository.currentAuthUserId()
                if (!uid.isNullOrBlank()) {
                    hydrateUserInternal(uid)
                } else {
                    _isAuthenticated.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to initialize application data."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun hydrateCurrentUser() {
        val uid = repository.currentAuthUserId()
        if (uid.isNullOrBlank()) {
            _currentUser.value = null
            _isAuthenticated.value = false
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.seedGlobalDataIfNeeded()
                hydrateUserInternal(uid)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load user profile."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun hydrateUserInternal(uid: String) {
        val userResult = repository.getUser(uid)
        val existingUser = userResult.getOrNull()
        val resolvedUser = if (existingUser != null) {
            existingUser
        } else {
            val email = repository.currentAuthUserEmail().orEmpty()
            val name = repository.currentAuthUserName()?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@").ifBlank { "Eco User" }
            val newUser = User(
                id = uid,
                email = email,
                name = name,
                profileImageUrl = repository.currentAuthUserPhotoUrl().orEmpty(),
                joinDate = System.currentTimeMillis(),
                lastActiveDate = DateUtils.getCurrentDate(),
                streakDays = 1,
                totalPoints = 0,
                level = 1,
                sustainabilityScore = 50
            )
            val created = repository.createUser(newUser).getOrElse { err ->
                _error.value = err.message ?: "Failed to initialize user account."
                return
            }
            repository.initializeUserCollections(uid)
            created
        }

        val updatedUser = updateUserStreakIfNeeded(resolvedUser)
        _currentUser.value = updatedUser
        _isAuthenticated.value = true
        loadUserDataInternal(updatedUser.id)
    }

    fun authenticateUser(email: String, password: String) {
        if (!ValidationUtils.isValidEmail(email)) {
            _error.value = "Please enter a valid email address."
            return
        }
        if (password.isBlank()) {
            _error.value = "Please enter your password."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val signInResult = repository.signInWithEmail(email.trim(), password)
                signInResult.fold(
                    onSuccess = { uid ->
                        repository.seedGlobalDataIfNeeded()
                        hydrateUserInternal(uid)
                    },
                    onFailure = { e ->
                        _error.value = e.message ?: "Authentication failed."
                        _isAuthenticated.value = false
                    }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun registerUser(email: String, password: String, name: String) {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        if (!ValidationUtils.isValidName(cleanName)) {
            _error.value = "Please enter a valid name (2-50 characters)."
            return
        }
        if (!ValidationUtils.isValidEmail(cleanEmail)) {
            _error.value = "Please enter a valid email address."
            return
        }
        if (!ValidationUtils.isValidPassword(password)) {
            _error.value = "Password must be at least 6 characters."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val regResult = repository.registerWithEmail(cleanEmail, password, cleanName)
                regResult.fold(
                    onSuccess = { uid ->
                        repository.seedGlobalDataIfNeeded()
                        val newUser = User(
                            id = uid,
                            email = cleanEmail,
                            name = cleanName,
                            joinDate = System.currentTimeMillis(),
                            lastActiveDate = DateUtils.getCurrentDate(),
                            streakDays = 1,
                            totalPoints = 50,
                            level = 1,
                            sustainabilityScore = 50
                        )
                        val createResult = repository.createUser(newUser)
                        createResult.fold(
                            onSuccess = { created ->
                                repository.initializeUserCollections(uid)
                                _currentUser.value = created
                                _isAuthenticated.value = true
                                loadUserDataInternal(uid)
                            },
                            onFailure = { e ->
                                _error.value = e.message ?: "Failed to create user profile."
                            }
                        )
                    },
                    onFailure = { e ->
                        _error.value = e.message ?: "Registration failed."
                        _isAuthenticated.value = false
                    }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        if (idToken.isBlank()) {
            _error.value = "Google Sign-In did not return a valid token."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val googleResult = repository.signInWithGoogle(idToken)
                googleResult.fold(
                    onSuccess = { account ->
                        repository.seedGlobalDataIfNeeded()
                        val existing = repository.getUser(account.uid).getOrNull()
                        if (existing != null) {
                            val updated = updateUserStreakIfNeeded(existing)
                            _currentUser.value = updated
                            _isAuthenticated.value = true
                            loadUserDataInternal(updated.id)
                        } else {
                            val newUser = User(
                                id = account.uid,
                                email = account.email,
                                name = account.displayName.ifBlank {
                                    account.email.substringBefore("@").ifBlank { "Eco User" }
                                },
                                profileImageUrl = account.photoUrl,
                                joinDate = System.currentTimeMillis(),
                                lastActiveDate = DateUtils.getCurrentDate(),
                                streakDays = 1,
                                totalPoints = 50,
                                level = 1,
                                sustainabilityScore = 50
                            )
                            repository.createUser(newUser).fold(
                                onSuccess = { created ->
                                    repository.initializeUserCollections(account.uid)
                                    _currentUser.value = created
                                    _isAuthenticated.value = true
                                    loadUserDataInternal(account.uid)
                                },
                                onFailure = { e ->
                                    _error.value = e.message ?: "Failed to create Google user profile."
                                }
                            )
                        }
                    },
                    onFailure = { e ->
                        _error.value = e.message ?: "Google Sign-In failed."
                    }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val cleanEmail = email.trim()
        if (!ValidationUtils.isValidEmail(cleanEmail)) {
            _error.value = "Please enter a valid email address to reset your password."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.sendPasswordReset(cleanEmail).fold(
                    onSuccess = { _passwordResetSent.value = true },
                    onFailure = { e -> _error.value = e.message ?: "Failed to send password reset email." }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        repository.signOut()
        _currentUser.value = null
        _isAuthenticated.value = false
        _carbonFootprints.value = emptyList()
        _trackingData.value = emptyList()
        _goals.value = emptyList()
        _wishlist.value = emptyList()
        _userAchievements.value = emptyList()
        _userChallenges.value = emptyList()
        _userTipStates.value = emptyList()
        _userPreferences.value = null
        _totalEmissions.value = 0.0
        _weeklyEmissions.value = 0.0
        _monthlyEmissions.value = 0.0
        _sustainabilityScore.value = 0
    }

    fun refreshDashboardData(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                loadUserDataInternal(userId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to refresh dashboard data."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadUserDataInternal(userId: String) {
        repository.getUser(userId).onSuccess { loaded ->
            if (loaded != null) {
                _currentUser.value = loaded
            }
        }

        val footprints = repository.getCarbonFootprintsByUser(userId).getOrElse {
            _error.value = it.message ?: "Failed to load carbon footprints."
            emptyList()
        }
        _carbonFootprints.value = footprints

        val tracking = repository.getTrackingDataByUser(userId).getOrElse {
            _error.value = it.message ?: "Failed to load tracking activities."
            emptyList()
        }
        _trackingData.value = tracking

        val goals = repository.getUserGoals(userId).getOrElse { emptyList() }
        _goals.value = goals

        val userAchievements = repository.getUserAchievements(userId).getOrElse { emptyList() }
        _userAchievements.value = userAchievements

        val achievements = repository.getAchievements().getOrElse { emptyList() }
        _achievements.value = achievements

        val userChallenges = repository.getUserChallenges(userId).getOrElse { emptyList() }
        _userChallenges.value = userChallenges

        val activeChallenges = repository.getActiveChallenges().getOrElse { emptyList() }
        _activeChallenges.value = activeChallenges

        val wishlist = repository.getUserWishlist(userId).getOrElse { emptyList() }
        _wishlist.value = wishlist

        val prefs = repository.getUserPreferences(userId).getOrNull()
        _userPreferences.value = prefs

        loadTipsInternal(userId)
        recalculateDashboardMetrics(footprints, tracking, goals)
    }

    private suspend fun updateUserStreakIfNeeded(user: User): User {
        val today = DateUtils.getCurrentDate()
        if (user.lastActiveDate == today) {
            return user
        }
        val newStreak = if (user.lastActiveDate.isNotBlank() && DateUtils.getDaysBetween(user.lastActiveDate, today) == 1L) {
            user.streakDays + 1
        } else {
            1
        }
        repository.updateUserStreak(user.id, newStreak, today)
        return user.copy(streakDays = newStreak, lastActiveDate = today)
    }

    private fun recalculateDashboardMetrics(
        footprints: List<CarbonFootprint>,
        tracking: List<TrackingData>,
        goals: List<Goal>
    ) {
        val total = if (footprints.isNotEmpty()) {
            footprints.sumOf { it.totalEmissions }
        } else {
            tracking.sumOf { it.emissions }
        }
        _totalEmissions.value = total

        val weekStart = DateUtils.getStartOfWeek()
        val monthStart = DateUtils.getStartOfMonth()
        val today = DateUtils.getCurrentDate()

        val weekly = if (footprints.isNotEmpty()) {
            footprints.filter { it.date >= weekStart && it.date <= today }.sumOf { it.totalEmissions }
        } else {
            tracking.filter { it.date >= weekStart && it.date <= today }.sumOf { it.emissions }
        }
        _weeklyEmissions.value = weekly

        val monthly = if (footprints.isNotEmpty()) {
            footprints.filter { it.date >= monthStart && it.date <= today }.sumOf { it.totalEmissions }
        } else {
            tracking.filter { it.date >= monthStart && it.date <= today }.sumOf { it.emissions }
        }
        _monthlyEmissions.value = monthly

        val user = _currentUser.value
        val score = if (user != null) {
            SustainabilityUtils.calculateSustainabilityScore(
                totalEmissions = total,
                streakDays = user.streakDays,
                completedGoals = goals.count { it.isCompleted },
                totalPoints = user.totalPoints
            )
        } else {
            50
        }
        _sustainabilityScore.value = score
    }

    fun loadUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.getUser(userId).fold(
                    onSuccess = { _currentUser.value = it },
                    onFailure = { _error.value = it.message ?: "Failed to load user." }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUser(user: User) {
        if (!ValidationUtils.isValidName(user.name)) {
            _error.value = "Name must be between 2 and 50 characters."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.updateUser(user).fold(
                    onSuccess = {
                        repository.updateAuthDisplayName(user.name)
                        _currentUser.value = user
                        _actionMessage.value = "Profile updated successfully."
                    },
                    onFailure = { _error.value = it.message ?: "Failed to update profile." }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadProfileImage(userId: String, imageBytes: ByteArray) {
        if (userId.isBlank() || imageBytes.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.uploadProfileImage(userId, imageBytes).fold(
                    onSuccess = { downloadUrl ->
                        val current = _currentUser.value
                        if (current != null) {
                            val updated = current.copy(profileImageUrl = downloadUrl)
                            repository.updateUser(updated).onSuccess {
                                _currentUser.value = updated
                                _actionMessage.value = "Profile photo updated."
                            }.onFailure { err ->
                                _error.value = err.message ?: "Failed to save profile photo URL."
                            }
                        }
                    },
                    onFailure = { err ->
                        _error.value = err.message ?: "Failed to upload profile photo. Check Firebase Storage configuration."
                    }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveCarbonFootprint(footprint: CarbonFootprint) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.saveCarbonFootprint(footprint).fold(
                    onSuccess = {
                        val updated = repository.getCarbonFootprintsByUser(footprint.userId).getOrElse { emptyList() }
                        _carbonFootprints.value = updated
                        recalculateDashboardMetrics(updated, _trackingData.value.orEmpty(), _goals.value.orEmpty())
                    },
                    onFailure = { _error.value = it.message ?: "Failed to save carbon footprint." }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCarbonFootprints(userId: String) {
        viewModelScope.launch {
            repository.getCarbonFootprintsByUser(userId).fold(
                onSuccess = { list ->
                    _carbonFootprints.value = list
                    recalculateDashboardMetrics(list, _trackingData.value.orEmpty(), _goals.value.orEmpty())
                },
                onFailure = { _error.value = it.message ?: "Failed to load carbon footprints." }
            )
        }
    }

    fun saveTrackingData(data: TrackingData) {
        if (data.userId.isBlank()) {
            _error.value = "User session not found. Please sign in again."
            return
        }
        if (data.value <= 0) {
            _error.value = "Value must be greater than zero."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.saveTrackingData(data).fold(
                    onSuccess = {
                        val updatedTracking = repository.getTrackingDataByUser(data.userId).getOrElse { emptyList() }
                        _trackingData.value = updatedTracking
                        val updatedFootprints = repository.getCarbonFootprintsByUser(data.userId).getOrElse { emptyList() }
                        _carbonFootprints.value = updatedFootprints

                        repository.incrementUserPoints(data.userId, 10).onSuccess { updatedUser ->
                            _currentUser.value = updatedUser
                        }
                        recalculateDashboardMetrics(updatedFootprints, updatedTracking, _goals.value.orEmpty())
                        _actionMessage.value = "Activity logged (+10 pts)."
                    },
                    onFailure = { _error.value = it.message ?: "Failed to save tracking activity." }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateTrackingData(data: TrackingData) {
        if (data.id.isBlank() || data.userId.isBlank()) {
            _error.value = "Invalid tracking record."
            return
        }
        if (data.value <= 0) {
            _error.value = "Value must be greater than zero."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.updateTrackingData(data).fold(
                    onSuccess = {
                        val updatedTracking = repository.getTrackingDataByUser(data.userId).getOrElse { emptyList() }
                        _trackingData.value = updatedTracking
                        val updatedFootprints = repository.getCarbonFootprintsByUser(data.userId).getOrElse { emptyList() }
                        _carbonFootprints.value = updatedFootprints
                        recalculateDashboardMetrics(updatedFootprints, updatedTracking, _goals.value.orEmpty())
                        _actionMessage.value = "Activity updated."
                    },
                    onFailure = { _error.value = it.message ?: "Failed to update tracking activity." }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTrackingData(trackingId: String, userId: String, date: String) {
        if (trackingId.isBlank() || userId.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteTrackingData(trackingId, userId, date).fold(
                    onSuccess = {
                        val updatedTracking = repository.getTrackingDataByUser(userId).getOrElse { emptyList() }
                        _trackingData.value = updatedTracking
                        val updatedFootprints = repository.getCarbonFootprintsByUser(userId).getOrElse { emptyList() }
                        _carbonFootprints.value = updatedFootprints
                        recalculateDashboardMetrics(updatedFootprints, updatedTracking, _goals.value.orEmpty())
                        _actionMessage.value = "Activity deleted."
                    },
                    onFailure = { _error.value = it.message ?: "Failed to delete tracking activity." }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadTrackingData(userId: String) {
        viewModelScope.launch {
            repository.getTrackingDataByUser(userId).fold(
                onSuccess = { list ->
                    _trackingData.value = list
                    recalculateDashboardMetrics(_carbonFootprints.value.orEmpty(), list, _goals.value.orEmpty())
                },
                onFailure = { _error.value = it.message ?: "Failed to load tracking data." }
            )
        }
    }

    fun loadEcoProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.getEcoProducts().fold(
                    onSuccess = { _ecoProducts.value = it },
                    onFailure = { _error.value = it.message ?: "Failed to load eco products." }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadEcoProductsByCategory(category: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.getEcoProductsByCategory(category).fold(
                    onSuccess = { _ecoProducts.value = it },
                    onFailure = { _error.value = it.message ?: "Failed to filter eco products." }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadRecommendedProducts() {
        viewModelScope.launch {
            repository.getRecommendedProducts().fold(
                onSuccess = { _recommendedProducts.value = it },
                onFailure = { _error.value = it.message ?: "Failed to load recommended products." }
            )
        }
    }

    fun loadProductReviews(productId: String) {
        viewModelScope.launch {
            repository.getProductReviews(productId).fold(
                onSuccess = { _productReviews.value = it },
                onFailure = { _error.value = it.message ?: "Failed to load product reviews." }
            )
        }
    }

    fun saveProductReview(review: ProductReview) {
        viewModelScope.launch {
            repository.saveProductReview(review).fold(
                onSuccess = {
                    val reviews = repository.getProductReviews(review.productId).getOrElse { emptyList() }
                    _productReviews.value = reviews
                    _actionMessage.value = "Review submitted."
                },
                onFailure = { _error.value = it.message ?: "Failed to save review." }
            )
        }
    }

    fun addToWishlist(wishlist: Wishlist) {
        viewModelScope.launch {
            repository.addToWishlist(wishlist).fold(
                onSuccess = {
                    val updated = repository.getUserWishlist(wishlist.userId).getOrElse { emptyList() }
                    _wishlist.value = updated
                    _actionMessage.value = "Added to wishlist."
                },
                onFailure = { _error.value = it.message ?: "Failed to add to wishlist." }
            )
        }
    }

    fun removeFromWishlist(wishlistId: String, userId: String) {
        viewModelScope.launch {
            repository.removeFromWishlist(wishlistId).fold(
                onSuccess = {
                    val updated = repository.getUserWishlist(userId).getOrElse { emptyList() }
                    _wishlist.value = updated
                    _actionMessage.value = "Removed from wishlist."
                },
                onFailure = { _error.value = it.message ?: "Failed to remove from wishlist." }
            )
        }
    }

    fun loadWishlist(userId: String) {
        viewModelScope.launch {
            repository.getUserWishlist(userId).fold(
                onSuccess = { _wishlist.value = it },
                onFailure = { _error.value = it.message ?: "Failed to load wishlist." }
            )
        }
    }

    fun loadTips(userId: String? = _currentUser.value?.id ?: repository.currentAuthUserId()) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                loadTipsInternal(userId)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadTipsInternal(userId: String?) {
        val rawTips = repository.getTips().getOrElse {
            _error.value = it.message ?: "Failed to load sustainability tips."
            return
        }
        val tipStates = if (!userId.isNullOrBlank()) {
            repository.getUserTipStates(userId).getOrElse { emptyList() }
        } else {
            emptyList()
        }
        _userTipStates.value = tipStates
        val stateByTipId = tipStates.associateBy { it.tipId }
        _tips.value = rawTips.map { tip ->
            val state = stateByTipId[tip.id]
            if (state != null) tip.copy(isBookmarked = state.isBookmarked) else tip
        }
    }

    fun createTip(tip: Tip) {
        if (tip.title.isBlank() || tip.content.isBlank()) {
            _error.value = "Tip title and content are required."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.createTip(tip).fold(
                    onSuccess = {
                        loadTipsInternal(_currentUser.value?.id ?: repository.currentAuthUserId())
                        _actionMessage.value = "Tip created."
                    },
                    onFailure = { _error.value = it.message ?: "Failed to create tip." }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleTipBookmark(tip: Tip) {
        val userId = _currentUser.value?.id ?: repository.currentAuthUserId()
        if (userId.isNullOrBlank()) {
            _error.value = "Please sign in to bookmark tips."
            return
        }
        val existing = _userTipStates.value.orEmpty().find { it.tipId == tip.id }
        val newBookmark = !(existing?.isBookmarked ?: tip.isBookmarked)
        val newState = (existing ?: UserTipState(userId = userId, tipId = tip.id))
            .copy(isBookmarked = newBookmark)

        viewModelScope.launch {
            repository.saveUserTipState(newState).fold(
                onSuccess = { saved ->
                    val updatedStates = _userTipStates.value.orEmpty()
                        .filterNot { it.tipId == tip.id } + saved
                    _userTipStates.value = updatedStates
                    _tips.value = _tips.value.orEmpty().map {
                        if (it.id == tip.id) it.copy(isBookmarked = newBookmark) else it
                    }
                    _actionMessage.value = if (newBookmark) "Tip bookmarked." else "Bookmark removed."
                },
                onFailure = { _error.value = it.message ?: "Failed to update bookmark." }
            )
        }
    }

    fun likeTip(tipId: String) {
        viewModelScope.launch {
            repository.likeTip(tipId).fold(
                onSuccess = {
                    _tips.value = _tips.value.orEmpty().map {
                        if (it.id == tipId) it.copy(likes = it.likes + 1) else it
                    }
                },
                onFailure = { _error.value = it.message ?: "Failed to like tip." }
            )
        }
    }

    fun markTipCompleted(tip: Tip, notes: String = "") {
        val userId = _currentUser.value?.id ?: repository.currentAuthUserId()
        if (userId.isNullOrBlank()) {
            _error.value = "Please sign in to track completed tips."
            return
        }
        val existing = _userTipStates.value.orEmpty().find { it.tipId == tip.id }
        val alreadyCompleted = existing?.isCompleted == true
        val newState = (existing ?: UserTipState(userId = userId, tipId = tip.id)).copy(
            isCompleted = true,
            notes = notes
        )

        viewModelScope.launch {
            repository.saveUserTipState(newState).fold(
                onSuccess = { saved ->
                    _userTipStates.value = _userTipStates.value.orEmpty()
                        .filterNot { it.tipId == tip.id } + saved
                    if (!alreadyCompleted) {
                        val reward = 25
                        repository.incrementUserPoints(userId, reward).onSuccess { updatedUser ->
                            _currentUser.value = updatedUser
                        }
                        _actionMessage.value = "Tip completed! +$reward points earned."
                    } else {
                        _actionMessage.value = "Tip implementation notes updated."
                    }
                },
                onFailure = { _error.value = it.message ?: "Failed to save tip completion." }
            )
        }
    }

    fun loadAchievements() {
        viewModelScope.launch {
            repository.getAchievements().fold(
                onSuccess = { _achievements.value = it },
                onFailure = { _error.value = it.message ?: "Failed to load achievements." }
            )
        }
    }

    fun loadUserAchievements(userId: String) {
        viewModelScope.launch {
            repository.getUserAchievements(userId).fold(
                onSuccess = { _userAchievements.value = it },
                onFailure = { _error.value = it.message ?: "Failed to load user achievements." }
            )
        }
    }

    fun saveGoal(goal: Goal) {
        if (goal.title.isBlank() || goal.targetValue <= 0) {
            _error.value = "Goal title and positive target value are required."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.saveGoal(goal).fold(
                    onSuccess = {
                        val updated = repository.getUserGoals(goal.userId).getOrElse { emptyList() }
                        _goals.value = updated
                        recalculateDashboardMetrics(_carbonFootprints.value.orEmpty(), _trackingData.value.orEmpty(), updated)
                        _actionMessage.value = "Goal created."
                    },
                    onFailure = { _error.value = it.message ?: "Failed to create goal." }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadGoals(userId: String) {
        viewModelScope.launch {
            repository.getUserGoals(userId).fold(
                onSuccess = { list ->
                    _goals.value = list
                    recalculateDashboardMetrics(_carbonFootprints.value.orEmpty(), _trackingData.value.orEmpty(), list)
                },
                onFailure = { _error.value = it.message ?: "Failed to load goals." }
            )
        }
    }

    fun updateGoal(goal: Goal) {
        val previous = _goals.value.orEmpty().find { it.id == goal.id }
        val newlyCompleted = goal.isCompleted && (previous?.isCompleted != true)

        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.updateGoal(goal).fold(
                    onSuccess = {
                        val updated = repository.getUserGoals(goal.userId).getOrElse { emptyList() }
                        _goals.value = updated
                        if (newlyCompleted) {
                            val reward = 100
                            repository.incrementUserPoints(goal.userId, reward).onSuccess { u ->
                                _currentUser.value = u
                            }
                            _actionMessage.value = "Goal completed! +$reward points earned."
                        } else {
                            _actionMessage.value = "Goal progress updated."
                        }
                        recalculateDashboardMetrics(_carbonFootprints.value.orEmpty(), _trackingData.value.orEmpty(), updated)
                    },
                    onFailure = { _error.value = it.message ?: "Failed to update goal." }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteGoal(goalId: String, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteGoal(goalId).fold(
                    onSuccess = {
                        val updated = repository.getUserGoals(userId).getOrElse { emptyList() }
                        _goals.value = updated
                        recalculateDashboardMetrics(_carbonFootprints.value.orEmpty(), _trackingData.value.orEmpty(), updated)
                        _actionMessage.value = "Goal deleted."
                    },
                    onFailure = { _error.value = it.message ?: "Failed to delete goal." }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadActiveChallenges() {
        viewModelScope.launch {
            repository.getActiveChallenges().fold(
                onSuccess = { _activeChallenges.value = it },
                onFailure = { _error.value = it.message ?: "Failed to load challenges." }
            )
        }
    }

    fun loadUserChallenges(userId: String) {
        viewModelScope.launch {
            repository.getUserChallenges(userId).fold(
                onSuccess = { _userChallenges.value = it },
                onFailure = { _error.value = it.message ?: "Failed to load user challenges." }
            )
        }
    }

    fun joinChallenge(challenge: Challenge) {
        val userId = _currentUser.value?.id ?: repository.currentAuthUserId()
        if (userId.isNullOrBlank()) {
            _error.value = "Please sign in to join challenges."
            return
        }
        val userChallenge = UserChallenge(
            id = "${userId}_${challenge.id}",
            userId = userId,
            challengeId = challenge.id,
            progress = 0.0,
            isCompleted = false,
            joinedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.saveUserChallenge(userChallenge).fold(
                onSuccess = {
                    val updated = repository.getUserChallenges(userId).getOrElse { emptyList() }
                    _userChallenges.value = updated
                    _actionMessage.value = "Joined challenge: ${challenge.title}"
                },
                onFailure = { _error.value = it.message ?: "Failed to join challenge." }
            )
        }
    }

    fun updateUserChallenge(userChallenge: UserChallenge) {
        val previous = _userChallenges.value.orEmpty().find { it.id == userChallenge.id }
        val newlyCompleted = userChallenge.isCompleted && (previous?.isCompleted != true)

        viewModelScope.launch {
            repository.updateUserChallenge(userChallenge).fold(
                onSuccess = {
                    val updated = repository.getUserChallenges(userChallenge.userId).getOrElse { emptyList() }
                    _userChallenges.value = updated
                    if (newlyCompleted) {
                        val reward = _activeChallenges.value.orEmpty()
                            .find { it.id == userChallenge.challengeId }
                            ?.points ?: 100
                        repository.incrementUserPoints(userChallenge.userId, reward).onSuccess { u ->
                            _currentUser.value = u
                        }
                        _actionMessage.value = "Challenge completed! +$reward points earned."
                    } else {
                        _actionMessage.value = "Challenge progress updated."
                    }
                },
                onFailure = { _error.value = it.message ?: "Failed to update challenge." }
            )
        }
    }

    fun loadUserPreferences(userId: String) {
        viewModelScope.launch {
            repository.getUserPreferences(userId).fold(
                onSuccess = { _userPreferences.value = it },
                onFailure = { _error.value = it.message ?: "Failed to load preferences." }
            )
        }
    }

    fun updateUserPreferences(preferences: UserPreferences) {
        viewModelScope.launch {
            repository.updateUserPreferences(preferences).fold(
                onSuccess = {
                    _userPreferences.value = preferences
                    _actionMessage.value = "Preferences saved."
                },
                onFailure = { _error.value = it.message ?: "Failed to save preferences." }
            )
        }
    }

    fun loadLeaderboard(limit: Int = 20) {
        viewModelScope.launch {
            repository.getLeaderboardUsers(limit).fold(
                onSuccess = { _leaderboardUsers.value = it },
                onFailure = { _error.value = it.message ?: "Failed to load leaderboard." }
            )
        }
    }
}
