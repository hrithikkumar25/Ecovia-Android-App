package com.greengauge

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeAppRepository
    private lateinit var viewModel: AppViewModel

    @Before
    fun setup() {
        fakeRepo = FakeAppRepository()
        viewModel = AppViewModel(fakeRepo)
    }

    @Test
    fun `registerUser creates user with Firebase UID and authenticates`() {
        viewModel.registerUser("alice@example.com", "password123", "Alice Eco")

        assertTrue(viewModel.isAuthenticated.value == true)
        val user = viewModel.currentUser.value
        assertNotNull(user)
        assertEquals(fakeRepo.authUid, user?.id)
        assertEquals("Alice Eco", user?.name)
        assertEquals(50, user?.totalPoints)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `authenticateUser fails cleanly on wrong password and succeeds on valid credentials`() {
        fakeRepo.users["u1"] = User(
            id = "u1",
            email = "bob@example.com",
            name = "Bob",
            lastActiveDate = DateUtils.getCurrentDate(),
            streakDays = 3
        )

        viewModel.authenticateUser("bob@example.com", "wrong")
        assertFalse(viewModel.isAuthenticated.value == true)
        assertNotNull(viewModel.error.value)

        viewModel.clearError()
        viewModel.authenticateUser("bob@example.com", "correct123")
        assertTrue(viewModel.isAuthenticated.value == true)
        assertEquals("u1", viewModel.currentUser.value?.id)
        assertEquals(3, viewModel.currentUser.value?.streakDays)
    }

    @Test
    fun `google sign in creates user when new and loads when existing`() {
        viewModel.signInWithGoogle("valid_google_token")
        assertTrue(viewModel.isAuthenticated.value == true)
        assertEquals("google_uid_123", viewModel.currentUser.value?.id)
    }

    @Test
    fun `password reset sets flag for valid email and errors on invalid email`() {
        viewModel.sendPasswordReset("bad-email")
        assertFalse(viewModel.passwordResetSent.value == true)
        assertNotNull(viewModel.error.value)

        viewModel.clearError()
        viewModel.sendPasswordReset("user@example.com")
        assertTrue(viewModel.passwordResetSent.value == true)
    }

    @Test
    fun `signOut clears user state and sets unauthenticated`() {
        viewModel.registerUser("charlie@example.com", "password123", "Charlie")
        assertTrue(viewModel.isAuthenticated.value == true)

        viewModel.signOut()
        assertFalse(viewModel.isAuthenticated.value == true)
        assertNull(viewModel.currentUser.value)
        assertEquals(0.0, viewModel.totalEmissions.value ?: -1.0, 0.001)
    }

    @Test
    fun `updateUser and uploadProfileImage persist profile changes`() {
        viewModel.registerUser("dana@example.com", "password123", "Dana")
        val user = viewModel.currentUser.value!!

        viewModel.updateUser(user.copy(name = "Dana Updated"))
        assertEquals("Dana Updated", viewModel.currentUser.value?.name)

        viewModel.uploadProfileImage(user.id, byteArrayOf(1, 2, 3))
        assertTrue(viewModel.currentUser.value?.profileImageUrl?.contains(user.id) == true)
    }

    @Test
    fun `tracking save update and delete recalculate dashboard totals accurately`() {
        viewModel.registerUser("evan@example.com", "password123", "Evan")
        val uid = viewModel.currentUser.value!!.id
        val today = DateUtils.getCurrentDate()

        val entry = TrackingData(
            userId = uid,
            category = "transportation",
            activityType = "Car",
            value = 10.0,
            unit = "km",
            emissions = 1.92,
            date = today
        )
        viewModel.saveTrackingData(entry)

        assertEquals(1, viewModel.trackingData.value?.size)
        assertEquals(1.92, viewModel.totalEmissions.value ?: 0.0, 0.001)
        assertEquals(1.92, viewModel.weeklyEmissions.value ?: 0.0, 0.001)
        assertEquals(1.92, viewModel.monthlyEmissions.value ?: 0.0, 0.001)
        assertEquals(60, viewModel.currentUser.value?.totalPoints)

        val saved = viewModel.trackingData.value!!.first()
        viewModel.updateTrackingData(saved.copy(value = 20.0, emissions = 3.84))
        assertEquals(3.84, viewModel.totalEmissions.value ?: 0.0, 0.001)

        viewModel.deleteTrackingData(saved.id, uid, today)
        assertTrue(viewModel.trackingData.value.isNullOrEmpty())
        assertEquals(0.0, viewModel.totalEmissions.value ?: -1.0, 0.001)
    }

    @Test
    fun `products filter and wishlist add remove work cleanly`() {
        fakeRepo.products.addAll(
            listOf(
                EcoProduct(id = "p1", name = "Bamboo Bottle", category = "kitchen", sustainabilityRating = 4.8f),
                EcoProduct(id = "p2", name = "Solar Charger", category = "electronics", sustainabilityRating = 4.6f)
            )
        )
        viewModel.registerUser("fay@example.com", "password123", "Fay")
        val uid = viewModel.currentUser.value!!.id

        viewModel.loadEcoProducts()
        assertEquals(2, viewModel.ecoProducts.value?.size)

        viewModel.loadEcoProductsByCategory("kitchen")
        assertEquals(1, viewModel.ecoProducts.value?.size)

        val wish = Wishlist(id = "${uid}_p1", userId = uid, productId = "p1")
        viewModel.addToWishlist(wish)
        assertEquals(1, viewModel.wishlist.value?.size)

        viewModel.removeFromWishlist(wish.id, uid)
        assertTrue(viewModel.wishlist.value.isNullOrEmpty())
    }

    @Test
    fun `tips create bookmark like and single-reward completion work properly`() {
        viewModel.registerUser("gus@example.com", "password123", "Gus")
        viewModel.createTip(Tip(title = "LED Bulbs", content = "Switch to LEDs", category = "energy"))

        val createdTip = viewModel.tips.value?.firstOrNull()
        assertNotNull(createdTip)

        viewModel.toggleTipBookmark(createdTip!!)
        assertTrue(viewModel.tips.value!!.first().isBookmarked)

        viewModel.toggleTipBookmark(viewModel.tips.value!!.first())
        assertFalse(viewModel.tips.value!!.first().isBookmarked)

        viewModel.likeTip(createdTip.id)
        assertEquals(1, viewModel.tips.value!!.first().likes)

        val pointsBefore = viewModel.currentUser.value!!.totalPoints
        viewModel.markTipCompleted(createdTip, "Replaced 4 bulbs")
        assertEquals(pointsBefore + 25, viewModel.currentUser.value!!.totalPoints)

        // Completing the same tip a second time must not award duplicate points
        viewModel.markTipCompleted(createdTip, "Updated notes")
        assertEquals(pointsBefore + 25, viewModel.currentUser.value!!.totalPoints)
    }

    @Test
    fun `goal completion awards points once and delete removes goal`() {
        viewModel.registerUser("hana@example.com", "password123", "Hana")
        val uid = viewModel.currentUser.value!!.id

        viewModel.saveGoal(Goal(userId = uid, title = "Bike 50km", targetValue = 50.0, unit = "km", category = "transportation"))
        val goal = viewModel.goals.value!!.first()
        val pointsBefore = viewModel.currentUser.value!!.totalPoints

        viewModel.updateGoal(goal.copy(currentValue = 50.0, isCompleted = true))
        assertEquals(pointsBefore + 100, viewModel.currentUser.value!!.totalPoints)

        // Updating an already completed goal must not award duplicate points
        viewModel.updateGoal(viewModel.goals.value!!.first().copy(currentValue = 55.0, isCompleted = true))
        assertEquals(pointsBefore + 100, viewModel.currentUser.value!!.totalPoints)

        viewModel.deleteGoal(goal.id, uid)
        assertTrue(viewModel.goals.value.isNullOrEmpty())
    }

    @Test
    fun `firestore errors surface to error LiveData`() {
        fakeRepo.nextError = "Firestore unavailable"
        viewModel.loadEcoProducts()
        assertEquals("Firestore unavailable", viewModel.error.value)
    }
}

