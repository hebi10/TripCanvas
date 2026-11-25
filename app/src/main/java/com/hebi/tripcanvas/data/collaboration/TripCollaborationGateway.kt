package com.hebi.tripcanvas.data.collaboration

import com.hebi.tripcanvas.domain.collaboration.TripMemberRole
import com.hebi.tripcanvas.domain.collaboration.TripSyncMutation

/**
 * Provider-neutral server contract for COLLAB-01/02. No implementation is
 * installed until the user approves a backend, auth provider and data policy.
 *
 * Server must check identity, trip membership and revision atomically on each
 * call. A local role, cached invitation or hidden button cannot authorize writes.
 */
interface TripCollaborationGateway {
    suspend fun requestInvite(tripId: String, inviteeAccountId: String, role: TripMemberRole): String
    suspend fun revokeInvite(tripId: String, inviteId: String)
    suspend fun acceptInvite(inviteId: String)
    suspend fun declineInvite(inviteId: String)
    suspend fun leaveTrip(tripId: String)
    suspend fun changeMemberRole(tripId: String, memberAccountId: String, role: TripMemberRole)
    suspend fun revokeMember(tripId: String, memberAccountId: String)
    suspend fun submitChange(mutation: TripSyncMutation, encryptedPayload: ByteArray): Long
}

class TripCollaborationUnavailable : IllegalStateException(
    "공동 여행 서버가 연결되지 않았습니다. 로컬 여행과 정산은 계속 사용할 수 있습니다."
)

/** Fail closed rather than create pretend invites or mark local edits synchronized. */
class DisabledTripCollaborationGateway : TripCollaborationGateway {
    override suspend fun requestInvite(tripId: String, inviteeAccountId: String, role: TripMemberRole): String =
        throw TripCollaborationUnavailable()
    override suspend fun revokeInvite(tripId: String, inviteId: String): Unit =
        throw TripCollaborationUnavailable()
    override suspend fun acceptInvite(inviteId: String): Unit =
        throw TripCollaborationUnavailable()
    override suspend fun declineInvite(inviteId: String): Unit =
        throw TripCollaborationUnavailable()
    override suspend fun leaveTrip(tripId: String): Unit =
        throw TripCollaborationUnavailable()
    override suspend fun changeMemberRole(tripId: String, memberAccountId: String, role: TripMemberRole): Unit =
        throw TripCollaborationUnavailable()
    override suspend fun revokeMember(tripId: String, memberAccountId: String): Unit =
        throw TripCollaborationUnavailable()
    override suspend fun submitChange(mutation: TripSyncMutation, encryptedPayload: ByteArray): Long =
        throw TripCollaborationUnavailable()
}
