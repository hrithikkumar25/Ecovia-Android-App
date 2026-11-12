package com.greengauge

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.util.Date

object DatabaseHelper {
    private const val TAG = "DatabaseHelper"

    val db: FirebaseFirestore by lazy { Firebase.firestore }

    private fun resolveUserId(providedUserId: String): String? {
        return providedUserId.ifBlank { FirebaseAuth.getInstance().currentUser?.uid }
    }

    suspend fun createInitialGoal(userId: String, goalData: Map<String, Any>): Result<String> {
        return try {
            val goalId = "goal_welcome_$userId"
            val docRef = db.collection(Constants.COLLECTION_GOALS).document(goalId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                return Result.success(goalId)
            }
            val goal = Goal(
                id = goalId,
                userId = userId,
                title = goalData["title"] as? String ?: "Welcome to Ecovia!",
                targetValue = (goalData["targetValue"] as? Number)?.toDouble() ?: 50.0,
                currentValue = 0.0,
                unit = goalData["unit"] as? String ?: "kg CO2 saved",
                category = goalData["category"] as? String ?: "getting_started",
                deadline = (goalData["deadline"] as? Number)?.toLong()
                    ?: (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000L),
                isCompleted = false
            )
            docRef.set(goal).await()
            Result.success(goalId)
        } catch (e: Exception) {
            Log.e(TAG, "createInitialGoal failed", e)
            Result.failure(e)
        }
    }

    suspend fun createCarbonFootprint(userId: String, footprintData: Map<String, Any>): Result<String> {
        return try {
            val today = DateUtils.getCurrentDate()
            val footprintId = "${userId}_$today"
            val docRef = db.collection(Constants.COLLECTION_CARBON_FOOTPRINTS).document(footprintId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                return Result.success(footprintId)
            }
            val footprint = CarbonFootprint(
                id = footprintId,
                userId = userId,
                date = today,
                transportationEmissions = (footprintData["transportationEmissions"] as? Number)?.toDouble() ?: 0.0,
                energyEmissions = (footprintData["energyEmissions"] as? Number)?.toDouble() ?: 0.0,
                dietEmissions = (footprintData["dietEmissions"] as? Number)?.toDouble() ?: 0.0,
                wasteEmissions = (footprintData["wasteEmissions"] as? Number)?.toDouble() ?: 0.0,
                waterEmissions = (footprintData["waterEmissions"] as? Number)?.toDouble() ?: 0.0,
                shoppingEmissions = (footprintData["shoppingEmissions"] as? Number)?.toDouble() ?: 0.0,
                totalEmissions = (footprintData["totalEmissions"] as? Number)?.toDouble() ?: 0.0
            )
            docRef.set(footprint).await()
            Result.success(footprintId)
        } catch (e: Exception) {
            Log.e(TAG, "createCarbonFootprint failed", e)
            Result.failure(e)
        }
    }

    suspend fun initializeUserCollections(userId: String): Result<Unit> {
        return try {
            val existingGoals = getUserGoals(userId).getOrNull()
            if (existingGoals.isNullOrEmpty()) {
                val welcomeGoal = mapOf(
                    "title" to "Welcome to Ecovia!",
                    "targetValue" to 50.0,
                    "unit" to "kg CO2 saved",
                    "category" to Constants.CATEGORY_TRANSPORTATION,
                    "deadline" to System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L)
                )
                createInitialGoal(userId, welcomeGoal).getOrThrow()
            }

            val existingFootprints = getCarbonFootprintsByUser(userId).getOrNull()
            if (existingFootprints.isNullOrEmpty()) {
                createCarbonFootprint(userId, emptyMap()).getOrThrow()
            }

            val existingPrefs = getUserPreferences(userId).getOrNull()
            if (existingPrefs == null) {
                val userPreferences = UserPreferences(
                    id = userId,
                    userId = userId,
                    notificationsEnabled = true,
                    darkModeEnabled = false,
                    trackingEnabled = true,
                    shareDataEnabled = false,
                    preferredCategories = listOf(Constants.CATEGORY_ENERGY, Constants.CATEGORY_WATER),
                    language = Constants.DEFAULT_LANGUAGE
                )
                saveUserPreferences(userPreferences).getOrThrow()
            }

            val existingAchievements = getUserAchievements(userId).getOrNull()
            if (existingAchievements.isNullOrEmpty()) {
                val initialAchievements = listOf(
                    UserAchievement(
                        id = "${userId}_first_login",
                        userId = userId,
                        achievementId = "first_login",
                        progress = 100,
                        isCompleted = true
                    ),
                    UserAchievement(
                        id = "${userId}_week_streak",
                        userId = userId,
                        achievementId = "week_streak",
                        progress = 0,
                        isCompleted = false
                    ),
                    UserAchievement(
                        id = "${userId}_eco_warrior",
                        userId = userId,
                        achievementId = "eco_warrior",
                        progress = 0,
                        isCompleted = false
                    )
                )
                initialAchievements.forEach { achievement ->
                    db.collection(Constants.COLLECTION_USER_ACHIEVEMENTS)
                        .document(achievement.id)
                        .set(achievement)
                        .await()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "initializeUserCollections failed for $userId", e)
            Result.failure(e)
        }
    }

    suspend fun seedGlobalDataIfNeeded(): Result<Unit> {
        return try {
            val tips = getTips().getOrNull()
            if (tips.isNullOrEmpty()) {
                createInitialTips().getOrThrow()
            }

            val products = getEcoProducts().getOrNull()
            if (products.isNullOrEmpty()) {
                createInitialProducts().getOrThrow()
            }

            val challenges = db.collection(Constants.COLLECTION_CHALLENGES).limit(1).get().await()
            if (challenges.isEmpty) {
                createInitialChallenges().getOrThrow()
            }

            val achievements = getAchievements().getOrNull()
            if (achievements.isNullOrEmpty()) {
                createInitialAchievements().getOrThrow()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "seedGlobalDataIfNeeded failed", e)
            Result.failure(e)
        }
    }

