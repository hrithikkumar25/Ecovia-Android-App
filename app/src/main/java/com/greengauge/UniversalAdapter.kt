package com.greengauge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

sealed class UniversalItem {
    data class UserItem(val user: User) : UniversalItem()
    data class CarbonFootprintItem(val carbonFootprint: CarbonFootprint) : UniversalItem()
    data class TrackingDataItem(val trackingData: TrackingData) : UniversalItem()
    data class EcoProductItem(val ecoProduct: EcoProduct) : UniversalItem()
    data class ProductReviewItem(val productReview: ProductReview) : UniversalItem()
    data class TipItem(val tip: Tip) : UniversalItem()
    data class AchievementItem(val achievement: Achievement) : UniversalItem()
    data class UserAchievementItem(val userAchievement: UserAchievement) : UniversalItem()
    data class GoalItem(val goal: Goal) : UniversalItem()
    data class ChallengeItem(val challenge: Challenge) : UniversalItem()
    data class UserChallengeItem(val userChallenge: UserChallenge) : UniversalItem()
    data class WishlistItem(val wishlist: Wishlist) : UniversalItem()
}

interface UniversalAdapterListener {
    fun onItemClick(item: UniversalItem, position: Int)
    fun onBookmarkClick(item: UniversalItem, position: Int)
    fun onShareClick(item: UniversalItem, position: Int)
    fun onFavoriteClick(item: UniversalItem, position: Int)
    fun onPrimaryActionClick(item: UniversalItem, position: Int)
}

