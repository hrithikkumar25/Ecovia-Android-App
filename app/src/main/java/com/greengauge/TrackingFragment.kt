package com.greengauge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.tabs.TabLayout
import com.greengauge.databinding.FragmentTrackingBinding

class TrackingFragment : Fragment(), UniversalAdapterListener {

    private var _binding: FragmentTrackingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()
    private lateinit var trackingAdapter: UniversalAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentTrackingMode = TrackingMode.MANUAL
    private var selectedTabCategory = "all"
    private var gpsStartLocation: Location? = null

    private enum class TrackingMode { MANUAL, GPS }

    private val categories = listOf(
        "Transportation",
        "Energy",
        "Diet",
        "Waste",
        "Water",
        "Shopping"
    )

    private val activitiesByCategory = mapOf(
        "Transportation" to listOf("Car", "Bus", "Train", "Subway", "Taxi", "Motorcycle", "Bicycle", "Walking"),
        "Energy" to listOf("Electricity", "Gas", "Heating", "Cooling"),
        "Diet" to listOf("Meat", "Dairy", "Vegetables", "Fruits", "Grains", "Processed Foods"),
        "Waste" to listOf("Landfill", "Recycling", "Composting", "Plastic", "Paper", "Electronics"),
        "Water" to listOf("Drinking", "Shower", "Laundry", "Dishes", "Garden", "Pool"),
        "Shopping" to listOf("Groceries", "Clothes", "Electronics", "Furniture", "Books", "Cosmetics")
    )

