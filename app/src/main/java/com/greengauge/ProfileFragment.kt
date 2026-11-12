package com.greengauge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.greengauge.databinding.FragmentProfileBinding

class ProfileFragment : Fragment(), UniversalAdapterListener {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()

    private lateinit var achievementsAdapter: UniversalAdapter
    private lateinit var goalsAdapter: UniversalAdapter
    private lateinit var challengesAdapter: UniversalAdapter

    private var pendingEditDialogImageView: ImageView? = null
    private var selectedPhotoUri: Uri? = null
    private var leaderboardAdapter: UniversalAdapter? = null
    private var isUpdatingSwitchFromState = false

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedPhotoUri = uri
            pendingEditDialogImageView?.let { imageView ->
                Glide.with(this)
                    .load(uri)
                    .circleCrop()
                    .into(imageView)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerViews() {
        achievementsAdapter = UniversalAdapter(this)
        binding.rvAchievements.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAchievements.adapter = achievementsAdapter

        goalsAdapter = UniversalAdapter(this)
        binding.rvGoals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGoals.adapter = goalsAdapter

        challengesAdapter = UniversalAdapter(this)
        binding.rvChallenges.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChallenges.adapter = challengesAdapter
    }

    private fun setupClickListeners() {
        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        binding.btnShareProfile.setOnClickListener {
            shareProfile()
        }

        binding.btnCreateGoal.setOnClickListener {
            showCreateGoalDialog()
        }

        binding.btnViewLeaderboard.setOnClickListener {
            showLeaderboardDialog()
        }

        binding.btnLogout.setOnClickListener {
            confirmLogout()
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingSwitchFromState) return@setOnCheckedChangeListener
            val userId = viewModel.currentUser.value?.id ?: return@setOnCheckedChangeListener
            val existing = viewModel.userPreferences.value ?: UserPreferences(id = userId, userId = userId)
            viewModel.updateUserPreferences(
                existing.copy(
                    id = userId,
                    userId = userId,
                    notificationsEnabled = isChecked
                )
            )
        }
    }

