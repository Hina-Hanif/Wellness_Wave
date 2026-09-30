package com.example.myapplication.data.revenuecat

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Clean integration layer for RevenueCat Purchases SDK.
 *
 * Configured identifiers:
 * - Entitlement: "wellness_pro"
 * - Offering: "default"
 * - Product: "wellness_pro_monthly"
 * - Package: "Monthly"
 */
object RevenueCatManager {

    private const val TAG = "RevenueCatManager"

    // Configuration constants matching dashboard setup
    const val ENTITLEMENT_WELLNESS_PRO = "wellness_pro"
    const val ENTITLEMENT_WELLNESS_WAVE_PRO = "wellness_wave_pro"
    const val OFFERING_DEFAULT = "default"
    const val PRODUCT_WELLNESS_PRO_MONTHLY = "wellness_pro_monthly"
    const val PACKAGE_MONTHLY = "Monthly"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _customerInfoFlow = MutableStateFlow<CustomerInfo?>(null)
    val customerInfoFlow: StateFlow<CustomerInfo?> = _customerInfoFlow.asStateFlow()

    private val _isProActiveFlow = MutableStateFlow(false)
    val isProActiveFlow: StateFlow<Boolean> = _isProActiveFlow.asStateFlow()

    private val _currentOfferingFlow = MutableStateFlow<Offering?>(null)
    val currentOfferingFlow: StateFlow<Offering?> = _currentOfferingFlow.asStateFlow()

    private val _isInitializedFlow = MutableStateFlow(false)
    val isInitializedFlow: StateFlow<Boolean> = _isInitializedFlow.asStateFlow()

