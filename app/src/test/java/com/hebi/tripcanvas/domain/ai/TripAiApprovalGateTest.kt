package com.hebi.tripcanvas.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class TripAiApprovalGateTest {
    private val proposal = TripAiChangeProposal("trip", "item", 4, 5, "제안 일정", "검증된 출처", 1)
    @Test fun aiNeverChangesSourceWithoutExplicitConsentAndMatchingRevision() {
        assertEquals(
            TripAiApprovalState.NO_CONSENT,
            evaluateAiChange(false, proposal, setOf("item"), 4)
        )
        assertEquals(
            TripAiApprovalState.AWAITING_REVIEW,
            evaluateAiChange(true, proposal, emptySet(), 4)
        )
        assertEquals(
            TripAiApprovalState.STALE_SOURCE,
            evaluateAiChange(true, proposal, setOf("item"), 5)
        )
        assertEquals(
            TripAiApprovalState.APPROVED,
            evaluateAiChange(true, proposal, setOf("item"), 4)
        )
    }
}
