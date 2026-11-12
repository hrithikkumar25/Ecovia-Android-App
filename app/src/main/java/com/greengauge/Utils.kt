package com.greengauge

import android.content.Context
import android.content.pm.PackageManager
import android.util.Patterns
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.roundToInt

object Constants {
    const val COLLECTION_USERS = "users"
    const val COLLECTION_CARBON_FOOTPRINTS = "carbon_footprints"
    const val COLLECTION_TRACKING_DATA = "tracking_data"
    const val COLLECTION_ECO_PRODUCTS = "eco_products"
    const val COLLECTION_PRODUCT_REVIEWS = "product_reviews"
    const val COLLECTION_TIPS = "tips"
    const val COLLECTION_ACHIEVEMENTS = "achievements"
    const val COLLECTION_USER_ACHIEVEMENTS = "user_achievements"
    const val COLLECTION_GOALS = "goals"
    const val COLLECTION_CHALLENGES = "challenges"
    const val COLLECTION_USER_CHALLENGES = "user_challenges"
    const val COLLECTION_WISHLIST = "wishlist"
    const val COLLECTION_USER_PREFERENCES = "user_preferences"
    const val COLLECTION_USER_TIP_STATES = "user_tip_states"

    const val TRANSPORT_CAR = "car"
    const val TRANSPORT_BUS = "bus"
    const val TRANSPORT_TRAIN = "train"
    const val TRANSPORT_BIKE = "bike"
    const val TRANSPORT_WALK = "walk"
    const val TRANSPORT_FLIGHT = "flight"

    const val CATEGORY_TRANSPORTATION = "transportation"
    const val CATEGORY_ENERGY = "energy"
    const val CATEGORY_DIET = "diet"
    const val CATEGORY_WASTE = "waste"
    const val CATEGORY_WATER = "water"
    const val CATEGORY_SHOPPING = "shopping"

    const val DIFFICULTY_BEGINNER = "beginner"
    const val DIFFICULTY_INTERMEDIATE = "intermediate"
    const val DIFFICULTY_ADVANCED = "advanced"

    const val IMPACT_LOW = "low"
    const val IMPACT_MEDIUM = "medium"
    const val IMPACT_HIGH = "high"

    const val DEFAULT_LANGUAGE = "en"
    const val MIN_PASSWORD_LENGTH = 8
    const val MIN_NAME_LENGTH = 2
    const val MAX_RATING = 5.0f
    const val MIN_RATING = 0.0f
    const val POINTS_PER_LEVEL = 1000
}

object ValidationUtils {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val URL_REGEX = Regex("^https?://.+")

    fun isValidEmail(email: String): Boolean {
        if (email.isBlank()) return false
        val pattern = Patterns.EMAIL_ADDRESS
        return pattern?.matcher(email)?.matches() ?: EMAIL_REGEX.matches(email)
    }

