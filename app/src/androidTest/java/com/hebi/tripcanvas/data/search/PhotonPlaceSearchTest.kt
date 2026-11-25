package com.hebi.tripcanvas.data.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotonPlaceSearchTest {
    @Test
    fun parsesCoordinatesAndRemovesDuplicateOsmIds() {
        val json = """
            {"features":[
              {"geometry":{"coordinates":[126.49,33.51]},
               "properties":{"osm_type":"N","osm_id":321,"name":"제주국제공항",
               "city":"제주","country":"대한민국"}},
              {"geometry":{"coordinates":[126.49,33.51]},
               "properties":{"osm_type":"N","osm_id":321,"name":"제주국제공항",
               "city":"제주","country":"대한민국"}},
              {"geometry":{"coordinates":[0,0]},
               "properties":{"osm_type":"N","osm_id":999,"name":""}}
            ]}
        """.trimIndent()
        val result = PhotonPlaceSearch.parseResponse(json)
        assertEquals(1, result.size)
        assertEquals("N:321", result.single().providerId)
        assertEquals("Photon/OSM", result.single().provider)
        assertEquals(33.51, result.single().latitude, 0.0001)
        assertEquals(126.49, result.single().longitude, 0.0001)
    }

    @Test
    fun unapprovedSearchEndpointIsRejectedBeforeNetworkRequest() = runBlocking {
        val service = PhotonPlaceSearch("https://invalid.example/api/")
        assertFalse(service.available)
        val failure = runCatching { service.search("제주국제공항") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun approvedProxyResponsePreservesOsmIdentityAndRejectsUnlicensedOrInvalidRows() {
        val valid = """{"provider":"Photon/OSM","places":[{"providerId":"N:321","name":"제주국제공항","address":"제주","latitude":33.51,"longitude":126.49}]}"""
        val place = ConfiguredPhotonProxySearch.parseResponse(valid).single()
        assertEquals("N:321", place.providerId)
        assertEquals("Photon/OSM", place.provider)
        assertEquals(33.51, place.latitude, 0.0001)
        listOf(valid.replace("Photon/OSM", "Unknown"),
            valid.replace("N:321", "../321"), valid.replace("33.51", "99.0"),
            valid.replace("33.51", "\"33.51\"")).forEach {
            assertTrue(runCatching { ConfiguredPhotonProxySearch.parseResponse(it) }.isFailure)
        }
        assertTrue(ConfiguredPhotonProxySearch.create("https://maps.tripcanvas.example.com/v1/places", null) == null)
    }

    @Test
    fun emptySearchNeverSendsNetworkRequest() = runBlocking {
        val service = PhotonPlaceSearch("https://invalid.example/api/")
        assertTrue(runCatching { service.search("") }.isFailure)
    }
}