    private val unitByCategory = mapOf(
        "Transportation" to "km",
        "Energy" to "kWh",
        "Diet" to "kg",
        "Waste" to "kg",
        "Water" to "liters",
        "Shopping" to "kg"
    )

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            captureGpsDistanceOrStartTrip()
        } else {
            Toast.makeText(requireContext(), "Location permission is required for GPS mode.", Toast.LENGTH_SHORT).show()
            binding.rbManual.isChecked = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        setupTabs()
        setupCategoryAndActivityDropdowns()
        setupTrackingModeRadioGroup()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupTabs() {
        val tabItems = listOf("All") + categories
        tabItems.forEach { label ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(label))
        }
        binding.tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedTabCategory = tab.text?.toString()?.lowercase() ?: "all"
                filterHistoryBySelectedTab()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupCategoryAndActivityDropdowns() {
        val catAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item_white_text, categories)
        binding.spinnerCategory.setAdapter(catAdapter)

        binding.spinnerCategory.setOnItemClickListener { _, _, position, _ ->
            val category = categories[position]
            updateActivitiesDropdown(binding.spinnerActivity, category, binding.etUnit)
        }

        val initialCategory = categories.first()
        binding.spinnerCategory.setText(initialCategory, false)
        updateActivitiesDropdown(binding.spinnerActivity, initialCategory, binding.etUnit)
    }

    private fun updateActivitiesDropdown(
        activityView: AutoCompleteTextView,
        category: String,
        unitView: EditText
    ) {
        val activities = activitiesByCategory[category] ?: emptyList()
        val adapter = ArrayAdapter(requireContext(), R.layout.dropdown_item_white_text, activities)
        activityView.setAdapter(adapter)
        if (activities.isNotEmpty()) {
            activityView.setText(activities.first(), false)
        } else {
            activityView.setText("", false)
        }
        unitView.setText(unitByCategory[category] ?: "units")
    }

    private fun setupTrackingModeRadioGroup() {
        binding.rgTrackingMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbManual -> {
                    currentTrackingMode = TrackingMode.MANUAL
                    gpsStartLocation = null
                }
                R.id.rbGPS -> {
                    currentTrackingMode = TrackingMode.GPS
                    binding.spinnerCategory.setText("Transportation", false)
                    updateActivitiesDropdown(binding.spinnerActivity, "Transportation", binding.etUnit)
                    requestGpsLocationIfPermitted()
                }
            }
        }
    }

    private fun requestGpsLocationIfPermitted() {
        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            captureGpsDistanceOrStartTrip()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun captureGpsDistanceOrStartTrip() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (_binding == null) return@addOnSuccessListener
                if (location == null) {
                    Toast.makeText(
                        requireContext(),
                        "GPS fix unavailable right now. Enter trip distance manually.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }
                val start = gpsStartLocation
                if (start == null) {
                    gpsStartLocation = location
                    Toast.makeText(
                        requireContext(),
                        "Trip start location captured. Tap GPS again at destination or enter distance.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val distanceKm = start.distanceTo(location) / 1000.0
                    binding.etValue.setText(NumberFormatter.formatNumber(distanceKm.coerceAtLeast(0.1), 2))
                    Toast.makeText(
                        requireContext(),
                        "Measured distance: ${NumberFormatter.formatNumber(distanceKm, 2)} km",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.addOnFailureListener {
                if (_binding != null) {
                    Toast.makeText(requireContext(), "Unable to read GPS location.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: SecurityException) {
            Toast.makeText(requireContext(), "Location permission missing.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        trackingAdapter = UniversalAdapter(this)
        binding.rvTrackingData.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTrackingData.adapter = trackingAdapter
    }

    private fun setupClickListeners() {
        binding.btnSaveTracking.setOnClickListener {
            saveInlineTrackingEntry()
        }
        binding.btnAddTracking.setOnClickListener {
            showAddTrackingDialog()
        }
    }

    private fun saveInlineTrackingEntry() {
        val category = binding.spinnerCategory.text?.toString()?.trim().orEmpty()
        val activity = binding.spinnerActivity.text?.toString()?.trim().orEmpty()
        val valueText = binding.etValue.text?.toString()?.trim().orEmpty()
        val unit = binding.etUnit.text?.toString()?.trim().orEmpty()
            .ifBlank { unitByCategory[category] ?: "units" }

        val value = valueText.toDoubleOrNull()
        if (category.isBlank() || activity.isBlank() || value == null || value <= 0.0) {
            Toast.makeText(
                requireContext(),
                "Select a category, activity, and enter a positive numeric value.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val userId = viewModel.currentUser.value?.id
        if (userId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "User profile not loaded yet.", Toast.LENGTH_SHORT).show()
            return
        }

        val emissions = CarbonCalculator.calculateEmissions(category, activity, value, unit)
        val entry = TrackingData(
            userId = userId,
            category = category.lowercase(),
            activityType = activity,
            value = value,
            unit = unit,
            emissions = emissions,
            date = DateUtils.getCurrentDate()
        )
        viewModel.saveTrackingData(entry)
        binding.etValue.text?.clear()
        gpsStartLocation = null
    }

    private fun showAddTrackingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_tracking, null)
        val spinnerCat = dialogView.findViewById<AutoCompleteTextView>(R.id.spinnerDialogCategory)
        val spinnerAct = dialogView.findViewById<AutoCompleteTextView>(R.id.spinnerDialogActivity)
        val etValue = dialogView.findViewById<EditText>(R.id.etDialogValue)
        val etUnit = dialogView.findViewById<EditText>(R.id.etDialogUnit)

        spinnerCat.setAdapter(ArrayAdapter(requireContext(), R.layout.dropdown_item_white_text, categories))
        spinnerCat.setText(categories.first(), false)
        updateActivitiesDropdown(spinnerAct, categories.first(), etUnit)

        spinnerCat.setOnItemClickListener { _, _, position, _ ->
            updateActivitiesDropdown(spinnerAct, categories[position], etUnit)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Add Carbon Activity")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val category = spinnerCat.text?.toString()?.trim().orEmpty()
                val activity = spinnerAct.text?.toString()?.trim().orEmpty()
                val value = etValue.text?.toString()?.trim()?.toDoubleOrNull()
                val unit = etUnit.text?.toString()?.trim().orEmpty()
                    .ifBlank { unitByCategory[category] ?: "units" }
                val userId = viewModel.currentUser.value?.id

                if (userId.isNullOrBlank() || category.isBlank() || activity.isBlank() || value == null || value <= 0.0) {
                    Toast.makeText(requireContext(), "Enter valid tracking values.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val emissions = CarbonCalculator.calculateEmissions(category, activity, value, unit)
                val entry = TrackingData(
                    userId = userId,
                    category = category.lowercase(),
                    activityType = activity,
                    value = value,
                    unit = unit,
                    emissions = emissions,
                    date = DateUtils.getCurrentDate()
                )
                viewModel.saveTrackingData(entry)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.totalEmissions.observe(viewLifecycleOwner) { total ->
            binding.tvTotalEmissions.text = total.formatEmission()
        }
        viewModel.weeklyEmissions.observe(viewLifecycleOwner) { weekly ->
            binding.tvWeeklyEmissions.text = weekly.formatEmission()
        }
        viewModel.monthlyEmissions.observe(viewLifecycleOwner) { monthly ->
            binding.tvMonthlyEmissions.text = monthly.formatEmission()
        }
        viewModel.trackingData.observe(viewLifecycleOwner) {
            filterHistoryBySelectedTab()
        }
    }

    private fun filterHistoryBySelectedTab() {
        val all = viewModel.trackingData.value.orEmpty()
        val filtered = if (selectedTabCategory == "all") {
            all
        } else {
            all.filter { it.category.equals(selectedTabCategory, ignoreCase = true) }
        }
        trackingAdapter.submitItems(filtered.map { UniversalItem.TrackingDataItem(it) })
        binding.tvNoTrackingHistory.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onItemClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.TrackingDataItem) {
            showEditTrackingDialog(item.trackingData)
        }
    }

    override fun onBookmarkClick(item: UniversalItem, position: Int) = Unit

    override fun onShareClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.TrackingDataItem) {
            val t = item.trackingData
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Tracked ${t.activityType} (${t.value} ${t.unit}) = ${t.emissions.formatEmission()} on GreenGauge."
                )
            }
            startActivity(Intent.createChooser(shareIntent, "Share Activity"))
        }
    }

    override fun onFavoriteClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.TrackingDataItem) {
            confirmDeleteTracking(item.trackingData)
        }
    }

    override fun onPrimaryActionClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.TrackingDataItem) {
            showEditTrackingDialog(item.trackingData)
        }
    }

    private fun showEditTrackingDialog(trackingData: TrackingData) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_tracking, null)
        val etValue = dialogView.findViewById<EditText>(R.id.etEditTrackValue)
        val etUnit = dialogView.findViewById<EditText>(R.id.etEditTrackUnit)

        etValue.setText(trackingData.value.toString())
        etUnit.setText(trackingData.unit)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit ${trackingData.activityType}")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val newValue = etValue.text?.toString()?.trim()?.toDoubleOrNull()
                val newUnit = etUnit.text?.toString()?.trim().orEmpty().ifBlank { trackingData.unit }
                if (newValue == null || newValue <= 0.0) {
                    Toast.makeText(requireContext(), "Enter a valid positive value.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val updatedEmissions = CarbonCalculator.calculateEmissions(
                    trackingData.category,
                    trackingData.activityType,
                    newValue,
                    newUnit
                )
                viewModel.updateTrackingData(
                    trackingData.copy(
                        value = newValue,
                        unit = newUnit,
                        emissions = updatedEmissions
                    )
                )
            }
            .setNeutralButton("Delete") { _, _ ->
                confirmDeleteTracking(trackingData)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteTracking(trackingData: TrackingData) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Activity")
            .setMessage("Delete '${trackingData.activityType}' (${trackingData.emissions.formatEmission()}) from your history?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteTrackingData(trackingData.id, trackingData.userId, trackingData.date)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
