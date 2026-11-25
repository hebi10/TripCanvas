package com.hebi.tripcanvas.domain.collaboration

/** Candidate sharing scope shown before any account/backend activation. */
enum class TripSharingSection(val label: String, val description: String) {
    TRIP_DETAILS("여행 기본 정보", "여행명, 기간, 목적지, 여행 설명"),
    ITINERARY("일정·장소", "등록한 일정, 장소, 주소와 좌표"),
    CHECKLIST("준비물", "준비물 목록과 완료 상태"),
    FINANCE("경비·정산", "지출 금액, 분류, 결제자, 동행자와 분담 내역")
}

object TripSharingBoundary {
    val reviewSections: List<TripSharingSection> = TripSharingSection.entries.toList()
    const val excludedDescription =
        "예약 확인번호·예약 첨부 문서·사진 원본·개인 메모의 공유 범위는 별도 확정이 필요합니다."

    /** Local settlement identities must not be replaced by account IDs. */
    fun validAccountLinks(localParticipantIds: Set<String>, links: Map<String, String>): Boolean =
        localParticipantIds.all(::validIdentifier) &&
            links.keys.all { it in localParticipantIds } &&
            links.values.all(::validIdentifier) &&
            links.values.size == links.values.toSet().size

    internal fun validIdentifier(value: String): Boolean =
        value.length in 1..128 && value == value.trim() && value.none { it.isISOControl() }
}

enum class TripInvitationStatus { PENDING, ACCEPTED, DECLINED, REVOKED, EXPIRED }

/**
 * Read model for a response from a future authenticated gateway.
 * These fields are not credentials and must never replace actual server authorization.
 */
data class TripMemberAccess(
    val tripId: String,
    val accountId: String,
    val role: TripMemberRole,
    val verifiedAtMillis: Long,
    val expiresAtMillis: Long,
    val revoked: Boolean = false
)

data class TripInvitationView(
    val invitationId: String,
    val tripId: String,
    val recipientAccountId: String,
    val role: TripMemberRole,
    val status: TripInvitationStatus,
    val expiresAtMillis: Long
)

object TripMembershipPresentationPolicy {
    private const val MAX_ACCESS_AGE_MILLIS = 5 * 60_000L

    fun canPresentAction(
        tripId: String,
        signedInAccountId: String?,
        access: TripMemberAccess?,
        action: TripMemberAction,
        now: Long
    ): Boolean {
        if (access == null || signedInAccountId == null || now <= 0L ||
            !TripSharingBoundary.validIdentifier(tripId) ||
            !TripSharingBoundary.validIdentifier(signedInAccountId) ||
            access.tripId != tripId || access.accountId != signedInAccountId ||
            access.revoked || access.verifiedAtMillis <= 0L ||
            access.verifiedAtMillis > now ||
            now - access.verifiedAtMillis >= MAX_ACCESS_AGE_MILLIS ||
            access.expiresAtMillis <= now
        ) return false
        return TripCollaborationPolicy.mayShowAction(access.role, action)
    }

    fun canPresentInvite(
        tripId: String,
        signedInAccountId: String?,
        access: TripMemberAccess?,
        inviteeAccountId: String,
        requestedRole: TripMemberRole,
        now: Long
    ): Boolean =
        canPresentAction(tripId, signedInAccountId, access, TripMemberAction.INVITE, now) &&
            TripSharingBoundary.validIdentifier(inviteeAccountId) &&
            inviteeAccountId != signedInAccountId &&
            requestedRole != TripMemberRole.OWNER

    fun canPresentInvitationResponse(
        invitation: TripInvitationView,
        expectedTripId: String,
        signedInAccountId: String?,
        now: Long
    ): Boolean =
        signedInAccountId != null && now > 0L &&
            TripSharingBoundary.validIdentifier(invitation.invitationId) &&
            TripSharingBoundary.validIdentifier(expectedTripId) &&
            TripSharingBoundary.validIdentifier(signedInAccountId) &&
            invitation.tripId == expectedTripId &&
            invitation.recipientAccountId == signedInAccountId &&
            invitation.status == TripInvitationStatus.PENDING &&
            invitation.role != TripMemberRole.OWNER &&
            invitation.expiresAtMillis > now

    fun canPresentMemberManagement(
        tripId: String,
        signedInAccountId: String?,
        access: TripMemberAccess?,
        target: TripMemberAccess,
        action: TripMemberAction,
        newRole: TripMemberRole? = null,
        now: Long
    ): Boolean {
        if (action != TripMemberAction.CHANGE_ROLE && action != TripMemberAction.REMOVE_MEMBER) return false
        if (!canPresentAction(tripId, signedInAccountId, access, action, now) ||
            target.tripId != tripId || target.accountId == signedInAccountId ||
            target.role == TripMemberRole.OWNER || target.revoked ||
            !TripSharingBoundary.validIdentifier(target.accountId)
        ) return false
        return when (action) {
            TripMemberAction.CHANGE_ROLE -> newRole != null && newRole != TripMemberRole.OWNER &&
                newRole != target.role
            TripMemberAction.REMOVE_MEMBER -> newRole == null
            else -> false
        }
    }
}
