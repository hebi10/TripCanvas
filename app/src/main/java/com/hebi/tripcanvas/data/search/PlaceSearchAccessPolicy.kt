package com.hebi.tripcanvas.data.search

/**
 * The public Photon demo is a development source, not an approved production provider.
 * Do not infer production approval from a URL or from the current release track.
 */
internal object PlaceSearchAccessPolicy {
    const val PHOTON_DEMO_ENDPOINT = "https://photon.komoot.io/api/"

    fun photonDemoAllowed(debugBuild: Boolean, endpoint: String): Boolean =
        debugBuild && endpoint == PHOTON_DEMO_ENDPOINT
}