    private suspend fun createInitialTips(): Result<Unit> {
        return try {
            val initialTips = listOf(
                Tip(
                    id = "tip_led_bulbs",
                    title = "Switch to LED Bulbs",
                    content = "Replace incandescent bulbs with LED bulbs to save energy and reduce your carbon footprint by up to 80%.",
                    category = Constants.CATEGORY_ENERGY,
                    difficulty = Constants.DIFFICULTY_BEGINNER,
                    impactLevel = Constants.IMPACT_MEDIUM,
                    steps = listOf("Choose Energy Star LED bulbs", "Replace old bulbs safely", "Recycle old bulbs properly"),
                    tags = listOf("energy", "lighting", "savings"),
                    likes = 42
                ),
                Tip(
                    id = "tip_public_transport",
                    title = "Use Public Transportation",
                    content = "Choose buses, trains, or carpooling instead of driving alone to significantly cut commute emissions.",
                    category = Constants.CATEGORY_TRANSPORTATION,
                    difficulty = Constants.DIFFICULTY_BEGINNER,
                    impactLevel = Constants.IMPACT_HIGH,
                    steps = listOf("Check local transit schedules", "Plan your commute route", "Use transit passes or carpool"),
                    tags = listOf("transport", "emissions", "commute"),
                    likes = 65
                ),
                Tip(
                    id = "tip_plant_based_diet",
                    title = "Try Plant-Based Meals",
                    content = "Swapping meat for plant-based meals a few days a week is one of the highest-impact ways to lower dietary emissions.",
                    category = Constants.CATEGORY_DIET,
                    difficulty = Constants.DIFFICULTY_INTERMEDIATE,
                    impactLevel = Constants.IMPACT_HIGH,
                    steps = listOf("Plan one meatless day per week", "Stock up on seasonal vegetables and legumes", "Explore plant-based recipes"),
                    tags = listOf("diet", "plant-based", "health"),
                    likes = 54
                ),
                Tip(
                    id = "tip_reduce_food_waste",
                    title = "Reduce Food Waste & Compost",
                    content = "Plan weekly meals, store produce properly, and compost organic scraps to keep methane-producing waste out of landfills.",
                    category = Constants.CATEGORY_WASTE,
                    difficulty = Constants.DIFFICULTY_INTERMEDIATE,
                    impactLevel = Constants.IMPACT_MEDIUM,
                    steps = listOf("Plan meals before shopping", "Store leftovers in reusable containers", "Compost fruit and vegetable scraps"),
                    tags = listOf("waste", "food", "composting"),
                    likes = 38
                ),
                Tip(
                    id = "tip_fix_water_leaks",
                    title = "Fix Water Leaks & Conserve",
                    content = "Repair dripping faucets, take shorter showers, and turn off taps while brushing to conserve fresh water and heating energy.",
                    category = Constants.CATEGORY_WATER,
                    difficulty = Constants.DIFFICULTY_BEGINNER,
                    impactLevel = Constants.IMPACT_MEDIUM,
                    steps = listOf("Inspect faucets and toilets for leaks", "Install low-flow aerators", "Collect rainwater for plants"),
                    tags = listOf("water", "conservation", "home"),
                    likes = 31
                )
            )
            initialTips.forEach { tip ->
                db.collection(Constants.COLLECTION_TIPS).document(tip.id).set(tip).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "createInitialTips failed", e)
            Result.failure(e)
        }
    }

    private suspend fun createInitialProducts(): Result<Unit> {
        return try {
            val initialProducts = listOf(
                EcoProduct(
                    id = "prod_solar_power_bank",
                    name = "Solar Power Bank",
                    category = Constants.CATEGORY_ENERGY,
                    sustainabilityRating = 4.5f,
                    price = 45.99,
                    description = "Portable solar charger with 10000mAh capacity for clean off-grid charging.",
                    brand = "EcoCharge",
                    isRecommended = true,
                    tags = listOf("Solar", "Portable", "Energy"),
                    carbonFootprint = 2.1,
                    isRecyclable = true,
                    isBiodegradable = false,
                    barcode = "123456789012"
                ),
                EcoProduct(
                    id = "prod_bamboo_toothbrush",
                    name = "Bamboo Toothbrush Set",
                    category = Constants.CATEGORY_WASTE,
                    sustainabilityRating = 4.8f,
                    price = 12.99,
                    description = "Biodegradable FSC-certified bamboo toothbrushes, pack of 4.",
                    brand = "BambooCare",
                    isRecommended = true,
                    tags = listOf("Bamboo", "Biodegradable", "Oral Care"),
                    carbonFootprint = 0.3,
                    isRecyclable = false,
                    isBiodegradable = true,
                    barcode = "123456789013"
                ),
                EcoProduct(
                    id = "prod_reusable_bottle",
                    name = "Reusable Steel Water Bottle",
                    category = Constants.CATEGORY_WATER,
                    sustainabilityRating = 4.7f,
                    price = 24.99,
                    description = "Insulated stainless steel water bottle that replaces single-use plastic.",
                    brand = "HydroEco",
                    isRecommended = true,
                    tags = listOf("Reusable", "Steel", "BPA-Free"),
                    carbonFootprint = 1.8,
                    isRecyclable = true,
                    isBiodegradable = false,
                    barcode = "123456789014"
                ),
                EcoProduct(
                    id = "prod_led_smart_bulb",
                    name = "LED Smart Bulb",
                    category = Constants.CATEGORY_ENERGY,
                    sustainabilityRating = 4.6f,
                    price = 19.99,
                    description = "Energy-efficient LED bulb with automated scheduling to cut standby power.",
                    brand = "SmartGlow",
                    isRecommended = false,
                    tags = listOf("LED", "Smart", "Energy Saving"),
                    carbonFootprint = 0.8,
                    isRecyclable = true,
                    isBiodegradable = false,
                    barcode = "123456789015"
                ),
                EcoProduct(
                    id = "prod_cotton_tote",
                    name = "Organic Cotton Tote Bag",
                    category = Constants.CATEGORY_WASTE,
                    sustainabilityRating = 4.4f,
                    price = 15.99,
                    description = "Heavy-duty organic cotton shopping bag built to replace plastic bags.",
                    brand = "EcoBag Co",
                    isRecommended = false,
                    tags = listOf("Organic", "Cotton", "Reusable"),
                    carbonFootprint = 0.5,
                    isRecyclable = false,
                    isBiodegradable = true,
                    barcode = "123456789016"
                )
            )
            initialProducts.forEach { product ->
                db.collection(Constants.COLLECTION_ECO_PRODUCTS).document(product.id).set(product).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "createInitialProducts failed", e)
            Result.failure(e)
        }
    }

