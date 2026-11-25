package com.hebi.tripcanvas.domain.collaboration

import com.hebi.tripcanvas.data.collaboration.DisabledTripCollaborationGateway
import com.hebi.tripcanvas.data.collaboration.TripCollaborationUnavailable
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSharingBoundaryTest {
    private val now = 600_000L
    private val access = TripMemberAccess("trip-a", "owner", TripMemberRole.OWNER, now - 1000, now + 60_000)
    private val editor = TripMemberAccess("trip-a", "editor", TripMemberRole.EDITOR, now - 1000, now + 60_000)
    private val invitation = TripInvitationView("invite-a", "trip-a", "guest", TripMemberRole.VIEWER,
        TripInvitationStatus.PENDING, now + 1000)

    @Test fun participantAccountLinksDoNotMergeOrReplaceLocalIdentities() {
        val locals = setOf("local-1", "local-2", "local-3")
        assertTrue(TripSharingBoundary.validAccountLinks(locals, mapOf("local-1" to "account-1")))
        assertTrue(TripSharingBoundary.validAccountLinks(locals, emptyMap()))
        assertFalse(TripSharingBoundary.validAccountLinks(locals, mapOf("other-trip" to "account-1")))
        assertFalse(TripSharingBoundary.validAccountLinks(locals,
            mapOf("local-1" to "account-1", "local-2" to "account-1")))
        assertFalse(TripSharingBoundary.validAccountLinks(locals, mapOf("local-1" to " ")))
    }

    @Test fun membershipMustMatchFreshTripAndAccount() {
        val policy = TripMembershipPresentationPolicy
        fun can(value: TripMemberAccess?) =
            policy.canPresentAction("trip-a", "owner", value, TripMemberAction.INVITE, now)
        assertTrue(can(access))
        assertFalse(can(null))
        assertFalse(can(access.copy(tripId = "trip-b")))
        assertFalse(can(access.copy(accountId = "someone-else")))
        assertFalse(can(access.copy(role = TripMemberRole.EDITOR)))
        assertFalse(can(access.copy(revoked = true)))
        assertFalse(can(access.copy(expiresAtMillis = now)))
        assertFalse(can(access.copy(verifiedAtMillis = now - 300_000)))
        assertFalse(can(access.copy(verifiedAtMillis = now + 1)))
        assertFalse(policy.canPresentAction("trip-a", null, access, TripMemberAction.INVITE, now))
    }

    @Test fun invitationsCannotGrantOwnershipOrTargetSelf() {
        val policy = TripMembershipPresentationPolicy
        assertTrue(policy.canPresentInvite("trip-a", "owner", access, "guest", TripMemberRole.EDITOR, now))
        assertFalse(policy.canPresentInvite("trip-a", "owner", access, "owner", TripMemberRole.VIEWER, now))
        assertFalse(policy.canPresentInvite("trip-a", "owner", access, "guest", TripMemberRole.OWNER, now))
        assertFalse(policy.canPresentInvite("trip-a", "editor", editor, "guest", TripMemberRole.VIEWER, now))
    }

    @Test fun onlyIntendedRecipientCanRespondToPendingUnexpiredInvitation() {
        val policy = TripMembershipPresentationPolicy
        fun can(value: TripInvitationView) =
            policy.canPresentInvitationResponse(value, "trip-a", "guest", now)
        assertTrue(can(invitation))
        assertFalse(can(invitation.copy(tripId = "trip-b")))
        assertFalse(can(invitation.copy(recipientAccountId = "someone-else")))
        assertFalse(can(invitation.copy(role = TripMemberRole.OWNER)))
        assertFalse(can(invitation.copy(expiresAtMillis = now)))
        TripInvitationStatus.entries.filter { it != TripInvitationStatus.PENDING }.forEach {
            assertFalse(can(invitation.copy(status = it)))
        }
        assertFalse(policy.canPresentInvitationResponse(invitation, "trip-a", null, now))
    }

    @Test fun ownerManagementDoesNotRemoveOrTransferOwnership() {
        val policy = TripMembershipPresentationPolicy
        assertTrue(policy.canPresentMemberManagement("trip-a", "owner", access, editor,
            TripMemberAction.CHANGE_ROLE, TripMemberRole.VIEWER, now))
        assertTrue(policy.canPresentMemberManagement("trip-a", "owner", access, editor,
            TripMemberAction.REMOVE_MEMBER, now = now))
        assertFalse(policy.canPresentMemberManagement("trip-a", "owner", access, access,
            TripMemberAction.REMOVE_MEMBER, now = now))
        assertFalse(policy.canPresentMemberManagement("trip-a", "owner", access, editor,
            TripMemberAction.CHANGE_ROLE, TripMemberRole.OWNER, now))
        assertFalse(policy.canPresentMemberManagement("trip-a", "owner", access, editor.copy(tripId = "other"),
            TripMemberAction.REMOVE_MEMBER, now = now))
    }

    @Test fun disabledGatewayNeverCreatesInvitesOrClaimsSync() = runBlocking {
        val gateway = DisabledTripCollaborationGateway()
        val actions: List<suspend () -> Any?> = listOf(
            { gateway.requestInvite("trip-a", "guest", TripMemberRole.VIEWER) },
            { gateway.revokeInvite("trip-a", "invite-a") },
            { gateway.acceptInvite("invite-a") },
            { gateway.declineInvite("invite-a") },
            { gateway.leaveTrip("trip-a") },
            { gateway.changeMemberRole("trip-a", "guest", TripMemberRole.EDITOR) },
            { gateway.revokeMember("trip-a", "guest") },
            { gateway.submitChange(TripSyncMutation("trip-a", "item-a", "itinerary", "mutation-a", 0, 1),
                byteArrayOf(1)) }
        )
        for (action in actions) {
            assertTrue(runCatching { action() }.exceptionOrNull() is TripCollaborationUnavailable)
        }
        assertEquals(4, TripSharingBoundary.reviewSections.size)
    }
}
