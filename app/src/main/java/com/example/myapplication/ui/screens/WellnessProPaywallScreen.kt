package com.example.myapplication.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.revenuecat.RevenueCatManager
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import kotlinx.coroutines.launch

/**
 * Clean, modern paywall screen for Wellness Pro.
 * Displays dynamic product details from RevenueCat's "default" offering and Monthly package.
 */
@Composable
fun WellnessProPaywallScreen(
    onDismiss: () -> Unit,
    onPurchaseSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentOffering by RevenueCatManager.currentOfferingFlow.collectAsState()
    val isProActive by RevenueCatManager.isProActiveFlow.collectAsState()

    var monthlyPackage by remember { mutableStateOf<Package?>(null) }
    var isLoadingOffering by remember { mutableStateOf(true) }
    var isPurchasing by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDashboardPaywall by remember { mutableStateOf(false) }

    if (showDashboardPaywall) {
        PaywallDialog(
            PaywallDialogOptions.Builder()
                .setDismissRequest { showDashboardPaywall = false }
                .setListener(object : PaywallListener {
                    override fun onPurchaseCompleted(customerInfo: CustomerInfo, storeTransaction: StoreTransaction) {
                        showDashboardPaywall = false
                        if (RevenueCatManager.checkEntitlementActive(customerInfo)) {
                            onPurchaseSuccess()
                        }
                    }
                    override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                        showDashboardPaywall = false
                        if (RevenueCatManager.checkEntitlementActive(customerInfo)) {
                            onPurchaseSuccess()
                        }
                    }
                })
                .build()
        )
    }

    // If entitlement becomes active at any point, immediately trigger unlock callback
    LaunchedEffect(isProActive) {
        if (isProActive) {
            onPurchaseSuccess()
        }
    }

    // Fetch dynamic Offerings from RevenueCat
    LaunchedEffect(Unit) {
        isLoadingOffering = true
        errorMessage = null

        val result = RevenueCatManager.getOfferings()
        result.onSuccess { offerings ->
            val activeOffering = offerings.current ?: offerings.getOffering(RevenueCatManager.OFFERING_DEFAULT)
            monthlyPackage = RevenueCatManager.getMonthlyPackage(activeOffering)
            isLoadingOffering = false
        }.onFailure { err ->
            errorMessage = "Unable to load subscription details: ${err.message}"
            isLoadingOffering = false
        }
    }

    // Keep monthly package in sync with currentOfferingFlow
    LaunchedEffect(currentOffering) {
        if (currentOffering != null) {
            monthlyPackage = RevenueCatManager.getMonthlyPackage(currentOffering)
            isLoadingOffering = false
        }
    }

    val accentGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            Color.Transparent
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Gradient glow at the top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(accentGradient)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar with Dismiss Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Premium Icon Badge
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Title and Subtitle
            Text(
                text = "Wellness Pro",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Unlock Study Rescue & Supercharge Focus",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Value Proposition / Features list
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FeatureRow(
                    icon = Icons.Rounded.School,
                    title = "Study Rescue Deep Focus",
                    description = "Set focused goals with strict distraction interception & timer tracking."
                )
                FeatureRow(
                    icon = Icons.Rounded.Shield,
                    title = "Distraction App Redirection",
                    description = "Automatic soft redirection away from Instagram, YouTube, and distracting apps."
                )
                FeatureRow(
                    icon = Icons.Rounded.Analytics,
                    title = "Focus Rescue Analytics",
                    description = "Track intervention counts, distraction trends, and session completion metrics."
                )
                FeatureRow(
                    icon = Icons.Rounded.Bolt,
                    title = "Unlimited Sessions & Recovery",
                    description = "No daily limits on focus sessions and smart cooldown recovery."
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Dynamic Subscription Card from RevenueCat Offering
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "MONTHLY PLAN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isLoadingOffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Fetching plan details...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (monthlyPackage != null) {
                        val product = monthlyPackage!!.product

                        // Clean title dynamically from RevenueCat product
                        val dynamicTitle = product.title
                            .replace(Regex("\\(.*\\)$"), "")
                            .trim()
                            .ifBlank { "Wellness Pro Monthly" }

                        Text(
                            text = dynamicTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Dynamic localized price from RevenueCat Test Store / Google Play
                        Text(
                            text = product.price.formatted,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "billed monthly • cancel anytime",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Offering couldn't be loaded or no package configured
                        Text(
                            text = "Wellness Pro Subscription",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Test Store Offering",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Error or Status message banner
            AnimatedVisibility(visible = errorMessage != null || statusMessage != null) {
                val isError = errorMessage != null
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isError) Color(0x33FF5252) else Color(0x3300E37C),
                    border = BorderStroke(
                        1.dp,
                        if (isError) Color(0xFFFF5252).copy(alpha = 0.5f) else Color(0xFF00E37C).copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = errorMessage ?: statusMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) Color(0xFFFF5252) else Color(0xFF00E37C),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // CTA: Start Wellness Pro Button
            Button(
                onClick = {
                    showDashboardPaywall = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                )
            ) {
                if (isPurchasing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Processing...",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Wellness Pro",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Restore Purchases Button
            TextButton(
                onClick = {
                    isRestoring = true
                    errorMessage = null
                    statusMessage = null

                    scope.launch {
                        val result = RevenueCatManager.restorePurchases()
                        isRestoring = false

                        result.onSuccess { customerInfo ->
                            val isEntitled = RevenueCatManager.checkEntitlementActive(customerInfo)
                            if (isEntitled) {
                                statusMessage = "Purchases restored! Wellness Pro active."
                                onPurchaseSuccess()
                            } else {
                                statusMessage = "No active Wellness Pro subscription found to restore."
                            }
                        }.onFailure { err ->
                            errorMessage = "Restore failed: ${err.message}"
                        }
                    }
                },
                enabled = !isPurchasing && !isRestoring
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "Restore Purchases",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legal & Terms note
            Text(
                text = "Subscription automatically renews monthly unless cancelled. Manage or cancel your subscription at any time in Google Play Account Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}
