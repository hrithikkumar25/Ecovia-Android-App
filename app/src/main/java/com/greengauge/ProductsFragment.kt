package com.greengauge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.zxing.integration.android.IntentIntegrator
import com.greengauge.databinding.FragmentProductsBinding

class ProductsFragment : Fragment(), UniversalAdapterListener {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppViewModel by activityViewModels()
    private lateinit var productsAdapter: UniversalAdapter

    private val selectedCompareProducts = mutableListOf<EcoProduct>()
    private var selectedCategory = "All"
    private var selectedSort = "Recommended"
    private var searchQuery = ""
    private var showWishlistOnly = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchBarcodeScanner()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to scan barcodes.", Toast.LENGTH_SHORT).show()
        }
    }

    private val barcodeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val scanResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        val scannedCode = scanResult?.contents
        if (!scannedCode.isNullOrBlank()) {
            handleScannedBarcode(scannedCode)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSortDropdown()
        setupClickListeners()
        observeViewModel()

        if (viewModel.ecoProducts.value.isNullOrEmpty()) {
            viewModel.loadEcoProducts()
        }
    }

    private fun setupRecyclerView() {
        productsAdapter = UniversalAdapter(this)
        binding.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProducts.adapter = productsAdapter
    }

    private fun setupSortDropdown() {
        val sortOptions = listOf(
            "Recommended",
            "Sustainability Rating",
            "Price: Low to High",
            "Price: High to Low",
            "Most Reviewed"
        )
        val sortAdapter = ArrayAdapter(requireContext(), R.layout.dropdown_item_white_text, sortOptions)
        binding.spinnerSort.setAdapter(sortAdapter)
        binding.spinnerSort.setText(selectedSort, false)
        binding.spinnerSort.setOnItemClickListener { _, _, position, _ ->
            selectedSort = sortOptions[position]
            applyFiltersAndDisplay()
        }
    }

    private fun setupClickListeners() {
        binding.etSearch.doAfterTextChanged { editable ->
            searchQuery = editable?.toString()?.trim().orEmpty()
            applyFiltersAndDisplay()
        }

        binding.btnSearch.setOnClickListener {
            searchQuery = binding.etSearch.text?.toString()?.trim().orEmpty()
            applyFiltersAndDisplay()
        }

        binding.btnScanBarcode.setOnClickListener {
            checkCameraAndScanBarcode()
        }

        binding.btnFilterCategory.setOnClickListener {
            showCategoryFilterDialog()
        }

        binding.btnWishlistView.setOnClickListener {
            showWishlistOnly = !showWishlistOnly
            binding.btnWishlistView.text = if (showWishlistOnly) "All" else "Wishlist"
            applyFiltersAndDisplay()
        }

        binding.btnCompare.setOnClickListener {
            showCompareDialog()
        }

        binding.btnLocalStores.setOnClickListener {
            openNearbyEcoStoresInMaps()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            val hasItems = !viewModel.ecoProducts.value.isNullOrEmpty()
            binding.progressProducts.visibility = if (loading && !hasItems) View.VISIBLE else View.GONE
        }

        viewModel.ecoProducts.observe(viewLifecycleOwner) {
            applyFiltersAndDisplay()
        }

        viewModel.wishlist.observe(viewLifecycleOwner) { wishlist ->
            productsAdapter.updateWishlistProductIds(wishlist.map { it.productId }.toSet())
            applyFiltersAndDisplay()
        }
    }

    private fun applyFiltersAndDisplay() {
        val allProducts = viewModel.ecoProducts.value.orEmpty()
        val wishlistIds = viewModel.wishlist.value.orEmpty().map { it.productId }.toSet()

        val filtered = allProducts.filter { product ->
            val matchesCategory = selectedCategory == "All" ||
                product.category.equals(selectedCategory, ignoreCase = true)
            val matchesWishlist = !showWishlistOnly || wishlistIds.contains(product.id)
            val matchesQuery = searchQuery.isBlank() ||
                product.name.contains(searchQuery, ignoreCase = true) ||
                product.brand.contains(searchQuery, ignoreCase = true) ||
                product.description.contains(searchQuery, ignoreCase = true) ||
                product.category.contains(searchQuery, ignoreCase = true) ||
                product.tags.any { it.contains(searchQuery, ignoreCase = true) }

            matchesCategory && matchesWishlist && matchesQuery
        }

        val sorted = when (selectedSort) {
            "Sustainability Rating" -> filtered.sortedByDescending { it.sustainabilityRating }
            "Price: Low to High" -> filtered.sortedBy { it.price }
            "Price: High to Low" -> filtered.sortedByDescending { it.price }
            "Most Reviewed" -> filtered.sortedByDescending { it.reviewCount }
            else -> filtered.sortedByDescending { it.isRecommended }
        }

        val modeLabel = if (showWishlistOnly) "Wishlist" else selectedCategory
        binding.tvProductFilterSummary.text = "Filter: $modeLabel • ${sorted.size} product(s)"

        productsAdapter.submitItems(sorted.map { UniversalItem.EcoProductItem(it) })
        binding.tvNoProducts.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        binding.tvNoProducts.text = when {
            showWishlistOnly && wishlistIds.isEmpty() -> "Your wishlist is empty. Tap the star icon on any eco product to save it."
            else -> "No eco products match your current filter."
        }
    }

    private fun showCategoryFilterDialog() {
        val allProducts = viewModel.ecoProducts.value.orEmpty()
        val dynamicCategories = allProducts.map { it.category.capitalizeWords() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val categories = (listOf("All") + dynamicCategories).distinct()
        val checkedIndex = categories.indexOfFirst { it.equals(selectedCategory, ignoreCase = true) }
            .coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Category")
            .setSingleChoiceItems(categories.toTypedArray(), checkedIndex) { dialog, which ->
                selectedCategory = categories[which]
                applyFiltersAndDisplay()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkCameraAndScanBarcode() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchBarcodeScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchBarcodeScanner() {
        val integrator = IntentIntegrator(requireActivity()).apply {
            setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES)
            setPrompt("Scan product barcode")
            setBeepEnabled(false)
            setOrientationLocked(true)
        }
        barcodeLauncher.launch(integrator.createScanIntent())
    }

    private fun handleScannedBarcode(code: String) {
        val match = viewModel.ecoProducts.value.orEmpty().find {
            it.barcode == code || it.id == code
        }
        if (match != null) {
            showProductDetailsDialog(match)
        } else {
            Toast.makeText(
                requireContext(),
                "No catalog product matches barcode $code.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openNearbyEcoStoresInMaps() {
        val geoUri = Uri.parse("geo:0,0?q=eco+friendly+zero+waste+store")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
        if (mapIntent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(mapIntent)
        } else {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=eco+friendly+store")
            )
            startActivity(webIntent)
        }
    }

    private fun showCompareDialog() {
        if (selectedCompareProducts.size < 2) {
            Toast.makeText(
                requireContext(),
                "Tap the compare icon on at least 2 products (${selectedCompareProducts.size}/2 selected).",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val first = selectedCompareProducts[selectedCompareProducts.size - 2]
        val second = selectedCompareProducts[selectedCompareProducts.size - 1]
        val comparison = """
            ${first.name} vs ${second.name}

            • Brand: ${first.brand} vs ${second.brand}
            • Price: ${first.price.formatPrice()} vs ${second.price.formatPrice()}
            • Sustainability Rating: ${first.sustainabilityRating.formatRating()}★ vs ${second.sustainabilityRating.formatRating()}★
            • Recyclable: ${if (first.isRecyclable == true) "Yes" else "No"} vs ${if (second.isRecyclable == true) "Yes" else "No"}
            • Biodegradable: ${if (first.isBiodegradable == true) "Yes" else "No"} vs ${if (second.isBiodegradable == true) "Yes" else "No"}
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Product Comparison")
            .setMessage(comparison)
            .setPositiveButton("Done", null)
            .setNeutralButton("Clear Selection") { _, _ ->
                selectedCompareProducts.clear()
                Toast.makeText(requireContext(), "Comparison list cleared.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onItemClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.EcoProductItem) {
            showProductDetailsDialog(item.ecoProduct)
        }
    }

    override fun onBookmarkClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.EcoProductItem) {
            toggleWishlist(item.ecoProduct)
        }
    }

    override fun onShareClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.EcoProductItem) {
            val p = item.ecoProduct
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Check out ${p.name} by ${p.brand} (${p.sustainabilityRating.formatRating()}★ eco rating) on GreenGauge!"
                )
            }
            startActivity(Intent.createChooser(intent, "Share Eco Product"))
        }
    }

    override fun onFavoriteClick(item: UniversalItem, position: Int) {
        if (item is UniversalItem.EcoProductItem) {
            val product = item.ecoProduct
            if (selectedCompareProducts.any { it.id == product.id }) {
                selectedCompareProducts.removeAll { it.id == product.id }
                Toast.makeText(requireContext(), "Removed ${product.name} from compare.", Toast.LENGTH_SHORT).show()
            } else {
                selectedCompareProducts.add(product)
                if (selectedCompareProducts.size > 2) {
                    selectedCompareProducts.removeAt(0)
                }
                Toast.makeText(
                    requireContext(),
                    "Added ${product.name} to compare (${selectedCompareProducts.size}/2).",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onPrimaryActionClick(item: UniversalItem, position: Int) {
        onItemClick(item, position)
    }

    private fun toggleWishlist(product: EcoProduct) {
        val userId = viewModel.currentUser.value?.id
        if (userId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Please wait for your profile to finish loading.", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = viewModel.wishlist.value.orEmpty().find { it.productId == product.id }
        if (existing != null) {
            viewModel.removeFromWishlist(existing.id, userId)
        } else {
            val newItem = Wishlist(
                id = "${userId}_${product.id}",
                userId = userId,
                productId = product.id
            )
            viewModel.addToWishlist(newItem)
        }
    }

    private fun showProductDetailsDialog(product: EcoProduct) {
        val isWishlisted = viewModel.wishlist.value.orEmpty().any { it.productId == product.id }
        val details = """
            Brand: ${product.brand}
            Category: ${product.category.capitalizeWords()}
            Price: ${product.price.formatPrice()}
            Sustainability Rating: ${product.sustainabilityRating.formatRating()} ★ (${product.reviewCount} reviews)
            Recyclable: ${if (product.isRecyclable == true) "Yes" else "Standard"}
            Biodegradable: ${if (product.isBiodegradable == true) "Yes" else "Standard"}
            Tags: ${product.tags.joinToString(", ").ifBlank { "Eco-Friendly" }}

            ${product.description}
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle(product.name)
            .setMessage(details)
            .setPositiveButton(if (isWishlisted) "Remove Wishlist" else "Add to Wishlist") { _, _ ->
                toggleWishlist(product)
            }
            .setNeutralButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
