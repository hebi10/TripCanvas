package com.hebi.tripcanvas.domain.collaboration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCollaborationPolicyTest {
    @Test fun rolesNeverGiveViewerWriteOrEditorMemberManagement() {
        assertTrue(TripCollaborationPolicy.mayShowAction(TripMemberRole.OWNER, TripMemberAction.INVITE))
        assertTrue(TripCollaborationPolicy.mayShowAction(TripMemberRole.EDITOR, TripMemberAction.EDIT))
        assertFalse(TripCollaborationPolicy.mayShowAction(TripMemberRole.EDITOR, TripMemberAction.CHANGE_ROLE))
        assertFalse(TripCollaborationPolicy.mayShowAction(TripMemberRole.VIEWER, TripMemberAction.EDIT))
        assertFalse(TripCollaborationPolicy.mayShowAction(null, TripMemberAction.VIEW))
    }

    @Test fun offlineOrRevokedChangesCannotClaimServerSynchronization() {
        assertFalse(TripCollaborationPolicy.canClaimSynced(true, true, false, 2, 2, false))
        assertFalse(TripCollaborationPolicy.canClaimSynced(true, true, true, 2, 2, true))
        assertFalse(TripCollaborationPolicy.canClaimSynced(true, true, true, 2, 1, false))
        assertFalse(TripCollaborationPolicy.canClaimSynced(false, true, true, 2, 2, false))
        assertFalse(TripCollaborationPolicy.canClaimSynced(true, false, true, 2, 2, false))
        assertTrue(TripCollaborationPolicy.canClaimSynced(true, true, true, 2, 2, false))
    }

    @Test fun conflictingRevisionMustWaitForExplicitResolution() {
        val mutation = TripSyncMutation("trip", "item", "itinerary", "request-1", 4L, 5L)
        assertEquals(
            TripSyncDecision.ServerAuthorizationRequired,
            evaluateTripSync(mutation, false, true, 4L, false)
        )
        assertEquals(
            TripSyncDecision.ServerAuthorizationRequired,
            evaluateTripSync(mutation, true, false, 4L, false)
        )
        assertEquals(
            TripSyncDecision.AccessRevoked,
            evaluateTripSync(mutation, true, true, 4L, true)
        )
        assertEquals(
            TripSyncDecision.Conflict(5L, 6L),
            evaluateTripSync(mutation, true, true, 6L, false)
        )
        assertEquals(
            TripSyncDecision.ReadyToSubmit,
            evaluateTripSync(mutation, true, true, 4L, false)
        )
    }

    @Test fun existingSettlementParticipantIdMustRemainStable() {
        assertTrue(TripCollaborationPolicy.preservesLocalParticipantId("local-uuid", "local-uuid"))
        assertFalse(TripCollaborationPolicy.preservesLocalParticipantId("local-uuid", "new-cloud-uuid"))
        assertFalse(TripCollaborationPolicy.preservesLocalParticipantId("", ""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidBaseRevision() {
        TripSyncMutation("trip", "expense", "expense", "request", -1, 0)
    }
}
