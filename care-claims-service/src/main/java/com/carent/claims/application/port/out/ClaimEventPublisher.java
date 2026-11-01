package com.carent.claims.application.port.out;

import com.carent.claims.domain.model.Claim;

/**
 * Outbound port for claim lifecycle events (specs/04 section 5). Implementations must not let a broker
 * failure surface to the caller: the claim is already stored when these methods are called.
 */
public interface ClaimEventPublisher {

    /** CLAIM_SUBMITTED: the claim has been persisted. */
    void claimSubmitted(Claim claim);

    /** CLAIM_ADJUDICATED: the claim reached {@code APPROVED} or {@code DENIED}. */
    void claimAdjudicated(Claim claim);
}
