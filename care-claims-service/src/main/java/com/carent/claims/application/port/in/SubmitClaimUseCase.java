package com.carent.claims.application.port.in;

import com.carent.claims.domain.model.Claim;

public interface SubmitClaimUseCase {

    /** Validates, adjudicates and stores a new claim, returning it in its final status. */
    Claim submit(SubmitClaimCommand command);
}
