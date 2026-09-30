package com.example.myapplication.data.revenuecat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RevenueCatManagerTest {

    @Test
    fun testConfigurationConstantsMatchDashboard() {
        assertEquals("wellness_pro", RevenueCatManager.ENTITLEMENT_WELLNESS_PRO)
        assertEquals("default", RevenueCatManager.OFFERING_DEFAULT)
        assertEquals("wellness_pro_monthly", RevenueCatManager.PRODUCT_WELLNESS_PRO_MONTHLY)
        assertEquals("Monthly", RevenueCatManager.PACKAGE_MONTHLY)
    }

    @Test
    fun testInitialFlowState() {
        assertNull(RevenueCatManager.customerInfoFlow.value)
        assertFalse(RevenueCatManager.isProActiveFlow.value)
        assertNull(RevenueCatManager.currentOfferingFlow.value)
        assertFalse(RevenueCatManager.isProActive())
    }

    @Test
    fun testCheckEntitlementActiveWithNullReturnsFalse() {
        assertFalse(RevenueCatManager.checkEntitlementActive(null))
        assertFalse(RevenueCatManager.checkEntitlementActive(null, "wellness_pro"))
        assertFalse(RevenueCatManager.checkEntitlementActive(null, "some_other_entitlement"))
    }

    @Test
    fun testGetMonthlyPackageWithNullOfferingReturnsNull() {
        assertNull(RevenueCatManager.getMonthlyPackage(null))
    }
}
