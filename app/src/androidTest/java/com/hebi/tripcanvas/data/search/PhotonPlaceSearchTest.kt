package com.hebi.tripcanvas.data.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun emptySearchNeverSendsNetworkRequest() = runBlocking {
        val service = PhotonPlaceSearch("https://invalid.example/api/")
        assertTrue(runCatching { service.search("") }.isFailure)
    }
}
