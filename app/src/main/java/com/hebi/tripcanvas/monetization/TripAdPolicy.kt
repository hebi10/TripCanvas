package com.hebi.tripcanvas.monetization

fun shouldShowTripBanner(
    adsConfigured: Boolean,
    consentGranted: Boolean,
    billingResolved: Boolean,
    adFree: Boolean
): Boolean = adsConfigured && consentGranted && billingResolved && !adFree