class UniversalAdapter(
    private val listener: UniversalAdapterListener
) : ListAdapter<UniversalItem, UniversalAdapter.UniversalViewHolder>(UniversalDiffCallback()) {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_CARBON_FOOTPRINT = 1
        private const val TYPE_TRACKING_DATA = 2
        private const val TYPE_ECO_PRODUCT = 3
        private const val TYPE_PRODUCT_REVIEW = 4
        private const val TYPE_TIP = 5
        private const val TYPE_ACHIEVEMENT = 6
        private const val TYPE_USER_ACHIEVEMENT = 7
        private const val TYPE_GOAL = 8
        private const val TYPE_CHALLENGE = 9
        private const val TYPE_USER_CHALLENGE = 10
        private const val TYPE_WISHLIST = 11
    }

    private var originalList = mutableListOf<UniversalItem>()
    private var filteredList = mutableListOf<UniversalItem>()
    private var wishlistedProductIds: Set<String> = emptySet()

    fun updateWishlistProductIds(ids: Set<String>) {
        wishlistedProductIds = ids
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is UniversalItem.UserItem -> TYPE_USER
            is UniversalItem.CarbonFootprintItem -> TYPE_CARBON_FOOTPRINT
            is UniversalItem.TrackingDataItem -> TYPE_TRACKING_DATA
            is UniversalItem.EcoProductItem -> TYPE_ECO_PRODUCT
            is UniversalItem.ProductReviewItem -> TYPE_PRODUCT_REVIEW
            is UniversalItem.TipItem -> TYPE_TIP
            is UniversalItem.AchievementItem -> TYPE_ACHIEVEMENT
            is UniversalItem.UserAchievementItem -> TYPE_USER_ACHIEVEMENT
            is UniversalItem.GoalItem -> TYPE_GOAL
            is UniversalItem.ChallengeItem -> TYPE_CHALLENGE
            is UniversalItem.UserChallengeItem -> TYPE_USER_CHALLENGE
            is UniversalItem.WishlistItem -> TYPE_WISHLIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UniversalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_universal, parent, false)
        return UniversalViewHolder(view)
    }

    override fun onBindViewHolder(holder: UniversalViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun submitList(list: MutableList<UniversalItem>?) {
        originalList = list?.toMutableList() ?: mutableListOf()
        filteredList = originalList.toMutableList()
        super.submitList(filteredList.toList())
    }

    fun submitItems(list: List<UniversalItem>) {
        originalList = list.toMutableList()
        filteredList = originalList.toMutableList()
        super.submitList(filteredList.toList())
    }

    fun filter(query: String) {
        filteredList = if (query.isBlank()) {
            originalList.toMutableList()
        } else {
            val q = query.trim()
            originalList.filter { item ->
                when (item) {
                    is UniversalItem.UserItem -> item.user.name.contains(q, true) || item.user.email.contains(q, true)
                    is UniversalItem.CarbonFootprintItem -> item.carbonFootprint.date.contains(q, true)
                    is UniversalItem.TrackingDataItem -> item.trackingData.activityType.contains(q, true) || item.trackingData.category.contains(q, true)
                    is UniversalItem.EcoProductItem -> item.ecoProduct.name.contains(q, true) || item.ecoProduct.description.contains(q, true) || item.ecoProduct.category.contains(q, true) || item.ecoProduct.brand.contains(q, true)
                    is UniversalItem.ProductReviewItem -> item.productReview.comment.contains(q, true)
                    is UniversalItem.TipItem -> item.tip.title.contains(q, true) || item.tip.content.contains(q, true) || item.tip.category.contains(q, true)
                    is UniversalItem.AchievementItem -> item.achievement.title.contains(q, true) || item.achievement.description.contains(q, true)
                    is UniversalItem.UserAchievementItem -> item.userAchievement.achievementId.contains(q, true)
                    is UniversalItem.GoalItem -> item.goal.title.contains(q, true) || item.goal.category.contains(q, true)
                    is UniversalItem.ChallengeItem -> item.challenge.title.contains(q, true) || item.challenge.description.contains(q, true)
                    is UniversalItem.UserChallengeItem -> item.userChallenge.challengeId.contains(q, true)
                    is UniversalItem.WishlistItem -> item.wishlist.productId.contains(q, true)
                }
            }.toMutableList()
        }
        super.submitList(filteredList.toList())
    }

    fun sortBy(sortType: String) {
        filteredList = when (sortType) {
            "name" -> filteredList.sortedBy { getItemTitle(it) }.toMutableList()
            "date" -> filteredList.sortedByDescending { getItemDate(it) }.toMutableList()
            "rating" -> filteredList.sortedByDescending { getItemRating(it) }.toMutableList()
            "points" -> filteredList.sortedByDescending { getItemPoints(it) }.toMutableList()
            else -> filteredList
        }
        super.submitList(filteredList.toList())
    }

    private fun getItemTitle(item: UniversalItem): String {
        return when (item) {
            is UniversalItem.UserItem -> item.user.name
            is UniversalItem.CarbonFootprintItem -> item.carbonFootprint.date
            is UniversalItem.TrackingDataItem -> item.trackingData.activityType
            is UniversalItem.EcoProductItem -> item.ecoProduct.name
            is UniversalItem.ProductReviewItem -> item.productReview.userName
            is UniversalItem.TipItem -> item.tip.title
            is UniversalItem.AchievementItem -> item.achievement.title
            is UniversalItem.UserAchievementItem -> item.userAchievement.achievementId
            is UniversalItem.GoalItem -> item.goal.title
            is UniversalItem.ChallengeItem -> item.challenge.title
            is UniversalItem.UserChallengeItem -> item.userChallenge.challengeId
            is UniversalItem.WishlistItem -> item.wishlist.productId
        }
    }

    private fun getItemDate(item: UniversalItem): Long {
        return when (item) {
            is UniversalItem.CarbonFootprintItem -> item.carbonFootprint.timestamp?.time ?: 0L
            is UniversalItem.TrackingDataItem -> item.trackingData.timestamp?.time ?: 0L
            is UniversalItem.ProductReviewItem -> item.productReview.timestamp?.time ?: 0L
            is UniversalItem.TipItem -> item.tip.createdAt?.time ?: 0L
            is UniversalItem.UserAchievementItem -> item.userAchievement.unlockedAt?.time ?: 0L
            is UniversalItem.GoalItem -> item.goal.createdAt?.time ?: 0L
            is UniversalItem.WishlistItem -> item.wishlist.addedAt?.time ?: 0L
            else -> 0L
        }
    }

    private fun getItemRating(item: UniversalItem): Float {
        return when (item) {
            is UniversalItem.EcoProductItem -> item.ecoProduct.sustainabilityRating
            is UniversalItem.ProductReviewItem -> item.productReview.rating
            else -> 0f
        }
    }

    private fun getItemPoints(item: UniversalItem): Int {
        return when (item) {
            is UniversalItem.UserItem -> item.user.totalPoints
            is UniversalItem.AchievementItem -> item.achievement.points
            is UniversalItem.ChallengeItem -> item.challenge.points
            is UniversalItem.TipItem -> item.tip.likes
            else -> 0
        }
    }

    inner class UniversalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivItemIcon: ImageView = itemView.findViewById(R.id.ivItemIcon)
        private val tvItemTitle: TextView = itemView.findViewById(R.id.tvItemTitle)
        private val tvItemSubtitle: TextView = itemView.findViewById(R.id.tvItemSubtitle)
        private val tvItemDescription: TextView = itemView.findViewById(R.id.tvItemDescription)
        private val ivBookmark: ImageView = itemView.findViewById(R.id.ivBookmark)
        private val llRatingContainer: LinearLayout = itemView.findViewById(R.id.llRatingContainer)
        private val rbItemRating: RatingBar = itemView.findViewById(R.id.rbItemRating)
        private val tvRatingText: TextView = itemView.findViewById(R.id.tvRatingText)
        private val llTagsContainer: LinearLayout = itemView.findViewById(R.id.llTagsContainer)
        private val tvTag1: TextView = itemView.findViewById(R.id.tvTag1)
        private val tvTag2: TextView = itemView.findViewById(R.id.tvTag2)
        private val tvTag3: TextView = itemView.findViewById(R.id.tvTag3)
        private val llMetricsContainer: LinearLayout = itemView.findViewById(R.id.llMetricsContainer)
        private val tvPrimaryMetricValue: TextView = itemView.findViewById(R.id.tvPrimaryMetricValue)
        private val tvPrimaryMetricLabel: TextView = itemView.findViewById(R.id.tvPrimaryMetricLabel)
        private val tvSecondaryMetricValue: TextView = itemView.findViewById(R.id.tvSecondaryMetricValue)
        private val tvSecondaryMetricLabel: TextView = itemView.findViewById(R.id.tvSecondaryMetricLabel)
        private val tvTertiaryMetricValue: TextView = itemView.findViewById(R.id.tvTertiaryMetricValue)
        private val tvTertiaryMetricLabel: TextView = itemView.findViewById(R.id.tvTertiaryMetricLabel)
        private val pbItemProgress: ProgressBar = itemView.findViewById(R.id.pbItemProgress)
        private val llActionButtons: LinearLayout = itemView.findViewById(R.id.llActionButtons)
        private val btnShare: ImageButton = itemView.findViewById(R.id.btnShare)
        private val btnFavorite: ImageButton = itemView.findViewById(R.id.btnFavorite)
        private val btnPrimaryAction: Button = itemView.findViewById(R.id.btnPrimaryAction)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val ivStatusIndicator: ImageView = itemView.findViewById(R.id.ivStatusIndicator)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvBadge)

        fun bind(item: UniversalItem, position: Int) {
            resetViews()
            when (item) {
                is UniversalItem.UserItem -> bindUser(item.user)
                is UniversalItem.CarbonFootprintItem -> bindCarbonFootprint(item.carbonFootprint)
                is UniversalItem.TrackingDataItem -> bindTrackingData(item.trackingData)
                is UniversalItem.EcoProductItem -> bindEcoProduct(item.ecoProduct)
                is UniversalItem.ProductReviewItem -> bindProductReview(item.productReview)
                is UniversalItem.TipItem -> bindTip(item.tip)
                is UniversalItem.AchievementItem -> bindAchievement(item.achievement)
                is UniversalItem.UserAchievementItem -> bindUserAchievement(item.userAchievement)
                is UniversalItem.GoalItem -> bindGoal(item.goal)
                is UniversalItem.ChallengeItem -> bindChallenge(item.challenge)
                is UniversalItem.UserChallengeItem -> bindUserChallenge(item.userChallenge)
                is UniversalItem.WishlistItem -> bindWishlist(item.wishlist)
            }
            setupClickListeners(item, position)
        }

        private fun resetViews() {
            val white = ContextCompat.getColor(itemView.context, android.R.color.white)
            val gray = ContextCompat.getColor(itemView.context, R.color.text_secondary)
            tvItemTitle.setTextColor(white)
            tvItemSubtitle.setTextColor(gray)
            tvItemDescription.setTextColor(white)
            tvPrimaryMetricValue.setTextColor(ContextCompat.getColor(itemView.context, R.color.accent_green_light))
            tvSecondaryMetricValue.setTextColor(white)
            tvTertiaryMetricValue.setTextColor(white)

            ivItemIcon.setImageResource(R.drawable.ic_eco_logo)
            ivBookmark.visibility = View.GONE
            ivBookmark.clearColorFilter()
            llRatingContainer.visibility = View.GONE
            llTagsContainer.visibility = View.GONE
            llMetricsContainer.visibility = View.GONE
            pbItemProgress.visibility = View.GONE
            llActionButtons.visibility = View.VISIBLE
            btnShare.visibility = View.VISIBLE
            btnFavorite.visibility = View.VISIBLE
            btnFavorite.setImageResource(android.R.drawable.btn_star_big_off)
            ivStatusIndicator.visibility = View.GONE
            tvBadge.visibility = View.GONE
            tvTag1.visibility = View.GONE
            tvTag2.visibility = View.GONE
            tvTag3.visibility = View.GONE
            tvTimestamp.text = ""
        }

        private fun bindUser(user: User) {
            tvItemTitle.text = user.name
            tvItemSubtitle.text = user.email
            tvItemDescription.text = "Level ${user.level} • ${user.totalPoints.formatInteger()} points"
            ivItemIcon.setImageResource(R.drawable.ic_profile)

            if (ValidationUtils.isValidUrl(user.profileImageUrl)) {
                Glide.with(itemView.context)
                    .load(user.profileImageUrl)
                    .placeholder(R.drawable.ic_profile)
                    .into(ivItemIcon)
            }

            llMetricsContainer.visibility = View.VISIBLE
            tvPrimaryMetricValue.text = user.sustainabilityScore.toString()
            tvPrimaryMetricLabel.text = "Score"
            tvSecondaryMetricValue.text = user.level.toString()
            tvSecondaryMetricLabel.text = "Level"
            tvTertiaryMetricValue.text = "${user.streakDays}d"
            tvTertiaryMetricLabel.text = "Streak"

            tvTimestamp.text = user.joinDate.formatDate()
            btnFavorite.visibility = View.GONE
            btnPrimaryAction.text = "View"
            if (user.isActive) {
                ivStatusIndicator.visibility = View.VISIBLE
                ivStatusIndicator.setImageResource(android.R.drawable.presence_online)
            }
        }

        private fun bindCarbonFootprint(carbonFootprint: CarbonFootprint) {
            tvItemTitle.text = "Carbon Footprint"
            tvItemSubtitle.text = carbonFootprint.date
            tvItemDescription.text = "Total emissions: ${carbonFootprint.totalEmissions.formatEmission()}"
            ivItemIcon.setImageResource(R.drawable.ic_tracking)

            llMetricsContainer.visibility = View.VISIBLE
            tvPrimaryMetricValue.text = carbonFootprint.totalEmissions.formatEmission()
            tvPrimaryMetricLabel.text = "Total"
            tvSecondaryMetricValue.text = carbonFootprint.transportationEmissions.formatEmission()
            tvSecondaryMetricLabel.text = "Transport"
            tvTertiaryMetricValue.text = carbonFootprint.energyEmissions.formatEmission()
            tvTertiaryMetricLabel.text = "Energy"

            tvTimestamp.text = carbonFootprint.timestamp?.time?.formatDate() ?: carbonFootprint.date
            btnFavorite.visibility = View.GONE
            btnPrimaryAction.text = "Details"
        }

        private fun bindTrackingData(trackingData: TrackingData) {
            tvItemTitle.text = trackingData.activityType.capitalizeWords()
            tvItemSubtitle.text = "${trackingData.category.capitalizeWords()} • ${trackingData.value} ${trackingData.unit}"
            tvItemDescription.text = "Date: ${trackingData.date}"
            ivItemIcon.setImageResource(R.drawable.ic_tracking)

            llMetricsContainer.visibility = View.VISIBLE
            tvPrimaryMetricValue.text = trackingData.emissions.formatEmission()
            tvPrimaryMetricLabel.text = "CO2e"
            tvSecondaryMetricValue.text = NumberFormatter.formatNumber(trackingData.value, 1)
            tvSecondaryMetricLabel.text = trackingData.unit
            tvTertiaryMetricValue.text = trackingData.category.capitalizeWords()
            tvTertiaryMetricLabel.text = "Category"

            tvTimestamp.text = trackingData.date
            btnFavorite.setImageResource(android.R.drawable.ic_menu_delete)
            btnFavorite.contentDescription = "Delete activity"
            btnPrimaryAction.text = "Edit"
        }

        private fun bindEcoProduct(ecoProduct: EcoProduct) {
            tvItemTitle.text = ecoProduct.name
            tvItemSubtitle.text = "${ecoProduct.category.capitalizeWords()} • ${ecoProduct.brand}"
            tvItemDescription.text = ecoProduct.description
            ivItemIcon.setImageResource(R.drawable.ic_products)

            if (ValidationUtils.isValidUrl(ecoProduct.imageUrl)) {
                Glide.with(itemView.context)
                    .load(ecoProduct.imageUrl)
                    .placeholder(R.drawable.ic_products)
                    .into(ivItemIcon)
            }

            ivBookmark.visibility = View.VISIBLE
            val isWishlisted = wishlistedProductIds.contains(ecoProduct.id)
            if (isWishlisted) {
                ivBookmark.setImageResource(android.R.drawable.btn_star_big_on)
                ivBookmark.contentDescription = "Remove from wishlist"
            } else {
                ivBookmark.setImageResource(android.R.drawable.btn_star_big_off)
                ivBookmark.contentDescription = "Add to wishlist"
            }

            llRatingContainer.visibility = View.VISIBLE
            rbItemRating.rating = ecoProduct.sustainabilityRating
            tvRatingText.text = "${ecoProduct.sustainabilityRating.formatRating()} (${ecoProduct.reviewCount})"

            if (ecoProduct.tags.isNotEmpty()) {
                llTagsContainer.visibility = View.VISIBLE
                setupTags(ecoProduct.tags)
            }

            llMetricsContainer.visibility = View.VISIBLE
            tvPrimaryMetricValue.text = ecoProduct.sustainabilityRating.formatRating()
            tvPrimaryMetricLabel.text = "Rating"
            tvSecondaryMetricValue.text = ecoProduct.price.formatPrice()
            tvSecondaryMetricLabel.text = "Price"
            tvTertiaryMetricValue.text = ecoProduct.reviewCount.toString()
            tvTertiaryMetricLabel.text = "Reviews"

            btnFavorite.setImageResource(android.R.drawable.ic_menu_sort_by_size)
            btnFavorite.contentDescription = "Compare product"
            btnPrimaryAction.text = "Details"

            if (ecoProduct.isRecommended) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "RECOMMENDED"
            }
        }

        private fun bindProductReview(productReview: ProductReview) {
            tvItemTitle.text = productReview.userName.ifBlank { "Eco Shopper" }
            tvItemSubtitle.text = "Product Review"
            tvItemDescription.text = productReview.comment

            llRatingContainer.visibility = View.VISIBLE
            rbItemRating.rating = productReview.rating
            tvRatingText.text = productReview.rating.formatRating()

            tvTimestamp.text = productReview.timestamp?.time?.formatDate().orEmpty()
            btnFavorite.visibility = View.GONE
            btnShare.visibility = View.GONE
            btnPrimaryAction.text = "View"
        }

        private fun bindTip(tip: Tip) {
            tvItemTitle.text = tip.title
            tvItemSubtitle.text = "${tip.category.capitalizeWords()} • ${tip.difficulty.capitalizeWords()}"
            tvItemDescription.text = tip.content
            ivItemIcon.setImageResource(R.drawable.ic_tips)

            if (ValidationUtils.isValidUrl(tip.imageUrl)) {
                Glide.with(itemView.context)
                    .load(tip.imageUrl)
                    .placeholder(R.drawable.ic_tips)
                    .into(ivItemIcon)
            }

            ivBookmark.visibility = View.VISIBLE
            if (tip.isBookmarked) {
                ivBookmark.setImageResource(android.R.drawable.btn_star_big_on)
                ivBookmark.contentDescription = "Remove bookmark"
            } else {
                ivBookmark.setImageResource(android.R.drawable.btn_star_big_off)
                ivBookmark.contentDescription = "Bookmark tip"
            }

            if (tip.tags.isNotEmpty()) {
                llTagsContainer.visibility = View.VISIBLE
                setupTags(tip.tags)
            }

            llMetricsContainer.visibility = View.VISIBLE
            tvPrimaryMetricValue.text = tip.likes.toString()
            tvPrimaryMetricLabel.text = "Likes"
            tvSecondaryMetricValue.text = "25 pts"
            tvSecondaryMetricLabel.text = "Reward"
            tvTertiaryMetricValue.text = tip.impactLevel.capitalizeWords()
            tvTertiaryMetricLabel.text = "Impact"

            tvTimestamp.text = tip.createdAt?.time?.formatDate().orEmpty()
            btnFavorite.setImageResource(android.R.drawable.ic_menu_send)
            btnFavorite.contentDescription = "Like tip"
            btnPrimaryAction.text = "Read Tip"
        }

        private fun bindAchievement(achievement: Achievement) {
            tvItemTitle.text = achievement.title
            tvItemSubtitle.text = achievement.category.capitalizeWords()
            tvItemDescription.text = achievement.description
            ivItemIcon.setImageResource(android.R.drawable.btn_star_big_on)

            if (ValidationUtils.isValidUrl(achievement.iconUrl)) {
                Glide.with(itemView.context)
                    .load(achievement.iconUrl)
                    .placeholder(android.R.drawable.btn_star_big_on)
                    .into(ivItemIcon)
            }

            llMetricsContainer.visibility = View.VISIBLE
            tvPrimaryMetricValue.text = achievement.points.toString()
            tvPrimaryMetricLabel.text = "Points"
            tvSecondaryMetricValue.text = achievement.requirement.toString()
            tvSecondaryMetricLabel.text = "Target"
            tvTertiaryMetricValue.text = if (achievement.isUnlocked) "Unlocked" else "Locked"
            tvTertiaryMetricLabel.text = "Status"

            btnFavorite.visibility = View.GONE
            btnShare.visibility = View.GONE
            btnPrimaryAction.text = "View"

            if (achievement.isUnlocked) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "UNLOCKED"
            }
        }

        private fun bindUserAchievement(userAchievement: UserAchievement) {
            tvItemTitle.text = userAchievement.achievementId.replace("_", " ").capitalizeWords()
            tvItemSubtitle.text = "Achievement Progress"
            tvItemDescription.text = if (userAchievement.isCompleted) "Unlocked!" else "Progress: ${userAchievement.progress}%"

            pbItemProgress.visibility = View.VISIBLE
            pbItemProgress.progress = userAchievement.progress

            llMetricsContainer.visibility = View.VISIBLE
            tvPrimaryMetricValue.text = "${userAchievement.progress}%"
            tvPrimaryMetricLabel.text = "Progress"
            tvSecondaryMetricValue.text = if (userAchievement.isCompleted) "Completed" else "In Progress"
            tvSecondaryMetricLabel.text = "Status"
            tvTertiaryMetricValue.text = ""
            tvTertiaryMetricLabel.text = ""

            tvTimestamp.text = userAchievement.unlockedAt?.time?.formatDate().orEmpty()
            btnFavorite.visibility = View.GONE
            btnPrimaryAction.text = "View"
        }

        private fun bindGoal(goal: Goal) {
            tvItemTitle.text = goal.title
            tvItemSubtitle.text = "${goal.category.capitalizeWords()} • ${goal.unit}"
            tvItemDescription.text = "Target: ${goal.targetValue} ${goal.unit}"

            val progressPercentage = SustainabilityUtils.calculateProgressPercentage(goal.currentValue, goal.targetValue)
            pbItemProgress.visibility = View.VISIBLE
            pbItemProgress.progress = NumberFormatter.roundToInt(progressPercentage)

            llMetricsContainer.visibility = View.VISIBLE
            tvPrimaryMetricValue.text = NumberFormatter.formatPercent(progressPercentage / 100)
            tvPrimaryMetricLabel.text = "Progress"
            tvSecondaryMetricValue.text = NumberFormatter.formatNumber(goal.currentValue, 1)
            tvSecondaryMetricLabel.text = "Current"
            tvTertiaryMetricValue.text = NumberFormatter.formatNumber(goal.targetValue, 1)
            tvTertiaryMetricLabel.text = "Target"

            tvTimestamp.text = if (goal.deadline > 0L) goal.deadline.formatDate() else ""
            btnFavorite.setImageResource(android.R.drawable.ic_menu_delete)
            btnFavorite.contentDescription = "Delete goal"
            btnPrimaryAction.text = if (goal.isCompleted) "Details" else "Update"

            if (goal.isCompleted) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "COMPLETED"
            }
        }

        private fun bindChallenge(challenge: Challenge) {
            tvItemTitle.text = challenge.title
            tvItemSubtitle.text = "${challenge.category.capitalizeWords()} • ${challenge.duration} days"
            tvItemDescription.text = challenge.description

            llMetricsContainer.visibility = View.VISIBLE
            tvPrimaryMetricValue.text = challenge.points.toString()
            tvPrimaryMetricLabel.text = "Points"
            tvSecondaryMetricValue.text = "${challenge.targetValue} ${challenge.unit}"
            tvSecondaryMetricLabel.text = "Target"
            tvTertiaryMetricValue.text = challenge.participantCount.toString()
            tvTertiaryMetricLabel.text = "Joined"

            tvTimestamp.text = "${challenge.startDate.formatDate()} → ${challenge.endDate.formatDate()}"
            btnFavorite.visibility = View.GONE
            btnPrimaryAction.text = "Join"

            if (challenge.isActive && SustainabilityUtils.isChallengeActive(challenge.startDate, challenge.endDate)) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "ACTIVE"
            }
        }

        private fun bindUserChallenge(userChallenge: UserChallenge) {
            tvItemTitle.text = userChallenge.challengeId.replace("_", " ").capitalizeWords()
            tvItemSubtitle.text = "Challenge Progress"
            val progressPct = userChallenge.progress.coerceIn(0.0, 100.0)
            tvItemDescription.text = "Progress: ${NumberFormatter.formatNumber(userChallenge.progress, 1)}"

            pbItemProgress.visibility = View.VISIBLE
            pbItemProgress.progress = NumberFormatter.roundToInt(progressPct)

            llMetricsContainer.visibility = View.VISIBLE
            tvPrimaryMetricValue.text = NumberFormatter.formatNumber(userChallenge.progress, 1)
            tvPrimaryMetricLabel.text = "Progress"
            tvSecondaryMetricValue.text = if (userChallenge.isCompleted) "Done" else "Active"
            tvSecondaryMetricLabel.text = "Status"
            tvTertiaryMetricValue.text = ""
            tvTertiaryMetricLabel.text = ""

            tvTimestamp.text = userChallenge.joinedAt.formatDate()
            btnFavorite.visibility = View.GONE
            btnPrimaryAction.text = "Update"

            if (userChallenge.isCompleted) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "COMPLETED"
            }
        }

        private fun bindWishlist(wishlist: Wishlist) {
            tvItemTitle.text = "Saved Eco Product"
            tvItemSubtitle.text = wishlist.productId
            tvItemDescription.text = "Saved in your wishlist"

            tvTimestamp.text = wishlist.addedAt?.time?.formatDate().orEmpty()
            btnFavorite.setImageResource(android.R.drawable.ic_menu_delete)
            btnFavorite.contentDescription = "Remove from wishlist"
            btnPrimaryAction.text = "View Product"
        }

        private fun setupTags(tags: List<String>) {
            val tagViews = listOf(tvTag1, tvTag2, tvTag3)
            tags.take(3).forEachIndexed { index, tag ->
                tagViews[index].apply {
                    text = tag.capitalizeWords()
                    visibility = View.VISIBLE
                }
            }
        }

        private fun setupClickListeners(item: UniversalItem, position: Int) {
            itemView.setOnClickListener { listener.onItemClick(item, position) }
            ivBookmark.setOnClickListener { listener.onBookmarkClick(item, position) }
            btnShare.setOnClickListener { listener.onShareClick(item, position) }
            btnFavorite.setOnClickListener { listener.onFavoriteClick(item, position) }
            btnPrimaryAction.setOnClickListener { listener.onPrimaryActionClick(item, position) }
        }
    }
}

