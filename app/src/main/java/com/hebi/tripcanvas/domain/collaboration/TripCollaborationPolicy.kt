package com.hebi.tripcanvas.domain.collaboration

enum class TripMemberRole { OWNER, EDITOR, VIEWER }
enum class TripMemberAction { VIEW, EDIT, INVITE, CHANGE_ROLE, REMOVE_MEMBER, DELETE_TRIP }
enum class TripSyncStatus { LOCAL_ONLY, QUEUED, SYNCING, SYNCED, CONFLICT, REVOKED }

/**
 * Client-side affordance only. Every remote read/write still requires server authorization.
 */
object TripCollaborationPolicy {
    fun mayShowAction(role: TripMemberRole?, action: TripMemberAction): Boolean = when (role) {
        TripMemberRole.OWNER -> true
        TripMemberRole.EDITOR -> action == TripMemberAction.VIEW || action == TripMemberAction.EDIT
        TripMemberRole.VIEWER -> action == TripMemberAction.VIEW
        null -> false
    }

    /**
     * Without a freshly authorized server response a client must not mark any
     * shared edit synced, even if a previous local role was OWNER.
     */
    fun canClaimSynced(
        sameTrip: Boolean,
        sameAccount: Boolean,
        serverAuthorized: Boolean,
        localRevision: Long,
        acknowledgedRevision: Long,
        accessRevoked: Boolean
    ): Boolean = sameTrip && sameAccount && serverAuthorized && !accessRevoked &&
        localRevision >= 0L && acknowledgedRevision == localRevision

    fun preservesLocalParticipantId(
        originalLocalParticipantId: String,
        linkedLocalParticipantId: String
    ): Boolean = originalLocalParticipantId.isNotBlank() &&
        originalLocalParticipantId == linkedLocalParticipantId
}

data class TripSyncMutation(
    val tripId: String,
    val entityId: String,
    val entityType: String,
    val mutationId: String,
    val baseServerRevision: Long,
    val localRevision: Long
) {
    init {
        require(tripId.isNotBlank() && entityId.isNotBlank() && entityType.isNotBlank())
        require(mutationId.isNotBlank() && baseServerRevision >= 0L && localRevision >= 0L)
    }
}

sealed interface TripSyncDecision {
    data object ServerAuthorizationRequired : TripSyncDecision
    data object AccessRevoked : TripSyncDecision
    data class Conflict(val localRevision: Long, val serverRevision: Long) : TripSyncDecision
    data object ReadyToSubmit : TripSyncDecision
}

fun evaluateTripSync(
    mutation: TripSyncMutation,
    hasOnlineAuthenticatedSession: Boolean,
    serverVerifiedPermission: Boolean,
    serverRevision: Long,
    accessRevoked: Boolean
): TripSyncDecision {
    if (accessRevoked) return TripSyncDecision.AccessRevoked
    if (!hasOnlineAuthenticatedSession || !serverVerifiedPermission || serverRevision < 0L) {
        return TripSyncDecision.ServerAuthorizationRequired
    }
    if (mutation.baseServerRevision != serverRevision) {
        return TripSyncDecision.Conflict(mutation.localRevision, serverRevision)
    }
    return TripSyncDecision.ReadyToSubmit
}

/** Never includes raw reservation references, photos, expenses or user text. */
data class SafeTripChangeSummary(
    val tripId: String,
    val entityId: String,
    val entityType: String,
    val operation: String,
    val editorAccountId: String,
    val changedAtMillis: Long
)