    private fun observeViewModel() {
        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.tvUserName.text = user.name
                binding.tvUserEmail.text = user.email
                binding.tvTotalPoints.text = user.totalPoints.formatInteger()
                binding.tvLevel.text = "Level ${user.level}"
                binding.tvStreakDays.text = "${user.streakDays} days"

                if (ValidationUtils.isValidUrl(user.profileImageUrl)) {
                    Glide.with(this)
                        .load(user.profileImageUrl)
                        .placeholder(R.drawable.ic_profile)
                        .circleCrop()
                        .into(binding.ivProfileImage)
                } else {
                    binding.ivProfileImage.setImageResource(R.drawable.ic_profile)
                }
            }
        }

        viewModel.sustainabilityScore.observe(viewLifecycleOwner) { score ->
            binding.tvSustainabilityScore.text = score.toString()
        }

        viewModel.achievements.observe(viewLifecycleOwner) { refreshAchievements() }
        viewModel.userAchievements.observe(viewLifecycleOwner) { refreshAchievements() }

        viewModel.goals.observe(viewLifecycleOwner) { goals ->
            goalsAdapter.submitItems(goals.map { UniversalItem.GoalItem(it) })
            binding.tvNoGoals.visibility = if (goals.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.userChallenges.observe(viewLifecycleOwner) { refreshChallenges() }
        viewModel.activeChallenges.observe(viewLifecycleOwner) { refreshChallenges() }

        viewModel.userPreferences.observe(viewLifecycleOwner) { prefs ->
            if (prefs != null) {
                isUpdatingSwitchFromState = true
                binding.switchNotifications.isChecked = prefs.notificationsEnabled
                isUpdatingSwitchFromState = false
            }
        }

        viewModel.leaderboardUsers.observe(viewLifecycleOwner) { users ->
            leaderboardAdapter?.submitItems(users.map { UniversalItem.UserItem(it) })
        }
    }

    private fun refreshAchievements() {
        val allAchievements = viewModel.achievements.value.orEmpty()
        val unlockedIds = viewModel.userAchievements.value.orEmpty()
            .filter { it.isCompleted }
            .map { it.achievementId }
            .toSet()

        val items = if (allAchievements.isNotEmpty()) {
            allAchievements.map { achievement ->
                UniversalItem.AchievementItem(
                    achievement.copy(isUnlocked = achievement.isUnlocked || unlockedIds.contains(achievement.id))
                )
            }
        } else {
            viewModel.userAchievements.value.orEmpty().map { UniversalItem.UserAchievementItem(it) }
        }
        achievementsAdapter.submitItems(items)
        binding.tvNoAchievements.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshChallenges() {
        val joined = viewModel.userChallenges.value.orEmpty()
        val joinedChallengeIds = joined.map { it.challengeId }.toSet()
        val availableToJoin = viewModel.activeChallenges.value.orEmpty()
            .filterNot { joinedChallengeIds.contains(it.id) }

        val combined = joined.map { UniversalItem.UserChallengeItem(it) } +
            availableToJoin.map { UniversalItem.ChallengeItem(it) }

        challengesAdapter.submitItems(combined)
        binding.tvNoChallenges.visibility = if (combined.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showEditProfileDialog() {
        val user = viewModel.currentUser.value
        if (user == null) {
            Toast.makeText(requireContext(), "Profile is still loading.", Toast.LENGTH_SHORT).show()
            return
        }

        selectedPhotoUri = null
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val ivPhoto = dialogView.findViewById<ImageView>(R.id.dialogEtProfileImage)
        val btnChangePhoto = dialogView.findViewById<Button>(R.id.dialogEtChangePhoto)
        val etName = dialogView.findViewById<EditText>(R.id.dialogEtName)
        val etEmail = dialogView.findViewById<EditText>(R.id.dialogEtEmail)
        val btnCancel = dialogView.findViewById<Button>(R.id.dialogEtBtnCancel)
        val btnSave = dialogView.findViewById<Button>(R.id.dialogEtBtnSave)

        pendingEditDialogImageView = ivPhoto
        etName.setText(user.name)
        etEmail.setText(user.email)
        if (ValidationUtils.isValidUrl(user.profileImageUrl)) {
            Glide.with(this)
                .load(user.profileImageUrl)
                .placeholder(R.drawable.ic_profile)
                .circleCrop()
                .into(ivPhoto)
        } else {
            ivPhoto.setImageResource(R.drawable.ic_profile)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Edit Profile")
            .setView(dialogView)
            .create()

        dialog.setOnDismissListener {
            pendingEditDialogImageView = null
        }

        btnChangePhoto.setOnClickListener {
            pickMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newName = etName.text?.toString()?.trim().orEmpty()
            if (!ValidationUtils.isValidName(newName)) {
                Toast.makeText(requireContext(), "Enter a valid name (2-50 characters).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newName != user.name) {
                viewModel.updateUser(user.copy(name = newName))
            }
            selectedPhotoUri?.let { uri ->
                val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    viewModel.uploadProfileImage(user.id, bytes)
                } else {
                    Toast.makeText(requireContext(), "Could not read selected image.", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun shareProfile() {
        val user = viewModel.currentUser.value ?: return
        val score = viewModel.sustainabilityScore.value ?: user.sustainabilityScore
        val shareText = """
            Check out my GreenGauge Eco Profile!
            • Level ${user.level} (${user.totalPoints} pts)
            • Sustainability Score: $score
            • Streak: ${user.streakDays} days
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Share Profile"))
    }

    private fun showCreateGoalDialog() {
        val userId = viewModel.currentUser.value?.id
        if (userId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Profile not loaded yet.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_create_goal, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.goalTitle)
        val etTargetValue = dialogView.findViewById<EditText>(R.id.goalTargetValue)
        val etUnit = dialogView.findViewById<EditText>(R.id.goalUnit)
        val spinnerCategory = dialogView.findViewById<AutoCompleteTextView>(R.id.goalSpinnerCategory)
        val datePicker = dialogView.findViewById<DatePicker>(R.id.goalDeadline)

        val categories = listOf("Transportation", "Energy", "Diet", "Waste", "Water", "Shopping")
        spinnerCategory.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item_white_text, categories))
        spinnerCategory.setText(categories.first(), false)

        AlertDialog.Builder(requireContext())
            .setTitle("Create Sustainability Goal")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val title = etTitle.text?.toString()?.trim().orEmpty()
                val targetValue = etTargetValue.text?.toString()?.trim()?.toDoubleOrNull()
                val unit = etUnit.text?.toString()?.trim().orEmpty().ifBlank { "units" }
                val category = spinnerCategory.text?.toString()?.trim()?.lowercase().orEmpty().ifBlank { "general" }

                if (title.isBlank() || targetValue == null || targetValue <= 0.0) {
                    Toast.makeText(requireContext(), "Enter a goal title and positive target.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val calendar = java.util.Calendar.getInstance().apply {
                    set(datePicker.year, datePicker.month, datePicker.dayOfMonth, 23, 59, 59)
                }

                val newGoal = Goal(
                    userId = userId,
                    title = title,
                    category = category,
                    targetValue = targetValue,
                    currentValue = 0.0,
                    unit = unit,
                    deadline = calendar.timeInMillis
                )
                viewModel.saveGoal(newGoal)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUpdateProgressDialog(
        currentValue: Double,
        targetValue: Double,
        onSave: (Double) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_update_progress, null)
        val tvCurrent = dialogView.findViewById<TextView>(R.id.tvCurrentValue)
        val tvTarget = dialogView.findViewById<TextView>(R.id.tvTargetValue)
        val etNewValue = dialogView.findViewById<EditText>(R.id.updateProgressValue)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnUpdate = dialogView.findViewById<Button>(R.id.btnUpdate)

        tvCurrent.text = NumberFormatter.formatNumber(currentValue, 1)
        tvTarget.text = NumberFormatter.formatNumber(targetValue, 1)
        etNewValue.setText(currentValue.toString())

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnUpdate.setOnClickListener {
            val entered = etNewValue.text?.toString()?.trim()?.toDoubleOrNull()
            if (entered == null || entered < 0.0) {
                Toast.makeText(requireContext(), "Enter a valid non-negative number.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onSave(entered)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showLeaderboardDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_leaderboard, null)
        val rvLeaderboard = dialogView.findViewById<RecyclerView>(R.id.rvLeaderboard)
        val btnClose = dialogView.findViewById<Button>(R.id.btnLeaderboardClose)

        val adapter = UniversalAdapter(this)
        leaderboardAdapter = adapter
        rvLeaderboard.layoutManager = LinearLayoutManager(requireContext())
        rvLeaderboard.adapter = adapter
        adapter.submitItems(viewModel.leaderboardUsers.value.orEmpty().map { UniversalItem.UserItem(it) })

        viewModel.loadLeaderboard(20)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.setOnDismissListener {
            leaderboardAdapter = null
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to sign out of GreenGauge?")
            .setPositiveButton("Logout") { _, _ ->
                val clientId = getString(R.string.default_web_client_id).trim()
                if (clientId.endsWith(".apps.googleusercontent.com")) {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(clientId)
                        .build()
                    GoogleSignIn.getClient(requireActivity(), gso).signOut()
                }
                viewModel.signOut()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onItemClick(item: UniversalItem, position: Int) {
        when (item) {
            is UniversalItem.GoalItem -> {
                val goal = item.goal
                showUpdateProgressDialog(goal.currentValue, goal.targetValue) { newVal ->
                    val completed = newVal >= goal.targetValue
                    viewModel.updateGoal(
                        goal.copy(
                            currentValue = newVal,
                            isCompleted = completed
                        )
                    )
                }
            }
            is UniversalItem.ChallengeItem -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Join ${item.challenge.title}?")
                    .setMessage("${item.challenge.description}\n\nTarget: ${item.challenge.targetValue} ${item.challenge.unit}\nReward: +${item.challenge.points} points")
                    .setPositiveButton("Join") { _, _ ->
                        viewModel.joinChallenge(item.challenge)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            is UniversalItem.UserChallengeItem -> {
                val uc = item.userChallenge
                val parentChallenge = viewModel.activeChallenges.value.orEmpty().find { it.id == uc.challengeId }
                val target = parentChallenge?.targetValue ?: 100.0
                showUpdateProgressDialog(uc.progress, target) { newVal ->
                    val completed = newVal >= target
                    viewModel.updateUserChallenge(
                        uc.copy(
                            progress = newVal,
                            isCompleted = completed
                        )
                    )
                }
            }
            is UniversalItem.AchievementItem -> {
                AlertDialog.Builder(requireContext())
                    .setTitle(item.achievement.title)
                    .setMessage("${item.achievement.description}\n\nPoints: ${item.achievement.points}\nStatus: ${if (item.achievement.isUnlocked) "Unlocked" else "Locked"}")
                    .setPositiveButton("OK", null)
                    .show()
            }
            else -> Unit
        }
    }

    override fun onBookmarkClick(item: UniversalItem, position: Int) = Unit

    override fun onShareClick(item: UniversalItem, position: Int) {
        val text = when (item) {
            is UniversalItem.AchievementItem -> "I unlocked '${item.achievement.title}' on GreenGauge!"
            is UniversalItem.GoalItem -> "Working on my eco goal '${item.goal.title}' on GreenGauge!"
            is UniversalItem.UserChallengeItem -> "Making progress on challenge '${item.userChallenge.challengeId}' on GreenGauge!"
            else -> return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share"))
    }

    override fun onFavoriteClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.GoalItem) {
            val goal = item.goal
            AlertDialog.Builder(requireContext())
                .setTitle("Delete Goal")
                .setMessage("Delete '${goal.title}'?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteGoal(goal.id, goal.userId)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onPrimaryActionClick(item: UniversalItem, position: Int) {
        onItemClick(item, position)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        leaderboardAdapter = null
        pendingEditDialogImageView = null
        _binding = null
    }
}