    /**
     * Initializes the RevenueCat Purchases SDK.
     * Should be called from Application.onCreate().
     */
    fun initialize(context: Context, apiKey: String) {
        if (Purchases.isConfigured) {
            Log.d(TAG, "RevenueCat is already initialized.")
            _isInitializedFlow.value = true
            return
        }

        if (apiKey.isBlank()) {
            Log.e(TAG, "Cannot initialize RevenueCat: API key is blank. Please specify 'revenuecat.apiKey' in local.properties.")
            return
        }

        try {
            // Enable detailed debug logging for development/testing
            Purchases.logLevel = LogLevel.DEBUG

            val configuration = PurchasesConfiguration.Builder(context.applicationContext, apiKey)
                .build()

            Purchases.configure(configuration)
            _isInitializedFlow.value = true
            Log.i(TAG, "RevenueCat Purchases SDK initialized successfully.")

            // Listen for real-time customer info updates (e.g. renewal, cancellation, purchase)
            Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
                handleCustomerInfo(customerInfo)
            }

            // Initial fetch of CustomerInfo and Offerings
            refreshCustomerInfo()
            refreshOfferings()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RevenueCat Purchases SDK: ${e.message}", e)
        }
    }

    /**
     * Updates internal state flows with new CustomerInfo.
     */
    private fun handleCustomerInfo(customerInfo: CustomerInfo) {
        _customerInfoFlow.value = customerInfo
        val proActive = checkEntitlementActive(customerInfo, ENTITLEMENT_WELLNESS_PRO)
        _isProActiveFlow.value = proActive
        Log.d(TAG, "CustomerInfo updated. Entitlement '$ENTITLEMENT_WELLNESS_PRO' active: $proActive. Active entitlements: ${customerInfo.entitlements.active.keys}, Active subscriptions: ${customerInfo.activeSubscriptions}")
    }

    /**
     * Checks if a specific entitlement is active on the given CustomerInfo.
     */
    fun checkEntitlementActive(customerInfo: CustomerInfo?, entitlementId: String = ENTITLEMENT_WELLNESS_PRO): Boolean {
        if (customerInfo == null) return false
        val entitlement = customerInfo.entitlements[entitlementId]
        if (entitlement != null && entitlement.isActive) return true

        // Check if dashboard entitlement "wellness_wave_pro" is active
        val waveProEntitlement = customerInfo.entitlements[ENTITLEMENT_WELLNESS_WAVE_PRO]
        if (waveProEntitlement != null && waveProEntitlement.isActive) return true

        // Case-insensitive check across active entitlements
        if (customerInfo.entitlements.active.any { 
            it.key.equals(entitlementId, ignoreCase = true) || 
            it.key.equals(ENTITLEMENT_WELLNESS_WAVE_PRO, ignoreCase = true) 
        }) {
            return true
        }

        // Direct check for active subscriptions (e.g. "monthly" or "wellness_pro_monthly")
        if (customerInfo.activeSubscriptions.any { 
            it.equals("monthly", ignoreCase = true) || 
            it.equals(PRODUCT_WELLNESS_PRO_MONTHLY, ignoreCase = true) 
        }) {
            return true
        }

        return false
    }

    /**
     * Suspends and fetches the latest CustomerInfo.
     */
    suspend fun getCustomerInfo(): Result<CustomerInfo> {
        if (!Purchases.isConfigured) {
            return Result.failure(IllegalStateException("RevenueCat is not initialized"))
        }

        return try {
            val customerInfo = Purchases.sharedInstance.awaitCustomerInfo()
            handleCustomerInfo(customerInfo)
            Result.success(customerInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching CustomerInfo: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Asynchronously refreshes CustomerInfo on IO scope.
     */
    fun refreshCustomerInfo() {
        if (!Purchases.isConfigured) return

        scope.launch {
            getCustomerInfo()
        }
    }

    /**
     * Suspends and fetches the current Offerings, updating the currentOfferingFlow.
     */
    suspend fun getOfferings(): Result<Offerings> {
        if (!Purchases.isConfigured) {
            return Result.failure(IllegalStateException("RevenueCat is not initialized"))
        }

        return try {
            val offerings = Purchases.sharedInstance.awaitOfferings()
            val current = offerings.current ?: offerings.getOffering(OFFERING_DEFAULT)
            _currentOfferingFlow.value = current
            Log.d(TAG, "Fetched offerings. Current offering: ${current?.identifier}")
            Result.success(offerings)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Offerings: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Asynchronously refreshes Offerings on IO scope.
     */
    fun refreshOfferings() {
        if (!Purchases.isConfigured) return

        scope.launch {
            getOfferings()
        }
    }

    /**
     * Synchronously or callback-based check for "wellness_pro" entitlement.
     */
    fun isProActive(): Boolean {
        return _isProActiveFlow.value
    }

    /**
     * Resolves the configured Monthly package from the given Offering.
     */
    fun getMonthlyPackage(offering: Offering?): Package? {
        if (offering == null) return null
        return offering.monthly
            ?: offering.getPackage(PACKAGE_MONTHLY)
            ?: offering.availablePackages.firstOrNull { it.identifier.equals(PACKAGE_MONTHLY, ignoreCase = true) }
            ?: offering.availablePackages.firstOrNull()
    }

    /**
     * Initiates a purchase for the given Package.
     * Reads CustomerInfo after purchase and updates pro entitlement state.
     */
    suspend fun purchasePackage(activity: Activity, packageToPurchase: Package): Result<CustomerInfo> {
        if (!Purchases.isConfigured) {
            return Result.failure(IllegalStateException("RevenueCat is not initialized"))
        }

        return try {
            val params = PurchaseParams.Builder(activity, packageToPurchase).build()
            val result = Purchases.sharedInstance.awaitPurchase(params)
            handleCustomerInfo(result.customerInfo)
            Log.i(TAG, "Purchase succeeded for package: ${packageToPurchase.identifier}")
            Result.success(result.customerInfo)
        } catch (e: PurchasesException) {
            Log.e(TAG, "Purchase failed: [${e.code}] ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during purchase: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Restores purchases previously made by the user.
     * Reads CustomerInfo after restore and updates pro entitlement state.
     */
    suspend fun restorePurchases(): Result<CustomerInfo> {
        if (!Purchases.isConfigured) {
            return Result.failure(IllegalStateException("RevenueCat is not initialized"))
        }

        return try {
            val customerInfo = Purchases.sharedInstance.awaitRestore()
            handleCustomerInfo(customerInfo)
            Log.i(TAG, "Purchases restored successfully.")
            Result.success(customerInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring purchases: ${e.message}", e)
            Result.failure(e)
        }
    }
}