class UniversalDiffCallback : DiffUtil.ItemCallback<UniversalItem>() {
    override fun areItemsTheSame(oldItem: UniversalItem, newItem: UniversalItem): Boolean {
        return when {
            oldItem is UniversalItem.UserItem && newItem is UniversalItem.UserItem -> oldItem.user.id == newItem.user.id
            oldItem is UniversalItem.CarbonFootprintItem && newItem is UniversalItem.CarbonFootprintItem -> oldItem.carbonFootprint.id == newItem.carbonFootprint.id
            oldItem is UniversalItem.TrackingDataItem && newItem is UniversalItem.TrackingDataItem -> oldItem.trackingData.id == newItem.trackingData.id
            oldItem is UniversalItem.EcoProductItem && newItem is UniversalItem.EcoProductItem -> oldItem.ecoProduct.id == newItem.ecoProduct.id
            oldItem is UniversalItem.ProductReviewItem && newItem is UniversalItem.ProductReviewItem -> oldItem.productReview.id == newItem.productReview.id
            oldItem is UniversalItem.TipItem && newItem is UniversalItem.TipItem -> oldItem.tip.id == newItem.tip.id
            oldItem is UniversalItem.AchievementItem && newItem is UniversalItem.AchievementItem -> oldItem.achievement.id == newItem.achievement.id
            oldItem is UniversalItem.UserAchievementItem && newItem is UniversalItem.UserAchievementItem -> oldItem.userAchievement.id == newItem.userAchievement.id
            oldItem is UniversalItem.GoalItem && newItem is UniversalItem.GoalItem -> oldItem.goal.id == newItem.goal.id
            oldItem is UniversalItem.ChallengeItem && newItem is UniversalItem.ChallengeItem -> oldItem.challenge.id == newItem.challenge.id
            oldItem is UniversalItem.UserChallengeItem && newItem is UniversalItem.UserChallengeItem -> oldItem.userChallenge.id == newItem.userChallenge.id
            oldItem is UniversalItem.WishlistItem && newItem is UniversalItem.WishlistItem -> oldItem.wishlist.id == newItem.wishlist.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: UniversalItem, newItem: UniversalItem): Boolean {
        return oldItem == newItem
    }
}