    private suspend fun createInitialChallenges(): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val thirtyDays = 30L * 24 * 60 * 60 * 1000L
            val initialChallenges = listOf(
                Challenge(
                    id = "chal_carbon_reduction",
                    title = "Carbon Reduction Challenge",
                    description = "Reduce your transportation carbon footprint by 20 kg this month.",
                    category = Constants.CATEGORY_TRANSPORTATION,
                    targetValue = 20.0,
                    unit = "kg CO2",
                    duration = 30,
                    points = 500,
                    startDate = now,
                    endDate = now + thirtyDays,
                    isActive = true,
                    participantCount = 0
                ),
                Challenge(
                    id = "chal_energy_saver",
                    title = "Energy Saver Challenge",
                    description = "Save 100 kWh of household energy this month.",
                    category = Constants.CATEGORY_ENERGY,
                    targetValue = 100.0,
                    unit = "kWh",
                    duration = 30,
                    points = 300,
                    startDate = now,
                    endDate = now + thirtyDays,
                    isActive = true,
                    participantCount = 0
                )
            )
            initialChallenges.forEach { challenge ->
                db.collection(Constants.COLLECTION_CHALLENGES).document(challenge.id).set(challenge).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "createInitialChallenges failed", e)
            Result.failure(e)
        }
    }

    private suspend fun createInitialAchievements(): Result<Unit> {
        return try {
            val initialAchievements = listOf(
                Achievement(
                    id = "ach_first_steps",
                    title = "First Steps",
                    description = "Complete your first activity tracking",
                    category = "getting_started",
                    requirement = 1,
                    points = 50,
                    isUnlocked = false
                ),
                Achievement(
                    id = "ach_week_warrior",
                    title = "Week Warrior",
                    description = "Maintain a 7-day streak",
                    category = "streaks",
                    requirement = 7,
                    points = 100,
                    isUnlocked = false
                ),
                Achievement(
                    id = "ach_eco_champion",
                    title = "Eco Champion",
                    description = "Earn 1000 points",
                    category = "points",
                    requirement = 1000,
                    points = 200,
                    isUnlocked = false
                )
            )
            initialAchievements.forEach { achievement ->
                db.collection(Constants.COLLECTION_ACHIEVEMENTS).document(achievement.id).set(achievement).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "createInitialAchievements failed", e)
            Result.failure(e)
        }
    }

    suspend fun createUser(user: User): Result<String> {
        return createUserWithResult(user).map { it.id }
    }

    suspend fun createUserWithResult(user: User): Result<User> {
        return try {
            if (!ValidationUtils.isValidEmail(user.email) || !ValidationUtils.isValidName(user.name)) {
                return Result.failure(Exception("Invalid user data: valid email and name are required"))
            }
            val uid = resolveUserId(user.id)
                ?: return Result.failure(Exception("Firebase Authentication UID is required"))

            val userWithId = user.copy(
                id = uid,
                joinDate = if (user.joinDate > 0L) user.joinDate else DateUtils.getTimestamp(),
                level = SustainabilityUtils.calculateLevel(user.totalPoints),
                lastActiveDate = user.lastActiveDate.ifBlank { DateUtils.getCurrentDate() }
            )
            db.collection(Constants.COLLECTION_USERS).document(uid).set(userWithId).await()
            initializeUserCollections(uid)
            Result.success(userWithId)
        } catch (e: Exception) {
            Log.e(TAG, "createUserWithResult failed", e)
            Result.failure(e)
        }
    }

    suspend fun getUser(userId: String): Result<User?> {
        return try {
            if (userId.isBlank()) return Result.failure(Exception("User ID cannot be blank"))
            val document = db.collection(Constants.COLLECTION_USERS).document(userId).get().await()
            val user = document.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "getUser failed for $userId", e)
            Result.failure(e)
        }
    }

    suspend fun updateUser(user: User): Result<Unit> {
        return try {
            if (user.id.isBlank()) {
                return Result.failure(Exception("User ID is required for update"))
            }
            if (!ValidationUtils.isValidName(user.name) ||
                !ValidationUtils.isValidScore(user.sustainabilityScore) ||
                !ValidationUtils.isValidPoints(user.totalPoints) ||
                !ValidationUtils.isValidLevel(user.level) ||
                !ValidationUtils.isValidStreakDays(user.streakDays)
            ) {
                return Result.failure(Exception("Invalid user data"))
            }
            val updatedUser = user.copy(level = SustainabilityUtils.calculateLevel(user.totalPoints))
            db.collection(Constants.COLLECTION_USERS).document(user.id).set(updatedUser, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUser failed for ${user.id}", e)
            Result.failure(e)
        }
    }

    suspend fun getUserByEmail(email: String): Result<User?> {
        return try {
            if (!ValidationUtils.isValidEmail(email)) {
                return Result.failure(Exception("Invalid email"))
            }
            val query = db.collection(Constants.COLLECTION_USERS)
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()
            val user = query.documents.firstOrNull()?.toObject(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "getUserByEmail failed", e)
            Result.failure(e)
        }
    }

    suspend fun getLeaderboardUsers(limit: Int = 20): Result<List<User>> {
        return try {
            val query = db.collection(Constants.COLLECTION_USERS)
                .orderBy("totalPoints", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            Result.success(query.toObjects(User::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "getLeaderboardUsers failed", e)
            Result.failure(e)
        }
    }

    suspend fun uploadProfileImage(userId: String, imageBytes: ByteArray): Result<String> {
        return try {
            val ref = Firebase.storage.reference.child("profile_images/$userId.jpg")
            ref.putBytes(imageBytes).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            db.collection(Constants.COLLECTION_USERS).document(userId)
                .update("profileImageUrl", downloadUrl)
                .await()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "uploadProfileImage failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveCarbonFootprint(carbonFootprint: CarbonFootprint): Result<String> {
        return saveOrUpdateCarbonFootprint(carbonFootprint).map { it.id }
    }

    suspend fun saveOrUpdateCarbonFootprint(carbonFootprint: CarbonFootprint): Result<CarbonFootprint> {
        return try {
            if (!ValidationUtils.isValidEmission(carbonFootprint.transportationEmissions) ||
                !ValidationUtils.isValidEmission(carbonFootprint.energyEmissions) ||
                !ValidationUtils.isValidEmission(carbonFootprint.dietEmissions) ||
                !ValidationUtils.isValidEmission(carbonFootprint.wasteEmissions) ||
                !ValidationUtils.isValidEmission(carbonFootprint.waterEmissions) ||
                !ValidationUtils.isValidEmission(carbonFootprint.shoppingEmissions)
            ) {
                return Result.failure(Exception("Invalid emissions data"))
            }
            val currentUserId = resolveUserId(carbonFootprint.userId)
                ?: return Result.failure(Exception("User not authenticated"))
            val date = carbonFootprint.date.ifBlank { DateUtils.getCurrentDate() }
            val id = carbonFootprint.id.ifBlank { "${currentUserId}_$date" }
            val total = CarbonCalculator.calculateTotalEmissions(carbonFootprint)
            val footprintWithId = carbonFootprint.copy(
                id = id,
                userId = currentUserId,
                date = date,
                totalEmissions = total,
                timestamp = carbonFootprint.timestamp ?: Date()
            )
            db.collection(Constants.COLLECTION_CARBON_FOOTPRINTS).document(id).set(footprintWithId).await()
            updateUserSustainabilityScore(currentUserId, footprintWithId)
            Result.success(footprintWithId)
        } catch (e: Exception) {
            Log.e(TAG, "saveOrUpdateCarbonFootprint failed", e)
            Result.failure(e)
        }
    }

    suspend fun getCarbonFootprintsByUser(userId: String): Result<List<CarbonFootprint>> {
        return try {
            val query = db.collection(Constants.COLLECTION_CARBON_FOOTPRINTS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val footprints = query.toObjects(CarbonFootprint::class.java)
                .sortedByDescending { it.date }
            Result.success(footprints)
        } catch (e: Exception) {
            Log.e(TAG, "getCarbonFootprintsByUser failed", e)
            Result.failure(e)
        }
    }

    suspend fun getCarbonFootprintsByDateRange(userId: String, startDate: String, endDate: String): Result<List<CarbonFootprint>> {
        return try {
            val startMillis = DateUtils.parseDate(startDate)
                ?.atStartOfDay()?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
            val endMillis = DateUtils.parseDate(endDate)
                ?.atStartOfDay()?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
            if (!ValidationUtils.isValidDateRange(startMillis, endMillis)) {
                return Result.failure(Exception("Invalid date range"))
            }
            val allResult = getCarbonFootprintsByUser(userId)
            if (allResult.isFailure) return allResult
            val filtered = (allResult.getOrNull() ?: emptyList())
                .filter { it.date in startDate..endDate }
                .sortedByDescending { it.date }
            Result.success(filtered)
        } catch (e: Exception) {
            Log.e(TAG, "getCarbonFootprintsByDateRange failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveTrackingData(trackingData: TrackingData): Result<String> {
        return saveTrackingDataItem(trackingData).map { it.id }
    }

    suspend fun saveTrackingDataItem(trackingData: TrackingData): Result<TrackingData> {
        return try {
            if (!ValidationUtils.isValidCategory(trackingData.category) ||
                !ValidationUtils.isValidActivityType(trackingData.activityType) ||
                !ValidationUtils.isValidUnit(trackingData.unit) ||
                trackingData.value <= 0.0
            ) {
                return Result.failure(Exception("Invalid tracking data"))
            }
            val currentUserId = resolveUserId(trackingData.userId)
                ?: return Result.failure(Exception("User not authenticated"))
            val id = trackingData.id.ifBlank { EncryptionUtils.generateSecureId() }
            val date = trackingData.date.ifBlank { DateUtils.getCurrentDate() }
            val emissions = CarbonCalculator.calculateEmissions(
                trackingData.category,
                trackingData.activityType,
                trackingData.value,
                trackingData.unit
            )
            val dataWithId = trackingData.copy(
                id = id,
                userId = currentUserId,
                date = date,
                emissions = emissions
            )
            db.collection(Constants.COLLECTION_TRACKING_DATA).document(id).set(dataWithId).await()
            recalculateCarbonFootprintForDate(currentUserId, date)
            Result.success(dataWithId)
        } catch (e: Exception) {
            Log.e(TAG, "saveTrackingDataItem failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateTrackingData(trackingData: TrackingData): Result<TrackingData> {
        return try {
            if (trackingData.id.isBlank()) {
                return Result.failure(Exception("Tracking ID is required for update"))
            }
            if (!ValidationUtils.isValidCategory(trackingData.category) ||
                !ValidationUtils.isValidActivityType(trackingData.activityType) ||
                !ValidationUtils.isValidUnit(trackingData.unit) ||
                trackingData.value <= 0.0
            ) {
                return Result.failure(Exception("Invalid tracking data"))
            }
            val currentUserId = resolveUserId(trackingData.userId)
                ?: return Result.failure(Exception("User not authenticated"))
            val date = trackingData.date.ifBlank { DateUtils.getCurrentDate() }
            val emissions = CarbonCalculator.calculateEmissions(
                trackingData.category,
                trackingData.activityType,
                trackingData.value,
                trackingData.unit
            )
            val updated = trackingData.copy(
                userId = currentUserId,
                date = date,
                emissions = emissions,
                timestamp = trackingData.timestamp ?: Date()
            )
            db.collection(Constants.COLLECTION_TRACKING_DATA).document(updated.id).set(updated).await()
            recalculateCarbonFootprintForDate(currentUserId, date)
            Result.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "updateTrackingData failed", e)
            Result.failure(e)
        }
    }

    suspend fun deleteTrackingData(trackingId: String, userId: String, date: String): Result<Unit> {
        return try {
            if (trackingId.isBlank()) return Result.failure(Exception("Tracking ID is required"))
            val currentUserId = resolveUserId(userId)
                ?: return Result.failure(Exception("User not authenticated"))
            db.collection(Constants.COLLECTION_TRACKING_DATA).document(trackingId).delete().await()
            val targetDate = date.ifBlank { DateUtils.getCurrentDate() }
            recalculateCarbonFootprintForDate(currentUserId, targetDate)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteTrackingData failed", e)
            Result.failure(e)
        }
    }

    private suspend fun recalculateCarbonFootprintForDate(userId: String, date: String) {
        val allTracking = getTrackingDataByUser(userId).getOrNull() ?: return
        val dayItems = allTracking.filter { it.date == date }
        val transport = dayItems.filter { it.category.equals(Constants.CATEGORY_TRANSPORTATION, true) }.sumOf { it.emissions.coerceAtLeast(0.0) }
        val energy = dayItems.filter { it.category.equals(Constants.CATEGORY_ENERGY, true) }.sumOf { it.emissions.coerceAtLeast(0.0) }
        val diet = dayItems.filter { it.category.equals(Constants.CATEGORY_DIET, true) }.sumOf { it.emissions.coerceAtLeast(0.0) }
        val waste = dayItems.filter { it.category.equals(Constants.CATEGORY_WASTE, true) }.sumOf { it.emissions.coerceAtLeast(0.0) }
        val water = dayItems.filter { it.category.equals(Constants.CATEGORY_WATER, true) }.sumOf { it.emissions.coerceAtLeast(0.0) }
        val shopping = dayItems.filter { it.category.equals(Constants.CATEGORY_SHOPPING, true) }.sumOf { it.emissions.coerceAtLeast(0.0) }

        val footprint = CarbonFootprint(
            id = "${userId}_$date",
            userId = userId,
            date = date,
            transportationEmissions = transport,
            energyEmissions = energy,
            dietEmissions = diet,
            wasteEmissions = waste,
            waterEmissions = water,
            shoppingEmissions = shopping,
            totalEmissions = transport + energy + diet + waste + water + shopping,
            timestamp = Date()
        )
        saveOrUpdateCarbonFootprint(footprint)
    }

    suspend fun getTrackingDataByUser(userId: String): Result<List<TrackingData>> {
        return try {
            val query = db.collection(Constants.COLLECTION_TRACKING_DATA)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val trackingData = query.toObjects(TrackingData::class.java)
                .sortedByDescending { it.timestamp?.time ?: 0L }
            Result.success(trackingData)
        } catch (e: Exception) {
            Log.e(TAG, "getTrackingDataByUser failed", e)
            Result.failure(e)
        }
    }

    suspend fun getTrackingDataByCategory(userId: String, category: String): Result<List<TrackingData>> {
        return try {
            if (!ValidationUtils.isValidCategory(category)) {
                return Result.failure(Exception("Invalid category"))
            }
            val query = db.collection(Constants.COLLECTION_TRACKING_DATA)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val trackingData = query.toObjects(TrackingData::class.java)
                .filter { it.category.equals(category, ignoreCase = true) }
                .sortedByDescending { it.timestamp?.time ?: 0L }
            Result.success(trackingData)
        } catch (e: Exception) {
            Log.e(TAG, "getTrackingDataByCategory failed", e)
            Result.failure(e)
        }
    }

    suspend fun getEcoProducts(): Result<List<EcoProduct>> {
        return try {
            val query = db.collection(Constants.COLLECTION_ECO_PRODUCTS)
                .get()
                .await()
            val products = query.toObjects(EcoProduct::class.java)
                .sortedByDescending { it.sustainabilityRating }
            Result.success(products)
        } catch (e: Exception) {
            Log.e(TAG, "getEcoProducts failed", e)
            Result.failure(e)
        }
    }

    suspend fun getEcoProductsByCategory(category: String): Result<List<EcoProduct>> {
        return try {
            if (!ValidationUtils.isValidCategory(category)) {
                return Result.failure(Exception("Invalid category"))
            }
            val query = db.collection(Constants.COLLECTION_ECO_PRODUCTS)
                .whereEqualTo("category", category)
                .get()
                .await()
            val products = query.toObjects(EcoProduct::class.java)
                .sortedByDescending { it.sustainabilityRating }
            Result.success(products)
        } catch (e: Exception) {
            Log.e(TAG, "getEcoProductsByCategory failed", e)
            Result.failure(e)
        }
    }

    suspend fun getUserCarbonFootprints(userId: String): Result<List<CarbonFootprint>> {
        return getCarbonFootprintsByUser(userId)
    }

    suspend fun getRecommendedProducts(): Result<List<EcoProduct>> {
        return try {
            val query = db.collection(Constants.COLLECTION_ECO_PRODUCTS)
                .whereEqualTo("isRecommended", true)
                .get()
                .await()
            val products = query.toObjects(EcoProduct::class.java)
                .sortedByDescending { it.sustainabilityRating }
            Result.success(products)
        } catch (e: Exception) {
            Log.e(TAG, "getRecommendedProducts failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveProductReview(review: ProductReview): Result<String> {
        return try {
            if (!ValidationUtils.isValidRating(review.rating) || review.comment.isBlank()) {
                return Result.failure(Exception("Invalid review data"))
            }
            val currentUserId = resolveUserId(review.userId)
                ?: return Result.failure(Exception("User not authenticated"))
            val id = EncryptionUtils.generateSecureId()
            val reviewWithId = review.copy(
                id = id,
                userId = currentUserId
            )
            db.collection(Constants.COLLECTION_PRODUCT_REVIEWS).document(id).set(reviewWithId).await()
            updateProductRating(review.productId)
            Result.success(id)
        } catch (e: Exception) {
            Log.e(TAG, "saveProductReview failed", e)
            Result.failure(e)
        }
    }

    suspend fun getProductReviews(productId: String): Result<List<ProductReview>> {
        return try {
            val query = db.collection(Constants.COLLECTION_PRODUCT_REVIEWS)
                .whereEqualTo("productId", productId)
                .get()
                .await()
            val reviews = query.toObjects(ProductReview::class.java)
                .sortedByDescending { it.timestamp?.time ?: 0L }
            Result.success(reviews)
        } catch (e: Exception) {
            Log.e(TAG, "getProductReviews failed", e)
            Result.failure(e)
        }
    }

    suspend fun getTips(): Result<List<Tip>> {
        return try {
            val query = db.collection(Constants.COLLECTION_TIPS)
                .get()
                .await()
            val tips = query.toObjects(Tip::class.java)
                .sortedByDescending { it.likes }
            Result.success(tips)
        } catch (e: Exception) {
            Log.e(TAG, "getTips failed", e)
            Result.failure(e)
        }
    }

    suspend fun createTip(tip: Tip): Result<Tip> {
        return try {
            if (tip.title.isBlank() || tip.content.isBlank()) {
                return Result.failure(Exception("Tip title and content are required"))
            }
            val id = tip.id.ifBlank { EncryptionUtils.generateSecureId() }
            val tipWithId = tip.copy(id = id, createdAt = tip.createdAt ?: Date())
            db.collection(Constants.COLLECTION_TIPS).document(id).set(tipWithId).await()
            Result.success(tipWithId)
        } catch (e: Exception) {
            Log.e(TAG, "createTip failed", e)
            Result.failure(e)
        }
    }

    suspend fun likeTip(tipId: String): Result<Int> {
        return try {
            val docRef = db.collection(Constants.COLLECTION_TIPS).document(tipId)
            docRef.update("likes", FieldValue.increment(1)).await()
            val updated = docRef.get().await().toObject(Tip::class.java)
            Result.success(updated?.likes ?: 1)
        } catch (e: Exception) {
            Log.e(TAG, "likeTip failed", e)
            Result.failure(e)
        }
    }

    suspend fun getUserTipStates(userId: String): Result<List<UserTipState>> {
        return try {
            val query = db.collection(Constants.COLLECTION_USER_TIP_STATES)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            Result.success(query.toObjects(UserTipState::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "getUserTipStates failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveUserTipState(state: UserTipState): Result<UserTipState> {
        return try {
            val currentUserId = resolveUserId(state.userId)
                ?: return Result.failure(Exception("User not authenticated"))
            if (state.tipId.isBlank()) return Result.failure(Exception("Tip ID is required"))
            val id = "${currentUserId}_${state.tipId}"
            val updated = state.copy(id = id, userId = currentUserId, updatedAt = Date())
            db.collection(Constants.COLLECTION_USER_TIP_STATES).document(id).set(updated).await()
            Result.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "saveUserTipState failed", e)
            Result.failure(e)
        }
    }

    suspend fun getTipsByCategory(category: String): Result<List<Tip>> {
        return try {
            if (!ValidationUtils.isValidCategory(category)) {
                return Result.failure(Exception("Invalid category"))
            }
            val query = db.collection(Constants.COLLECTION_TIPS)
                .whereEqualTo("category", category)
                .get()
                .await()
            Result.success(query.toObjects(Tip::class.java).sortedByDescending { it.likes })
        } catch (e: Exception) {
            Log.e(TAG, "getTipsByCategory failed", e)
            Result.failure(e)
        }
    }

    suspend fun getTipsByDifficulty(difficulty: String): Result<List<Tip>> {
        return try {
            if (!ValidationUtils.isValidDifficulty(difficulty)) {
                return Result.failure(Exception("Invalid difficulty"))
            }
            val query = db.collection(Constants.COLLECTION_TIPS)
                .whereEqualTo("difficulty", difficulty)
                .get()
                .await()
            Result.success(query.toObjects(Tip::class.java).sortedByDescending { it.likes })
        } catch (e: Exception) {
            Log.e(TAG, "getTipsByDifficulty failed", e)
            Result.failure(e)
        }
    }

    suspend fun getAchievements(): Result<List<Achievement>> {
        return try {
            val query = db.collection(Constants.COLLECTION_ACHIEVEMENTS)
                .get()
                .await()
            Result.success(query.toObjects(Achievement::class.java).sortedByDescending { it.points })
        } catch (e: Exception) {
            Log.e(TAG, "getAchievements failed", e)
            Result.failure(e)
        }
    }

    suspend fun getUserAchievements(userId: String): Result<List<UserAchievement>> {
        return try {
            val query = db.collection(Constants.COLLECTION_USER_ACHIEVEMENTS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val userAchievements = query.toObjects(UserAchievement::class.java)
                .sortedByDescending { it.unlockedAt?.time ?: 0L }
            Result.success(userAchievements)
        } catch (e: Exception) {
            Log.e(TAG, "getUserAchievements failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveUserAchievement(userAchievement: UserAchievement): Result<String> {
        return try {
            if (!ValidationUtils.isValidProgress(userAchievement.progress.toDouble())) {
                return Result.failure(Exception("Invalid progress"))
            }
            val currentUserId = resolveUserId(userAchievement.userId)
                ?: return Result.failure(Exception("User not authenticated"))
            val id = if (userAchievement.achievementId.isNotBlank()) {
                "${currentUserId}_${userAchievement.achievementId}"
            } else {
                userAchievement.id.ifBlank { EncryptionUtils.generateSecureId() }
            }
            val achievementWithId = userAchievement.copy(id = id, userId = currentUserId)
            db.collection(Constants.COLLECTION_USER_ACHIEVEMENTS).document(id).set(achievementWithId).await()
            Result.success(id)
        } catch (e: Exception) {
            Log.e(TAG, "saveUserAchievement failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveGoal(goal: Goal): Result<String> {
        return saveGoalItem(goal).map { it.id }
    }

    suspend fun saveGoalItem(goal: Goal): Result<Goal> {
        return try {
            if (goal.title.isBlank() ||
                !ValidationUtils.isValidTargetValue(goal.targetValue) ||
                !ValidationUtils.isValidCurrentValue(goal.currentValue) ||
                !ValidationUtils.isValidUnit(goal.unit) ||
                !ValidationUtils.isValidCategory(goal.category) ||
                !ValidationUtils.isValidDeadline(goal.deadline)
            ) {
                return Result.failure(Exception("Invalid goal data"))
            }
            val currentUserId = resolveUserId(goal.userId)
                ?: return Result.failure(Exception("User not authenticated"))
            val id = goal.id.ifBlank { EncryptionUtils.generateSecureId() }
            val goalWithId = goal.copy(
                id = id,
                userId = currentUserId,
                isCompleted = SustainabilityUtils.isGoalCompleted(goal.currentValue, goal.targetValue),
                createdAt = goal.createdAt ?: Date()
            )
            db.collection(Constants.COLLECTION_GOALS).document(id).set(goalWithId).await()
            Result.success(goalWithId)
        } catch (e: Exception) {
            Log.e(TAG, "saveGoalItem failed", e)
            Result.failure(e)
        }
    }

    suspend fun getUserGoals(userId: String): Result<List<Goal>> {
        return try {
            val query = db.collection(Constants.COLLECTION_GOALS)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val goals = query.toObjects(Goal::class.java)
                .sortedByDescending { it.createdAt?.time ?: 0L }
            Result.success(goals)
        } catch (e: Exception) {
            Log.e(TAG, "getUserGoals failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateGoal(goal: Goal): Result<Unit> {
        return try {
            if (goal.id.isBlank() ||
                !ValidationUtils.isValidTargetValue(goal.targetValue) ||
                !ValidationUtils.isValidCurrentValue(goal.currentValue)
            ) {
                return Result.failure(Exception("Invalid goal data"))
            }
            val currentUserId = resolveUserId(goal.userId)
                ?: return Result.failure(Exception("User not authenticated"))
            val updatedGoal = goal.copy(
                userId = currentUserId,
                isCompleted = SustainabilityUtils.isGoalCompleted(goal.currentValue, goal.targetValue)
            )
            db.collection(Constants.COLLECTION_GOALS).document(goal.id).set(updatedGoal).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateGoal failed", e)
            Result.failure(e)
        }
    }

    suspend fun deleteGoal(goalId: String): Result<Unit> {
        return try {
            if (goalId.isBlank()) return Result.failure(Exception("Goal ID is required"))
            db.collection(Constants.COLLECTION_GOALS).document(goalId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteGoal failed", e)
            Result.failure(e)
        }
    }

    suspend fun getActiveChallenges(): Result<List<Challenge>> {
        return try {
            val query = db.collection(Constants.COLLECTION_CHALLENGES)
                .whereEqualTo("isActive", true)
                .get()
                .await()
            val challenges = query.toObjects(Challenge::class.java)
                .filter { SustainabilityUtils.isChallengeActive(it.startDate, it.endDate) }
                .sortedByDescending { it.startDate }
            Result.success(challenges)
        } catch (e: Exception) {
            Log.e(TAG, "getActiveChallenges failed", e)
            Result.failure(e)
        }
    }

    suspend fun getUserChallenges(userId: String): Result<List<UserChallenge>> {
        return try {
            val query = db.collection(Constants.COLLECTION_USER_CHALLENGES)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val userChallenges = query.toObjects(UserChallenge::class.java)
                .sortedByDescending { it.joinedAt }
            Result.success(userChallenges)
        } catch (e: Exception) {
            Log.e(TAG, "getUserChallenges failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveUserChallenge(userChallenge: UserChallenge): Result<String> {
        return try {
            if (!ValidationUtils.isValidProgress(userChallenge.progress)) {
                return Result.failure(Exception("Invalid progress"))
            }
            val currentUserId = resolveUserId(userChallenge.userId)
                ?: return Result.failure(Exception("User not authenticated"))
            val id = "${currentUserId}_${userChallenge.challengeId}"
            val docRef = db.collection(Constants.COLLECTION_USER_CHALLENGES).document(id)
            val alreadyJoined = docRef.get().await().exists()
            val challengeWithId = userChallenge.copy(id = id, userId = currentUserId)
            docRef.set(challengeWithId).await()
            if (!alreadyJoined && userChallenge.challengeId.isNotBlank()) {
                updateChallengeParticipantCount(userChallenge.challengeId, 1)
            }
            Result.success(id)
        } catch (e: Exception) {
            Log.e(TAG, "saveUserChallenge failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserChallenge(userChallenge: UserChallenge): Result<Unit> {
        return try {
            if (!ValidationUtils.isValidProgress(userChallenge.progress)) {
                return Result.failure(Exception("Invalid progress"))
            }
            val currentUserId = resolveUserId(userChallenge.userId)
                ?: return Result.failure(Exception("User not authenticated"))
            val updatedChallenge = userChallenge.copy(userId = currentUserId)
            db.collection(Constants.COLLECTION_USER_CHALLENGES).document(userChallenge.id).set(updatedChallenge).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserChallenge failed", e)
            Result.failure(e)
        }
    }

    suspend fun addToWishlist(wishlist: Wishlist): Result<String> {
        return addToWishlistItem(wishlist).map { it.id }
    }

    suspend fun addToWishlistItem(wishlist: Wishlist): Result<Wishlist> {
        return try {
            val currentUserId = resolveUserId(wishlist.userId)
                ?: return Result.failure(Exception("User not authenticated"))
            if (wishlist.productId.isBlank()) {
                return Result.failure(Exception("Product ID is required"))
            }
            val id = "${currentUserId}_${wishlist.productId}"
            val wishlistWithId = wishlist.copy(
                id = id,
                userId = currentUserId,
                addedAt = wishlist.addedAt ?: Date()
            )
            db.collection(Constants.COLLECTION_WISHLIST).document(id).set(wishlistWithId).await()
            Result.success(wishlistWithId)
        } catch (e: Exception) {
            Log.e(TAG, "addToWishlistItem failed", e)
            Result.failure(e)
        }
    }

    suspend fun getUserWishlist(userId: String): Result<List<Wishlist>> {
        return try {
            val query = db.collection(Constants.COLLECTION_WISHLIST)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            val wishlist = query.toObjects(Wishlist::class.java)
                .sortedByDescending { it.addedAt?.time ?: 0L }
            Result.success(wishlist)
        } catch (e: Exception) {
            Log.e(TAG, "getUserWishlist failed", e)
            Result.failure(e)
        }
    }

    suspend fun removeFromWishlist(wishlistId: String): Result<Unit> {
        return try {
            if (wishlistId.isBlank()) return Result.failure(Exception("Wishlist ID is required"))
            db.collection(Constants.COLLECTION_WISHLIST).document(wishlistId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "removeFromWishlist failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveUserPreferences(preferences: UserPreferences): Result<String> {
        return try {
            if (!ValidationUtils.isValidLanguage(preferences.language)) {
                return Result.failure(Exception("Invalid language"))
            }
            val currentUserId = resolveUserId(preferences.userId)
                ?: return Result.failure(Exception("User not authenticated"))
            val preferencesWithId = preferences.copy(
                id = currentUserId,
                userId = currentUserId,
                language = preferences.language.ifBlank { Constants.DEFAULT_LANGUAGE },
                updatedAt = Date()
            )
            db.collection(Constants.COLLECTION_USER_PREFERENCES).document(currentUserId).set(preferencesWithId).await()
            Result.success(currentUserId)
        } catch (e: Exception) {
            Log.e(TAG, "saveUserPreferences failed", e)
            Result.failure(e)
        }
    }

    suspend fun getUserPreferences(userId: String): Result<UserPreferences?> {
        return try {
            val doc = db.collection(Constants.COLLECTION_USER_PREFERENCES).document(userId).get().await()
            if (doc.exists()) {
                return Result.success(doc.toObject(UserPreferences::class.java))
            }
            val query = db.collection(Constants.COLLECTION_USER_PREFERENCES)
                .whereEqualTo("userId", userId)
                .limit(1)
                .get()
                .await()
            Result.success(query.documents.firstOrNull()?.toObject(UserPreferences::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "getUserPreferences failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserPreferences(preferences: UserPreferences): Result<Unit> {
        return saveUserPreferences(preferences).map { }
    }

    suspend fun updateUserSustainabilityScore(userId: String, carbonFootprint: CarbonFootprint): Result<Unit> {
        return try {
            val score = SustainabilityUtils.calculateSustainabilityScore(carbonFootprint)
            db.collection(Constants.COLLECTION_USERS).document(userId)
                .update("sustainabilityScore", score)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserSustainabilityScore failed", e)
            Result.failure(e)
        }
    }

    suspend fun incrementUserPoints(userId: String, points: Int): Result<Unit> {
        return incrementUserPointsAndReturn(userId, points).map { }
    }

    suspend fun incrementUserPointsAndReturn(userId: String, points: Int): Result<User> {
        return try {
            if (!ValidationUtils.isValidPoints(points)) {
                return Result.failure(Exception("Invalid points"))
            }
            val user = getUser(userId).getOrNull()
                ?: return Result.failure(Exception("User not found"))
            val newTotalPoints = user.totalPoints + points
            val newLevel = SustainabilityUtils.calculateLevel(newTotalPoints)
            db.collection(Constants.COLLECTION_USERS).document(userId).update(
                mapOf(
                    "totalPoints" to newTotalPoints,
                    "level" to newLevel
                )
            ).await()
            Result.success(user.copy(totalPoints = newTotalPoints, level = newLevel))
        } catch (e: Exception) {
            Log.e(TAG, "incrementUserPointsAndReturn failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserStreak(userId: String, streakDays: Int, lastActiveDate: String = DateUtils.getCurrentDate()): Result<Unit> {
        return try {
            if (!ValidationUtils.isValidStreakDays(streakDays)) {
                return Result.failure(Exception("Invalid streak days"))
            }
            db.collection(Constants.COLLECTION_USERS).document(userId)
                .update(
                    mapOf(
                        "streakDays" to streakDays,
                        "lastActiveDate" to lastActiveDate
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "updateUserStreak failed", e)
            Result.failure(e)
        }
    }

    private suspend fun updateProductRating(productId: String) {
        try {
            val reviewsResult = getProductReviews(productId)
            val reviews = reviewsResult.getOrNull().orEmpty()
            if (reviews.isNotEmpty()) {
                val averageRating = reviews.map { it.rating }.average().toFloat()
                val reviewCount = reviews.size
                db.collection(Constants.COLLECTION_ECO_PRODUCTS).document(productId).update(
                    mapOf(
                        "averageRating" to averageRating,
                        "reviewCount" to reviewCount
                    )
                ).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateProductRating failed for $productId", e)
        }
    }

    private suspend fun updateChallengeParticipantCount(challengeId: String, increment: Int) {
        try {
            db.collection(Constants.COLLECTION_CHALLENGES).document(challengeId)
                .update("participantCount", FieldValue.increment(increment.toLong()))
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "updateChallengeParticipantCount failed for $challengeId", e)
        }
    }
}