    fun isValidPassword(password: String): Boolean = password.length >= Constants.MIN_PASSWORD_LENGTH
    fun isValidName(name: String): Boolean = name.isNotBlank() && name.trim().length >= Constants.MIN_NAME_LENGTH
    fun isValidRating(rating: Float): Boolean = rating in Constants.MIN_RATING..Constants.MAX_RATING
    fun isValidScore(score: Int): Boolean = score >= 0
    fun isValidLevel(level: Int): Boolean = level >= 1
    fun isValidEmission(emission: Double): Boolean = emission >= 0.0
    fun isValidPrice(price: Double): Boolean = price >= 0.0
    fun isValidProgress(progress: Double): Boolean = progress >= 0.0
    fun isValidCategory(category: String): Boolean = category.isNotBlank()
    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return true
        val pattern = Patterns.WEB_URL
        return pattern?.matcher(url)?.matches() ?: URL_REGEX.matches(url)
    }
    fun isValidPoints(points: Int): Boolean = points >= 0
    fun isValidStreakDays(days: Int): Boolean = days >= 0
    fun isValidRequirement(requirement: Int): Boolean = requirement > 0
    fun isValidDuration(duration: Int): Boolean = duration > 0
    fun isValidTargetValue(value: Double): Boolean = value > 0.0
    fun isValidCurrentValue(value: Double): Boolean = value >= 0.0
    fun isValidDeadline(deadline: Long): Boolean = deadline > Instant.now().toEpochMilli()
    fun isValidDateRange(startDate: Long, endDate: Long): Boolean = startDate <= endDate
    fun isValidUnit(unit: String): Boolean = unit.isNotBlank()
    fun isValidActivityType(activityType: String): Boolean = activityType.isNotBlank()
    fun isValidDifficulty(difficulty: String): Boolean = difficulty in listOf(Constants.DIFFICULTY_BEGINNER, Constants.DIFFICULTY_INTERMEDIATE, Constants.DIFFICULTY_ADVANCED)
    fun isValidImpactLevel(impactLevel: String): Boolean = impactLevel in listOf(Constants.IMPACT_LOW, Constants.IMPACT_MEDIUM, Constants.IMPACT_HIGH)
    fun isValidLanguage(language: String): Boolean = language.matches(Regex("^[a-z]{2}$"))
}

object EncryptionUtils {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SALT_SIZE_BYTES = 16
    private const val ITERATION_COUNT = 310000
    private const val KEY_LENGTH_BITS = 256

    fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE_BYTES)
        random.nextBytes(salt)
        return salt
    }

    fun hashPassword(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    fun verifyPassword(passwordAttempt: CharArray, storedSalt: ByteArray, storedHash: ByteArray): Boolean {
        val attemptHash = hashPassword(passwordAttempt, storedSalt)
        return MessageDigest.isEqual(attemptHash, storedHash)
    }

    fun generateSecureId(): String = UUID.randomUUID().toString()
}

object DateUtils {
    private const val DATE_PATTERN = "yyyy-MM-dd"
    private const val TIME_PATTERN = "HH:mm"
    private const val FULL_PATTERN = "yyyy-MM-dd HH:mm:ss"

    private fun getFormatter(pattern: String): DateTimeFormatter {
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    }

    fun getCurrentDate(): String {
        return LocalDate.now().format(getFormatter(DATE_PATTERN))
    }

    fun getCurrentTime(): String {
        return LocalTime.now().format(getFormatter(TIME_PATTERN))
    }

    fun formatDate(timestamp: Long): String {
        val localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        return localDateTime.format(getFormatter(DATE_PATTERN))
    }

    fun formatFullDate(timestamp: Long): String {
        val localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        return localDateTime.format(getFormatter(FULL_PATTERN))
    }

