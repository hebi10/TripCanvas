package com.hebi.tripcanvas.data.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchAccessPolicyTest {
    @Test fun onlyDebugBuildWithExactDemoEndpointMayUsePhoton() {
        val endpoint = PlaceSearchAccessPolicy.PHOTON_DEMO_ENDPOINT
        assertTrue(PlaceSearchAccessPolicy.photonDemoAllowed(true, endpoint))
        assertFalse(PlaceSearchAccessPolicy.photonDemoAllowed(false, endpoint))
        listOf("", "http://photon.komoot.io/api/", "https://photon.komoot.io/api/?q=test",
            "https://photon.komoot.io.evil.example/api/", "https://other.example/api/",
            "https://photon.komoot.io/api").forEach {
            assertFalse(PlaceSearchAccessPolicy.photonDemoAllowed(true, it))
            assertFalse(PlaceSearchAccessPolicy.photonDemoAllowed(false, it))
        }
    }
}
