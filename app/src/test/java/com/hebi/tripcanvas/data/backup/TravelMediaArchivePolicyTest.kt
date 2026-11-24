package com.hebi.tripcanvas.data.backup

import com.hebi.tripcanvas.data.local.TravelMomentEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TravelMediaArchivePolicyTest {
    @Test fun filenamesUseOnlyDeterministicIndexAndSupportedType() {
        assertEquals("photos/000.jpg", TravelMediaArchivePolicy.photoPath(0, "image/jpeg"))
        assertEquals("photos/099.png", TravelMediaArchivePolicy.photoPath(99, "image/png"))
        assertEquals("photos/003.heic", TravelMediaArchivePolicy.photoPath(3, "image/heic"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownMediaType() {
        TravelMediaArchivePolicy.photoPath(1, "application/pdf")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPathIndexOutsideLimit() {
        TravelMediaArchivePolicy.photoPath(100, "image/png")
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToExportAnotherTripsMoments() {
        TravelMediaArchivePolicy.acceptedPhotoCount(listOf(moment("other", "content://photo/1")), "trip")
    }

    @Test fun onlyCountsAttachedPhotosAndLeavesTextOnlyRecords() {
        assertEquals(
            1, TravelMediaArchivePolicy.acceptedPhotoCount(
                listOf(moment("trip", null), moment("trip", "content://photo/1")), "trip"
            )
        )
    }

    private fun moment(tripId: String, uri: String?) = TravelMomentEntity(
        id = "id", tripId = tripId, visitId = null,
        photoUri = uri, caption = "memo", createdAtUtcMillis = 1
    )
}
