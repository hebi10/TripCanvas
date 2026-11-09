package com.hebi.tripcanvas.data.search

data class RemotePlace(
    val providerId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val provider: String = "Photon/OSM"
) {
    init {
        require(providerId.isNotBlank() && providerId.length <= 200)
        require(name.isNotBlank() && name.length <= 100)
        require(address.length <= 500)
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}
