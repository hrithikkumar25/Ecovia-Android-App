package com.greengauge
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class User(
    @DocumentId
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val profileImageUrl: String = "",
    val sustainabilityScore: Int = 0,
    val totalPoints: Int = 0,
    val level: Int = 1,
    val streakDays: Int = 0,
    val lastActiveDate: String = "",
    val joinDate: Long = System.currentTimeMillis(),
    val preferences: Map<String, Boolean> = mapOf(),
    val isActive: Boolean = true
)

data class CarbonFootprint(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val date: String = "",
    val transportationEmissions: Double = 0.0,
    val energyEmissions: Double = 0.0,
    val dietEmissions: Double = 0.0,
    val wasteEmissions: Double = 0.0,
    val waterEmissions: Double = 0.0,
    val shoppingEmissions: Double = 0.0,
    val totalEmissions: Double = 0.0,
    @ServerTimestamp
    val timestamp: Date? = null
)

data class TrackingData(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val category: String = "",
    val activityType: String = "",
    val value: Double = 0.0,
    val unit: String = "",
    val emissions: Double = 0.0,
    val date: String = "",
    @ServerTimestamp
    val timestamp: Date? = null
)

data class EcoProduct(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val sustainabilityRating: Float = 0f,
    val price: Double = 0.0,
    val description: String = "",
    val imageUrl: String = "",
    val brand: String = "",
    val isRecommended: Boolean = false,
    val tags: List<String> = listOf(),
    val averageRating: Float = 0f,
    val reviewCount: Int = 0,
    val isWishlisted: Boolean = false,
    val barcode: String? = null,
    @ServerTimestamp
    val createdAt: Date? = null,
    val carbonFootprint: Double? = null,
    val isRecyclable: Boolean? = null,
    val isBiodegradable: Boolean? = null
)

data class ProductReview(
    @DocumentId
    val id: String = "",
    val productId: String = "",
    val userId: String = "",
    val userName: String = "",
    val rating: Float = 0f,
    val comment: String = "",
    @ServerTimestamp
    val timestamp: Date? = null
)

data class Tip(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "",
    val difficulty: String = "",
    val impactLevel: String = "",
    val imageUrl: String = "",
    val videoUrl: String = "",
    val steps: List<String> = listOf(),
    val tags: List<String> = listOf(),
    val likes: Int = 0,
    val isBookmarked: Boolean = false,
    @ServerTimestamp
    val createdAt: Date? = null
)

data class Achievement(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val iconUrl: String = "",
    val category: String = "",
    val requirement: Int = 0,
    val points: Int = 0,
    val isUnlocked: Boolean = false
)

data class UserAchievement(
    @DocumentId val id: String = "",
    val userId: String = "",
    val achievementId: String = "",
    val progress: Int = 0,
    val isCompleted: Boolean = false,
    @ServerTimestamp val unlockedAt: Date? = null
)

data class Goal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val targetValue: Double = 0.0,
    val currentValue: Double = 0.0,
    val unit: String = "",
    val category: String = "",
    val deadline: Long = 0L,
    val isCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null
)

data class Challenge(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val targetValue: Double = 0.0,
    val unit: String = "",
    val duration: Int = 0,
    val points: Int = 0,
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val isActive: Boolean = true,
    val participantCount: Int = 0
)
data class UserChallenge(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val challengeId: String = "",
    val progress: Double = 0.0,
    val isCompleted: Boolean = false,
    val joinedAt: Long = System.currentTimeMillis(),
    @ServerTimestamp
    val completedAt: Date? = null
)
data class Wishlist(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val productId: String = "",
    @ServerTimestamp
    val addedAt: Date? = null
)
data class UserPreferences(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val notificationsEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false,
    val trackingEnabled: Boolean = true,
    val shareDataEnabled: Boolean = false,
    val preferredCategories: List<String> = listOf(),
    val language: String = "en",
    @ServerTimestamp
    val updatedAt: Date? = null
)

data class UserTipState(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val tipId: String = "",
    val isBookmarked: Boolean = false,
    val isCompleted: Boolean = false,
    val notes: String = "",
    @ServerTimestamp
    val updatedAt: Date? = null
)
