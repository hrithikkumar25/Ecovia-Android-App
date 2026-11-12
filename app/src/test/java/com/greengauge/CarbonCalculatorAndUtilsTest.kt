package com.greengauge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbonCalculatorAndUtilsTest {

    @Test
    fun `transportation emissions calculate accurately for all modes`() {
        assertEquals(1.92, CarbonCalculator.calculateEmissions("Transportation", "Car", 10.0), 0.001)
        assertEquals(0.89, CarbonCalculator.calculateEmissions("Transportation", "Bus", 10.0), 0.001)
        assertEquals(0.41, CarbonCalculator.calculateEmissions("Transportation", "Train", 10.0), 0.001)
        assertEquals(0.28, CarbonCalculator.calculateEmissions("Transportation", "Subway", 10.0), 0.001)
        assertEquals(2.04, CarbonCalculator.calculateEmissions("Transportation", "Taxi", 10.0), 0.001)
        assertEquals(0.84, CarbonCalculator.calculateEmissions("Transportation", "Motorcycle", 10.0), 0.001)
        assertEquals(0.0, CarbonCalculator.calculateEmissions("Transportation", "Walking", 10.0), 0.001)
        assertEquals(0.0, CarbonCalculator.calculateEmissions("Transportation", "Bicycle", 10.0), 0.001)
    }

    @Test
    fun `energy diet waste water and shopping emissions calculate accurately`() {
        assertEquals(5.0, CarbonCalculator.calculateEmissions("Energy", "Electricity", 10.0), 0.001)
        assertEquals(1.85, CarbonCalculator.calculateEmissions("Energy", "Gas", 10.0), 0.001)
        assertEquals(6.6, CarbonCalculator.calculateEmissions("Diet", "Meat", 2.0), 0.001)
        assertEquals(0.8, CarbonCalculator.calculateEmissions("Diet", "Vegetables", 2.0), 0.001)
        assertEquals(-1.5, CarbonCalculator.calculateEmissions("Waste", "Recycling", 5.0), 0.001)
        assertEquals(2.5, CarbonCalculator.calculateEmissions("Waste", "Landfill", 5.0), 0.001)
        assertEquals(0.03, CarbonCalculator.calculateEmissions("Water", "Drinking", 100.0), 0.001)
        assertEquals(16.0, CarbonCalculator.calculateEmissions("Shopping", "Clothes", 2.0), 0.001)
    }

    @Test
    fun `negative and zero inputs clamp to zero`() {
        assertEquals(0.0, CarbonCalculator.calculateEmissions("Transportation", "Car", 0.0), 0.001)
        assertEquals(0.0, CarbonCalculator.calculateEmissions("Energy", "Electricity", -5.0), 0.001)
    }

    @Test
    fun `calculateTotalEmissions sums all categories`() {
        val fp = CarbonFootprint(
            transportationEmissions = 2.0,
            energyEmissions = 3.0,
            dietEmissions = 4.0,
            wasteEmissions = 1.0,
            waterEmissions = 0.5,
            shoppingEmissions = 1.5
        )
        assertEquals(12.0, CarbonCalculator.calculateTotalEmissions(fp), 0.001)
    }

    @Test
    fun `validation utils validate email password name and date ranges`() {
        assertTrue(ValidationUtils.isValidEmail("user@greengauge.app"))
        assertFalse(ValidationUtils.isValidEmail("invalid-email"))
        assertTrue(ValidationUtils.isValidPassword("secret123"))
        assertFalse(ValidationUtils.isValidPassword("123"))
        assertTrue(ValidationUtils.isValidName("Alex Green"))
        assertFalse(ValidationUtils.isValidName(" "))
        assertTrue(ValidationUtils.isValidDateRange(1000L, 1000L))
        assertFalse(ValidationUtils.isValidDateRange(2000L, 1000L))
    }

    @Test
    fun `sustainability utils level progress and daily tip work as expected`() {
        assertEquals(1, SustainabilityUtils.calculateLevel(0))
        assertEquals(2, SustainabilityUtils.calculateLevel(1000))
        assertEquals(50.0, SustainabilityUtils.calculateProgressPercentage(5.0, 10.0), 0.001)
        assertEquals(100.0, SustainabilityUtils.calculateProgressPercentage(15.0, 10.0), 0.001)
        assertTrue(SustainabilityUtils.isGoalCompleted(10.0, 10.0))

        val score = SustainabilityUtils.calculateSustainabilityScore(
            totalEmissions = 4.0,
            streakDays = 5,
            completedGoals = 2,
            totalPoints = 200
        )
        assertTrue(score in 90..100)

        val tip = SustainabilityUtils.generateDailyTip(listOf(Tip(id = "t1", title = "Save Water")))
        assertNotNull(tip)
    }
}

