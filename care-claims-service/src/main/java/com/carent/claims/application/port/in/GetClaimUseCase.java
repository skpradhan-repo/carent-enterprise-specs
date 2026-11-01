package com.carent.claims.application.port.in;

import com.carent.claims.domain.model.Claim;

public interface GetClaimUseCase {

    /**
     * @throws com.carent.claims.domain.exception.InvalidClaimException  if the reference is malformed
     * @throws com.carent.claims.domain.exception.ClaimNotFoundException if no such claim exists
     */
    Claim getByReference(String claimReference);
}
