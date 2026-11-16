package com.hebi.tripcanvas.monetization

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.hebi.tripcanvas.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TripAdConsent(private val activity: Activity) {
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private val allowed = MutableStateFlow(false)
    val canShowAds = allowed.asStateFlow()

    fun request() {
        if (!BuildConfig.ADS_CONFIGURED) return
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                updatePermission()
            }
            updatePermission()
        }, {
            updatePermission()
        })
    }

    private fun updatePermission() {
        if (consent.canRequestAds()) {
            allowed.value = true
            MobileAds.initialize(activity.applicationContext) {}
        } else {
            allowed.value = false
        }
    }

    fun showPrivacyOptions() {
        if (!BuildConfig.ADS_CONFIGURED ||
            consent.privacyOptionsRequirementStatus !=
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        ) return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { updatePermission() }
    }

    val privacyOptionsRequired: Boolean
        get() = BuildConfig.ADS_CONFIGURED &&
            consent.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
}

@Composable
fun TripHomeBanner(canRequestAds: Boolean, adFree: Boolean) {
    if (!shouldShowTripBanner(BuildConfig.ADS_CONFIGURED, canRequestAds, true, adFree)) return
    val context = LocalContext.current
    val adView = remember(context) {
        AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(AdSize.BANNER)
        }
    }
    DisposableEffect(adView) {
        adView.loadAd(AdRequest.Builder().build())
        onDispose { adView.destroy() }
    }
    AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
}
