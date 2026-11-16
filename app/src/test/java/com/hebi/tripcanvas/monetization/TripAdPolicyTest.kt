package com.hebi.tripcanvas.monetization

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripAdPolicyTest {
    @Test fun enabledOnlyAfterConsentAndBillingResolution() {
        assertFalse(shouldShowTripBanner(false, true, true, false))
        assertFalse(shouldShowTripBanner(true, false, true, false))
        assertFalse(shouldShowTripBanner(true, true, false, false))
        assertFalse(shouldShowTripBanner(true, true, true, true))
        assertTrue(shouldShowTripBanner(true, true, true, false))
    }
}
