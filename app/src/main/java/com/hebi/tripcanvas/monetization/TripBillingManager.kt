package com.hebi.tripcanvas.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val AD_FREE_PRODUCT_ID = "tripcanvas_ad_free"
internal const val MONTHLY_PLAN_ID = "monthly"
internal const val YEARLY_PLAN_ID = "yearly"

data class AdFreePlan(val basePlanId: String, val price: String)

data class AdFreeBillingState(
    val connected: Boolean = false,
    val resolved: Boolean = false,
    val adFree: Boolean = false,
    val plans: List<AdFreePlan> = emptyList(),
    val message: String? = null
)

class TripBillingManager(context: Context) : PurchasesUpdatedListener {
    private val mutableState = MutableStateFlow(AdFreeBillingState())
    val state = mutableState.asStateFlow()
    private var productDetails: ProductDetails? = null

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    mutableState.value = mutableState.value.copy(connected = true, message = null)
                    refresh()
                } else {
                    mutableState.value = AdFreeBillingState(
                        message = "Google Play 결제 서비스에 연결하지 못했습니다."
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                mutableState.value = mutableState.value.copy(connected = false, resolved = false)
            }
        })
    }

    fun refresh() {
        if (!client.isReady) {
            mutableState.value = mutableState.value.copy(resolved = false)
            connect()
            return
        }
        mutableState.value = mutableState.value.copy(resolved = false)
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.value = mutableState.value.copy(
                    resolved = false, message = "구독 상태를 확인할 수 없습니다."
                )
            } else {
                processPurchases(purchases)
            }
        }
        queryPlans()
    }

    private fun queryPlans() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(AD_FREE_PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )).build()
        client.queryProductDetailsAsync(params) { result, response ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                mutableState.value = mutableState.value.copy(plans = emptyList())
                return@queryProductDetailsAsync
            }
            productDetails = response.productDetailsList.firstOrNull {
                it.productId == AD_FREE_PRODUCT_ID
            }
            val plans = productDetails?.subscriptionOfferDetails.orEmpty()
                .filter { it.offerId == null && it.basePlanId in setOf(MONTHLY_PLAN_ID, YEARLY_PLAN_ID) }
                .mapNotNull { offer ->
                    val price = offer.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice
                    price?.let { AdFreePlan(offer.basePlanId, it) }
                }
                .sortedBy { if (it.basePlanId == MONTHLY_PLAN_ID) 0 else 1 }
            mutableState.value = mutableState.value.copy(plans = plans)
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val owned = purchases.filter {
            AD_FREE_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (owned.isEmpty()) {
            mutableState.value = mutableState.value.copy(
                resolved = true, adFree = false, message = null
            )
            return
        }
        val acknowledged = owned.any { it.isAcknowledged }
        mutableState.value = mutableState.value.copy(
            resolved = true, adFree = acknowledged,
            message = if (acknowledged) null else "결제 승인 확인 중입니다."
        )
        owned.filterNot { it.isAcknowledged }.forEach { purchase ->
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh()
                } else {
                    mutableState.value = mutableState.value.copy(
                        resolved = false, message = "구매 승인 확인에 실패했습니다. 다시 시도해 주세요."
                    )
                }
            }
        }
    }

    fun purchase(activity: Activity, basePlanId: String) {
        if (!client.isReady || state.value.adFree) return
        val detail = productDetails ?: return
        val offer = detail.subscriptionOfferDetails.orEmpty().firstOrNull {
            it.basePlanId == basePlanId && it.offerId == null
        } ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(detail)
                    .setOfferToken(offer.offerToken)
                    .build()
            )).build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            mutableState.value = mutableState.value.copy(
                message = "Google Play 결제 화면을 열지 못했습니다."
            )
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> refresh()
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> mutableState.value = mutableState.value.copy(
                message = "결제 처리가 완료되지 않았습니다. 구매 내역을 다시 확인해 주세요."
            )
        }
    }

    fun close() = client.endConnection()
}