    fun parseDate(dateString: String): LocalDate? {
        return try {
            LocalDate.parse(dateString, getFormatter(DATE_PATTERN))
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun getTimestamp(): Long = Instant.now().toEpochMilli()

    fun getDaysDifference(startTimestamp: Long, endTimestamp: Long): Long {
        val startDate = Instant.ofEpochMilli(startTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val endDate = Instant.ofEpochMilli(endTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        return ChronoUnit.DAYS.between(startDate, endDate)
    }

    fun isToday(timestamp: Long): Boolean {
        val dateFromTimestamp = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        return dateFromTimestamp.isEqual(LocalDate.now(ZoneId.systemDefault()))
    }

    fun getWeekStart(): Long {
        val today = LocalDate.now(ZoneId.systemDefault())
        val monday = today.with(DayOfWeek.MONDAY)
        return monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun getMonthStart(): Long {
        val today = LocalDate.now(ZoneId.systemDefault())
        return today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun getStartOfWeek(): String {
        val today = LocalDate.now(ZoneId.systemDefault())
        return today.with(DayOfWeek.MONDAY).format(getFormatter(DATE_PATTERN))
    }

    fun getStartOfMonth(): String {
        val today = LocalDate.now(ZoneId.systemDefault())
        return today.withDayOfMonth(1).format(getFormatter(DATE_PATTERN))
    }

    fun getDaysBetween(startDate: String, endDate: String): Long {
        val start = parseDate(startDate) ?: return 0L
        val end = parseDate(endDate) ?: return 0L
        return ChronoUnit.DAYS.between(start, end)
    }
}

object SustainabilityUtils {
    fun calculateSustainabilityScore(carbonFootprint: CarbonFootprint): Int {
        val totalEmissions = CarbonCalculator.calculateTotalEmissions(carbonFootprint)
        return when {
            totalEmissions <= 5.0 -> 100
            totalEmissions <= 10.0 -> 80
            totalEmissions <= 15.0 -> 60
            totalEmissions <= 20.0 -> 40
            totalEmissions <= 25.0 -> 20
            else -> 0
        }
    }

    fun calculateSustainabilityScore(
        totalEmissions: Double,
        streakDays: Int,
        completedGoals: Int,
        totalPoints: Int
    ): Int {
        val baseScore = when {
            totalEmissions <= 5.0 -> 90
            totalEmissions <= 15.0 -> 75
            totalEmissions <= 30.0 -> 60
            totalEmissions <= 60.0 -> 45
            else -> 30
        }
        val bonus = (streakDays.coerceAtMost(10)) +
            (completedGoals * 5).coerceAtMost(15) +
            (totalPoints / 100).coerceAtMost(10)
        return (baseScore + bonus).coerceIn(0, 100)
    }

    fun generateDailyTip(tips: List<Tip>): Tip? {
        if (tips.isEmpty()) return null
        val dayIndex = LocalDate.now(ZoneId.systemDefault()).dayOfYear % tips.size
        return tips[dayIndex]
    }

    fun getEcoTipForWeather(weather: String): String {
        return when (weather.lowercase(Locale.ROOT)) {
            "rainy" -> "Rainy day tip: Unplug idle electronics and plan indoor batch cooking to save energy."
            "cloudy" -> "Mild weather tip: Walk or take transit for errands under 3 km."
            else -> "Sunny day tip: Air-dry laundry and cycle or walk instead of driving short trips."
        }
    }

    fun calculateLevel(totalPoints: Int): Int = (totalPoints / Constants.POINTS_PER_LEVEL) + 1

    fun calculateProgressPercentage(currentValue: Double, targetValue: Double): Double {
        return if (targetValue > 0) (currentValue / targetValue * 100.0).coerceAtMost(100.0) else 0.0
    }

    fun isGoalCompleted(currentValue: Double, targetValue: Double): Boolean = currentValue >= targetValue

    fun isChallengeActive(startDate: Long, endDate: Long): Boolean {
        val now = Instant.now().toEpochMilli()
        return now in startDate..endDate
    }
}

object CarbonCalculator {
    private const val CAR_EMISSION_FACTOR = 0.192
    private const val BUS_EMISSION_FACTOR = 0.089
    private const val TRAIN_EMISSION_FACTOR = 0.041
    private const val SUBWAY_EMISSION_FACTOR = 0.028
    private const val TAXI_EMISSION_FACTOR = 0.204
    private const val MOTORCYCLE_EMISSION_FACTOR = 0.084
    private const val FLIGHT_EMISSION_FACTOR = 0.255

    private const val ELECTRICITY_EMISSION_FACTOR = 0.5
    private const val GAS_EMISSION_FACTOR = 0.185
    private const val HEATING_EMISSION_FACTOR = 0.2
    private const val COOLING_EMISSION_FACTOR = 0.15
    private const val DEFAULT_ENERGY_FACTOR = 0.1

    private const val MEAT_EMISSION_FACTOR = 3.3
    private const val DAIRY_EMISSION_FACTOR = 1.9
    private const val VEGETARIAN_EMISSION_FACTOR = 0.9
    private const val VEGAN_EMISSION_FACTOR = 0.5
    private const val VEGETABLES_EMISSION_FACTOR = 0.4
    private const val FRUITS_EMISSION_FACTOR = 0.3
    private const val GRAINS_EMISSION_FACTOR = 0.7
    private const val PROCESSED_FOODS_EMISSION_FACTOR = 2.1

    private const val WASTE_EMISSION_FACTOR = 0.5
    private const val WATER_EMISSION_FACTOR = 0.0003

    /**
     * Unified emission calculation across all supported tracking categories and activity types.
     * Negative values are clamped to 0.0 for input safety; recycling/composting can yield
     * negative (saved) emissions when explicitly selected under waste.
     */
    fun calculateEmissions(category: String, activity: String, value: Double, unit: String = ""): Double {
        if (value <= 0.0) return 0.0
        return when (category.lowercase(Locale.ROOT)) {
            Constants.CATEGORY_TRANSPORTATION -> calculateTransportEmission(value, activity)
            Constants.CATEGORY_ENERGY -> calculateEnergyEmission(value, activity)
            Constants.CATEGORY_DIET -> calculateDietEmission(value, activity)
            Constants.CATEGORY_WASTE -> calculateWasteEmission(value, activity)
            Constants.CATEGORY_WATER -> calculateWaterEmission(value, activity)
            Constants.CATEGORY_SHOPPING -> calculateShoppingEmission(value, activity)
            else -> 0.0
        }
    }

    fun calculateTransportEmission(distance: Double, transportType: String): Double {
        if (distance <= 0.0) return 0.0
        return when (transportType.lowercase(Locale.ROOT)) {
            Constants.TRANSPORT_CAR -> distance * CAR_EMISSION_FACTOR
            Constants.TRANSPORT_BUS -> distance * BUS_EMISSION_FACTOR
            Constants.TRANSPORT_TRAIN -> distance * TRAIN_EMISSION_FACTOR
            "subway" -> distance * SUBWAY_EMISSION_FACTOR
            "taxi" -> distance * TAXI_EMISSION_FACTOR
            "motorcycle" -> distance * MOTORCYCLE_EMISSION_FACTOR
            Constants.TRANSPORT_FLIGHT -> distance * FLIGHT_EMISSION_FACTOR
            Constants.TRANSPORT_BIKE, Constants.TRANSPORT_WALK, "walking", "cycling" -> 0.0
            else -> 0.0
        }
    }

    fun calculateEnergyEmission(usage: Double, energyType: String): Double {
        if (usage <= 0.0) return 0.0
        return when (energyType.lowercase(Locale.ROOT)) {
            Constants.CATEGORY_ENERGY.lowercase(Locale.ROOT), "electricity" -> usage * ELECTRICITY_EMISSION_FACTOR
            "gas" -> usage * GAS_EMISSION_FACTOR
            "heating" -> usage * HEATING_EMISSION_FACTOR
            "cooling" -> usage * COOLING_EMISSION_FACTOR
            "appliances", "lighting" -> usage * DEFAULT_ENERGY_FACTOR
            else -> usage * DEFAULT_ENERGY_FACTOR
        }
    }

    fun calculateDietEmission(meals: Double, dietType: String): Double {
        if (meals <= 0.0) return 0.0
        return when (dietType.lowercase(Locale.ROOT)) {
            "meat" -> meals * MEAT_EMISSION_FACTOR
            "dairy" -> meals * DAIRY_EMISSION_FACTOR
            "vegetarian" -> meals * VEGETARIAN_EMISSION_FACTOR
            "vegan" -> meals * VEGAN_EMISSION_FACTOR
            "vegetables" -> meals * VEGETABLES_EMISSION_FACTOR
            "fruits" -> meals * FRUITS_EMISSION_FACTOR
            "grains" -> meals * GRAINS_EMISSION_FACTOR
            "processed foods" -> meals * PROCESSED_FOODS_EMISSION_FACTOR
            else -> meals * MEAT_EMISSION_FACTOR
        }
    }

    fun calculateWasteEmission(weight: Double, wasteType: String = "landfill"): Double {
        if (weight <= 0.0) return 0.0
        val factor = when (wasteType.lowercase(Locale.ROOT)) {
            "recycling" -> -0.3
            "composting" -> -0.1
            "landfill" -> WASTE_EMISSION_FACTOR
            "plastic" -> 1.8
            "paper" -> 0.9
            "electronics" -> 4.5
            else -> WASTE_EMISSION_FACTOR
        }
        return weight * factor
    }

    fun calculateWaterEmission(liters: Double, waterType: String = "drinking"): Double {
        if (liters <= 0.0) return 0.0
        val factor = when (waterType.lowercase(Locale.ROOT)) {
            "drinking" -> WATER_EMISSION_FACTOR
            "shower" -> 0.0004
            "laundry" -> 0.0005
            "dishes" -> 0.0004
            "garden" -> 0.0002
            "pool" -> 0.0006
            else -> WATER_EMISSION_FACTOR
        }
        return liters * factor
    }

    fun calculateShoppingEmission(items: Double, itemType: String = "groceries"): Double {
        if (items <= 0.0) return 0.0
        val factor = when (itemType.lowercase(Locale.ROOT)) {
            "groceries" -> 0.5
            "clothes" -> 8.0
            "electronics" -> 25.0
            "furniture" -> 15.0
            "books" -> 1.2
            "cosmetics" -> 2.5
            else -> 1.0
        }
        return items * factor
    }

    fun calculateTotalEmissions(carbonFootprint: CarbonFootprint): Double {
        return carbonFootprint.transportationEmissions +
                carbonFootprint.energyEmissions +
                carbonFootprint.dietEmissions +
                carbonFootprint.wasteEmissions +
                carbonFootprint.waterEmissions +
                carbonFootprint.shoppingEmissions
    }
}

object NumberFormatter {
    private fun getDecimalFormat(pattern: String): DecimalFormat {
        return DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.getDefault()))
    }

    private fun getIntegerFormat(): NumberFormat {
        return NumberFormat.getIntegerInstance(Locale.getDefault())
    }

    private fun getPercentFormat(): NumberFormat {
        return NumberFormat.getPercentInstance(Locale.getDefault())
    }

    private fun getCurrencyFormat(): NumberFormat {
        return NumberFormat.getCurrencyInstance(Locale.getDefault())
    }

    fun formatDecimal(value: Double, pattern: String = "#.##"): String = getDecimalFormat(pattern).format(value)

    fun formatNumber(value: Double, decimalPlaces: Int = 1): String {
        val pattern = if (decimalPlaces <= 0) "#" else "#." + "#".repeat(decimalPlaces)
        return getDecimalFormat(pattern).format(value)
    }

    fun formatInteger(value: Number): String = getIntegerFormat().format(value)

    fun formatPercent(value: Double): String {
        val formatter = getPercentFormat()
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 1
        return formatter.format(value)
    }

    fun formatEmission(emission: Double): String = "${formatDecimal(emission)} kg CO₂"

    fun formatPrice(price: Double): String = getCurrencyFormat().format(price)

    fun formatRating(rating: Float): String = formatDecimal(rating.toDouble(), "#.#")

    fun roundToInt(value: Double): Int = value.roundToInt()
}

object PermissionUtils {
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    fun hasStoragePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

fun String.isValidEmail(): Boolean = ValidationUtils.isValidEmail(this)

fun String.capitalizeWords(): String = this.split(" ").joinToString(" ") { word ->
    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

fun Double.formatEmission(): String = NumberFormatter.formatEmission(this)

fun Double.formatPrice(): String = NumberFormatter.formatPrice(this)

fun Float.formatRating(): String = NumberFormatter.formatRating(this)

fun Int.formatInteger(): String = NumberFormatter.formatInteger(this)

fun Long.formatInteger(): String = NumberFormatter.formatInteger(this)

fun Long.formatDate(): String = DateUtils.formatDate(this)

fun Long.isToday(): Boolean = DateUtils.isToday(this)
