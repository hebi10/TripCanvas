package com.hebi.tripcanvas.data.route

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteEndpointPolicyTest {
    @Test fun acceptsExplicitTrustedHttpsProxyPath() {
        assertTrue(RouteEndpointPolicy.approved("https://routes.tripcanvas.example.com/v1/routes"))
    }

    @Test fun refusesDemoLocalCredentialsQueryAndNonTls() {
        listOf(
            "", "http://routes.tripcanvas.com/v1/routes",
            "https://localhost/v1/routes",
            "https://127.0.0.1/v1/routes",
            "https://service.local/v1/routes",
            "https://route.example/v1/routes",
            "https://routes.tripcanvas.example.com/api",
            "https://person:secret@routes.tripcanvas.com/v1/routes",
            "https://routes.tripcanvas.com/v1/routes?key=secret",
            "https://routes.tripcanvas.com:8080/v1/routes",
            "https://routes.tripcanvas.com/v1/routes#anchor"
        ).forEach { assertFalse(it, RouteEndpointPolicy.approved(it)) }
    }

    @Test fun checksCoordinatesWithoutApproximation() {
        assertTrue(ConfiguredTripRouteProvider.validCoordinates(-90.0, 180.0))
        assertFalse(ConfiguredTripRouteProvider.validCoordinates(91.0, 0.0))
        assertFalse(ConfiguredTripRouteProvider.validCoordinates(0.0, Double.NaN))
        assertFalse(ConfiguredTripRouteProvider.validCoordinates(0.0, Double.POSITIVE_INFINITY))
    }
}
