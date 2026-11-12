package com.greengauge

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.greengauge.databinding.FragmentTipsBinding

class TipsFragment : Fragment(), UniversalAdapterListener {

    private var _binding: FragmentTipsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()
    private lateinit var tipsAdapter: UniversalAdapter

    private var selectedCategoryTab = "All"
    private var selectedDifficulty = "All"
    private var searchQuery = ""

    private val tipTabs = listOf(
        "All",
        "Bookmarked",
        "Energy",
        "Water",
        "Transportation",
        "Food",
        "Waste",
        "Shopping"
    )

    private val difficulties = listOf("All", "Beginner", "Intermediate", "Advanced")
    private val createCategories = listOf("Energy", "Water", "Transportation", "Food", "Waste", "Shopping")
    private val impactLevels = listOf("Low", "Medium", "High")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTipsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupTabs()
        setupDifficultySpinner()
        setupSearchView()
        setupClickListeners()
        observeViewModel()

        if (viewModel.tips.value.isNullOrEmpty()) {
            viewModel.loadTips()
        }
    }

    private fun setupRecyclerView() {
        tipsAdapter = UniversalAdapter(this)
        binding.rvTips.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTips.adapter = tipsAdapter
    }

    private fun setupTabs() {
        tipTabs.forEach { label ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(label))
        }
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedCategoryTab = tab.text?.toString() ?: "All"
                applyFiltersAndDisplay()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupDifficultySpinner() {
        val adapter = ArrayAdapter(requireContext(), R.layout.dropdown_item_white_text, difficulties)
        binding.spinnerDifficulty.setAdapter(adapter)
        binding.spinnerDifficulty.setText("All", false)
        binding.spinnerDifficulty.setOnItemClickListener { _, _, position, _ ->
            selectedDifficulty = difficulties[position]
            applyFiltersAndDisplay()
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query?.trim().orEmpty()
                applyFiltersAndDisplay()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText?.trim().orEmpty()
                applyFiltersAndDisplay()
                return true
            }
        })
    }

    private fun setupClickListeners() {
        binding.btnCreateTip.setOnClickListener {
            showCreateTipDialog()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            val hasTips = !viewModel.tips.value.isNullOrEmpty()
            binding.progressTips.visibility = if (loading && !hasTips) View.VISIBLE else View.GONE
        }

        viewModel.tips.observe(viewLifecycleOwner) {
            applyFiltersAndDisplay()
        }

        viewModel.userTipStates.observe(viewLifecycleOwner) {
            applyFiltersAndDisplay()
        }
    }

    private fun applyFiltersAndDisplay() {
        val allTips = viewModel.tips.value.orEmpty()
        val filtered = allTips.filter { tip ->
            val matchesCategory = when (selectedCategoryTab) {
                "All" -> true
                "Bookmarked" -> tip.isBookmarked
                else -> tip.category.equals(selectedCategoryTab, ignoreCase = true)
            }
            val matchesDifficulty = selectedDifficulty == "All" ||
                tip.difficulty.equals(selectedDifficulty, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                tip.title.contains(searchQuery, ignoreCase = true) ||
                tip.content.contains(searchQuery, ignoreCase = true) ||
                tip.category.contains(searchQuery, ignoreCase = true) ||
                tip.tags.any { it.contains(searchQuery, ignoreCase = true) }

            matchesCategory && matchesDifficulty && matchesSearch
        }

        tipsAdapter.submitItems(filtered.map { UniversalItem.TipItem(it) })
        binding.tvNoTips.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.tvNoTips.text = if (selectedCategoryTab == "Bookmarked" && filtered.isEmpty()) {
            "No bookmarked tips yet. Tap the star icon on any tip to bookmark it."
        } else {
            "No sustainability tips match your current filter."
        }
    }

    private fun showCreateTipDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_tip, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.tipTitle)
        val etContent = dialogView.findViewById<EditText>(R.id.tipContent)
        val etImageUrl = dialogView.findViewById<EditText>(R.id.tipImageUrl)
        val etVideoUrl = dialogView.findViewById<EditText>(R.id.tipVideoUrl)
        val spinnerCategory = dialogView.findViewById<AutoCompleteTextView>(R.id.tipSpinnerCategory)
        val spinnerDifficulty = dialogView.findViewById<AutoCompleteTextView>(R.id.tipSpinnerDifficulty)
        val spinnerImpact = dialogView.findViewById<AutoCompleteTextView>(R.id.tipSpinnerImpact)
        val etSteps = dialogView.findViewById<EditText>(R.id.tipSteps)
        val etTags = dialogView.findViewById<EditText>(R.id.tipTags)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnTipCancel)
        val btnCreate = dialogView.findViewById<Button>(R.id.btnTipCreate)

        spinnerCategory.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item_white_text, createCategories))
        spinnerCategory.setText(createCategories.first(), false)

        val diffList = difficulties.drop(1)
        spinnerDifficulty.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item_white_text, diffList))
        spinnerDifficulty.setText(diffList.first(), false)

        spinnerImpact.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item_white_text, impactLevels))
        spinnerImpact.setText("Medium", false)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Create Eco Tip")
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            val title = etTitle.text?.toString()?.trim().orEmpty()
            val content = etContent.text?.toString()?.trim().orEmpty()
            val imageUrl = etImageUrl.text?.toString()?.trim().orEmpty()
            val videoUrl = etVideoUrl.text?.toString()?.trim().orEmpty()
            val category = spinnerCategory.text?.toString()?.trim()?.lowercase().orEmpty().ifBlank { "energy" }
            val difficulty = spinnerDifficulty.text?.toString()?.trim()?.lowercase().orEmpty().ifBlank { "beginner" }
            val impact = spinnerImpact.text?.toString()?.trim()?.lowercase().orEmpty().ifBlank { "medium" }
            val steps = etSteps.text?.toString()?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            val tags = etTags.text?.toString()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

            if (title.isBlank() || content.isBlank()) {
                Toast.makeText(requireContext(), "Please enter both a title and content.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newTip = Tip(
                title = title,
                content = content,
                category = category,
                difficulty = difficulty,
                impactLevel = impact,
                imageUrl = imageUrl,
                videoUrl = videoUrl,
                steps = steps,
                tags = tags
            )
            viewModel.createTip(newTip)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showTipDetailsDialog(tip: Tip) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tip_details, null)
        dialogView.findViewById<TextView>(R.id.tipTitle).text = tip.title
        dialogView.findViewById<TextView>(R.id.tipContent).text = tip.content
        dialogView.findViewById<TextView>(R.id.tipCategory).text = "Category: ${tip.category.capitalizeWords()}"
        dialogView.findViewById<TextView>(R.id.tipDifficulty).text = "Difficulty: ${tip.difficulty.capitalizeWords()}"
        dialogView.findViewById<TextView>(R.id.tipImpact).text = "Impact: ${tip.impactLevel.capitalizeWords()} (+25 pts)"
        dialogView.findViewById<TextView>(R.id.tipLikes).text = "Likes: ${tip.likes}"
        dialogView.findViewById<TextView>(R.id.tipSteps).text = if (tip.steps.isNotEmpty()) {
            "Steps:\n" + tip.steps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n")
        } else {
            "Follow the steps above to build this eco habit."
        }
        dialogView.findViewById<TextView>(R.id.tipTags).text = if (tip.tags.isNotEmpty()) {
            "Tags: ${tip.tags.joinToString(", ")}"
        } else {
            ""
        }

        val btnClose = dialogView.findViewById<Button>(R.id.btnTipDetailsClose)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Mark Implemented") { _, _ ->
                showTipImplementationDialog(tip)
            }
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showTipImplementationDialog(tip: Tip) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tip_implementation, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tipImpTitle)
        val cbImplemented = dialogView.findViewById<CheckBox>(R.id.cbImplemented)
        val etNotes = dialogView.findViewById<EditText>(R.id.tipImpNotes)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnTipImpCancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btnTipImpSave)

        val existingState = viewModel.userTipStates.value.orEmpty().find { it.tipId == tip.id }
        tvTitle.text = tip.title
        cbImplemented.isChecked = existingState?.isCompleted == true
        etNotes.setText(existingState?.notes.orEmpty())

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            if (!cbImplemented.isChecked) {
                Toast.makeText(requireContext(), "Check the box to mark this tip as implemented.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val notes = etNotes.text?.toString()?.trim().orEmpty()
            viewModel.markTipCompleted(tip, notes)
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onItemClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.TipItem) {
            showTipDetailsDialog(item.tip)
        }
    }

    override fun onBookmarkClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.TipItem) {
            viewModel.toggleTipBookmark(item.tip)
        }
    }

    override fun onShareClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.TipItem) {
            val t = item.tip
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "GreenGauge Tip: ${t.title}\n\n${t.content}\n\nCategory: ${t.category.capitalizeWords()}"
                )
            }
            startActivity(Intent.createChooser(shareIntent, "Share Eco Tip"))
        }
    }

    override fun onFavoriteClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.TipItem) {
            viewModel.likeTip(item.tip.id)
        }
    }

    override fun onPrimaryActionClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.TipItem) {
            showTipDetailsDialog(item.tip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
