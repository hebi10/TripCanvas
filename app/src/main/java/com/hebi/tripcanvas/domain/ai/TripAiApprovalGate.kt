package com.hebi.tripcanvas.domain.ai

data class TripAiChangeProposal(
    val tripId: String,
    val targetItemId: String,
    val existingRevision: Long,
    val proposedRevision: Long,
    val proposedTitle: String,
    val citedSource: String,
    val requestedAtMillis: Long
)

enum class TripAiApprovalState { NO_CONSENT, AWAITING_REVIEW, STALE_SOURCE, APPROVED }

/**
 * This gate only describes user approval of a proposal, not server truth.
 * It never changes a Room row; persistence must separately check revision.
 */
fun evaluateAiChange(
    consentedToExternalProcessing: Boolean,
    proposal: TripAiChangeProposal?,
    confirmedTargetIds: Set<String>,
    persistedRevision: Long
): TripAiApprovalState = when {
    !consentedToExternalProcessing -> TripAiApprovalState.NO_CONSENT
    proposal == null || proposal.targetItemId !in confirmedTargetIds ||
        proposal.citedSource.isBlank() || proposal.proposedTitle.isBlank() ->
        TripAiApprovalState.AWAITING_REVIEW
    persistedRevision != proposal.existingRevision || proposal.proposedRevision <= persistedRevision ->
        TripAiApprovalState.STALE_SOURCE
    else -> TripAiApprovalState.APPROVED
}
