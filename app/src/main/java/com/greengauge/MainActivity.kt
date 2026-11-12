package com.greengauge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.greengauge.databinding.ActivityMainBinding
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: AppViewModel

    private lateinit var dashboardFragment: DashboardFragment
    private lateinit var trackingFragment: TrackingFragment
    private lateinit var productsFragment: ProductsFragment
    private lateinit var tipsFragment: TipsFragment
    private lateinit var profileFragment: ProfileFragment
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseAuth.getInstance().currentUser == null) {
            redirectToAuth()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AppViewModel::class.java]

        setupFragments(savedInstanceState)
        setupBottomNavigation()
        observeViewModel()
        schedulePeriodicSync()
        handleIntent(intent)

        if (savedInstanceState == null) {
            viewModel.hydrateCurrentUser()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun setupFragments(savedInstanceState: Bundle?) {
        val fm = supportFragmentManager
        if (savedInstanceState == null) {
            dashboardFragment = DashboardFragment()
            trackingFragment = TrackingFragment()
            productsFragment = ProductsFragment()
            tipsFragment = TipsFragment()
            profileFragment = ProfileFragment()

            fm.beginTransaction()
                .add(R.id.fragmentContainer, profileFragment, TAG_PROFILE).hide(profileFragment)
                .add(R.id.fragmentContainer, tipsFragment, TAG_TIPS).hide(tipsFragment)
                .add(R.id.fragmentContainer, productsFragment, TAG_PRODUCTS).hide(productsFragment)
                .add(R.id.fragmentContainer, trackingFragment, TAG_TRACKING).hide(trackingFragment)
                .add(R.id.fragmentContainer, dashboardFragment, TAG_DASHBOARD)
                .commit()

            activeFragment = dashboardFragment
        } else {
            dashboardFragment = (fm.findFragmentByTag(TAG_DASHBOARD) as? DashboardFragment) ?: DashboardFragment()
            trackingFragment = (fm.findFragmentByTag(TAG_TRACKING) as? TrackingFragment) ?: TrackingFragment()
            productsFragment = (fm.findFragmentByTag(TAG_PRODUCTS) as? ProductsFragment) ?: ProductsFragment()
            tipsFragment = (fm.findFragmentByTag(TAG_TIPS) as? TipsFragment) ?: TipsFragment()
            profileFragment = (fm.findFragmentByTag(TAG_PROFILE) as? ProfileFragment) ?: ProfileFragment()

            activeFragment = listOf(
                dashboardFragment,
                trackingFragment,
                productsFragment,
                tipsFragment,
                profileFragment
            ).firstOrNull { !it.isHidden } ?: dashboardFragment
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val target: Fragment = when (item.itemId) {
                R.id.nav_dashboard -> dashboardFragment
                R.id.nav_tracking -> trackingFragment
                R.id.nav_products -> productsFragment
                R.id.nav_tips -> tipsFragment
                R.id.nav_profile -> profileFragment
                else -> return@setOnItemSelectedListener false
            }

            if (target !== activeFragment) {
                val tx = supportFragmentManager.beginTransaction()
                activeFragment?.let { tx.hide(it) }
                tx.show(target)
                tx.commit()
                activeFragment = target
            }
            updateToolbarTitle(item.itemId)
            true
        }
        updateToolbarTitle(binding.bottomNavigation.selectedItemId)
    }

    private fun updateToolbarTitle(itemId: Int) {
        binding.toolbarTitle.text = when (itemId) {
            R.id.nav_dashboard -> getString(R.string.nav_dashboard)
            R.id.nav_tracking -> getString(R.string.nav_tracking)
            R.id.nav_products -> getString(R.string.nav_products)
            R.id.nav_tips -> getString(R.string.nav_tips)
            R.id.nav_profile -> getString(R.string.nav_profile)
            else -> getString(R.string.app_name)
        }
    }

    fun navigateToTab(itemId: Int) {
        if (binding.bottomNavigation.selectedItemId != itemId) {
            binding.bottomNavigation.selectedItemId = itemId
        }
    }

    private fun observeViewModel() {
        viewModel.isAuthenticated.observe(this) { authenticated ->
            if (authenticated == false) {
                redirectToAuth()
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.mainProgressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { errorMsg ->
            if (!errorMsg.isNullOrBlank()) {
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.actionMessage.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearActionMessage()
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        when (data.host) {
            "dashboard" -> navigateToTab(R.id.nav_dashboard)
            "tracking" -> navigateToTab(R.id.nav_tracking)
            "products" -> navigateToTab(R.id.nav_products)
            "tips" -> navigateToTab(R.id.nav_tips)
            "profile" -> navigateToTab(R.id.nav_profile)
        }
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val syncWork = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "greengauge_daily_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncWork
        )
    }

    private fun redirectToAuth() {
        val intent = Intent(this, AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG_DASHBOARD = "tab_dashboard"
        private const val TAG_TRACKING = "tab_tracking"
        private const val TAG_PRODUCTS = "tab_products"
        private const val TAG_TIPS = "tab_tips"
        private const val TAG_PROFILE = "tab_profile"
    }
}

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        return try {
            runBlocking {
                DatabaseHelper.getUser(uid)
                DatabaseHelper.getCarbonFootprintsByUser(uid)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
