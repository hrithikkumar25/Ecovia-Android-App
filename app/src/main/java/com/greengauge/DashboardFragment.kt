package com.greengauge

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.greengauge.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment(), UniversalAdapterListener {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()

    private lateinit var recentActivitiesAdapter: UniversalAdapter
    private lateinit var achievementsAdapter: UniversalAdapter
    private lateinit var dailyTipsAdapter: UniversalAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupChartAppearance()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerViews() {
        recentActivitiesAdapter = UniversalAdapter(this)
        binding.rvRecentActivities.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentActivities.adapter = recentActivitiesAdapter

        achievementsAdapter = UniversalAdapter(this)
        binding.rvAchievements.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAchievements.adapter = achievementsAdapter

        dailyTipsAdapter = UniversalAdapter(this)
        binding.rvDailyTips.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDailyTips.adapter = dailyTipsAdapter
    }

    private fun setupChartAppearance() {
        binding.chartProgress.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setDragEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            setNoDataText("Log carbon activities to view your emissions trend.")
            setNoDataTextColor(Color.LTGRAY)
            axisRight.isEnabled = false
            xAxis.textColor = Color.LTGRAY
            axisLeft.textColor = Color.LTGRAY
            legend.textColor = Color.WHITE
        }
    }

    private fun setupClickListeners() {
        binding.btnRefreshDashboard.setOnClickListener {
            val uid = viewModel.currentUser.value?.id
            if (!uid.isNullOrBlank()) {
                viewModel.refreshDashboardData(uid)
            } else {
                viewModel.hydrateCurrentUser()
            }
        }

        binding.btnTrackActivity.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(R.id.nav_tracking)
        }

        binding.btnViewProducts.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(R.id.nav_products)
        }

        binding.btnViewTips.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(R.id.nav_tips)
        }

        binding.btnViewProfile.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(R.id.nav_profile)
        }
    }

    private fun observeViewModel() {
        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.tvWelcome.text = "Welcome back, ${user.name}!"
                binding.tvTotalPoints.text = user.totalPoints.formatInteger()
                binding.tvLevel.text = "Level ${user.level}"
                binding.tvStreakDays.text = "${user.streakDays} days"
            } else {
                binding.tvWelcome.text = "Welcome to GreenGauge"
            }
        }

        viewModel.totalEmissions.observe(viewLifecycleOwner) { total ->
            binding.tvCarbonFootprint.text = total.formatEmission()
        }

        viewModel.weeklyEmissions.observe(viewLifecycleOwner) { weekly ->
            binding.tvWeeklyEmissions.text = "This week: ${weekly.formatEmission()}"
        }

        viewModel.monthlyEmissions.observe(viewLifecycleOwner) { monthly ->
            binding.tvMonthlyEmissions.text = "This month: ${monthly.formatEmission()}"
        }

        viewModel.sustainabilityScore.observe(viewLifecycleOwner) { score ->
            binding.tvSustainabilityScore.text = score.toString()
        }

        viewModel.carbonFootprints.observe(viewLifecycleOwner) { footprints ->
            updateProgressChart(footprints)
            updateImpactSummary(footprints)
        }

        viewModel.trackingData.observe(viewLifecycleOwner) {
            refreshRecentActivitiesSection()
        }

        viewModel.goals.observe(viewLifecycleOwner) {
            refreshRecentActivitiesSection()
        }

        viewModel.tips.observe(viewLifecycleOwner) { tips ->
            if (tips.isNotEmpty()) {
                val dailyTip = SustainabilityUtils.generateDailyTip(tips) ?: tips.first()
                binding.tvDailyTipTitle.text = dailyTip.title
                binding.tvDailyTipContent.text = dailyTip.content
                binding.tvWeatherSuggestion.text = SustainabilityUtils.getEcoTipForWeather("sunny")
                dailyTipsAdapter.submitItems(tips.take(3).map { UniversalItem.TipItem(it) })
            } else {
                binding.tvDailyTipTitle.text = "Daily Sustainability Tip"
                binding.tvDailyTipContent.text = "Log your daily transport, energy, or diet choices to get personalized eco insights."
                binding.tvWeatherSuggestion.text = SustainabilityUtils.getEcoTipForWeather("sunny")
                dailyTipsAdapter.submitItems(emptyList())
            }
        }

        viewModel.achievements.observe(viewLifecycleOwner) {
            refreshAchievementsSection()
        }

        viewModel.userAchievements.observe(viewLifecycleOwner) {
            refreshAchievementsSection()
        }
    }

    private fun refreshRecentActivitiesSection() {
        val tracking = viewModel.trackingData.value.orEmpty()
            .take(4)
            .map { UniversalItem.TrackingDataItem(it) }
        val activeGoals = viewModel.goals.value.orEmpty()
            .filter { !it.isCompleted }
            .take(2)
            .map { UniversalItem.GoalItem(it) }

        val combined = tracking + activeGoals
        recentActivitiesAdapter.submitItems(combined)
        binding.tvNoActivities.visibility = if (combined.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshAchievementsSection() {
        val allAchievements = viewModel.achievements.value.orEmpty()
        val unlockedIds = viewModel.userAchievements.value.orEmpty()
            .filter { it.isCompleted }
            .map { it.achievementId }
            .toSet()

        val displayItems = if (allAchievements.isNotEmpty()) {
            allAchievements.take(4).map { achievement ->
                UniversalItem.AchievementItem(
                    achievement.copy(isUnlocked = achievement.isUnlocked || unlockedIds.contains(achievement.id))
                )
            }
        } else {
            viewModel.userAchievements.value.orEmpty().take(4).map {
                UniversalItem.UserAchievementItem(it)
            }
        }

        achievementsAdapter.submitItems(displayItems)
        binding.tvNoAchievements.visibility = if (displayItems.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateProgressChart(footprints: List<CarbonFootprint>) {
        if (footprints.isEmpty()) {
            binding.chartProgress.clear()
            binding.chartProgress.invalidate()
            return
        }

        val sorted = footprints.sortedBy { it.date }.takeLast(7)
        val entries = sorted.mapIndexed { index, fp ->
            Entry(index.toFloat(), fp.totalEmissions.toFloat())
        }
        val labels = sorted.map { fp ->
            if (fp.date.length >= 10) fp.date.substring(5) else fp.date
        }

        val dataSet = LineDataSet(entries, "Daily CO2 Emissions (kg)").apply {
            color = Color.parseColor("#4CAF50")
            setCircleColor(Color.parseColor("#81C784"))
            valueTextColor = Color.WHITE
            lineWidth = 2.5f
            circleRadius = 4f
            valueTextSize = 10f
            setDrawFilled(true)
            fillColor = Color.parseColor("#2E7D32")
            fillAlpha = 80
        }

        binding.chartProgress.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.chartProgress.xAxis.granularity = 1f
        binding.chartProgress.data = LineData(dataSet)
        binding.chartProgress.invalidate()
    }

    private fun updateImpactSummary(footprints: List<CarbonFootprint>) {
        if (footprints.isEmpty()) {
            binding.llImpactSummary.visibility = View.GONE
            return
        }
        binding.llImpactSummary.visibility = View.VISIBLE
        binding.tvTransportEmissions.text = footprints.sumOf { it.transportationEmissions }.formatEmission()
        binding.tvEnergyEmissions.text = footprints.sumOf { it.energyEmissions }.formatEmission()
        binding.tvDietEmissions.text = footprints.sumOf { it.dietEmissions }.formatEmission()
        binding.tvWasteEmissions.text = footprints.sumOf { it.wasteEmissions }.formatEmission()
        binding.tvWaterEmissions.text = footprints.sumOf { it.waterEmissions }.formatEmission()
        binding.tvShoppingEmissions.text = footprints.sumOf { it.shoppingEmissions }.formatEmission()
    }

    override fun onItemClick(item: UniversalItem, position: Int) {
        when (item) {
            is UniversalItem.TipItem -> showTipDetailDialog(item.tip)
            is UniversalItem.TrackingDataItem -> (activity as? MainActivity)?.navigateToTab(R.id.nav_tracking)
            is UniversalItem.GoalItem -> (activity as? MainActivity)?.navigateToTab(R.id.nav_profile)
            is UniversalItem.AchievementItem -> showAchievementDialog(item.achievement)
            else -> Unit
        }
    }

    override fun onBookmarkClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.TipItem) {
            viewModel.toggleTipBookmark(item.tip)
        }
    }

    override fun onShareClick(item: UniversalItem, position: Int) {
        val shareText = when (item) {
            is UniversalItem.TipItem -> "GreenGauge Eco Tip: ${item.tip.title}\n${item.tip.content}"
            is UniversalItem.TrackingDataItem -> "I tracked ${item.trackingData.activityType} (${item.trackingData.emissions.formatEmission()}) on GreenGauge!"
            is UniversalItem.AchievementItem -> "Achievement on GreenGauge: ${item.achievement.title} (${item.achievement.points} pts)"
            is UniversalItem.GoalItem -> "My sustainability goal: ${item.goal.title}"
            else -> return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    override fun onFavoriteClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.TipItem) {
            viewModel.likeTip(item.tip.id)
        }
    }

    override fun onPrimaryActionClick(item: UniversalItem, position: Int) {
        onItemClick(item, position)
    }

    private fun showTipDetailDialog(tip: Tip) {
        AlertDialog.Builder(requireContext())
            .setTitle(tip.title)
            .setMessage("${tip.content}\n\nDifficulty: ${tip.difficulty}\nImpact: ${tip.impactLevel}\nReward: +25 pts")
            .setPositiveButton("Mark Completed") { _, _ ->
                viewModel.markTipCompleted(tip)
            }
            .setNeutralButton(if (tip.isBookmarked) "Unbookmark" else "Bookmark") { _, _ ->
                viewModel.toggleTipBookmark(tip)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAchievementDialog(achievement: Achievement) {
        AlertDialog.Builder(requireContext())
            .setTitle(achievement.title)
            .setMessage("${achievement.description}\n\nPoints: ${achievement.points}\nStatus: ${if (achievement.isUnlocked) "Unlocked" else "Locked"}")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
